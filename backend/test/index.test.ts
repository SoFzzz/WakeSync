import { describe, it, expect, beforeEach, vi } from 'vitest';
import worker from '../src/index';
import { Env } from '../src/types';
import { resetRateLimits } from '../src/utils';

const mockEnv: Env = {
  GEMINI_MODEL: 'gemini-2.5-flash',
  MAPBOX_ACCESS_TOKEN: 'test-mapbox-token',
  GEMINI_API_KEY: 'test-gemini-key',
  APP_TOKEN: 'correct-secret-token-32bytes',
};

describe('WakeSync Gateway - Router, Auth and Rate Limiting', () => {
  beforeEach(() => {
    resetRateLimits();
    vi.restoreAllMocks();
  });

  it('GET /v1/health should respond 200 without authentication header (Appendix F.2)', async () => {
    const req = new Request('http://localhost/v1/health', {
      method: 'GET',
    });

    const res = await worker.fetch(req, mockEnv);
    expect(res.status).toBe(200);

    const body: any = await res.json();
    expect(body.status).toBe('ok');
  });

  it('Protected route without token should return 401 unauthorized (RF-BE-03)', async () => {
    const req = new Request('http://localhost/v1/places/autocomplete', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ query: 'universidad', sessionToken: 'uuid-123' }),
    });

    const res = await worker.fetch(req, mockEnv);
    expect(res.status).toBe(401);

    const body: any = await res.json();
    expect(body.error).toBe('unauthorized');
  });

  it('Protected route with invalid token should return 401 unauthorized', async () => {
    const req = new Request('http://localhost/v1/places/autocomplete', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-WakeSync-App-Token': 'wrong-token',
      },
      body: JSON.stringify({ query: 'universidad', sessionToken: 'uuid-123' }),
    });

    const res = await worker.fetch(req, mockEnv);
    expect(res.status).toBe(401);

    const body: any = await res.json();
    expect(body.error).toBe('unauthorized');
  });

  it('Protected route returns 401 when the APP_TOKEN secret is not configured (fail closed)', async () => {
    const envWithoutToken: Env = { ...mockEnv, APP_TOKEN: '' };

    for (const headers of [{}, { 'X-WakeSync-App-Token': '' }] as Record<string, string>[]) {
      const req = new Request('http://localhost/v1/geocode/reverse?lat=6.25&lng=-75.56', {
        method: 'GET',
        headers,
      });
      const res = await worker.fetch(req, envWithoutToken);
      expect(res.status).toBe(401);
    }

    const missingSecretEnv = { ...mockEnv } as Partial<Env>;
    delete missingSecretEnv.APP_TOKEN;
    const res = await worker.fetch(new Request('http://localhost/v1/unknown'), missingSecretEnv as Env);
    expect(res.status).toBe(401);
  });

  it('Should enforce 60 requests/minute per client IP (RF-BE-04)', async () => {
    const clientIp = '192.168.1.50';

    // 60 requests succeed through auth and hit 404 (not found) or validation
    for (let i = 0; i < 60; i++) {
      const req = new Request('http://localhost/v1/unknown', {
        method: 'GET',
        headers: {
          'X-WakeSync-App-Token': mockEnv.APP_TOKEN,
          'cf-connecting-ip': clientIp,
        },
      });
      const res = await worker.fetch(req, mockEnv);
      expect(res.status).not.toBe(429);
    }

    // 61st request must trigger 429 rate_limited
    const req61 = new Request('http://localhost/v1/unknown', {
      method: 'GET',
      headers: {
        'X-WakeSync-App-Token': mockEnv.APP_TOKEN,
        'cf-connecting-ip': clientIp,
      },
    });
    const res61 = await worker.fetch(req61, mockEnv);
    expect(res61.status).toBe(429);

    const body: any = await res61.json();
    expect(body.error).toBe('rate_limited');
  });
});
