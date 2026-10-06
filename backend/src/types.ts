/**
 * WakeSync Gateway - Environment and DTO Types
 *
 * Adheres to Appendix F of WAKESYNC_MASTER_DOCUMENTATION.md.
 */

export interface Env {
  // ---------------------------------------------------------------------------
  // Configuración NO secreta (inyectada automáticamente vía [vars] en wrangler.toml)
  // ---------------------------------------------------------------------------
  DEEPSEEK_MODEL: string;

  // ---------------------------------------------------------------------------
  // Secretos de Cloudflare (inyectados exclusivamente vía `wrangler secret put`,
  // NUNCA en wrangler.toml, NUNCA en el repositorio, NUNCA en el APK)
  // ---------------------------------------------------------------------------
  MAPBOX_ACCESS_TOKEN: string;
  DEEPSEEK_API_KEY: string;
  APP_TOKEN: string;
}

/**
 * Standard error response structure (Appendix F.2).
 */
export interface ErrorResponse {
  error: 'invalid_request' | 'unauthorized' | 'rate_limited' | 'upstream_error' | 'upstream_timeout';
  message: string;
}

/**
 * Location bias coordinates (Appendix F.3).
 */
export interface LocationBias {
  lat: number;
  lng: number;
}

/**
 * Autocomplete request body (Appendix F.3).
 */
export interface AutocompleteRequest {
  query: string;
  sessionToken: string;
  bias?: LocationBias;
}

/**
 * Single place prediction item (Appendix F.3).
 */
export interface PlacePrediction {
  placeId: string;
  primaryText: string;
  secondaryText?: string;
}

/**
 * Autocomplete response body (Appendix F.3).
 */
export interface AutocompleteResponse {
  predictions: PlacePrediction[];
}

/**
 * Place details response body (Appendix F.3).
 */
export interface PlaceDetailsResponse {
  placeId: string;
  name: string;
  address: string;
  lat: number;
  lng: number;
}

/**
 * Reverse geocode response body (Appendix F.3).
 */
export interface ReverseGeocodeResponse {
  name: string;
  address: string;
}

/**
 * Post-session insights request body (RF-INS-01, Appendix F.3).
 * Must contain EXACTLY these 4 aggregated fields.
 */
export interface InsightRequest {
  sessionType: 'NAP' | 'TRANSIT';
  durationSeconds: number;
  restLatencySeconds: number | null;
  outcome: 'COMPLETED' | 'DISMISSED' | 'TIMED_OUT' | 'CANCELLED';
}

/**
 * Post-session insights response body (Appendix F.3).
 */
export interface InsightResponse {
  insight: string;
}
