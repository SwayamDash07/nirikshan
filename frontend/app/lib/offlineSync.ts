"use client";

export type OfflineReport = { zoneId: number; description: string; clientEventId: string };
export type OfflineQueueItem = OfflineReport & { id: string; createdAt: string };

const DB_NAME = "nirikshan-offline";
const DB_VERSION = 1;
const CACHE_STORE = "safe-cache";
const OUTBOX_STORE = "report-outbox";

function supported() { return typeof window !== "undefined" && "indexedDB" in window; }
function database(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const request = indexedDB.open(DB_NAME, DB_VERSION);
    request.onupgradeneeded = () => {
      const db = request.result;
      if (!db.objectStoreNames.contains(CACHE_STORE)) db.createObjectStore(CACHE_STORE, { keyPath: "key" });
      if (!db.objectStoreNames.contains(OUTBOX_STORE)) db.createObjectStore(OUTBOX_STORE, { keyPath: "id" });
    };
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error);
  });
}

export function safeOfflinePath(path: string) {
  return path.startsWith("/api/venues") || path.startsWith("/api/venue/") || path.startsWith("/api/alerts") || path.startsWith("/api/recommendations/customer") || path === "/api/health";
}

export async function readOfflineCache(key: string): Promise<unknown | undefined> {
  if (!supported()) return undefined;
  const db = await database();
  return new Promise((resolve) => {
    const request = db.transaction(CACHE_STORE, "readonly").objectStore(CACHE_STORE).get(key);
    request.onsuccess = () => resolve(request.result?.value);
    request.onerror = () => resolve(undefined);
  });
}

export async function writeOfflineCache(key: string, value: unknown) {
  if (!supported() || !safeOfflinePath(key)) return;
  const db = await database();
  await new Promise<void>((resolve) => {
    const request = db.transaction(CACHE_STORE, "readwrite").objectStore(CACHE_STORE).put({ key, value, savedAt: new Date().toISOString() });
    request.onsuccess = () => resolve(); request.onerror = () => resolve();
  });
}

export async function queueOfflineReport(report: OfflineReport) {
  if (!supported()) return false;
  const db = await database();
  const item: OfflineQueueItem = { ...report, id: report.clientEventId, createdAt: new Date().toISOString() };
  await new Promise<void>((resolve) => {
    const request = db.transaction(OUTBOX_STORE, "readwrite").objectStore(OUTBOX_STORE).put(item);
    request.onsuccess = () => resolve(); request.onerror = () => resolve();
  });
  return true;
}

export async function pendingOfflineReports(): Promise<OfflineQueueItem[]> {
  if (!supported()) return [];
  const db = await database();
  return new Promise((resolve) => {
    const request = db.transaction(OUTBOX_STORE, "readonly").objectStore(OUTBOX_STORE).getAll();
    request.onsuccess = () => resolve((request.result || []) as OfflineQueueItem[]);
    request.onerror = () => resolve([]);
  });
}

export async function removeOfflineReport(id: string) {
  if (!supported()) return;
  const db = await database();
  await new Promise<void>((resolve) => {
    const request = db.transaction(OUTBOX_STORE, "readwrite").objectStore(OUTBOX_STORE).delete(id);
    request.onsuccess = () => resolve(); request.onerror = () => resolve();
  });
}

export async function syncOfflineReports(send: (report: OfflineReport) => Promise<unknown>) {
  const items = await pendingOfflineReports();
  for (const item of items) {
    try { await send(item); await removeOfflineReport(item.id); }
    catch { break; }
  }
  return pendingOfflineReports();
}
