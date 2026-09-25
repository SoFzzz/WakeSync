import { Env, ReverseGeocodeResponse, ErrorResponse } from './types';
import { upstreamFetch, UpstreamTimeoutError } from './utils';

const GOOGLE_TIMEOUT_MS = 4000; // 4 seconds (RF-BE-01, Appendix F)

/**
 * Handler for GET /v1/geocode/reverse?lat=...&lng=... (RF-PLC-03, Appendix F.3).
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

  const url = `https://maps.googleapis.com/maps/api/geocode/json?latlng=${lat},${lng}&language=es&key=${encodeURIComponent(
    env.GOOGLE_MAPS_API_KEY
  )}`;

  try {
    const upstreamRes = await upstreamFetch(url, { method: 'GET' }, GOOGLE_TIMEOUT_MS);

    if (!upstreamRes.ok) {
      return jsonError('upstream_error', `Geocoding API error: ${upstreamRes.status}`, 502);
    }

    const data: any = await upstreamRes.json();
    const firstResult = Array.isArray(data.results) && data.results.length > 0 ? data.results[0] : null;

    if (!firstResult) {
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

    // Extract readable name: prefer street address / sublocality / point of interest, fallback to formatted address
    const name = firstResult.address_components?.[0]?.long_name || firstResult.formatted_address || 'Punto seleccionado';
    const address = firstResult.formatted_address || name;

    const responseBody: ReverseGeocodeResponse = { name, address };
    return new Response(JSON.stringify(responseBody), {
      status: 200,
      headers: { 'Content-Type': 'application/json' },
    });
  } catch (err: any) {
    if (err instanceof UpstreamTimeoutError) {
      return jsonError('upstream_timeout', 'Google Geocoding API timed out', 504);
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
