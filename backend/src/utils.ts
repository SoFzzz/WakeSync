/**
 * Utility functions for WakeSync Gateway:
 * - Constant-time token comparison (mitigating timing attacks)
 * - In-memory IP rate limiter (60 req/min)
 * - Sanitized request logging (method, path, status, latency only - RNF-BE-02)
 * - Upstream fetch with strict AbortController timeouts (4s Mapbox / 8s DeepSeek)
 */

/** Timeout for map provider calls (Mapbox Search Box, Geocoding and Static Images APIs). */
export const UPSTREAM_TIMEOUT_MS = 4000;

/** Base URL of the Mapbox REST APIs. */
export const MAPBOX_API_BASE = 'https://api.mapbox.com';

export class UpstreamTimeoutError extends Error {
  constructor(message = 'Upstream service timeout') {
    super(message);
    this.name = 'UpstreamTimeoutError';
  }
}

export class UpstreamHttpError extends Error {
  status: number;
  statusText: string;

  constructor(status: number, statusText: string, message?: string) {
    super(message ?? `Upstream returned HTTP ${status}: ${statusText}`);
    this.name = 'UpstreamHttpError';
    this.status = status;
    this.statusText = statusText;
  }
}

/**
 * Constant-time comparison between two strings to protect against timing attacks.
 */
export function timingSafeEqual(a: string, b: string): boolean {
  if (typeof a !== 'string' || typeof b !== 'string') {
    return false;
  }

  const enc = new TextEncoder();
  const bufA = enc.encode(a);
  const bufB = enc.encode(b);

  if (bufA.byteLength !== bufB.byteLength) {
    return false;
  }

  let mismatch = 0;
  for (let i = 0; i < bufA.byteLength; i++) {
    mismatch |= bufA[i] ^ bufB[i];
  }

  return mismatch === 0;
}

/**
 * In-memory sliding window rate limiter (60 req/min per IP).
 * RFC/Appendix F.1 compliant.
 */
interface RateLimitBucket {
  count: number;
  resetAt: number;
}

const rateLimitMap = new Map<string, RateLimitBucket>();

export function checkRateLimit(clientIp: string, limit = 60, windowMs = 60000): boolean {
  const now = Date.now();
  const bucket = rateLimitMap.get(clientIp);

  if (!bucket || now >= bucket.resetAt) {
    rateLimitMap.set(clientIp, { count: 1, resetAt: now + windowMs });
    return true;
  }

  if (bucket.count >= limit) {
    return false;
  }

  bucket.count++;
  return true;
}

/**
 * Reset rate limit map (useful for isolated unit testing).
 */
export function resetRateLimits(): void {
  rateLimitMap.clear();
}

/**
 * Sanitized logger meeting RNF-BE-02:
 * Logs ONLY method, path, HTTP status, and latency.
 * NEVER logs search queries, coordinates, payloads, headers, or biometric aggregates.
 */
export function logSanitized(method: string, path: string, status: number, latencyMs: number): void {
  const sanitizedPath = toLogPath(path);
  console.log(`[WakeSyncGateway] ${method} ${sanitizedPath} - ${status} (${latencyMs.toFixed(1)}ms)`);
}

/**
 * Reduces a request path to a loggable route template: strips the query string and replaces
 * the place identifier, which reveals the place the user chose, with `{placeId}`.
 */
export function toLogPath(path: string): string {
  const withoutQuery = path.split('?')[0];
  if (withoutQuery.startsWith('/v1/places/') && withoutQuery !== '/v1/places/autocomplete') {
    return '/v1/places/{placeId}';
  }
  return withoutQuery;
}

/**
 * Dispatches an upstream fetch request with an enforced timeout via AbortController.
 */
export async function upstreamFetch(
  url: string,
  options: RequestInit,
  timeoutMs: number
): Promise<Response> {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);

  try {
    const response = await fetch(url, {
      ...options,
      signal: controller.signal,
    });
    return response;
  } catch (error: any) {
    if (error.name === 'AbortError' || controller.signal.aborted) {
      throw new UpstreamTimeoutError(`Upstream call timed out after ${timeoutMs}ms`);
    }
    throw error;
  } finally {
    clearTimeout(timer);
  }
}
