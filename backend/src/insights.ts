import { Env, InsightRequest, InsightResponse, ErrorResponse } from './types';
import { upstreamFetch, UpstreamTimeoutError } from './utils';

const GEMINI_TIMEOUT_MS = 8000; // 8 seconds (RF-BE-01, Appendix F)
const MAX_INSIGHT_CHARS = 140;

const SYSTEM_PROMPT =
  'Eres el asistente de bienestar de WakeSync, una app de smartwatch. Recibes el resumen agregado de una sesión de siesta (NAP) o de viaje en transporte (TRANSIT). Responde en español con una sola frase amable y práctica de máximo 140 caracteres. No des diagnósticos médicos ni menciones trastornos del sueño. No uses emojis ni formato markdown.';

const VALID_SESSION_TYPES = new Set(['NAP', 'TRANSIT']);
const VALID_OUTCOMES = new Set(['COMPLETED', 'DISMISSED', 'TIMED_OUT', 'CANCELLED']);
const EXACT_KEYS = ['durationSeconds', 'outcome', 'restLatencySeconds', 'sessionType'];

/**
 * Handler for POST /v1/insights (RF-INS-01, RF-INS-04, Appendix F.3).
 */
export async function handleInsights(request: Request, env: Env): Promise<Response> {
  let body: any;
  try {
    body = await request.json();
  } catch {
    return jsonError('invalid_request', 'Malformed JSON body', 400);
  }

  if (typeof body !== 'object' || body === null || Array.isArray(body)) {
    return jsonError('invalid_request', 'Request body must be a JSON object', 400);
  }

  // Strict verification of EXACTLY 4 keys to guarantee strict privacy (RNF-INS-01, RF-BE-05)
  const keys = Object.keys(body).sort();
  if (keys.length !== 4 || keys.some((k, i) => k !== EXACT_KEYS[i])) {
    return jsonError(
      'invalid_request',
      'Body must contain strictly the 4 aggregated fields: sessionType, durationSeconds, restLatencySeconds, outcome',
      400
    );
  }

  const { sessionType, durationSeconds, restLatencySeconds, outcome } = body as InsightRequest;

  if (!VALID_SESSION_TYPES.has(sessionType)) {
    return jsonError('invalid_request', 'sessionType must be either "NAP" or "TRANSIT"', 400);
  }

  if (!VALID_OUTCOMES.has(outcome)) {
    return jsonError(
      'invalid_request',
      'outcome must be one of: COMPLETED, DISMISSED, TIMED_OUT, CANCELLED',
      400
    );
  }

  if (typeof durationSeconds !== 'number' || durationSeconds < 0 || !Number.isInteger(durationSeconds)) {
    return jsonError('invalid_request', 'durationSeconds must be a non-negative integer', 400);
  }

  if (
    restLatencySeconds !== null &&
    (typeof restLatencySeconds !== 'number' || restLatencySeconds < 0 || !Number.isInteger(restLatencySeconds))
  ) {
    return jsonError('invalid_request', 'restLatencySeconds must be a non-negative integer or null', 400);
  }

  const userText = `Tipo de sesión: ${sessionType}, Duración: ${durationSeconds} segundos, Latencia de reposo: ${
    restLatencySeconds !== null ? `${restLatencySeconds} segundos` : 'No registrada'
  }, Resultado: ${outcome}.`;

  const geminiPayload = {
    systemInstruction: {
      parts: [{ text: SYSTEM_PROMPT }],
    },
    contents: [
      {
        parts: [{ text: userText }],
      },
    ],
    generationConfig: {
      maxOutputTokens: 100,
      temperature: 0.7,
    },
  };

  const model = env.GEMINI_MODEL || 'gemini-1.5-flash';
  const url = `https://generativelanguage.googleapis.com/v1beta/models/${encodeURIComponent(
    model
  )}:generateContent`;

  try {
    const upstreamRes = await upstreamFetch(
      url,
      {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'x-goog-api-key': env.GEMINI_API_KEY,
        },
        body: JSON.stringify(geminiPayload),
      },
      GEMINI_TIMEOUT_MS
    );

    if (!upstreamRes.ok) {
      const errText = await upstreamRes.text().catch(() => '');
      return jsonError('upstream_error', `Gemini API error: ${upstreamRes.status} ${errText}`, 502);
    }

    const data: any = await upstreamRes.json();
    let rawInsight = data.candidates?.[0]?.content?.parts?.[0]?.text || '';

    // Sanitization: strip markdown, newlines, and trim
    let cleanInsight = rawInsight
      .replace(/[*_#`~]/g, '')
      .replace(/\r?\n|\r/g, ' ')
      .trim();

    // Defense-in-depth: clamp to maximum 140 chars
    if (cleanInsight.length > MAX_INSIGHT_CHARS) {
      cleanInsight = cleanInsight.slice(0, MAX_INSIGHT_CHARS).trim();
    }

    if (!cleanInsight) {
      cleanInsight = 'Sesión completada satisfactoriamente.';
    }

    const responseBody: InsightResponse = { insight: cleanInsight };
    return new Response(JSON.stringify(responseBody), {
      status: 200,
      headers: { 'Content-Type': 'application/json' },
    });
  } catch (err: any) {
    if (err instanceof UpstreamTimeoutError) {
      return jsonError('upstream_timeout', 'Gemini API timed out', 504);
    }
    return jsonError('upstream_error', err.message || 'Unknown upstream error', 502);
  }
}

function jsonError(error: ErrorResponse['error'], message: string, status: number): Response {
  const body: ErrorResponse = { error, message };
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}
