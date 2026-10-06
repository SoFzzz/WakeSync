import { Env, AutocompleteRequest, AutocompleteResponse, PlaceDetailsResponse, ErrorResponse } from './types';
import { upstreamFetch, UpstreamTimeoutError, UPSTREAM_TIMEOUT_MS, MAPBOX_API_BASE } from './utils';

const MAX_PREDICTIONS = 5; // Maximum 5 items (RF-PLC-01)
const SUGGEST_TYPES = 'poi,address,place,street';

/**
 * Handler for POST /v1/places/autocomplete (RF-PLC-01, Appendix F.3).
 *
 * Adapts the request to the Mapbox Search Box API `/suggest` endpoint. The HTTP contract
 * towards the watch (Appendix F) is unchanged.
 */
export async function handlePlacesAutocomplete(
  request: Request,
  env: Env
): Promise<Response> {
  let body: Partial<AutocompleteRequest>;
  try {
    body = await request.json();
  } catch {
    return jsonError('invalid_request', 'Malformed JSON body', 400);
  }

  const { query, sessionToken, bias } = body;

  // Validate query: string between 3 and 100 characters (RF-BE-05)
  if (typeof query !== 'string' || query.trim().length < 3 || query.length > 100) {
    return jsonError('invalid_request', 'query must be between 3 and 100 characters', 400);
  }

  // Validate sessionToken
  if (typeof sessionToken !== 'string' || sessionToken.trim().length === 0) {
    return jsonError('invalid_request', 'sessionToken is required', 400);
  }

  // Validate bias coordinates if provided
  if (bias !== undefined) {
    if (
      typeof bias !== 'object' ||
      typeof bias.lat !== 'number' ||
      typeof bias.lng !== 'number' ||
      bias.lat < -90 || bias.lat > 90 ||
      bias.lng < -180 || bias.lng > 180
    ) {
      return jsonError('invalid_request', 'Invalid location bias coordinates', 400);
    }
  }

  const params = new URLSearchParams({
    q: query.trim(),
    session_token: sessionToken.trim(),
    country: 'co',
    language: 'es',
    limit: String(MAX_PREDICTIONS),
    // Excludes category/brand suggestions, which have no coordinates to navigate to
    types: SUGGEST_TYPES,
    access_token: env.MAPBOX_ACCESS_TOKEN,
  });
  if (bias) {
    // Mapbox expects "longitude,latitude" (reversed with respect to the app's lat/lng)
    params.set('proximity', `${bias.lng},${bias.lat}`);
  }

  try {
    const upstreamRes = await upstreamFetch(
      `${MAPBOX_API_BASE}/search/searchbox/v1/suggest?${params.toString()}`,
      { method: 'GET' },
      UPSTREAM_TIMEOUT_MS
    );

    if (!upstreamRes.ok) {
      return jsonError('upstream_error', `Search suggest error: ${upstreamRes.status}`, 502);
    }

    const data: any = await upstreamRes.json();
    const suggestions = Array.isArray(data.suggestions) ? data.suggestions : [];

    const predictions = suggestions
      .filter((s: any) => typeof s.mapbox_id === 'string' && s.mapbox_id.length > 0)
      .slice(0, MAX_PREDICTIONS)
      .map((s: any) => ({
        placeId: s.mapbox_id,
        primaryText: s.name || '',
        secondaryText: s.place_formatted || s.full_address || undefined,
      }));

    const responseBody: AutocompleteResponse = { predictions };
    return new Response(JSON.stringify(responseBody), {
      status: 200,
      headers: { 'Content-Type': 'application/json' },
    });
  } catch (err: unknown) {
    if (err instanceof UpstreamTimeoutError) {
      return jsonError('upstream_timeout', 'Search suggest timed out', 504);
    }
    return jsonError('upstream_error', 'Search suggest request failed', 502);
  }
}

/**
 * Handler for GET /v1/places/{placeId} (RF-PLC-02, Appendix F.3).
 *
 * Adapts the request to the Mapbox Search Box API `/retrieve/{mapbox_id}` endpoint, which
 * closes the billing session opened by `/suggest` with the same session token.
 */
export async function handlePlaceDetails(
  placeId: string,
  sessionToken: string | null,
  env: Env
): Promise<Response> {
  if (!placeId || placeId.trim().length === 0) {
    return jsonError('invalid_request', 'placeId is required', 400);
  }

  if (!sessionToken || sessionToken.trim().length === 0) {
    return jsonError('invalid_request', 'sessionToken query parameter is required', 400);
  }

  const params = new URLSearchParams({
    session_token: sessionToken.trim(),
    language: 'es',
    access_token: env.MAPBOX_ACCESS_TOKEN,
  });
  const url = `${MAPBOX_API_BASE}/search/searchbox/v1/retrieve/${encodeURIComponent(placeId)}?${params.toString()}`;

  try {
    const upstreamRes = await upstreamFetch(url, { method: 'GET' }, UPSTREAM_TIMEOUT_MS);

    if (!upstreamRes.ok) {
      return jsonError('upstream_error', `Search retrieve error: ${upstreamRes.status}`, 502);
    }

    const data: any = await upstreamRes.json();
    const feature = Array.isArray(data.features) && data.features.length > 0 ? data.features[0] : null;
    const coordinates = feature?.geometry?.coordinates;

    if (!Array.isArray(coordinates) || coordinates.length < 2) {
      return jsonError('upstream_error', 'Search retrieve returned no coordinates', 502);
    }

    const properties = feature.properties ?? {};
    // GeoJSON coordinates are [longitude, latitude]
    const [lng, lat] = coordinates;
    const responseBody: PlaceDetailsResponse = {
      placeId: properties.mapbox_id || placeId,
      name: properties.name || '',
      address: properties.full_address || properties.place_formatted || '',
      lat,
      lng,
    };

    return new Response(JSON.stringify(responseBody), {
      status: 200,
      headers: { 'Content-Type': 'application/json' },
    });
  } catch (err: unknown) {
    if (err instanceof UpstreamTimeoutError) {
      return jsonError('upstream_timeout', 'Search retrieve timed out', 504);
    }
    return jsonError('upstream_error', 'Search retrieve request failed', 502);
  }
}

function jsonError(error: ErrorResponse['error'], message: string, status: number): Response {
  const body: ErrorResponse = { error, message };
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}
