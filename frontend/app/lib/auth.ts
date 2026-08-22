import { readOfflineCache, safeOfflinePath, writeOfflineCache } from "./offlineSync";

export type Role = "ADMIN" | "SECURITY" | "CITIZEN";

export type UserInfo = {
  id: number;
  email: string;
  name: string;
  role: Role;
  mustChangePassword: boolean;
  assignedZoneId?: number | null;
  assignedZoneName?: string | null;
  active: boolean;
  protectedAdmin: boolean;
};

export type Session = { token: string; user: UserInfo };

export class ApiError extends Error {
  constructor(message: string, public readonly status?: number, public readonly path?: string, public readonly requestId?: string) {
    super(message);
    this.name = "ApiError";
  }
}

const key = "nirikshan.session";
export const apiBase = (process.env.NEXT_PUBLIC_API_BASE_URL || "http://localhost:8080").replace(/\/$/, "");
const GET_CACHE_TTL_MS = 4000;
const responseCache = new Map<string, { expiresAt: number; value: unknown }>();
const pendingGets = new Map<string, Promise<unknown>>();

export function readSession(): Session | null {
  if (typeof window === "undefined") return null;
  try { return JSON.parse(window.localStorage.getItem(key) || "null") as Session | null; }
  catch { return null; }
}

export function saveSession(session: Session) { window.localStorage.setItem(key, JSON.stringify(session)); }
export function clearSession() { window.localStorage.removeItem(key); }

export async function api<T>(path: string, init: RequestInit = {}): Promise<T> {
  const session = readSession();
  const method = (init.method || "GET").toUpperCase();
  const bypassMemoryCache = init.cache === "no-store";
  const cacheKey = `${session?.token || "anonymous"}:${path}`;
  if (method === "GET" && !bypassMemoryCache) {
    const cached = responseCache.get(cacheKey);
    if (cached && cached.expiresAt > Date.now()) return cached.value as T;
    const pending = pendingGets.get(cacheKey);
    if (pending) return pending as Promise<T>;
  }
  const headers = new Headers(init.headers);
  if (session?.token) headers.set("Authorization", `Bearer ${session.token}`);
  if (init.body && !(init.body instanceof FormData) && !headers.has("Content-Type")) headers.set("Content-Type", "application/json");

  const request = (async () => {
    let response: Response;
    try {
      response = await fetch(`${apiBase}${path}`, { ...init, headers, cache: "no-store" });
    } catch {
      const cached = await readOfflineCache(path);
      if (cached !== undefined) return cached as T;
      throw new ApiError(`Cannot reach the backend at ${apiBase}. Check that Spring Boot is running.`, undefined, path);
    }

  const requestId = response.headers.get("X-Request-Id") || undefined;
  const contentType = response.headers.get("content-type") || "";
  const text = response.status === 204 ? "" : await response.text();
  let payload: unknown = text;
  if (text && contentType.includes("json")) {
    try { payload = JSON.parse(text); } catch { }
  }

    if (!response.ok) {
      const data = payload && typeof payload === "object" ? payload as { error?: string; message?: string } : undefined;
      const rawMessage = data?.message || data?.error || (typeof payload === "string" && payload.trim()) || `Backend returned HTTP ${response.status}`;
      const message = rawMessage.includes("<html") || rawMessage.includes("<!DOCTYPE") ? `Backend returned HTTP ${response.status} for ${path}` : rawMessage;
      if (response.status === 401 || response.status === 403) clearSession();
      throw new ApiError(message, response.status, path, requestId);
    }

    if (response.status === 204 || !text) return undefined as T;
    if (typeof payload === "string") {
      try {
        const parsed = JSON.parse(payload) as T;
        if (safeOfflinePath(path)) void writeOfflineCache(path, parsed);
        return parsed;
      } catch { throw new ApiError("Backend returned an invalid response", response.status, path, requestId); }
    }
    if (safeOfflinePath(path)) void writeOfflineCache(path, payload);
    return payload as T;
  })();
  if (method !== "GET") { responseCache.clear(); return request; }
  if (bypassMemoryCache) return request;
  pendingGets.set(cacheKey, request);
  try {
    const value = await request;
    responseCache.set(cacheKey, { expiresAt: Date.now() + GET_CACHE_TTL_MS, value });
    return value;
  } finally {
    pendingGets.delete(cacheKey);
  }
}
