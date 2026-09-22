import { describe, it, expect, beforeEach, vi } from 'vitest';
import worker from '../src/index';
import { Env } from '../src/types';
import { resetRateLimits } from '../src/utils';

const mockEnv: Env = {
  GEMINI_MODEL: 'gemini-1.5-flash',
  GOOGLE_MAPS_API_KEY: 'test-google-key',
  GEMINI_API_KEY: 'test-gemini-key',
  APP_TOKEN: 'correct-secret-token-32bytes',
};

describe('WakeSync Gateway - Places, Geocoding & Static Maps', () => {
  beforeEach(() => {
    resetRateLimits();
    vi.restoreAllMocks();
  });

  it('POST /v1/places/autocomplete rejects query with less than 3 characters (400 invalid_request)', async () => {
    const req = new Request('http://localhost/v1/places/autocomplete', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-WakeSync-App-Token': mockEnv.APP_TOKEN,
      },
      body: JSON.stringify({ query: 'ab', sessionToken: 'uuid-123' }),
    });

    const res = await worker.fetch(req, mockEnv);
    expect(res.status).toBe(400);

    const body: any = await res.json();
    expect(body.error).toBe('invalid_request');
  });

  it('POST /v1/places/autocomplete rejects out-of-range bias coordinates (400 invalid_request)', async () => {
    const req = new Request('http://localhost/v1/places/autocomplete', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-WakeSync-App-Token': mockEnv.APP_TOKEN,
      },
      body: JSON.stringify({
        query: 'universidad',
        sessionToken: 'uuid-123',
        bias: { lat: 95.0, lng: -75.0 }, // lat > 90
      }),
    });

    const res = await worker.fetch(req, mockEnv);
    expect(res.status).toBe(400);
  });

  it('POST /v1/places/autocomplete proxies and formats up to 5 predictions', async () => {
    const fakeGoogleResponse = {
      suggestions: [
        {
          placePrediction: {
            placeId: 'ChIJ111',
            structuredFormat: {
              mainText: { text: 'UCC Medellín' },
              secondaryText: { text: 'Calle 50 #45, Medellín' },
            },
          },
        },
        {
          placePrediction: {
            placeId: 'ChIJ222',
            structuredFormat: {
              mainText: { text: 'UCC Envigado' },
              secondaryText: { text: 'Carrera 43A, Envigado' },
            },
          },
        },
      ],
    };

    // Mock global fetch
    vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(
      new Response(JSON.stringify(fakeGoogleResponse), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      })
    );

    const req = new Request('http://localhost/v1/places/autocomplete', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-WakeSync-App-Token': mockEnv.APP_TOKEN,
      },
      body: JSON.stringify({
        query: 'universidad cooperativa',
        sessionToken: 'test-session-uuid',
        bias: { lat: 6.25, lng: -75.57 },
      }),
    });

    const res = await worker.fetch(req, mockEnv);
    expect(res.status).toBe(200);

    const body: any = await res.json();
    expect(body.predictions).toHaveLength(2);
    expect(body.predictions[0].placeId).toBe('ChIJ111');
    expect(body.predictions[0].primaryText).toBe('UCC Medellín');
    expect(body.predictions[0].secondaryText).toBe('Calle 50 #45, Medellín');
  });

  it('GET /v1/places/:placeId resolves place details with sessionToken', async () => {
    const fakeDetailsResponse = {
      id: 'ChIJ111',
      displayName: { text: 'Universidad Cooperativa de Colombia' },
      formattedAddress: 'Cl. 50 #45-34, La Candelaria, Medellín',
      location: { latitude: 6.248, longitude: -75.568 },
    };

    vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(
      new Response(JSON.stringify(fakeDetailsResponse), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      })
    );

    const req = new Request('http://localhost/v1/places/ChIJ111?sessionToken=test-session-uuid', {
      method: 'GET',
      headers: {
        'X-WakeSync-App-Token': mockEnv.APP_TOKEN,
      },
    });

    const res = await worker.fetch(req, mockEnv);
    expect(res.status).toBe(200);

    const body: any = await res.json();
    expect(body.placeId).toBe('ChIJ111');
    expect(body.name).toBe('Universidad Cooperativa de Colombia');
    expect(body.lat).toBe(6.248);
    expect(body.lng).toBe(-75.568);
  });

  it('GET /v1/geocode/reverse resolves coordinates to human-readable address', async () => {
    const fakeGeocodeResponse = {
      results: [
        {
          formatted_address: 'Calle 50 #45-20, Medellín, Antioquia',
          address_components: [{ long_name: 'Calle 50 #45-20', types: ['street_address'] }],
        },
      ],
    };

    vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(
      new Response(JSON.stringify(fakeGeocodeResponse), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      })
    );

    const req = new Request('http://localhost/v1/geocode/reverse?lat=6.2518&lng=-75.5684', {
      method: 'GET',
      headers: {
        'X-WakeSync-App-Token': mockEnv.APP_TOKEN,
      },
    });

    const res = await worker.fetch(req, mockEnv);
    expect(res.status).toBe(200);

    const body: any = await res.json();
    expect(body.name).toBe('Calle 50 #45-20');
    expect(body.address).toBe('Calle 50 #45-20, Medellín, Antioquia');
  });

  it('GET /v1/maps/static rejects invalid zoom or size bounds (400)', async () => {
    // Zoom out of [10, 19]
    const reqInvalidZoom = new Request(
      'http://localhost/v1/maps/static?lat=6.25&lng=-75.56&zoom=25&size=227',
      {
        method: 'GET',
        headers: { 'X-WakeSync-App-Token': mockEnv.APP_TOKEN },
      }
    );
    const resZoom = await worker.fetch(reqInvalidZoom, mockEnv);
    expect(resZoom.status).toBe(400);

    // Size out of [100, 320]
    const reqInvalidSize = new Request(
      'http://localhost/v1/maps/static?lat=6.25&lng=-75.56&zoom=16&size=500',
      {
        method: 'GET',
        headers: { 'X-WakeSync-App-Token': mockEnv.APP_TOKEN },
      }
    );
    const resSize = await worker.fetch(reqInvalidSize, mockEnv);
    expect(resSize.status).toBe(400);
  });

  it('GET /v1/maps/static proxies PNG binary image stream', async () => {
    const fakeImageBuffer = new Uint8Array([137, 80, 78, 71, 13, 10, 26, 10]); // PNG header

    vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(
      new Response(fakeImageBuffer, {
        status: 200,
        headers: { 'Content-Type': 'image/png' },
      })
    );

    const req = new Request(
      'http://localhost/v1/maps/static?lat=6.2518&lng=-75.5684&zoom=16&size=227',
      {
        method: 'GET',
        headers: { 'X-WakeSync-App-Token': mockEnv.APP_TOKEN },
      }
    );

    const res = await worker.fetch(req, mockEnv);
    expect(res.status).toBe(200);
    expect(res.headers.get('Content-Type')).toBe('image/png');

    const bytes = new Uint8Array(await res.arrayBuffer());
    expect(bytes[0]).toBe(137);
  });
});
