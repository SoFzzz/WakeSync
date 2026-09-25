import { Env, ErrorResponse } from './types';
import { timingSafeEqual, checkRateLimit, logSanitized } from './utils';
import { handlePlacesAutocomplete, handlePlaceDetails } from './places';
import { handleReverseGeocode } from './geocode';
import { handleMapsStatic } from './maps';
import { handleInsights } from './insights';

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const startTime = performance.now();
    const url = new URL(request.url);
    const pathname = url.pathname.replace(/\/+$/, '') || '/';
    const method = request.method.toUpperCase();

    let response: Response;

    try {
      // 1. Health check (Only public route - Appendix F.2)
      if (method === 'GET' && pathname === '/v1/health') {
        response = new Response(JSON.stringify({ status: 'ok' }), {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        });
        logSanitized(method, pathname, response.status, performance.now() - startTime);
        return response;
      }

      // 2. Authentication: Validate X-WakeSync-App-Token via timing-safe comparison (RF-BE-03)
      const appToken = request.headers.get('X-WakeSync-App-Token') || '';
      if (!timingSafeEqual(appToken, env.APP_TOKEN || '')) {
        response = jsonError('unauthorized', 'Missing or invalid app token', 401);
        logSanitized(method, pathname, response.status, performance.now() - startTime);
        return response;
      }

      // 3. Rate limiting: 60 requests/minute per client IP (RF-BE-04)
      const clientIp = request.headers.get('cf-connecting-ip') || '127.0.0.1';
      if (!checkRateLimit(clientIp, 60, 60000)) {
        response = jsonError('rate_limited', 'Too many requests. Limit is 60 requests per minute.', 429);
        logSanitized(method, pathname, response.status, performance.now() - startTime);
        return response;
      }

      // 4. Router dispatch
      if (method === 'POST' && pathname === '/v1/places/autocomplete') {
        response = await handlePlacesAutocomplete(request, env);
      } else if (method === 'GET' && pathname.startsWith('/v1/places/')) {
        const placeId = decodeURIComponent(pathname.substring('/v1/places/'.length));
        response = await handlePlaceDetails(placeId, url.searchParams.get('sessionToken'), env);
      } else if (method === 'GET' && pathname === '/v1/geocode/reverse') {
        response = await handleReverseGeocode(
          url.searchParams.get('lat'),
          url.searchParams.get('lng'),
          env
        );
      } else if (method === 'GET' && pathname === '/v1/maps/static') {
        response = await handleMapsStatic(
          url.searchParams.get('lat'),
          url.searchParams.get('lng'),
          url.searchParams.get('zoom'),
          url.searchParams.get('size'),
          env
        );
      } else if (method === 'POST' && pathname === '/v1/insights') {
        response = await handleInsights(request, env);
      } else {
        response = jsonError('invalid_request', `Endpoint not found: ${method} ${pathname}`, 404);
      }
    } catch (err: any) {
      console.error('[WakeSyncGateway] Unexpected error processing request:', err);
      response = jsonError('upstream_error', 'Internal server error', 500);
    }

    const latencyMs = performance.now() - startTime;
    logSanitized(method, pathname, response.status, latencyMs);
    return response;
  },
};

function jsonError(error: ErrorResponse['error'], message: string, status: number): Response {
  const body: ErrorResponse = { error, message };
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}
