import { Env, AutocompleteRequest, AutocompleteResponse, PlaceDetailsResponse, ErrorResponse } from './types';
import { upstreamFetch, UpstreamTimeoutError } from './utils';

const GOOGLE_TIMEOUT_MS = 4000; // 4 seconds (RF-BE-01, Appendix F)

/**
 * Handler for POST /v1/places/autocomplete (RF-PLC-01, Appendix F.3).
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

  const googlePayload: any = {
    input: query.trim(),
    sessionToken: sessionToken.trim(),
    languageCode: 'es',
    regionCode: 'co',
  };

  if (bias) {
    googlePayload.locationBias = {
      circle: {
        center: {
          latitude: bias.lat,
          longitude: bias.lng,
        },
        radius: 20000.0, // 20 km circle
      },
    };
  }

  try {
    const upstreamRes = await upstreamFetch(
      'https://places.googleapis.com/v1/places:autocomplete',
      {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'X-Goog-Api-Key': env.GOOGLE_MAPS_API_KEY,
        },
        body: JSON.stringify(googlePayload),
      },
      GOOGLE_TIMEOUT_MS
    );

    if (!upstreamRes.ok) {
      const errText = await upstreamRes.text().catch(() => '');
      return jsonError('upstream_error', `Places API error: ${upstreamRes.status} ${errText}`, 502);
    }

    const data: any = await upstreamRes.json();
    const suggestions = Array.isArray(data.suggestions) ? data.suggestions : [];

    const predictions = suggestions
      .filter((s: any) => s.placePrediction)
      .slice(0, 5) // Maximum 5 items (RF-PLC-01)
      .map((s: any) => {
        const p = s.placePrediction;
        return {
          placeId: p.placeId || '',
          primaryText: p.structuredFormat?.mainText?.text || p.text?.text || '',
          secondaryText: p.structuredFormat?.secondaryText?.text,
        };
      });

    const responseBody: AutocompleteResponse = { predictions };
    return new Response(JSON.stringify(responseBody), {
      status: 200,
      headers: { 'Content-Type': 'application/json' },
    });
  } catch (err: any) {
    if (err instanceof UpstreamTimeoutError) {
      return jsonError('upstream_timeout', 'Google Places API timed out', 504);
    }
    return jsonError('upstream_error', err.message || 'Unknown upstream error', 502);
  }
}

/**
 * Handler for GET /v1/places/{placeId} (RF-PLC-02, Appendix F.3).
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

  const url = `https://places.googleapis.com/v1/places/${encodeURIComponent(
    placeId
  )}?languageCode=es&sessionToken=${encodeURIComponent(sessionToken)}`;

  try {
    const upstreamRes = await upstreamFetch(
      url,
      {
        method: 'GET',
        headers: {
          'X-Goog-Api-Key': env.GOOGLE_MAPS_API_KEY,
          'X-Goog-FieldMask': 'id,displayName,formattedAddress,location',
        },
      },
      GOOGLE_TIMEOUT_MS
    );

    if (!upstreamRes.ok) {
      return jsonError('upstream_error', `Places Details API error: ${upstreamRes.status}`, 502);
    }

    const data: any = await upstreamRes.json();
    const responseBody: PlaceDetailsResponse = {
      placeId: data.id || placeId,
      name: data.displayName?.text || '',
      address: data.formattedAddress || '',
      lat: data.location?.latitude || 0,
      lng: data.location?.longitude || 0,
    };

    return new Response(JSON.stringify(responseBody), {
      status: 200,
      headers: { 'Content-Type': 'application/json' },
    });
  } catch (err: any) {
    if (err instanceof UpstreamTimeoutError) {
      return jsonError('upstream_timeout', 'Google Place Details API timed out', 504);
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
