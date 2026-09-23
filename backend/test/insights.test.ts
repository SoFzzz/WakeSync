import { describe, it, expect, beforeEach, vi } from 'vitest';
import worker from '../src/index';
import { Env } from '../src/types';
import { resetRateLimits } from '../src/utils';
import { FALLBACK_INSIGHT } from '../src/insights';

const mockEnv: Env = {
  GEMINI_MODEL: 'gemini-3.1-flash-lite',
  MAPBOX_ACCESS_TOKEN: 'test-mapbox-token',
  GEMINI_API_KEY: 'test-gemini-key',
  APP_TOKEN: 'correct-secret-token-32bytes',
};

describe('WakeSync Gateway - AI Insights with Gemini', () => {
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
    const fakeGeminiResponse = {
      candidates: [
        {
          content: {
            parts: [
              {
                text: 'Tardaste 4 min en relajarte y completaste tu siesta con exito. Buen descanso para continuar tu dia.',
              },
            ],
          },
        },
      ],
    };

    vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(
      new Response(JSON.stringify(fakeGeminiResponse), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      })
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

    const fakeGeminiResponse = {
      candidates: [
        {
          content: {
            parts: [{ text: overlyVerboseText }],
          },
        },
      ],
    };

    vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(
      new Response(JSON.stringify(fakeGeminiResponse), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      })
    );

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

  it('POST /v1/insights falls back to gemini-3.1-flash-lite when GEMINI_MODEL is not set', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(
      new Response(JSON.stringify({ candidates: [{ content: { parts: [{ text: 'Buen viaje.' }] } }] }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      })
    );

    await worker.fetch(insightRequest(), { ...mockEnv, GEMINI_MODEL: '' });
    expect(String(fetchSpy.mock.calls[0][0])).toContain('/models/gemini-3.1-flash-lite:generateContent');
  });

  it('POST /v1/insights calls gemini-3.1-flash-lite with minimal thinking and 256 output tokens', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(
      new Response(JSON.stringify({ candidates: [{ content: { parts: [{ text: 'Buen viaje.' }] } }] }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      })
    );

    const res = await worker.fetch(insightRequest(), mockEnv);
    expect(res.status).toBe(200);

    const [calledUrl, init] = fetchSpy.mock.calls[0];
    expect(String(calledUrl)).toContain('/models/gemini-3.1-flash-lite:generateContent');
    const sentBody = JSON.parse(String((init as RequestInit).body));
    expect(sentBody.generationConfig.thinkingConfig).toEqual({ thinkingLevel: 'minimal' });
    expect(sentBody.generationConfig.maxOutputTokens).toBe(256);
  });

  it('POST /v1/insights logs finishReason without content and falls back when Gemini text is empty', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(
      new Response(
        JSON.stringify({ candidates: [{ content: { parts: [{ text: '' }] }, finishReason: 'MAX_TOKENS' }] }),
        { status: 200, headers: { 'Content-Type': 'application/json' } }
      )
    );
    const warnSpy = vi.spyOn(console, 'warn').mockImplementation(() => {});

    const res = await worker.fetch(insightRequest(), mockEnv);
    expect(res.status).toBe(200);

    const body: any = await res.json();
    expect(body.insight).toBe(FALLBACK_INSIGHT);
    expect(warnSpy).toHaveBeenCalledTimes(1);
    const logged = String(warnSpy.mock.calls[0][0]);
    expect(logged).toContain('finishReason=MAX_TOKENS');
    expect(logged).not.toContain('TRANSIT');
    expect(logged).not.toContain('600');
  });

  it('POST /v1/insights falls back when Gemini returns no candidates', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(
      new Response(JSON.stringify({}), { status: 200, headers: { 'Content-Type': 'application/json' } })
    );
    const warnSpy = vi.spyOn(console, 'warn').mockImplementation(() => {});

    const res = await worker.fetch(insightRequest(), mockEnv);
    const body: any = await res.json();
    expect(body.insight).toBe(FALLBACK_INSIGHT);
    expect(String(warnSpy.mock.calls[0][0])).toContain('finishReason=UNKNOWN');
  });

  it('POST /v1/insights does not echo the upstream error body', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(
      new Response('{"error":{"message":"API key not valid secret-detail"}}', { status: 400 })
    );

    const res = await worker.fetch(insightRequest(), mockEnv);
    expect(res.status).toBe(502);
    const text = await res.text();
    expect(text).toContain('400');
    expect(text).not.toContain('secret-detail');
  });
});
