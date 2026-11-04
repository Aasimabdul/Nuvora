export type AudiusTrack = {
  id: string;
  title: string;
  user?: { name?: string };
  artwork?: { "150x150"?: string; "480x480"?: string; "1000x1000"?: string } | null;
  duration?: number;
  genre?: string;
  permalink?: string;
};

const API = "https://discoveryprovider.audius.co/v1";
const APP_NAME = "nuvora_desktop";

async function request<T>(path: string, signal?: AbortSignal): Promise<T> {
  const url = new URL(`${API}${path}`);
  url.searchParams.set("app_name", APP_NAME);
  const response = await fetch(url, { signal, headers: { Accept: "application/json" } });
  if (!response.ok) throw new Error(`Audius returned HTTP ${response.status}`);
  const body: unknown = await response.json();
  if (!body || typeof body !== "object" || !("data" in body)) {
    throw new Error("Audius returned an unexpected response.");
  }
  return (body as { data: T }).data;
}

export async function searchAudius(query: string, signal?: AbortSignal): Promise<AudiusTrack[]> {
  const trimmed = query.trim();
  if (!trimmed) return [];
  const params = new URLSearchParams({ query: trimmed, limit: "20" });
  const data = await request<unknown>(`/tracks/search?${params.toString()}`, signal);
  if (!Array.isArray(data)) throw new Error("Audius search response did not contain a track list.");
  return data.filter((item): item is AudiusTrack =>
    !!item && typeof item === "object" &&
    typeof (item as AudiusTrack).id === "string" &&
    typeof (item as AudiusTrack).title === "string"
  );
}

export async function getTrendingAudius(signal?: AbortSignal): Promise<AudiusTrack[]> {
  const data = await request<unknown>("/tracks/trending?limit=12", signal);
  if (!Array.isArray(data)) throw new Error("Audius trending response did not contain a track list.");
  return data.filter((item): item is AudiusTrack =>
    !!item && typeof item === "object" &&
    typeof (item as AudiusTrack).id === "string" &&
    typeof (item as AudiusTrack).title === "string"
  );
}

export function audiusStreamUrl(trackId: string): string {
  if (!/^[a-zA-Z0-9_-]+$/.test(trackId)) throw new Error("Invalid Audius track identifier.");
  const url = new URL(`${API}/tracks/${encodeURIComponent(trackId)}/stream`);
  url.searchParams.set("app_name", APP_NAME);
  return url.toString();
}

export function artworkUrl(track: AudiusTrack): string | undefined {
  return track.artwork?.["480x480"] ?? track.artwork?.["150x150"] ?? track.artwork?.["1000x1000"] ?? undefined;
}

export function formatDuration(seconds?: number): string {
  if (!Number.isFinite(seconds) || !seconds || seconds < 0) return "--:--";
  const whole = Math.floor(seconds);
  return `${Math.floor(whole / 60)}:${String(whole % 60).padStart(2, "0")}`;
}
