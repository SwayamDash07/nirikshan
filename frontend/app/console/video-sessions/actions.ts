"use server";

import { access, readFile } from "node:fs/promises";
import path from "node:path";

export type RecordedRiskLevel = "LOW" | "MEDIUM" | "HIGH" | "CRITICAL";

export type RecordedTelemetry = {
  secondsIntoClip: number;
  peopleCount: number;
  densityScore: number;
  movementSpeed: number;
  riskLevel: RecordedRiskLevel;
  explanation: string;
};

export type RecordedSession = {
  id: string;
  zoneId: number;
  zoneName: string;
  cameraLabel: string;
  videoUrl: string;
  telemetrySource: string;
  evacuationRoute: string;
  telemetry: RecordedTelemetry[];
  videoAvailable: boolean;
};

type RawEvent = {
  timestamp?: string;
  secondsIntoClip?: number;
  peopleCount?: number;
  densityScore?: number;
  movementSpeed?: number;
  riskLevel?: string;
  explanation?: string;
};

type SessionDefinition = {
  id: string;
  zoneId: number;
  zoneName: string;
  cameraLabel: string;
  eventCandidates: string[];
  videoCandidates: string[];
  evacuationRoute: string;
};

const sessionDefinitions: SessionDefinition[] = [
  {
    id: "main-gate",
    zoneId: 1,
    zoneName: "Main Gate",
    cameraLabel: "Main Gate camera",
    eventCandidates: ["recorded-sessions/zone-1/events.json", "4/events.json"],
    videoCandidates: ["recorded-sessions/zone-1/annotated.mp4"],
    evacuationRoute: "Main Gate → Hostel 25 Gate → Main Gate Exit",
  },
  {
    id: "hostel-25-gate",
    zoneId: 2,
    zoneName: "Hostel 25 Gate",
    cameraLabel: "Hostel 25 Gate camera",
    eventCandidates: ["recorded-sessions/zone-2/events.json", "1/events.json"],
    videoCandidates: ["recorded-sessions/zone-2/annotated.mp4"],
    evacuationRoute: "Hostel 25 Gate → Main Gate Exit",
  },
  {
    id: "cafeteria",
    zoneId: 3,
    zoneName: "Cafeteria",
    cameraLabel: "Cafeteria camera",
    eventCandidates: ["recorded-sessions/zone-3/events.json", "5/events.json"],
    videoCandidates: ["recorded-sessions/zone-3/annotated.mp4"],
    evacuationRoute: "Cafeteria → A Block Entrance → Main Gate Exit",
  },
  {
    id: "a-block-entrance",
    zoneId: 4,
    zoneName: "A Block Entrance",
    cameraLabel: "A Block Entrance camera",
    eventCandidates: ["recorded-sessions/zone-4/events.json", "6/events.json"],
    videoCandidates: ["recorded-sessions/zone-4/annotated.mp4"],
    evacuationRoute: "A Block Entrance → Main Gate Exit",
  },
  {
    id: "c-block-gate",
    zoneId: 5,
    zoneName: "C Block Gate",
    cameraLabel: "C Block Gate camera",
    eventCandidates: ["recorded-sessions/zone-5/events.json", "7/events.json"],
    videoCandidates: ["recorded-sessions/zone-5/annotated.mp4"],
    evacuationRoute: "C Block Gate → Hostel 25 Gate → Main Gate Exit",
  },
  {
    id: "main-gate-exit",
    zoneId: 6,
    zoneName: "Main Gate Exit",
    cameraLabel: "Main Gate Exit camera",
    eventCandidates: ["recorded-sessions/zone-6/events.json", "live/zone-6/events.json"],
    videoCandidates: ["recorded-sessions/zone-6/annotated.mp4"],
    evacuationRoute: "Main Gate Exit",
  },
];

const outputRoot = path.resolve(process.env.NIRIKSHAN_CV_OUTPUT_DIR || path.join(process.cwd(), "..", "cv-pipeline", "outputs"));
const apiBase = (process.env.NEXT_PUBLIC_API_BASE_URL || "http://localhost:8080").replace(/\/$/, "");

async function firstExistingBinary(candidates: string[]) {
  for (const candidate of candidates) {
    const filePath = path.resolve(outputRoot, candidate);
    if (!filePath.startsWith(`${outputRoot}${path.sep}`)) continue;
    try {
      await access(filePath);
      return candidate;
    } catch {
      continue;
    }
  }
  return undefined;
}

function normalizeEvents(rawEvents: RawEvent[]): RecordedTelemetry[] {
  const firstTimestamp = rawEvents.find((event) => event.timestamp)?.timestamp;
  const firstTime = firstTimestamp ? new Date(firstTimestamp).valueOf() : NaN;
  return rawEvents.map((event, index) => {
    const timestamp = event.timestamp ? new Date(event.timestamp).valueOf() : NaN;
    const secondsIntoClip = Number.isFinite(event.secondsIntoClip)
      ? Math.max(0, Number(event.secondsIntoClip))
      : Number.isFinite(timestamp) && Number.isFinite(firstTime)
        ? Math.max(0, (timestamp - firstTime) / 1000)
        : index;
    const riskLevel = ["LOW", "MEDIUM", "HIGH", "CRITICAL"].includes(event.riskLevel || "")
      ? event.riskLevel as RecordedRiskLevel
      : "LOW";
    return {
      secondsIntoClip,
      peopleCount: Math.max(0, Math.round(Number(event.peopleCount) || 0)),
      densityScore: Math.max(0, Number(event.densityScore) || 0),
      movementSpeed: Math.max(0, Number(event.movementSpeed) || 0),
      riskLevel,
      explanation: event.explanation || "Recorded telemetry is available for this point in the session.",
    };
  }).sort((left, right) => left.secondsIntoClip - right.secondsIntoClip);
}

async function readTelemetry(definition: SessionDefinition) {
  for (const candidate of definition.eventCandidates) {
    const filePath = path.resolve(outputRoot, candidate);
    if (!filePath.startsWith(`${outputRoot}${path.sep}`)) continue;
    try {
      const raw = JSON.parse(await readFile(filePath, "utf8")) as RawEvent[];
      if (Array.isArray(raw)) return { telemetry: normalizeEvents(raw), source: candidate };
    } catch {
      continue;
    }
  }
  return { telemetry: [], source: "No processed telemetry JSON found" };
}

export async function getRecordedSessions(): Promise<RecordedSession[]> {
  return Promise.all(sessionDefinitions.map(async (definition) => {
    const [eventData, videoCandidate] = await Promise.all([
      readTelemetry(definition),
      firstExistingBinary(definition.videoCandidates),
    ]);
    return {
      id: definition.id,
      zoneId: definition.zoneId,
      zoneName: definition.zoneName,
      cameraLabel: definition.cameraLabel,
      videoUrl: videoCandidate ? `${apiBase}/job-files/${videoCandidate}` : "",
      telemetrySource: eventData.source,
      evacuationRoute: definition.evacuationRoute,
      telemetry: eventData.telemetry,
      videoAvailable: Boolean(videoCandidate),
    };
  }));
}
