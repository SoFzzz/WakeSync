import { Env, ErrorResponse } from './types';
import { upstreamFetch, UpstreamTimeoutError, UPSTREAM_TIMEOUT_MS, MAPBOX_API_BASE } from './utils';

const MAPBOX_STYLE_PATH = 'mapbox/streets-v12';

/**
 * Converts the watch's zoom level to the Mapbox Static Images API zoom level.
 *
 * The watch (WebMercatorProjection, TILE_SIZE = 256) computes pan/tap math assuming a
 * 256 px world tile, where the world is 256 * 2^zoom logical px wide. Mapbox renders with
 * 512 px tiles, so its world is 512 * 2^zoom logical px wide. Requesting `zoom - 1` makes
 * both worlds the same size, so one logical px of the image matches one logical px of the
 * watch's projection.
 */
export function toMapboxZoom(appZoom: number): number {
  return appZoom - 1;
}

/**
 * Handler for GET /v1/maps/static?lat=...&lng=...&zoom=...&size=... (RF-PLC-03, Appendix F.3).
 *
 * Adapts the request to the Mapbox Static Images API (vector style, returns PNG).
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

  // Mapbox Static Images API: @2x gives high-DPI (logical px * 2 = physical px), matching the
  // watch's MAP_SCALE = 2. No markers requested: the watch draws its own stationary center pin.
  // Logo and attribution are drawn by the watch UI outside the image (RNF-PLC-03).
  // Mapbox expects "longitude,latitude" in the path.
  const params = new URLSearchParams({
    attribution: 'false',
    logo: 'false',
    access_token: env.MAPBOX_ACCESS_TOKEN,
  });
  const url =
    `${MAPBOX_API_BASE}/styles/v1/${MAPBOX_STYLE_PATH}/static/` +
    `${lng},${lat},${toMapboxZoom(zoom)},0/${size}x${size}@2x?${params.toString()}`;

  try {
    const upstreamRes = await upstreamFetch(url, { method: 'GET' }, UPSTREAM_TIMEOUT_MS);

    if (!upstreamRes.ok) {
      return jsonError('upstream_error', `Static map error: ${upstreamRes.status}`, 502);
    }

    const imageBytes = await upstreamRes.arrayBuffer();

    return new Response(imageBytes, {
      status: 200,
      headers: {
        'Content-Type': 'image/png',
        'Cache-Control': 'public, max-age=3600',
      },
    });
  } catch (err: unknown) {
    if (err instanceof UpstreamTimeoutError) {
      return jsonError('upstream_timeout', 'Static map timed out', 504);
    }
    return jsonError('upstream_error', 'Static map request failed', 502);
  }
}

function jsonError(error: ErrorResponse['error'], message: string, status: number): Response {
  const body: ErrorResponse = { error, message };
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}
