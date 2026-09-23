import { describe, it, expect, beforeEach, vi } from 'vitest';
import worker from '../src/index';
import { Env } from '../src/types';
import { resetRateLimits } from '../src/utils';
import { toMapboxZoom } from '../src/maps';

const mockEnv: Env = {
  GEMINI_MODEL: 'gemini-3.1-flash-lite',
  MAPBOX_ACCESS_TOKEN: 'test-mapbox-token',
  GEMINI_API_KEY: 'test-gemini-key',
  APP_TOKEN: 'correct-secret-token-32bytes',
};

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

function authedGet(path: string): Request {
  return new Request(`http://localhost${path}`, {
    method: 'GET',
    headers: { 'X-WakeSync-App-Token': mockEnv.APP_TOKEN },
  });
}

function autocompleteRequest(payload: unknown): Request {
  return new Request('http://localhost/v1/places/autocomplete', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-WakeSync-App-Token': mockEnv.APP_TOKEN,
    },
    body: JSON.stringify(payload),
  });
}

describe('WakeSync Gateway - Places, Geocoding & Static Maps (Mapbox)', () => {
  beforeEach(() => {
    resetRateLimits();
    vi.restoreAllMocks();
  });

  it('POST /v1/places/autocomplete rejects query with less than 3 characters (400 invalid_request)', async () => {
    const res = await worker.fetch(autocompleteRequest({ query: 'ab', sessionToken: 'uuid-123' }), mockEnv);
    expect(res.status).toBe(400);

    const body: any = await res.json();
    expect(body.error).toBe('invalid_request');
  });

  it('POST /v1/places/autocomplete rejects out-of-range bias coordinates (400 invalid_request)', async () => {
    const res = await worker.fetch(
      autocompleteRequest({
        query: 'universidad',
        sessionToken: 'uuid-123',
        bias: { lat: 95.0, lng: -75.0 }, // lat > 90
      }),
      mockEnv
    );
    expect(res.status).toBe(400);
  });

  it('POST /v1/places/autocomplete maps Search Box suggestions to the Appendix F contract', async () => {
    const fakeSuggestResponse = {
      suggestions: [
        {
          mapbox_id: 'dXJuOm1ieHBvaTox',
          name: 'Universidad Cooperativa de Colombia',
          place_formatted: 'Medellín, Antioquia, Colombia',
          full_address: 'Calle 50 #40-74, Medellín, Antioquia, Colombia',
        },
        {
          mapbox_id: 'dXJuOm1ieHBvaToy',
          name: 'UCC Envigado',
          full_address: 'Carrera 43A, Envigado, Antioquia, Colombia',
        },
        { name: 'Entry without mapbox_id is discarded' },
      ],
    };

    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(jsonResponse(fakeSuggestResponse));

    const res = await worker.fetch(
      autocompleteRequest({
        query: 'universidad cooperativa',
        sessionToken: 'test-session-uuid',
        bias: { lat: 6.25, lng: -75.57 },
      }),
      mockEnv
    );
    expect(res.status).toBe(200);

    const body: any = await res.json();
    expect(body.predictions).toHaveLength(2);
    expect(body.predictions[0]).toEqual({
      placeId: 'dXJuOm1ieHBvaTox',
      primaryText: 'Universidad Cooperativa de Colombia',
      secondaryText: 'Medellín, Antioquia, Colombia',
    });
    // Falls back to full_address when place_formatted is missing
    expect(body.predictions[1].secondaryText).toBe('Carrera 43A, Envigado, Antioquia, Colombia');

    const calledUrl = new URL(String(fetchSpy.mock.calls[0][0]));
    expect(calledUrl.pathname).toBe('/search/searchbox/v1/suggest');
    expect(calledUrl.searchParams.get('q')).toBe('universidad cooperativa');
    expect(calledUrl.searchParams.get('session_token')).toBe('test-session-uuid');
    expect(calledUrl.searchParams.get('country')).toBe('co');
    expect(calledUrl.searchParams.get('language')).toBe('es');
    expect(calledUrl.searchParams.get('limit')).toBe('5');
    // Only navigable feature types; category suggestions are excluded
    expect(calledUrl.searchParams.get('types')).toBe('poi,address,place,street');
    // Mapbox proximity is "lng,lat"; this fails if the order is ever inverted
    expect(calledUrl.searchParams.get('proximity')).toBe('-75.57,6.25');
  });

  it('POST /v1/places/autocomplete omits proximity when there is no bias', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(jsonResponse({ suggestions: [] }));

    const res = await worker.fetch(
      autocompleteRequest({ query: 'universidad', sessionToken: 'uuid-123' }),
      mockEnv
    );
    expect(res.status).toBe(200);

    const calledUrl = new URL(String(fetchSpy.mock.calls[0][0]));
    expect(calledUrl.searchParams.has('proximity')).toBe(false);
  });

  it('POST /v1/places/autocomplete returns only the upstream status, never its body', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(
      new Response('{"message":"Not Authorized - Invalid Token secret-detail"}', { status: 401 })
    );

    const res = await worker.fetch(
      autocompleteRequest({ query: 'universidad', sessionToken: 'uuid-123' }),
      mockEnv
    );
    expect(res.status).toBe(502);

    const text = await res.text();
    expect(text).toContain('401');
    expect(text).not.toContain('secret-detail');
    expect(text).not.toContain(mockEnv.MAPBOX_ACCESS_TOKEN);
  });

  it('GET /v1/places/:placeId resolves retrieve GeoJSON, swapping [lng, lat] to lat/lng', async () => {
    const fakeRetrieveResponse = {
      type: 'FeatureCollection',
      features: [
        {
          type: 'Feature',
          geometry: { type: 'Point', coordinates: [-75.568, 6.248] },
          properties: {
            mapbox_id: 'dXJuOm1ieHBvaTox',
            name: 'Universidad Cooperativa de Colombia',
            full_address: 'Calle 50 #40-74, Medellín, Antioquia, Colombia',
          },
        },
      ],
    };

    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(jsonResponse(fakeRetrieveResponse));

    const res = await worker.fetch(
      authedGet('/v1/places/dXJuOm1ieHBvaTox?sessionToken=test-session-uuid'),
      mockEnv
    );
    expect(res.status).toBe(200);

    const body: any = await res.json();
    expect(body.placeId).toBe('dXJuOm1ieHBvaTox');
    expect(body.name).toBe('Universidad Cooperativa de Colombia');
    expect(body.address).toBe('Calle 50 #40-74, Medellín, Antioquia, Colombia');
    expect(body.lat).toBe(6.248);
    expect(body.lng).toBe(-75.568);

    const calledUrl = new URL(String(fetchSpy.mock.calls[0][0]));
    expect(calledUrl.pathname).toBe('/search/searchbox/v1/retrieve/dXJuOm1ieHBvaTox');
    expect(calledUrl.searchParams.get('session_token')).toBe('test-session-uuid');
  });

  it('GET /v1/places/:placeId returns 502 when retrieve has no features', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(jsonResponse({ type: 'FeatureCollection', features: [] }));

    const res = await worker.fetch(authedGet('/v1/places/unknown-id?sessionToken=uuid-123'), mockEnv);
    expect(res.status).toBe(502);
  });

  it('GET /v1/geocode/reverse resolves coordinates with Geocoding v6 (longitude/latitude params)', async () => {
    const fakeReverseResponse = {
      type: 'FeatureCollection',
      features: [
        {
          type: 'Feature',
          properties: {
            name: 'Calle 50 #45-20',
            full_address: 'Calle 50 #45-20, Medellín, Antioquia, Colombia',
            place_formatted: 'Medellín, Antioquia, Colombia',
          },
        },
      ],
    };

    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(jsonResponse(fakeReverseResponse));

    const res = await worker.fetch(authedGet('/v1/geocode/reverse?lat=6.2518&lng=-75.5684'), mockEnv);
    expect(res.status).toBe(200);

    const body: any = await res.json();
    expect(body.name).toBe('Calle 50 #45-20');
    expect(body.address).toBe('Calle 50 #45-20, Medellín, Antioquia, Colombia');

    const calledUrl = new URL(String(fetchSpy.mock.calls[0][0]));
    expect(calledUrl.pathname).toBe('/search/geocode/v6/reverse');
    expect(calledUrl.searchParams.get('longitude')).toBe('-75.5684');
    expect(calledUrl.searchParams.get('latitude')).toBe('6.2518');
    expect(calledUrl.searchParams.get('language')).toBe('es');
  });

  it('GET /v1/geocode/reverse keeps the "Punto en el mapa" fallback when there are no features', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(jsonResponse({ type: 'FeatureCollection', features: [] }));

    const res = await worker.fetch(authedGet('/v1/geocode/reverse?lat=6.2518&lng=-75.5684'), mockEnv);
    expect(res.status).toBe(200);

    const body: any = await res.json();
    expect(body.name).toBe('Punto en el mapa (6.2518, -75.5684)');
  });

  it('GET /v1/maps/static rejects invalid zoom or size bounds (400)', async () => {
    const resZoom = await worker.fetch(authedGet('/v1/maps/static?lat=6.25&lng=-75.56&zoom=25&size=227'), mockEnv);
    expect(resZoom.status).toBe(400);

    const resSize = await worker.fetch(authedGet('/v1/maps/static?lat=6.25&lng=-75.56&zoom=16&size=500'), mockEnv);
    expect(resSize.status).toBe(400);
  });

  it('GET /v1/maps/static requests "lng,lat,zoom-1" @2x without logo/attribution and proxies PNG', async () => {
    const fakeImageBuffer = new Uint8Array([137, 80, 78, 71, 13, 10, 26, 10]); // PNG header

    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(
      new Response(fakeImageBuffer, {
        status: 200,
        headers: { 'Content-Type': 'image/png' },
      })
    );

    const res = await worker.fetch(authedGet('/v1/maps/static?lat=6.2518&lng=-75.5684&zoom=16&size=227'), mockEnv);
    expect(res.status).toBe(200);
    expect(res.headers.get('Content-Type')).toBe('image/png');

    const bytes = new Uint8Array(await res.arrayBuffer());
    expect(bytes[0]).toBe(137);

    const calledUrl = new URL(String(fetchSpy.mock.calls[0][0]));
    // Longitude first, then latitude; app zoom 16 -> Mapbox zoom 15 (512 px tiles)
    expect(calledUrl.pathname).toBe('/styles/v1/mapbox/streets-v12/static/-75.5684,6.2518,15,0/227x227@2x');
    expect(calledUrl.searchParams.get('attribution')).toBe('false');
    expect(calledUrl.searchParams.get('logo')).toBe('false');
  });

  it('toMapboxZoom compensates 512 px Mapbox tiles against the 256 px app projection', () => {
    expect(toMapboxZoom(10)).toBe(9);
    expect(toMapboxZoom(19)).toBe(18);
  });
});
