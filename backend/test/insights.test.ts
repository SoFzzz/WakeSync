import { describe, it, expect, beforeEach, vi } from 'vitest';
import worker from '../src/index';
import { Env } from '../src/types';
import { resetRateLimits } from '../src/utils';
import { FALLBACK_INSIGHT } from '../src/insights';

const mockEnv: Env = {
  DEEPSEEK_MODEL: 'deepseek-flash',
  MAPBOX_ACCESS_TOKEN: 'test-mapbox-token',
  DEEPSEEK_API_KEY: 'test-deepseek-key',
  APP_TOKEN: 'correct-secret-token-32bytes',
};

/** Builds an OpenAI-format Chat Completions response as returned by DeepSeek. */
function deepseekResponse(content: string | null, finishReason = 'stop'): Response {
  return new Response(
    JSON.stringify({ choices: [{ index: 0, message: { role: 'assistant', content }, finish_reason: finishReason }] }),
    { status: 200, headers: { 'Content-Type': 'application/json' } }
  );
}

describe('WakeSync Gateway - AI Insights with DeepSeek', () => {
  beforeEach(() => {
    resetRateLimits();
    vi.restoreAllMocks();
  });

  it('POST /v1/insights rejects payloads containing extra fields like heartRate or coords (400 invalid_request)', async () => {
    const forbiddenPayload = {
      sessionType: 'NAP',
      durationSeconds: 1200,
      restLatencySeconds: 240,
      outcome: 'COMPLETED',
      heartRate: 65, // FORBIDDEN!
    };

    const req = new Request('http://localhost/v1/insights', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-WakeSync-App-Token': mockEnv.APP_TOKEN,
      },
      body: JSON.stringify(forbiddenPayload),
    });

    const res = await worker.fetch(req, mockEnv);
    expect(res.status).toBe(400);

    const body: any = await res.json();
    expect(body.error).toBe('invalid_request');
  });

  it('POST /v1/insights rejects payloads missing required fields (400)', async () => {
    const incompletePayload = {
      sessionType: 'NAP',
      durationSeconds: 1200,
      // missing restLatencySeconds and outcome
    };

    const req = new Request('http://localhost/v1/insights', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-WakeSync-App-Token': mockEnv.APP_TOKEN,
      },
      body: JSON.stringify(incompletePayload),
    });

    const res = await worker.fetch(req, mockEnv);
    expect(res.status).toBe(400);
  });

  it('POST /v1/insights accepts valid 4 aggregated fields and returns clamped insight (<= 140 chars)', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(
      deepseekResponse('Tardaste 4 min en relajarte y completaste tu siesta con exito. Buen descanso para continuar tu dia.')
    );

    const validPayload = {
      sessionType: 'NAP',
      durationSeconds: 1320,
      restLatencySeconds: 240,
      outcome: 'COMPLETED',
    };

    const req = new Request('http://localhost/v1/insights', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-WakeSync-App-Token': mockEnv.APP_TOKEN,
      },
      body: JSON.stringify(validPayload),
    });

    const res = await worker.fetch(req, mockEnv);
    expect(res.status).toBe(200);

    const body: any = await res.json();
    expect(body.insight).toBeDefined();
    expect(body.insight.length).toBeLessThanOrEqual(140);
    expect(body.insight).toContain('Tardaste 4 min');
  });

  it('POST /v1/insights clamps overly verbose responses to 140 chars max', async () => {
    const overlyVerboseText =
      'Esta es una respuesta extremadamente larga generada por el modelo que supera con creces los ciento cuarenta caracteres que estan normativamente definidos para el reloj inteligente Wear OS porque la pantalla es pequena y no cabe tanto texto.';

    vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(deepseekResponse(overlyVerboseText));

    const req = new Request('http://localhost/v1/insights', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-WakeSync-App-Token': mockEnv.APP_TOKEN,
      },
      body: JSON.stringify({
        sessionType: 'TRANSIT',
        durationSeconds: 1800,
        restLatencySeconds: null,
        outcome: 'COMPLETED',
      }),
    });

    const res = await worker.fetch(req, mockEnv);
    expect(res.status).toBe(200);

    const body: any = await res.json();
    expect(body.insight.length).toBeLessThanOrEqual(140);
  });
  const validTransitPayload = {
    sessionType: 'TRANSIT',
    durationSeconds: 600,
    restLatencySeconds: null,
    outcome: 'DISMISSED',
  };

  function insightRequest(): Request {
    return new Request('http://localhost/v1/insights', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-WakeSync-App-Token': mockEnv.APP_TOKEN,
      },
      body: JSON.stringify(validTransitPayload),
    });
  }

  it('POST /v1/insights falls back to deepseek-flash when DEEPSEEK_MODEL is not set', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(deepseekResponse('Buen viaje.'));

    await worker.fetch(insightRequest(), { ...mockEnv, DEEPSEEK_MODEL: '' });
    const sentBody = JSON.parse(String((fetchSpy.mock.calls[0][1] as RequestInit).body));
    expect(sentBody.model).toBe('deepseek-flash');
  });

  it('POST /v1/insights calls DeepSeek chat completions with Bearer auth, thinking disabled and 120 max tokens', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(deepseekResponse('Buen viaje.'));

    const res = await worker.fetch(insightRequest(), mockEnv);
    expect(res.status).toBe(200);

    const [calledUrl, init] = fetchSpy.mock.calls[0];
    expect(String(calledUrl)).toBe('https://api.deepseek.com/chat/completions');
    const headers = (init as RequestInit).headers as Record<string, string>;
    expect(headers.Authorization).toBe('Bearer test-deepseek-key');
    const sentBody = JSON.parse(String((init as RequestInit).body));
    expect(sentBody.model).toBe('deepseek-flash');
    expect(sentBody.thinking).toEqual({ type: 'disabled' });
    expect(sentBody.max_tokens).toBe(120);
    expect(sentBody.temperature).toBe(0.3);
    expect(sentBody.messages[0].role).toBe('system');
    expect(sentBody.messages[1]).toEqual({
      role: 'user',
      content: 'Tipo de sesión: TRANSIT, Duración: 600 segundos, Latencia de reposo: No registrada, Resultado: DISMISSED.',
    });
  });

  it('POST /v1/insights logs finish_reason without content and falls back when DeepSeek text is empty', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(deepseekResponse('', 'length'));
    const warnSpy = vi.spyOn(console, 'warn').mockImplementation(() => {});

    const res = await worker.fetch(insightRequest(), mockEnv);
    expect(res.status).toBe(200);

    const body: any = await res.json();
    expect(body.insight).toBe(FALLBACK_INSIGHT);
    expect(warnSpy).toHaveBeenCalledTimes(1);
    const logged = String(warnSpy.mock.calls[0][0]);
    expect(logged).toContain('finish_reason=length');
    expect(logged).not.toContain('TRANSIT');
    expect(logged).not.toContain('600');
  });

  it('POST /v1/insights falls back when DeepSeek returns no choices', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(
      new Response(JSON.stringify({}), { status: 200, headers: { 'Content-Type': 'application/json' } })
    );
    const warnSpy = vi.spyOn(console, 'warn').mockImplementation(() => {});

    const res = await worker.fetch(insightRequest(), mockEnv);
    const body: any = await res.json();
    expect(body.insight).toBe(FALLBACK_INSIGHT);
    expect(String(warnSpy.mock.calls[0][0])).toContain('finish_reason=UNKNOWN');
  });

  it('POST /v1/insights never returns reasoning_content and falls back when content is null', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(
      new Response(
        JSON.stringify({
          choices: [
            {
              message: { role: 'assistant', content: null, reasoning_content: 'razonamiento interno' },
              finish_reason: 'length',
            },
          ],
        }),
        { status: 200, headers: { 'Content-Type': 'application/json' } }
      )
    );
    vi.spyOn(console, 'warn').mockImplementation(() => {});

    const res = await worker.fetch(insightRequest(), mockEnv);
    const body: any = await res.json();
    expect(body.insight).toBe(FALLBACK_INSIGHT);
    expect(JSON.stringify(body)).not.toContain('razonamiento');
  });

  it('POST /v1/insights maps a DeepSeek 402 to 502 and logs only the upstream status', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(
      new Response('{"error":{"message":"Insufficient Balance secret-detail","type":"billing"}}', { status: 402 })
    );
    const warnSpy = vi.spyOn(console, 'warn').mockImplementation(() => {});

    const res = await worker.fetch(insightRequest(), mockEnv);
    expect(res.status).toBe(502);
    const body: any = await res.json();
    expect(body.error).toBe('upstream_error');
    expect(body.insight).toBeUndefined();
    expect(JSON.stringify(body)).not.toContain('secret-detail');

    expect(warnSpy).toHaveBeenCalledTimes(1);
    const logged = String(warnSpy.mock.calls[0][0]);
    expect(logged).toBe('[WakeSyncGateway] DeepSeek upstream status=402');
    expect(logged).not.toContain('Insufficient');
    expect(logged).not.toContain('TRANSIT');
  });

  it('POST /v1/insights does not echo the upstream error body', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(
      new Response('{"error":{"message":"Authentication Fails secret-detail"}}', { status: 401 })
    );
    vi.spyOn(console, 'warn').mockImplementation(() => {});

    const res = await worker.fetch(insightRequest(), mockEnv);
    expect(res.status).toBe(502);
    const text = await res.text();
    expect(text).toContain('401');
    expect(text).not.toContain('secret-detail');
  });
});
