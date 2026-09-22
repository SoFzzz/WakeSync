import { Env, ErrorResponse } from './types';
import { upstreamFetch, UpstreamTimeoutError } from './utils';

const GOOGLE_TIMEOUT_MS = 4000; // 4 seconds (RF-BE-01, Appendix F)

/**
 * Handler for GET /v1/maps/static?lat=...&lng=...&zoom=...&size=... (RF-PLC-03, Appendix F.3).
 */
export async function handleMapsStatic(
  latStr: string | null,
  lngStr: string | null,
  zoomStr: string | null,
  sizeStr: string | null,
  env: Env
): Promise<Response> {
  if (!latStr || !lngStr || !zoomStr || !sizeStr) {
    return jsonError('invalid_request', 'lat, lng, zoom, and size query parameters are required', 400);
  }

  const lat = parseFloat(latStr);
  const lng = parseFloat(lngStr);
  const zoom = parseInt(zoomStr, 10);
  const size = parseInt(sizeStr, 10);

  // Validate coordinates
  if (isNaN(lat) || isNaN(lng) || lat < -90 || lat > 90 || lng < -180 || lng > 180) {
    return jsonError('invalid_request', 'lat must be in [-90, 90] and lng in [-180, 180]', 400);
  }

  // Validate zoom in [10, 19] (RF-BE-05, MAP_MIN_ZOOM to MAP_MAX_ZOOM)
  if (isNaN(zoom) || zoom < 10 || zoom > 19) {
    return jsonError('invalid_request', 'zoom must be an integer between 10 and 19', 400);
  }

  // Validate size in [100, 320] (RF-BE-05, Appendix F.3)
  if (isNaN(size) || size < 100 || size > 320) {
    return jsonError('invalid_request', 'size must be an integer between 100 and 320', 400);
  }

  // Google Maps Static API request: scale=2 gives high-DPI (logical px * 2 = physical px)
  // No markers requested: the Wear OS app renders its own stationary center pin overlay
  const url = `https://maps.googleapis.com/maps/api/staticmap?center=${lat},${lng}&zoom=${zoom}&size=${size}x${size}&scale=2&maptype=roadmap&language=es&key=${encodeURIComponent(
    env.GOOGLE_MAPS_API_KEY
  )}`;

  try {
    const upstreamRes = await upstreamFetch(url, { method: 'GET' }, GOOGLE_TIMEOUT_MS);

    if (!upstreamRes.ok) {
      return jsonError('upstream_error', `Maps Static API error: ${upstreamRes.status}`, 502);
    }

    const imageBytes = await upstreamRes.arrayBuffer();

    return new Response(imageBytes, {
      status: 200,
      headers: {
        'Content-Type': 'image/png',
        'Cache-Control': 'public, max-age=3600',
      },
    });
  } catch (err: any) {
    if (err instanceof UpstreamTimeoutError) {
      return jsonError('upstream_timeout', 'Google Maps Static API timed out', 504);
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
