import { Env, ReverseGeocodeResponse, ErrorResponse } from './types';
import { upstreamFetch, UpstreamTimeoutError, UPSTREAM_TIMEOUT_MS, MAPBOX_API_BASE } from './utils';

/**
 * Handler for GET /v1/geocode/reverse?lat=...&lng=... (RF-PLC-03, Appendix F.3).
 *
 * Adapts the request to the Mapbox Geocoding API v6 `/reverse` endpoint (temporary results).
 */
export async function handleReverseGeocode(
  latStr: string | null,
  lngStr: string | null,
  env: Env
): Promise<Response> {
  if (!latStr || !lngStr) {
    return jsonError('invalid_request', 'lat and lng query parameters are required', 400);
  }

  const lat = parseFloat(latStr);
  const lng = parseFloat(lngStr);

  if (isNaN(lat) || isNaN(lng) || lat < -90 || lat > 90 || lng < -180 || lng > 180) {
    return jsonError('invalid_request', 'lat must be in [-90, 90] and lng in [-180, 180]', 400);
  }

  const params = new URLSearchParams({
    longitude: String(lng),
    latitude: String(lat),
    language: 'es',
    limit: '1',
    access_token: env.MAPBOX_ACCESS_TOKEN,
  });
  const url = `${MAPBOX_API_BASE}/search/geocode/v6/reverse?${params.toString()}`;

  try {
    const upstreamRes = await upstreamFetch(url, { method: 'GET' }, UPSTREAM_TIMEOUT_MS);

    if (!upstreamRes.ok) {
      return jsonError('upstream_error', `Reverse geocoding error: ${upstreamRes.status}`, 502);
    }

    const data: any = await upstreamRes.json();
    const firstFeature = Array.isArray(data.features) && data.features.length > 0 ? data.features[0] : null;
    const properties = firstFeature?.properties;

    if (!properties) {
      const fallbackName = `Punto en el mapa (${lat.toFixed(4)}, ${lng.toFixed(4)})`;
      const responseBody: ReverseGeocodeResponse = {
        name: fallbackName,
        address: fallbackName,
      };
      return new Response(JSON.stringify(responseBody), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      });
    }

    // Most specific feature first: prefer its canonical name, fall back to the full address
    const name = properties.name_preferred || properties.name || properties.full_address || 'Punto seleccionado';
    const address = properties.full_address || properties.place_formatted || name;

    const responseBody: ReverseGeocodeResponse = { name, address };
    return new Response(JSON.stringify(responseBody), {
      status: 200,
      headers: { 'Content-Type': 'application/json' },
    });
  } catch (err: unknown) {
    if (err instanceof UpstreamTimeoutError) {
      return jsonError('upstream_timeout', 'Reverse geocoding timed out', 504);
    }
    return jsonError('upstream_error', 'Reverse geocoding request failed', 502);
  }
}

function jsonError(error: ErrorResponse['error'], message: string, status: number): Response {
  const body: ErrorResponse = { error, message };
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}
