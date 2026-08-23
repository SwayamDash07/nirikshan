"use server";

import { readFile, stat } from "node:fs/promises";
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
  measuredAreaSqMeters: number;
  videoUrl: string;
  telemetrySource: string;
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
  measuredAreaSqMeters: number;
  eventCandidates: string[];
  videoCandidates: string[];
};

const sessionDefinitions: SessionDefinition[] = [
  {
    id: "main-gate",
    zoneId: 1,
    zoneName: "Main Gate",
    cameraLabel: "Main Gate camera",
    measuredAreaSqMeters: 18,
    eventCandidates: ["recorded-sessions/zone-1/events.json"],
    videoCandidates: ["recorded-sessions/zone-1/annotated.mp4"],
  },
  {
    id: "hostel-25-gate",
    zoneId: 2,
    zoneName: "Hostel 25 Gate",
    cameraLabel: "Hostel 25 Gate camera",
    measuredAreaSqMeters: 12,
    eventCandidates: ["recorded-sessions/zone-2/events.json"],
    videoCandidates: ["recorded-sessions/zone-2/annotated.mp4"],
  },
  {
    id: "cafeteria",
    zoneId: 3,
    zoneName: "Cafeteria",
    cameraLabel: "Cafeteria camera",
    measuredAreaSqMeters: 12,
    eventCandidates: ["recorded-sessions/zone-3/events.json"],
    videoCandidates: ["recorded-sessions/zone-3/annotated.mp4"],
  },
  {
    id: "a-block-entrance",
    zoneId: 4,
    zoneName: "A Block Entrance",
    cameraLabel: "A Block Entrance camera",
    measuredAreaSqMeters: 18,
    eventCandidates: ["recorded-sessions/zone-4/events.json"],
    videoCandidates: ["recorded-sessions/zone-4/annotated.mp4"],
  },
  {
    id: "c-block-gate",
    zoneId: 5,
    zoneName: "C Block Gate",
    cameraLabel: "C Block Gate camera",
    measuredAreaSqMeters: 15,
    eventCandidates: ["recorded-sessions/zone-5/events.json"],
    videoCandidates: ["recorded-sessions/zone-5/annotated.mp4"],
  },
  {
    id: "c-block-exit",
    zoneId: 6,
    zoneName: "C Block Exit",
    cameraLabel: "C Block Exit camera",
    measuredAreaSqMeters: 18,
    eventCandidates: ["recorded-sessions/zone-6/events.json"],
    videoCandidates: ["recorded-sessions/zone-6/annotated.mp4"],
  },
];

const outputRoot = path.resolve(process.env.NIRIKSHAN_CV_OUTPUT_DIR || path.join(process.cwd(), "..", "cv-pipeline", "outputs"));
const apiBase = (process.env.NEXT_PUBLIC_API_BASE_URL || "http://localhost:8080").replace(/\/$/, "");

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

  const remoteCandidate = definition.eventCandidates.find((candidate) => candidate.startsWith("recorded-sessions/"));
  if (remoteCandidate) {
    try {
      const response = await fetch(`${apiBase}/job-files/${remoteCandidate}`, { cache: "no-store" });
      if (response.ok) {
        const raw = await response.json() as RawEvent[];
        if (Array.isArray(raw)) return { telemetry: normalizeEvents(raw), source: remoteCandidate };
      }
    } catch {
      // The local file fallback below provides a useful state during local-only development.
    }
  }

  return { telemetry: [], source: "No processed telemetry JSON found" };
}

export async function getRecordedSessions(): Promise<RecordedSession[]> {
  return Promise.all(sessionDefinitions.map(async (definition) => {
    const eventData = await readTelemetry(definition);
    const videoCandidate = definition.videoCandidates[0];
    const videoPath = path.resolve(outputRoot, videoCandidate);
    const videoVersion = await stat(videoPath).then((metadata) => Math.floor(metadata.mtimeMs)).catch(() => 0);
    return {
      id: definition.id,
      zoneId: definition.zoneId,
      zoneName: definition.zoneName,
      cameraLabel: definition.cameraLabel,
      measuredAreaSqMeters: definition.measuredAreaSqMeters,
      videoUrl: `${apiBase}/job-files/${videoCandidate}?v=${videoVersion}`,
      telemetrySource: eventData.source,
      telemetry: eventData.telemetry,
      videoAvailable: Boolean(videoCandidate),
    };
  }));
}
