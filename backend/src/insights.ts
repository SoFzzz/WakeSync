import { Env, InsightRequest, InsightResponse, ErrorResponse } from './types';
import { upstreamFetch, UpstreamTimeoutError } from './utils';

const DEEPSEEK_TIMEOUT_MS = 8000; // 8 seconds (RF-BE-01, Appendix F)
const DEEPSEEK_CHAT_URL = 'https://api.deepseek.com/chat/completions';
const DEFAULT_DEEPSEEK_MODEL = 'deepseek-flash';
const MAX_INSIGHT_CHARS = 140;
export const FALLBACK_INSIGHT = 'Sesión completada satisfactoriamente.';

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

  // OpenAI-compatible Chat Completions body (api-docs.deepseek.com, "Create Chat Completion")
  const deepseekPayload = {
    model: env.DEEPSEEK_MODEL || DEFAULT_DEEPSEEK_MODEL,
    messages: [
      { role: 'system', content: SYSTEM_PROMPT },
      { role: 'user', content: userText },
    ],
    // Thinking is on by default for deepseek-flash; disabling it keeps latency under the 8 s
    // budget and makes temperature effective (it is ignored in thinking mode).
    thinking: { type: 'disabled' },
    // ~140 Spanish characters fit in well under 120 tokens; low ceiling bounds the cost per call
    max_tokens: 120,
    temperature: 0.3,
  };

  try {
    const upstreamRes = await upstreamFetch(
      DEEPSEEK_CHAT_URL,
      {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${env.DEEPSEEK_API_KEY}`,
        },
        body: JSON.stringify(deepseekPayload),
      },
      DEEPSEEK_TIMEOUT_MS
    );

    if (!upstreamRes.ok) {
      // Only the upstream status code is logged: never the body, which may echo request data (RNF-BE-02)
      console.warn(`[WakeSyncGateway] DeepSeek upstream status=${upstreamRes.status}`);
      return jsonError('upstream_error', `DeepSeek API error: ${upstreamRes.status}`, 502);
    }

    const data: any = await upstreamRes.json();
    const choice = data.choices?.[0];
    // Only the final answer is used; reasoning_content (thinking mode) is never read
    const rawInsight: string = typeof choice?.message?.content === 'string' ? choice.message.content : '';

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
      // Surface the silent fallback without logging any model content (RNF-BE-02)
      console.warn(
        `[WakeSyncGateway] Empty DeepSeek insight, using fallback text (finish_reason=${
          choice?.finish_reason ?? 'UNKNOWN'
        })`
      );
      cleanInsight = FALLBACK_INSIGHT;
    }

    const responseBody: InsightResponse = { insight: cleanInsight };
    return new Response(JSON.stringify(responseBody), {
      status: 200,
      headers: { 'Content-Type': 'application/json' },
    });
  } catch (err: unknown) {
    if (err instanceof UpstreamTimeoutError) {
      return jsonError('upstream_timeout', 'DeepSeek API timed out', 504);
    }
    return jsonError('upstream_error', 'DeepSeek API request failed', 502);
  }
}

function jsonError(error: ErrorResponse['error'], message: string, status: number): Response {
  const body: ErrorResponse = { error, message };
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}
