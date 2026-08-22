"use server";

export type CheckIn = {
  id: number;
  staffName: string;
  triggeredAt: string;
  respondedAt?: string | null;
  status: "pending" | "confirmed";
};

const apiBase = (process.env.NEXT_PUBLIC_API_BASE_URL || "http://localhost:8080").replace(/\/$/, "");

async function request<T>(token: string, path: string, init: RequestInit = {}): Promise<T> {
  if (!token) throw new Error("Your session has expired. Sign in again.");
  const headers = new Headers(init.headers);
  headers.set("Authorization", `Bearer ${token}`);
  if (init.body && !headers.has("Content-Type")) headers.set("Content-Type", "application/json");
  const response = await fetch(`${apiBase}${path}`, { ...init, headers, cache: "no-store" });
  const text = response.status === 204 ? "" : await response.text();
  let payload: unknown = text;
  if (text) {
    try { payload = JSON.parse(text); } catch { payload = text; }
  }
  if (!response.ok) {
    const data = payload && typeof payload === "object" ? payload as { message?: string; error?: string } : undefined;
    throw new Error(data?.message || data?.error || (typeof payload === "string" && payload) || `Backend returned HTTP ${response.status}`);
  }
  return (text ? payload : null) as T;
}

export async function getCheckIns(token: string): Promise<CheckIn[]> {
  return request<CheckIn[]>(token, "/api/admin/check-ins");
}

export async function triggerCheckIns(token: string): Promise<CheckIn[]> {
  return request<CheckIn[]>(token, "/api/admin/check-ins", { method: "POST" });
}

export async function getStaffCheckIn(token: string, staffName: string): Promise<CheckIn | null> {
  return request<CheckIn | null>(token, `/api/check-ins/staff/${encodeURIComponent(staffName)}`);
}

export async function confirmStaffCheckIn(token: string, staffName: string): Promise<CheckIn | null> {
  return request<CheckIn | null>(token, `/api/check-ins/staff/${encodeURIComponent(staffName)}/confirm`, { method: "POST" });
}
