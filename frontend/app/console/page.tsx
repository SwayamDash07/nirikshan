"use client";

import {
  CSSProperties,
  type ReactNode,
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
} from "react";
import dynamic from "next/dynamic";
import { Client, IMessage } from "@stomp/stompjs";
import {
  CartesianGrid,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import AppShell, { primaryNavItems } from "../components/AppShell";
import Icon from "../components/Icon";
import { Button, Card, Spinner } from "../components/ui";
import { api, clearSession, readSession, type UserInfo } from "../lib/auth";
import styles from "./console.module.css";

const LeafletVenueMap = dynamic(() => import("../LeafletVenueMap"), {
  ssr: false,
  loading: () => <div className={styles.mapLoading}>Loading venue map</div>,
});

type RiskLevel = "LOW" | "MEDIUM" | "HIGH" | "CRITICAL";
type Zone = {
  id: number;
  name: string;
  latitude: number;
  longitude: number;
  radiusMeters?: number;
  currentDensity: number;
  currentPeopleCount: number;
  currentRiskLevel: RiskLevel;
  lastUpdated: string;
  feedStatus?: "OFFLINE" | "LIVE";
  bottleneckDetected?: boolean;
  simulationActive?: boolean;
};
type Venue = { id: number; name: string; description?: string };
type RiskEvent = {
  id?: number;
  zoneId: number;
  timestamp: string;
  densityScore: number;
  peopleCount?: number;
  movementSpeed: number;
  riskLevel: RiskLevel;
  explanation: string;
  sourceClipId?: string;
  source?: "LIVE" | "SIMULATION";
  hotspotRegions?: HotspotRegion[];
  bottleneckDetected?: boolean;
  densityChange?: number;
  movementSlowdown?: number;
  hotspotPersistenceSeconds?: number;
  dominantDirection?: string;
  directionDegrees?: number;
  directionConfidence?: number;
  directionalConsistency?: number;
  reverseMovementRatio?: number;
  conflictingMovementRatio?: number;
  behaviorState?: FlowBehaviorState;
  behaviorExplanation?: string;
};
type HotspotRegion = { gridPosition: string; relativeDensity: number };
type HotspotSummary = { regions: HotspotRegion[]; durationSeconds: number };
type CitizenReport = {
  id: number;
  zoneId: number;
  zoneName: string;
  description: string;
  timestamp: string;
  status: string;
};
type Health = {
  status: string;
  totalZones: number;
  totalRiskEvents: number;
  activeAlerts: number;
};
type ForecastState = "STABLE" | "RISING" | "SURGE_RISK" | "CRUSH_RISK" | "RECOVERING" | "INSUFFICIENT_DATA";
type FlowBehaviorState = "NORMAL_FLOW" | "RISING_FLOW" | "SLOWING_FLOW" | "REVERSE_FLOW" | "CONFLICTING_FLOW" | "UNUSUAL_BEHAVIOR" | "INSUFFICIENT_DATA";
type RiskForecast = {
  zoneId: number;
  zoneName: string;
  generatedAt: string;
  lastTelemetryAt?: string;
  currentRisk: RiskLevel;
  projectedRisk: RiskLevel;
  forecastHorizonSeconds: number;
  estimatedSecondsToProjectedRisk?: number | null;
  currentDensity: number;
  projectedDensity: number;
  densityTrendPerMinute: number;
  currentMovementSpeed: number;
  movementSlowdown: number;
  movementSlowdownTrendPerMinute: number;
  hotspotPersistenceSeconds: number;
  bottleneckDetected: boolean;
  confidence: number;
  state: ForecastState;
  explanation: string;
  source: "LIVE" | "SIMULATION";
  stale: boolean;
  projections: Array<{ horizonSeconds: number; projectedDensity: number }>;
  dominantDirection?: string;
  directionDegrees?: number;
  directionConfidence?: number;
  directionalConsistency?: number;
  reverseMovementRatio?: number;
  conflictingMovementRatio?: number;
  behaviorState?: FlowBehaviorState;
  behaviorExplanation?: string;
  analysisGeneratedAt?: string;
  analysisWindowStart?: string;
  analysisWindowEnd?: string;
  nextAnalysisAt?: string;
  analysisIntervalSeconds?: number;
  dataSufficiency?: "SUFFICIENT" | "PARTIAL" | "INSUFFICIENT_DATA" | "STALE";
  flowState?: FlowBehaviorState;
  direction?: string;
  analysisPeopleCount?: number;
  analysisHotspotRegions?: HotspotRegion[];
  stampedeLikelihood?: { score: number; level: "LOW" | "MEDIUM" | "HIGH" | "INSUFFICIENT_DATA"; evidence: string[]; explanation: string };
  panicPropagation?: { state: string; sourceZoneId?: number; sourceZoneName?: string; affectedZoneIds: number[]; confidence: number; explanation: string; source: "LIVE" | "SIMULATION" };
  unusualBehavior?: { detected: boolean; state: string; persistentReadings: number; confidence: number; evidence: string[]; explanation: string };
};
type FlowStatus = {
  zoneId: number;
  zoneName: string;
  observedAt?: string;
  dominantDirection?: string;
  directionDegrees?: number;
  directionConfidence: number;
  directionalConsistency: number;
  reverseMovementRatio: number;
  conflictingMovementRatio: number;
  behaviorState: FlowBehaviorState;
  behaviorExplanation: string;
  sufficientData: boolean;
  analysisGeneratedAt?: string;
  analysisWindowStart?: string;
  analysisWindowEnd?: string;
  nextAnalysisAt?: string;
  analysisIntervalSeconds?: number;
  dataSufficiency?: string;
  unusualBehavior?: RiskForecast["unusualBehavior"];
};
type RouteRecommendation = {
  recommendedRoute?: { routeId: string; routeName: string; exitOrGate: string; expectedTravelTimeSeconds: number; riskScore: number; reason: string; nodeLabels: string[] };
  rejectedRoutes: Array<{ routeName: string; exitOrGate: string; expectedTravelTimeSeconds: number; riskScore: number; reason: string; blocked: boolean }>;
  reason: string;
  expectedTravelTimeSeconds: number;
  riskScore: number;
  gateAction: string;
  gateActionReason: string;
  gateActionDetail?: { action: "KEEP_GATE_OPEN" | "OPEN_ALTERNATE_EXIT" | "CLOSE_ENTRY_GATE" | "TEMPORARILY_CLOSE_EXIT" | "NO_CHANGE"; reason: string; affectedRoute: string; confidence: number; source: "LIVE" | "SIMULATION" };
  source: "LIVE" | "SIMULATION";
  blockage?: { status: "OPEN" | "DEGRADED" | "BLOCKED" | "UNKNOWN"; reason: string; evidence: string[]; source: "LIVE" | "SIMULATION" };
};
type RouteGraph = { nodes: Array<{ id: string; label: string; kind: string }>; paths: Array<{ id: string; fromNodeId: string; toNodeId: string; capacity: number; travelTimeSeconds: number; open: boolean; blocked: boolean }> };

const WS_URL = process.env.NEXT_PUBLIC_WS_URL || `${(process.env.NEXT_PUBLIC_API_BASE_URL || "http://localhost:8080").replace(/\/$/, "").replace(/^http/, "ws")}/ws`;
const riskMeta: Record<
  RiskLevel,
  { label: string; color: string; soft: string }
> = {
  LOW: {
    label: "Normal",
    color: "var(--risk-low)",
    soft: "var(--risk-low-soft)",
  },
  MEDIUM: {
    label: "Watch",
    color: "var(--risk-medium)",
    soft: "var(--risk-medium-soft)",
  },
  HIGH: {
    label: "High",
    color: "var(--risk-high)",
    soft: "var(--risk-high-soft)",
  },
  CRITICAL: {
    label: "Critical",
    color: "var(--risk-critical)",
    soft: "var(--risk-critical-soft)",
  },
};
const riskRank: Record<RiskLevel, number> = {
  LOW: 0,
  MEDIUM: 1,
  HIGH: 2,
  CRITICAL: 3,
};
const DETAIL_UPDATE_WINDOW_MS = 8_000;
const DETAIL_CONFIDENCE_TOLERANCE = 0.05;
const STAMPEDE_MEDIUM_SCORE = 0.4;
const STAMPEDE_HIGH_SCORE = 0.7;
const navItems = primaryNavItems;

function formatTime(value?: string) {
  if (!value) return "Not available";
  const date = new Date(value);
  return Number.isNaN(date.valueOf())
    ? "Not available"
    : date.toLocaleTimeString([], {
        hour: "2-digit",
        minute: "2-digit",
        second: "2-digit",
      });
}
function formatAge(value?: string, now = Date.now()) {
  if (!value) return "Awaiting signal";
  const seconds = Math.max(
    0,
    Math.round((now - new Date(value).valueOf()) / 1000),
  );
  return seconds < 60 ? `${seconds}s ago` : `${Math.floor(seconds / 60)}m ago`;
}
function formatCountdown(value?: string, now = Date.now()) {
  if (!value) return "Unavailable";
  const seconds = Math.max(0, Math.ceil((new Date(value).valueOf() - now) / 1000));
  return seconds === 0 ? "now" : `in ${seconds}s`;
}
function analysisWindowLabel(start?: string, end?: string) {
  if (!start || !end) return "Unavailable";
  return `${formatTime(start)}–${formatTime(end)}`;
}
function flowDataIsSufficient(forecast?: RiskForecast, flowStatus?: FlowStatus) {
  if (flowStatus) return flowStatus.sufficientData;
  return Boolean(forecast && (forecast.flowState || forecast.behaviorState) !== "INSUFFICIENT_DATA" && forecast.dataSufficiency === "SUFFICIENT");
}
function directionDataIsAvailable(forecast?: RiskForecast, flowStatus?: FlowStatus) {
  return Boolean(
    (flowStatus || forecast) &&
      (flowStatus ? true : !forecast?.stale) &&
      (flowStatus?.directionDegrees ?? forecast?.directionDegrees) != null &&
      (flowStatus?.directionConfidence ?? forecast?.directionConfidence ?? 0) > 0 &&
      (flowStatus?.dominantDirection ?? forecast?.dominantDirection) &&
      (flowStatus?.dominantDirection ?? forecast?.dominantDirection) !== "Unknown",
  );
}
function flowPresentation(forecast?: RiskForecast, flowStatus?: FlowStatus) {
  const state = flowStatus?.behaviorState || forecast?.flowState || forecast?.behaviorState || "INSUFFICIENT_DATA";
  const sufficient = flowDataIsSufficient(forecast, flowStatus);
  const directionAvailable = directionDataIsAvailable(forecast, flowStatus);
  const behaviorStabilizing = !sufficient && directionAvailable && state === "INSUFFICIENT_DATA";
  return {
    stateLabel: behaviorStabilizing ? "STABILIZING" : state.replaceAll("_", " "),
    resultLabel: forecast?.stale && !flowStatus ? "STALE" : forecast?.source === "SIMULATION" ? "SIMULATION" : sufficient ? "LIVE" : directionAvailable ? "PARTIAL" : "INSUFFICIENT_DATA",
    sufficient,
    directionAvailable,
    dominantDirection: flowStatus?.dominantDirection ?? forecast?.dominantDirection,
    directionDegrees: flowStatus?.directionDegrees ?? forecast?.directionDegrees,
    directionConfidence: flowStatus?.directionConfidence ?? forecast?.directionConfidence,
    behaviorExplanation: flowStatus?.behaviorExplanation || forecast?.behaviorExplanation,
    analysisGeneratedAt: flowStatus?.analysisGeneratedAt ?? forecast?.analysisGeneratedAt,
    analysisWindowStart: flowStatus?.analysisWindowStart ?? forecast?.analysisWindowStart,
    analysisWindowEnd: flowStatus?.analysisWindowEnd ?? forecast?.analysisWindowEnd,
    nextAnalysisAt: flowStatus?.nextAnalysisAt ?? forecast?.nextAnalysisAt,
    analysisIntervalSeconds: flowStatus?.analysisIntervalSeconds ?? forecast?.analysisIntervalSeconds,
  };
}
type DetailForecastSample = { forecast: RiskForecast; receivedAt: number };
type DetailZoneSample = { zone: Zone; receivedAt: number };

function average(values: number[]) {
  return values.length ? values.reduce((sum, value) => sum + value, 0) / values.length : 0;
}

function aggregateForecastSamples(samples: DetailForecastSample[]) {
  const ordered = [...samples].sort((left, right) => left.receivedAt - right.receivedAt);
  const latest = ordered[ordered.length - 1]?.forecast;
  if (!latest) return undefined;
  const confidenceValues = ordered.map(({ forecast }) => forecast.confidence).filter(Number.isFinite);
  const directionConfidenceValues = ordered.map(({ forecast }) => forecast.directionConfidence).filter((value): value is number => value != null && Number.isFinite(value));
  const directionDegreesValues = ordered.map(({ forecast }) => forecast.directionDegrees).filter((value): value is number => value != null && Number.isFinite(value));
  const stampedeValues = ordered.map(({ forecast }) => forecast.stampedeLikelihood?.score).filter((value): value is number => value != null && Number.isFinite(value));
  const confidence = average(confidenceValues);
  const confidenceStable = ordered.length >= 2
    && ordered[ordered.length - 1].receivedAt - ordered[0].receivedAt >= DETAIL_UPDATE_WINDOW_MS - 1000
    && Math.max(...confidenceValues) - Math.min(...confidenceValues) <= DETAIL_CONFIDENCE_TOLERANCE;
  const stampedeScore = average(stampedeValues);
  const stampedeLevel: NonNullable<RiskForecast["stampedeLikelihood"]>["level"] = stampedeScore >= STAMPEDE_HIGH_SCORE ? "HIGH" : stampedeScore >= STAMPEDE_MEDIUM_SCORE ? "MEDIUM" : stampedeValues.length ? "LOW" : "INSUFFICIENT_DATA";
  return {
    forecast: {
      ...latest,
      confidence,
      directionConfidence: directionConfidenceValues.length ? average(directionConfidenceValues) : latest.directionConfidence,
      directionDegrees: directionDegreesValues.length ? average(directionDegreesValues) : latest.directionDegrees,
      stampedeLikelihood: latest.stampedeLikelihood && stampedeValues.length
        ? { ...latest.stampedeLikelihood, score: stampedeScore, level: stampedeLevel }
        : latest.stampedeLikelihood,
    },
    confidenceStable,
  };
}

function aggregateZoneSamples(samples: DetailZoneSample[]) {
  const ordered = [...samples].sort((left, right) => left.receivedAt - right.receivedAt);
  const latest = ordered[ordered.length - 1]?.zone;
  if (!latest) return undefined;
  const riskCounts = ordered.reduce<Partial<Record<RiskLevel, number>>>((counts, sample) => {
    counts[sample.zone.currentRiskLevel] = (counts[sample.zone.currentRiskLevel] || 0) + 1;
    return counts;
  }, {});
  const riskLevel = (Object.keys(riskCounts) as RiskLevel[]).sort((left, right) => {
    const countDifference = (riskCounts[right] || 0) - (riskCounts[left] || 0);
    return countDifference || (ordered.findLast((sample) => sample.zone.currentRiskLevel === right)?.receivedAt || 0) - (ordered.findLast((sample) => sample.zone.currentRiskLevel === left)?.receivedAt || 0);
  })[0] || latest.currentRiskLevel;
  return { ...latest, currentRiskLevel: riskLevel };
}
function highestRisk(zones: Zone[]): RiskLevel {
  return zones.reduce<RiskLevel>(
    (highest, zone) =>
      riskRank[zone.currentRiskLevel] > riskRank[highest]
        ? zone.currentRiskLevel
        : highest,
    "LOW",
  );
}

function StatusBadge({ level }: { level: RiskLevel }) {
  const meta = riskMeta[level];
  return (
    <span
      className={styles.statusBadge}
      style={
        {
          "--badge-color": meta.color,
          "--badge-bg": meta.soft,
        } as CSSProperties
      }
    >
      <i />
      {meta.label}
    </span>
  );
}
function gridPositionLabel(position: string) {
  const [row, column] = position.split(",").map(Number);
  return `${row === 1 ? "top" : row === 3 ? "bottom" : "center"}-${column === 1 ? "left" : column === 3 ? "right" : "center"}`;
}
const LIVE_SIGNAL_WINDOW_MS = 30_000;
function summarizeHotspots(events: RiskEvent[], now = Date.now()): HotspotSummary | undefined {
  const ordered = [...events].sort((a, b) => new Date(b.timestamp).valueOf() - new Date(a.timestamp).valueOf());
  const latest = ordered[0];
  if (!latest?.hotspotRegions?.length) return undefined;
  const latestTime = new Date(latest.timestamp).valueOf();
  const anchor = now - latestTime <= LIVE_SIGNAL_WINDOW_MS ? now : latestTime;
  let oldest = latest;
  for (const event of ordered.slice(1)) {
    if (!event.hotspotRegions?.length || latestTime - new Date(event.timestamp).valueOf() > LIVE_SIGNAL_WINDOW_MS) break;
    oldest = event;
  }
  return {
    regions: latest.hotspotRegions,
    durationSeconds: Math.max(0, Math.round((anchor - new Date(oldest.timestamp).valueOf()) / 1000)),
  };
}

function InformativeBottleneckBadge({ summary }: { summary?: HotspotSummary }) {
  const detail = summary?.regions.length
    ? `${summary.regions.map((region) => `${gridPositionLabel(region.gridPosition)} ${region.relativeDensity.toFixed(1)}x`).join(", ")} · detected for ${summary.durationSeconds}s`
    : "Recent hotspot detail is unavailable";
  return <span className={styles.bottleneckBadge} tabIndex={0} title={detail} aria-label={detail}>
    <i />Bottleneck detected
    <span className={styles.hotspotTooltip} role="tooltip">{summary?.regions.map((region) => <span key={region.gridPosition}>{gridPositionLabel(region.gridPosition)} · {region.relativeDensity.toFixed(1)}x zone average</span>)}<small>{summary ? `Detected for ${summary.durationSeconds} seconds` : "Recent hotspot detail unavailable"}</small></span>
  </span>;
}

function EarlyWarningPanel({ forecast, loading, error, now, updatedAt, stampedeConfidenceStable }: { forecast?: RiskForecast; loading: boolean; error?: string; now: number; updatedAt?: number; stampedeConfidenceStable: boolean }) {
  const stateLabel: Record<ForecastState, string> = {
    STABLE: "Stable",
    RISING: "Rising",
    SURGE_RISK: "Surge risk",
    CRUSH_RISK: "Projected crush risk",
    RECOVERING: "Recovering",
    INSUFFICIENT_DATA: "Insufficient data",
  };
  const stateClass: Record<ForecastState, string> = {
    STABLE: styles.forecastStable,
    RISING: styles.forecastRising,
    SURGE_RISK: styles.forecastSurge,
    CRUSH_RISK: styles.forecastCrush,
    RECOVERING: styles.forecastRecovering,
    INSUFFICIENT_DATA: styles.forecastInsufficient,
  };
  const analysisUpdated = Boolean(updatedAt && now - updatedAt < 5000);
  const hysteresisHeld = Boolean(forecast?.explanation.toLowerCase().includes("held by"));
  const stampedeLevel = forecast?.stampedeLikelihood?.level || "INSUFFICIENT_DATA";
  const stampedePriority = stampedeConfidenceStable && (forecast?.confidence || 0) >= 0.5 && (stampedeLevel === "MEDIUM" || stampedeLevel === "HIGH");
  return <Card className={`${styles.forecastCard} ${forecast ? stateClass[forecast.state] : styles.forecastInsufficient} ${stampedePriority ? styles.forecastPriority : styles.forecastResting}`} id="forecast">
    <div className={styles.cardHeader}>
      <div><span className={styles.kicker}>EARLY WARNING</span><h2>Risk forecast</h2><p>Projected risk is decision support, not a confirmed incident.</p></div>
      <div className={styles.forecastBadges}>
        {analysisUpdated && <span className={styles.forecastUpdated}>UPDATED FROM NEW READING</span>}
        {hysteresisHeld && <span className={styles.forecastHeld}>HYSTERESIS HOLD</span>}
        {forecast?.stale && <span className={styles.forecastStale}>STALE</span>}
        {forecast?.source === "SIMULATION" && <span className={styles.simulationBadge}>SIMULATION</span>}
        {forecast?.stampedeLikelihood && <span className={stampedePriority ? styles.stampedeBadge : styles.forecastHeld}>STAMPEDE {stampedeLevel.replaceAll("_", " ")}</span>}
      </div>
    </div>
    {loading ? <div className={styles.forecastEmpty}>Calculating forecast from recent readings…</div> : error ? <div className={styles.forecastEmpty}><strong>Forecast unavailable</strong><span>{error}</span></div> : !forecast ? <div className={styles.forecastEmpty}>Select a zone to calculate its forecast.</div> : <>
      <div className={styles.forecastHeadline}><div><span>Current</span><strong>{riskMeta[forecast.currentRisk].label}</strong></div><span className={styles.forecastArrow}>→</span><div><span>Projected</span><strong>{riskMeta[forecast.projectedRisk].label}</strong></div><b className={styles.forecastState}>{stateLabel[forecast.state]}</b></div>
      {forecast.stale || forecast.state === "INSUFFICIENT_DATA" ? <div className={styles.forecastNotice}>{forecast.explanation}</div> : <div className={styles.forecastNotice}>{forecast.estimatedSecondsToProjectedRisk != null && forecast.projectedRisk !== forecast.currentRisk ? `Projected ${riskMeta[forecast.projectedRisk].label.toLowerCase()} risk in approximately ${Math.max(0, Math.round(forecast.estimatedSecondsToProjectedRisk / 60))} minutes.` : forecast.explanation}</div>}
      <div className={styles.forecastStats}><div><span>Density now → projected</span><strong>{forecast.currentDensity.toFixed(2)} → {forecast.projectedDensity.toFixed(2)}</strong></div><div><span>Density trend</span><strong>{forecast.densityTrendPerMinute >= 0 ? "+" : ""}{forecast.densityTrendPerMinute.toFixed(2)} / min</strong></div><div><span>Confidence</span><strong>{Math.round(forecast.confidence * 100)}%</strong></div><div><span>Telemetry</span><strong>{forecast.lastTelemetryAt ? `${formatAge(forecast.lastTelemetryAt, now)}${forecast.stale ? " · STALE" : ""}` : "No recent data"}</strong></div></div>
      {forecast.stampedeLikelihood && <div className={stampedePriority ? styles.stampedePanel : styles.signalFacts}><span className={styles.kicker}>STAMPEDE LIKELIHOOD · HEURISTIC DECISION SUPPORT</span><strong>{stampedeLevel.replaceAll("_", " ")} · {Math.round(forecast.stampedeLikelihood.score * 100)}%</strong><p>{forecast.stampedeLikelihood.explanation}</p>{forecast.stampedeLikelihood.evidence.length > 0 && <small>{forecast.stampedeLikelihood.evidence.join(" · ")}</small>}</div>}
      {forecast.panicPropagation && forecast.panicPropagation.state !== "NONE" && <p className={styles.forecastNotice}>Propagation: {forecast.panicPropagation.explanation}</p>}
      {forecast.unusualBehavior?.detected && <p className={styles.forecastNotice}>Unusual behavior persisted across {forecast.unusualBehavior.persistentReadings} readings: {forecast.unusualBehavior.explanation}</p>}
      <div className={styles.forecastMeta}><span>Forecast age: {formatAge(forecast.generatedAt, now)}</span><span>{hysteresisHeld ? "State held by hysteresis" : analysisUpdated ? "Updated from new reading" : "Analysis unchanged"}</span>{forecast.stale && <span>Forecast based on last telemetry</span>}</div>
      <p className={styles.forecastExplanation}>{forecast.explanation}</p>
    </>}
  </Card>;
}

function FlowIntelligencePanel({ forecast: inputForecast, flowStatus, route, graph, now, compact = false, onViewMore }: { forecast?: RiskForecast; flowStatus?: FlowStatus; route?: RouteRecommendation; graph?: RouteGraph; now: number; compact?: boolean; onViewMore?: () => void }) {
  const forecast = inputForecast;
  const { stateLabel, resultLabel, sufficient, directionAvailable, dominantDirection, directionDegrees, directionConfidence, behaviorExplanation, analysisGeneratedAt, analysisWindowStart, analysisWindowEnd, nextAnalysisAt, analysisIntervalSeconds } = flowPresentation(forecast, flowStatus);
  const reverseWarning = sufficient && (flowStatus?.reverseMovementRatio ?? forecast?.reverseMovementRatio ?? 0) >= 0.45;
  const conflictWarning = sufficient && (flowStatus?.conflictingMovementRatio ?? forecast?.conflictingMovementRatio ?? 0) >= 0.30;
  if (compact) {
    return <Card className={`${styles.zoneContext} ${styles.summaryCard}`}>
      <div className={styles.cardHeader}>
        <div><span className={styles.kicker}>VENUE FLOW</span><h2>Flow intelligence</h2></div>
        <span className={forecast?.source === "SIMULATION" ? styles.simulationBadge : styles.forecastHeld}>{resultLabel}</span>
      </div>
      <div className={styles.contextStats}>
        <div><span>Behavior</span><strong>{stateLabel}</strong></div>
        <div><span>Direction</span><strong>{directionAvailable && dominantDirection && directionDegrees != null ? `${dominantDirection} · ${Math.round(directionDegrees)}°` : "Unknown"}</strong></div>
        <div><span>Confidence</span><strong>{directionAvailable && directionConfidence != null ? `${Math.round(directionConfidence * 100)}%` : "Unavailable"}</strong></div>
      </div>
      <div className={styles.summaryRow}>
        <span>Route</span><strong>{route?.recommendedRoute?.exitOrGate || "Unavailable"}</strong>
        <span>Gate action</span><strong>{route?.gateActionDetail?.action.replaceAll("_", " ") || route?.gateAction?.replaceAll("_", " ") || "Unavailable"}</strong>
      </div>
      {route?.blockage && <div className={styles.summaryRow}><span>Route state</span><strong>{route.blockage.status}</strong></div>}
      <button type="button" className={styles.viewMoreButton} onClick={onViewMore}>View more <Icon name="arrow" /></button>
    </Card>;
  }
  return <Card className={styles.zoneContext}>
     <div className={styles.cardHeader}><div><span className={styles.kicker}>VENUE FLOW INTELLIGENCE</span><h2>Observed flow & route action</h2><p>Observed behavior, predicted risk, and recommended action are shown separately.</p></div><span className={forecast?.source === "SIMULATION" ? styles.simulationBadge : styles.forecastHeld}>{resultLabel}</span></div>
    <div className={styles.contextStats}>
      <div><span>Observed behavior</span><strong>{stateLabel}</strong></div>
      <div><span>Dominant direction</span><strong>{directionAvailable && dominantDirection && directionDegrees != null ? `${dominantDirection} · ${Math.round(directionDegrees)}°` : "Unknown"}</strong></div>
      <div><span>Flow confidence</span><strong>{directionAvailable && directionConfidence != null ? `${Math.round(directionConfidence * 100)}%` : "Unavailable"}</strong></div>
    </div>
    <p className={styles.signalFacts}>{behaviorExplanation || "No tracked-person movement is available for a reliable flow estimate."}</p>
    <div className={styles.forecastMeta}><span>Last analysis: {formatTime(analysisGeneratedAt)}</span><span>Window: {analysisWindowLabel(analysisWindowStart, analysisWindowEnd)}</span><span>Next analysis: {formatCountdown(nextAnalysisAt, now)}</span><span>Interval: {analysisIntervalSeconds || 30}s</span></div>
    {(reverseWarning || conflictWarning) && <p className={styles.forecastNotice}>{reverseWarning ? "Reverse movement warning." : "Conflicting movement warning."} {conflictWarning ? "Crossing flow is elevated." : "People are moving against the dominant direction."}</p>}
    <div className={styles.signalFacts}><span className={styles.kicker}>PREDICTED RISK</span><p>{forecast ? `${forecast.currentRisk} now → ${forecast.projectedRisk} projected. ${forecast.explanation}` : "Forecast unavailable."}</p></div>
    <div className={styles.signalFacts}><span className={styles.kicker}>RECOMMENDED ACTION</span>{route?.recommendedRoute ? <><p><strong>{route.recommendedRoute.routeName}</strong> · {route.expectedTravelTimeSeconds}s · risk score {route.riskScore.toFixed(2)}</p><p>{route.reason}</p><p><strong>Route state:</strong> {route.blockage?.status || "UNKNOWN"} · {route.blockage?.reason || "No blockage evidence available."}</p><p><strong>Gate action:</strong> {route.gateActionDetail?.action.replaceAll("_", " ") || route.gateAction} · {route.gateActionDetail?.reason || route.gateActionReason}{route.gateActionDetail ? ` · ${Math.round(route.gateActionDetail.confidence * 100)}% confidence` : ""}</p><small>Gate actions are recommendations for staff approval; Nirikshan does not control physical gates.</small></> : <p>{route?.reason || "Route recommendation unavailable."}</p>}</div>
    {route?.rejectedRoutes?.length ? <div className={styles.signalFacts}><span className={styles.kicker}>REJECTED ROUTES</span>{route.rejectedRoutes.map((item) => <p key={item.routeName}><strong>{item.routeName}</strong> · {item.blocked ? "BLOCKED" : item.reason}</p>)}</div> : null}
    {graph && <div className={styles.signalFacts}><span className={styles.kicker}>LOCAL ROUTE GRAPH</span><p>{graph.nodes.filter((node) => node.kind === "ZONE").length} zones · {graph.nodes.filter((node) => node.kind === "EXIT").length} exits · {graph.paths.length} directed paths</p></div>}
  </Card>;
}

function HotspotDetail({ summary }: { summary: HotspotSummary }) {
  const active = new Map(summary.regions.map((region) => [region.gridPosition, region]));
  return <section className={styles.hotspotDetail}>
    <div className={styles.hotspotDetailHeader}><div><span>HOTSPOT DETAIL</span><strong>{summary.regions.length} active region{summary.regions.length === 1 ? "" : "s"}</strong></div><small>Persisting for {summary.durationSeconds}s</small></div>
    <div className={styles.hotspotGrid} aria-label="3 by 3 hotspot map">
      {Array.from({ length: 9 }, (_, index) => {
        const position = `${Math.floor(index / 3) + 1},${(index % 3) + 1}`;
        const region = active.get(position);
        return <span className={region ? styles.hotspotCellActive : styles.hotspotCell} key={position} title={region ? `${gridPositionLabel(position)} · ${region.relativeDensity.toFixed(1)}x zone average` : "No hotspot"}>{region ? `${region.relativeDensity.toFixed(1)}x` : ""}</span>;
      })}
    </div>
    <div className={styles.hotspotRegionList}>{summary.regions.map((region) => <span key={region.gridPosition}><b>{gridPositionLabel(region.gridPosition)}</b><small>{region.relativeDensity.toFixed(1)}x zone average · {region.relativeDensity >= 2 ? "severe" : "elevated"}</small></span>)}</div>
  </section>;
}

function SelectedZonePanel({ selectedZone, displayedZone, selectedAnalysis, selectedHotspotSummary, analysisBottleneck, liveTelemetryAt, now, compact = false, onViewMore }: {
  selectedZone?: Zone;
  displayedZone?: Zone;
  selectedAnalysis?: RiskForecast;
  selectedHotspotSummary?: HotspotSummary;
  analysisBottleneck: boolean;
  liveTelemetryAt?: string;
  now: number;
  compact?: boolean;
  onViewMore?: () => void;
}) {
  console.log('[SELECTED-ZONE-RENDER]', Date.now());
  console.log('[SELECTED-ZONE-VALUES]', { headcount: selectedZone?.currentPeopleCount, density: selectedZone?.currentDensity, lastUpdated: selectedZone?.lastUpdated });
  const selectedZoneRenderCountRef = useRef(0);
  selectedZoneRenderCountRef.current += 1;
  const detailedZone = displayedZone || selectedZone;
  if (compact) {
    return <Card className={`${styles.zoneContext} ${styles.summaryCard}`}>
      <div className={styles.cardHeader}>
        <div><span className={styles.kicker}>SELECTED ZONE</span><h2>{selectedZone?.name || "No zone selected"}</h2><small>Render count: {selectedZoneRenderCountRef.current}</small></div>
        <StatusBadge level={detailedZone?.currentRiskLevel || "LOW"} />
      </div>
      {selectedZone ? <>
        {analysisBottleneck && <InformativeBottleneckBadge summary={selectedHotspotSummary} />}
        <div className={styles.contextStats}>
          <div><span>Headcount</span><strong>{selectedZone.currentPeopleCount ?? 0}</strong></div>
          <div><span>Density</span><strong>{selectedZone.currentDensity.toFixed(2)}</strong></div>
          <div><span>Last telemetry</span><strong>{formatTime(liveTelemetryAt)}</strong></div>
        </div>
        <div className={styles.summaryRow}><span>Risk</span><strong>{detailedZone?.currentRiskLevel || selectedZone.currentRiskLevel}</strong><span>Analysis</span><strong>{formatTime(selectedAnalysis?.analysisGeneratedAt)}</strong></div>
        <button type="button" className={styles.viewMoreButton} onClick={onViewMore}>View more <Icon name="arrow" /></button>
      </> : <p className={styles.noDataNotice}>Select a zone from the map.</p>}
    </Card>;
  }
  return <Card className={styles.zoneContext}>
    <div className={styles.cardHeader}>
      <div>
        <span className={styles.kicker}>SELECTED ZONE</span>
        <h2>{selectedZone?.name || "No zone selected"}</h2>
        <small>Render count: {selectedZoneRenderCountRef.current}</small>
        {selectedZone?.simulationActive && <span className={styles.simulationBadge}>SIMULATION MODE</span>}
      </div>
      <StatusBadge level={detailedZone?.currentRiskLevel || "LOW"} />
    </div>
    {selectedZone ? (
      <>
        {analysisBottleneck && <InformativeBottleneckBadge summary={selectedHotspotSummary} />}
        <div className={styles.contextStats}>
          <div><span>Headcount</span><strong>{selectedZone.currentPeopleCount ?? 0}</strong></div>
          <div><span>Density</span><strong>{selectedZone.currentDensity.toFixed(2)}</strong></div>
          <div><span>Last telemetry</span><strong>{formatTime(liveTelemetryAt)}</strong></div>
        </div>
        {selectedAnalysis ? <div className={styles.signalFacts}>
          <span className={styles.kicker}>DETECTED FACTS</span>
          <p>{selectedAnalysis.explanation}</p>
          <div><span>Density change</span><strong>{(selectedAnalysis.densityTrendPerMinute >= 0 ? "+" : "")}{selectedAnalysis.densityTrendPerMinute.toFixed(2)} / min</strong><span>Movement slowdown</span><strong>{(selectedAnalysis.movementSlowdown * 100).toFixed(0)}%</strong><span>Hotspot persistence</span><strong>{selectedAnalysis.hotspotPersistenceSeconds}s</strong></div>
          <div className={styles.forecastMeta}><span>Last analysis: {formatTime(selectedAnalysis.analysisGeneratedAt)}</span><span>Window: {analysisWindowLabel(selectedAnalysis.analysisWindowStart, selectedAnalysis.analysisWindowEnd)}</span><span>Next analysis: {formatCountdown(selectedAnalysis.nextAnalysisAt, now)}</span></div>
        </div> : <div className={styles.noDataNotice}>No recent data for this zone.</div>}
        {analysisBottleneck && selectedHotspotSummary && <HotspotDetail summary={selectedHotspotSummary} />}
      </>
    ) : <p>Select a zone from the map or register below.</p>}
    <a className={styles.cardLink} href="#zones">View all zones <Icon name="arrow" /></a>
  </Card>;
}

function DetailModal({ title, children, onClose }: { title: string; children: ReactNode; onClose: () => void }) {
  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => { if (event.key === "Escape") onClose(); };
    document.addEventListener("keydown", onKeyDown);
    return () => document.removeEventListener("keydown", onKeyDown);
  }, [onClose]);
  return <div className={styles.modalBackdrop} role="presentation" onMouseDown={onClose}>
    <div className={styles.detailModal} role="dialog" aria-modal="true" aria-label={title} onMouseDown={(event) => event.stopPropagation()}>
      <div className={styles.modalHeader}><div><span className={styles.kicker}>DETAIL VIEW</span><h2>{title}</h2></div><button type="button" className={styles.modalClose} onClick={onClose} aria-label="Close detail view">×</button></div>
      {children}
    </div>
  </div>;
}

function Metric({
  label,
  value,
  detail,
  tone = "default",
}: {
  label: string;
  value: string | number;
  detail: string;
  tone?: "default" | "danger" | "success";
}) {
  return (
    <div className={`${styles.metric} ${styles[`metric${tone}`]}`}>
      <span>{label}</span>
      <strong>{value}</strong>
      <small>{detail}</small>
    </div>
  );
}

function ZoneRow({
  zone,
  selected,
  onSelect,
  now,
  lastSignalAt,
  hotspotSummary,
}: {
  zone: Zone;
  selected: boolean;
  onSelect: () => void;
  now: number;
  lastSignalAt?: string;
  hotspotSummary?: HotspotSummary;
}) {
  console.log('[ZONE-ROW-RENDER]', zone.id, Date.now());
  console.log('[ZONE-ROW-VALUES]', zone.id, { headcount: zone.currentPeopleCount, density: zone.currentDensity, lastUpdated: zone.lastUpdated });
  return (
    <button
      type="button"
      className={`${styles.zoneRow} ${selected ? styles.selectedRow : ""}`}
      onClick={onSelect}
    >
      <span className={styles.zoneName}>
        <b>Zone {String(zone.id).padStart(2, "0")}</b>
        <strong>{zone.name}</strong>
        {zone.simulationActive && <span className={styles.simulationBadge}>SIMULATION</span>}
      </span>
      <span>{zone.currentPeopleCount ?? 0} people</span>
      <span>{zone.currentDensity.toFixed(2)} people per m2</span>
      <span className={styles.rowStatus}>
        <StatusBadge level={zone.currentRiskLevel} />
        {zone.bottleneckDetected && <InformativeBottleneckBadge summary={hotspotSummary} />}
      </span>
      <span className={styles.rowAge}>
        {formatAge(lastSignalAt || zone.lastUpdated, now)} <Icon name="arrow" />
      </span>
    </button>
  );
}

type TrendPoint = { timestamp: number; density: number | null };

const TREND_WINDOW_MS = 30 * 1000;
const TREND_TICK_MS = 5 * 1000;

function buildTrendData(events: RiskEvent[], now: number): TrendPoint[] {
  const points = events
    .map((event) => ({
      timestamp: new Date(event.timestamp).valueOf(),
      density: event.densityScore,
    }))
    .filter(
      (point) =>
        Number.isFinite(point.timestamp) &&
        point.timestamp <= now &&
        now - point.timestamp <= TREND_WINDOW_MS,
    )
    .sort((a, b) => a.timestamp - b.timestamp);
  return points.reduce<TrendPoint[]>((result, point, index) => {
    const previous = points[index - 1];
    if (previous && point.timestamp - previous.timestamp > TREND_TICK_MS + 1500) {
      result.push({ timestamp: previous.timestamp + 1, density: null });
    }
    result.push(point);
    return result;
  }, []);
}

function TrendCard({ zone, events }: { zone?: Zone; events: RiskEvent[] }) {
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(timer);
  }, []);
  const data = buildTrendData(events, now);
  const actualPoints = data.filter((point) => point.density !== null);
  const visibleDuration = TREND_WINDOW_MS;
  const windowStart = now - visibleDuration;
  const windowEnd = now;
  const ticks = Array.from(
    { length: 7 },
    (_, index) => windowStart + TREND_TICK_MS * index,
  );
  const clock = new Date(now).toLocaleTimeString([], {
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  });
  const axisTime = (value: number) =>
    new Date(value).toLocaleTimeString([], {
      hour: "2-digit",
      minute: "2-digit",
      second: "2-digit",
      hour12: false,
    });
  return (
    <Card className={styles.trendCard}>
      <div className={styles.cardHeader}>
        <div>
          <span className={styles.kicker}>LIVE SIGNAL HISTORY</span>
          <h2>Density over time</h2>
          <p>{zone ? zone.name : "Select a zone to inspect recent readings"}</p>
        </div>
        <div className={styles.chartClock}>
          <span>Current time</span>
          <strong>{clock}</strong>
        </div>
      </div>
      {actualPoints.length ? (
        <div className={styles.chart}>
          <ResponsiveContainer width="100%" height="100%">
            <LineChart data={data} margin={{ left: 4, right: 12 }}>
              <CartesianGrid stroke="var(--border)" verticalPoints={ticks} />
              <XAxis
                type="number"
                dataKey="timestamp"
                domain={[windowStart, windowEnd]}
                ticks={ticks}
                tick={{ fill: "var(--text-muted)", fontSize: 9 }}
                tickLine={false}
                axisLine={false}
                tickFormatter={axisTime}
              />
              <YAxis
                tick={{ fill: "var(--text-muted)", fontSize: 10 }}
                tickLine={false}
                axisLine={false}
                width={34}
              />
              <Tooltip
                labelFormatter={(value) => axisTime(Number(value))}
                contentStyle={{
                  background: "var(--surface)",
                  border: "1px solid var(--border)",
                  borderRadius: 8,
                  color: "var(--text)",
                }}
                formatter={(value) =>
                  value === null
                    ? ["No reading", "Density"]
                    : [`${Number(value).toFixed(2)} people per m2`, "Density"]
                }
              />
              <Line
                type="monotone"
                dataKey="density"
                stroke="var(--primary)"
                strokeWidth={2.5}
                dot={{ r: 2.5, fill: "var(--primary)", strokeWidth: 0 }}
                activeDot={{ r: 5 }}
                connectNulls={false}
                isAnimationActive
                animationDuration={350}
              />
            </LineChart>
          </ResponsiveContainer>
        </div>
      ) : (
        <div className={styles.chartEmpty}>
          No recent readings for this zone.
          <span>The live worker has not supplied a reading in the chart window.</span>
        </div>
      )}
    </Card>
  );
}

function ConsoleApp({ user }: { user: UserInfo }) {
  console.log('[RENDER-INVOKED]', Date.now());
  const [venue, setVenue] = useState<Venue>();
  const [zones, setZones] = useState<Zone[]>([]);
  const [reports, setReports] = useState<CitizenReport[]>([]);
  const [health, setHealth] = useState<Health>();
  const [events, setEvents] = useState<RiskEvent[]>([]);
  const [forecast, setForecast] = useState<RiskForecast>();
  const [flowStatus, setFlowStatus] = useState<FlowStatus>();
  const [forecastUpdatedAt, setForecastUpdatedAt] = useState<number>();
  const [forecastLoading, setForecastLoading] = useState(false);
  const [forecastError, setForecastError] = useState("");
  const [liveTelemetryAtByZone, setLiveTelemetryAtByZone] = useState<Record<number, string>>({});
  const [route, setRoute] = useState<RouteRecommendation>();
  const [routeGraph, setRouteGraph] = useState<RouteGraph>();
  const [hotspotEventsByZone, setHotspotEventsByZone] = useState<Record<number, RiskEvent[]>>({});
  const [selectedZoneId, setSelectedZoneId] = useState<number>();
  const [detailView, setDetailView] = useState<"flow" | "zone">();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [connected, setConnected] = useState(false);
  const [now, setNow] = useState(() => Date.now());
  const zoneTableRenderCountRef = useRef(0);
  zoneTableRenderCountRef.current += 1;
  useEffect(() => {
    if (typeof window !== "undefined") {
      (window as Window & { DEBUG_ZONES?: Zone[] }).DEBUG_ZONES = zones;
    }
  }, [zones]);
  const stompRef = useRef<Client | null>(null);
  const forecastRef = useRef<RiskForecast | undefined>(undefined);
  const forecastRefreshAtRef = useRef<number>();
  const selectedZoneIdRef = useRef<number>();
  const detailForecastSamplesRef = useRef<DetailForecastSample[]>([]);
  const detailZoneSamplesRef = useRef<Record<number, DetailZoneSample[]>>({});
  const detailWindowStartedAtRef = useRef(Date.now());
  const detailForecastSeededRef = useRef(false);
  const detailZoneSeededRef = useRef(false);
  const lastRecordedDetailZoneAtRef = useRef<Record<number, string>>({});
  const [displayedForecast, setDisplayedForecast] = useState<RiskForecast>();
  const [displayedZones, setDisplayedZones] = useState<Record<number, Zone>>({});
  const [stampedeConfidenceStable, setStampedeConfidenceStable] = useState(false);
  const selectedZone = zones.find((zone) => zone.id === selectedZoneId);
  const detailedForecast = displayedForecast?.zoneId === selectedZoneId ? displayedForecast : forecast?.zoneId === selectedZoneId ? forecast : undefined;
  const detailedZone = selectedZoneId ? displayedZones[selectedZoneId] || selectedZone : selectedZone;
  const selectedHotspotSummary = detailedForecast?.analysisHotspotRegions?.length
    ? { regions: detailedForecast.analysisHotspotRegions, durationSeconds: detailedForecast.hotspotPersistenceSeconds }
    : undefined;
  const zonesRequiringAttention = zones.filter((zone) => riskRank[zone.currentRiskLevel] >= riskRank.MEDIUM).length;
  const freshSignals = zones.filter((zone) => zone.lastUpdated && now - new Date(zone.lastUpdated).valueOf() <= LIVE_SIGNAL_WINDOW_MS).length;
  const totalHeadcount = zones.reduce(
    (sum, zone) => sum + (zone.currentPeopleCount ?? 0),
    0,
  );
  const overallRisk = highestRisk(zones);
  const overallMeta = riskMeta[overallRisk];
  const simulationZoneIds = new Set(zones.filter((zone) => zone.simulationActive).map((zone) => zone.id));
  const liveTelemetryAt = selectedZoneId ? liveTelemetryAtByZone[selectedZoneId] || selectedZone?.lastUpdated : selectedZone?.lastUpdated;
  const telemetryStale = Boolean(liveTelemetryAt) && now - new Date(liveTelemetryAt!).valueOf() > LIVE_SIGNAL_WINDOW_MS;
  const selectedAnalysis = detailedForecast;
  const analysisBottleneck = detailedForecast?.bottleneckDetected ?? selectedZone?.bottleneckDetected ?? false;
  useEffect(() => { forecastRef.current = forecast; }, [forecast]);
  useEffect(() => { selectedZoneIdRef.current = selectedZoneId; }, [selectedZoneId]);
  const recordDetailForecast = useCallback((nextForecast: RiskForecast) => {
    if (nextForecast.zoneId !== selectedZoneIdRef.current) return;
    const now = Date.now();
    if (!detailForecastSeededRef.current) {
      detailForecastSeededRef.current = true;
      setDisplayedForecast(nextForecast);
    }
    detailForecastSamplesRef.current = [...detailForecastSamplesRef.current, { forecast: nextForecast, receivedAt: now }]
      .filter((sample) => now - sample.receivedAt <= DETAIL_UPDATE_WINDOW_MS * 4);
  }, []);
  const recordDetailZone = useCallback((zone: Zone) => {
    if (zone.id !== selectedZoneIdRef.current) return;
    const now = Date.now();
    if (!detailZoneSeededRef.current) {
      detailZoneSeededRef.current = true;
      setDisplayedZones((current) => ({ ...current, [zone.id]: zone }));
    }
    detailZoneSamplesRef.current = {
      ...detailZoneSamplesRef.current,
      [zone.id]: [...(detailZoneSamplesRef.current[zone.id] || []), { zone, receivedAt: now }]
        .filter((sample) => now - sample.receivedAt <= DETAIL_UPDATE_WINDOW_MS * 4),
    };
  }, []);
  useEffect(() => {
    if (!selectedZoneId) return;
    const selected = zones.find((zone) => zone.id === selectedZoneId);
    if (!selected || !selected.lastUpdated || lastRecordedDetailZoneAtRef.current[selected.id] === selected.lastUpdated) return;
    lastRecordedDetailZoneAtRef.current[selected.id] = selected.lastUpdated;
    recordDetailZone(selected);
  }, [recordDetailZone, selectedZoneId, zones]);
  const loadInitial = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const venues = await api<Venue[]>("/api/venues");
      if (!venues.length) {
        setVenue(undefined);
        setZones([]);
        setLoading(false);
        return;
      }
      const currentVenue = venues[0];
      const [venueZones, status, nextReports] =
        await Promise.all([
          api<Zone[]>("/api/admin/zones"),
          api<Health>("/api/health"),
         api<CitizenReport[]>("/api/citizen-reports"),
        ]);
      const activeRuns = await api<Array<{ zoneId: number; status: string }>>("/api/admin/scenarios/active").catch(() => []);
      const activeZoneIds = new Set(activeRuns.filter((run) => run.status === "PENDING" || run.status === "RUNNING").map((run) => run.zoneId));
      setVenue(currentVenue);
      setZones(venueZones.map((zone) => ({ ...zone, simulationActive: activeZoneIds.has(zone.id) })));
      const recentEvents = await Promise.all(venueZones.map(async (zone) => {
        try { return [zone.id, await api<RiskEvent[]>(`/api/zones/${zone.id}/risk-events?limit=50`)] as const; }
        catch { return [zone.id, []] as const; }
      }));
       const recentEventsByZone = Object.fromEntries(recentEvents) as Record<number, RiskEvent[]>;
       setHotspotEventsByZone(recentEventsByZone);
       setReports(nextReports);
       setHealth(status);
       const nextSelectedZoneId = selectedZoneIdRef.current && venueZones.some((zone) => zone.id === selectedZoneIdRef.current)
         ? selectedZoneIdRef.current
         : venueZones[0]?.id;
       setSelectedZoneId(nextSelectedZoneId);
       setEvents(nextSelectedZoneId ? recentEventsByZone[nextSelectedZoneId] || [] : []);
    } catch (reason) {
      setError(
        reason instanceof Error
          ? reason.message
          : "Could not connect to the Nirikshan backend.",
      );
    } finally {
      setLoading(false);
    }
  }, []);
  const loadForecast = useCallback(async (zoneId: number, clearCurrent = false) => {
    setForecastLoading(true);
    if (clearCurrent) {
      setForecast(undefined);
      setForecastUpdatedAt(undefined);
    }
    setForecastError("");
    try {
      const nextForecast = await api<RiskForecast>(`/api/zones/${zoneId}/risk-forecast`);
      if (selectedZoneIdRef.current === zoneId) {
        recordDetailForecast(nextForecast);
        setForecast(nextForecast);
        setForecastUpdatedAt(Date.now());
      }
    } catch (reason) {
      if (clearCurrent) setForecast(undefined);
      setForecastError(reason instanceof Error ? reason.message : "Could not load the risk forecast.");
    }
    finally { setForecastLoading(false); }
  }, [recordDetailForecast]);
  const loadFlowStatus = useCallback(async (zoneId: number, clearCurrent = false) => {
    if (clearCurrent) setFlowStatus(undefined);
    try {
      const nextStatus = await api<FlowStatus>(`/api/zones/${zoneId}/flow-status`, { cache: "no-store" });
      if (selectedZoneIdRef.current === zoneId) setFlowStatus(nextStatus);
    } catch {
      if (clearCurrent) setFlowStatus(undefined);
    }
  }, []);
  const loadRoutes = useCallback(async (zoneId: number, venueId?: number) => {
    if (!venueId) return;
    try {
      const [nextRoute, nextGraph] = await Promise.all([
        api<RouteRecommendation>(`/api/venues/${venueId}/route-recommendation?originZoneId=${zoneId}`),
        api<RouteGraph>(`/api/venues/${venueId}/route-graph`),
      ]);
      setRoute(nextRoute); setRouteGraph(nextGraph);
    } catch { setRoute(undefined); setRouteGraph(undefined); }
  }, []);
  useEffect(() => {
    if (user.mustChangePassword) {
      window.location.replace("/alerts/security");
      return;
    }
    loadInitial();
  }, [loadInitial, user.mustChangePassword]);
  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(timer);
  }, []);
  useEffect(() => {
    detailWindowStartedAtRef.current = Date.now();
    detailForecastSamplesRef.current = [];
    detailZoneSamplesRef.current = {};
    detailForecastSeededRef.current = false;
    detailZoneSeededRef.current = false;
    setDisplayedForecast(undefined);
    setDisplayedZones({});
    setStampedeConfidenceStable(false);
    if (!selectedZoneId) return;
    const timer = window.setInterval(() => {
      const currentTime = Date.now();
      try {
        if (currentTime - detailWindowStartedAtRef.current < DETAIL_UPDATE_WINDOW_MS) return;
        const forecastAggregation = aggregateForecastSamples(detailForecastSamplesRef.current);
        const zoneAggregation = aggregateZoneSamples(detailZoneSamplesRef.current[selectedZoneId] || []);
        if (forecastAggregation) {
          setDisplayedForecast(forecastAggregation.forecast);
          setStampedeConfidenceStable(forecastAggregation.confidenceStable);
        }
        if (zoneAggregation) {
          setDisplayedZones((current) => ({ ...current, [selectedZoneId]: zoneAggregation }));
        }
        detailForecastSamplesRef.current = [];
        detailZoneSamplesRef.current = {};
        detailWindowStartedAtRef.current = currentTime;
      } catch (reason) {
        console.error("Console detail window update failed", reason);
      }
    }, 1000);
    return () => window.clearInterval(timer);
  }, [selectedZoneId]);
  useEffect(() => {
    if (!selectedZoneId) return;
    let refreshInFlight = false;
    let disposed = false;
    const refresh = async () => {
      if (refreshInFlight || disposed) return;
      refreshInFlight = true;
      try {
        await Promise.all([
          api<Zone[]>("/api/admin/zones", { cache: "no-store" }).then((latestZones) => {
            setZones((current) => {
              const next = latestZones.map((zone) => {
                const local = current.find((item) => item.id === zone.id);
                const hasIncomingTelemetry = Number.isFinite(zone.currentDensity)
                  && Number.isFinite(zone.currentPeopleCount)
                  && Boolean(zone.currentRiskLevel)
                  && Number.isFinite(new Date(zone.lastUpdated).valueOf());
                const nextZone: Zone = {
                  ...zone,
                  currentDensity: hasIncomingTelemetry ? zone.currentDensity : local?.currentDensity ?? zone.currentDensity ?? 0,
                  currentPeopleCount: hasIncomingTelemetry ? zone.currentPeopleCount : local?.currentPeopleCount ?? zone.currentPeopleCount ?? 0,
                  currentRiskLevel: hasIncomingTelemetry ? zone.currentRiskLevel : local?.currentRiskLevel ?? zone.currentRiskLevel ?? "LOW",
                  lastUpdated: hasIncomingTelemetry ? zone.lastUpdated : local?.lastUpdated ?? zone.lastUpdated ?? new Date(0).toISOString(),
                  bottleneckDetected: hasIncomingTelemetry ? zone.bottleneckDetected : local?.bottleneckDetected ?? zone.bottleneckDetected,
                  simulationActive: local?.simulationActive ?? zone.simulationActive,
                };
                const serverTime = new Date(nextZone.lastUpdated).valueOf();
                const localTime = local ? new Date(local.lastUpdated).valueOf() : 0;
                return local && localTime > serverTime ? local : nextZone;
              });
              return next;
            });
            setLiveTelemetryAtByZone((current) => {
              const next = { ...current };
              latestZones.forEach((zone) => {
                const incomingTime = new Date(zone.lastUpdated).valueOf();
                const currentTime = new Date(next[zone.id] || 0).valueOf();
                if (Number.isFinite(incomingTime) && incomingTime >= currentTime) {
                  next[zone.id] = zone.lastUpdated;
                }
              });
              return next;
            });
          }),
          api<RiskEvent[]>(`/api/zones/${selectedZoneId}/risk-events?limit=50`, { cache: "no-store" }).then((recent) => {
            setEvents((current) => {
              const merged = new Map(current.map((event) => [event.timestamp, event]));
              recent.forEach((event) => merged.set(event.timestamp, event));
              return [...merged.values()]
                .sort((a, b) => new Date(b.timestamp).valueOf() - new Date(a.timestamp).valueOf())
                .slice(0, 50);
            });
            setHotspotEventsByZone((current) => {
              const existing = current[selectedZoneId] || [];
              const merged = new Map(existing.map((event) => [event.timestamp, event]));
              recent.forEach((event) => merged.set(event.timestamp, event));
              return {
                ...current,
                [selectedZoneId]: [...merged.values()]
                  .sort((a, b) => new Date(b.timestamp).valueOf() - new Date(a.timestamp).valueOf())
                  .slice(0, 50),
              };
            });
          }),
        ]);
      } catch {
      } finally {
        refreshInFlight = false;
      }
    };
    void refresh();
    const timer = window.setInterval(refresh, 5000);
    return () => {
      disposed = true;
      window.clearInterval(timer);
    };
  }, [selectedZoneId]);
  useEffect(() => {
    if (!selectedZoneId) {
      setFlowStatus(undefined);
      return;
    }
    loadForecast(selectedZoneId, true);
    loadFlowStatus(selectedZoneId, true);
    const timer = window.setInterval(() => loadFlowStatus(selectedZoneId), DETAIL_UPDATE_WINDOW_MS);
    return () => window.clearInterval(timer);
  }, [loadFlowStatus, loadForecast, selectedZoneId]);
  useEffect(() => {
    const nextAnalysisAt = forecast?.nextAnalysisAt ? new Date(forecast.nextAnalysisAt).valueOf() : NaN;
    if (!selectedZoneId || !Number.isFinite(nextAnalysisAt) || now < nextAnalysisAt || forecastLoading || forecastRefreshAtRef.current === nextAnalysisAt) return;
    forecastRefreshAtRef.current = nextAnalysisAt;
    void loadForecast(selectedZoneId);
  }, [forecast?.nextAnalysisAt, forecastLoading, loadForecast, now, selectedZoneId]);
  useEffect(() => {
    if (selectedZoneId && venue?.id) loadRoutes(selectedZoneId, venue.id);
  }, [selectedZoneId, venue?.id, loadRoutes]);
  useEffect(() => {
    const client = new Client({
      brokerURL: WS_URL,
      reconnectDelay: 5000,
      onConnect: () => {
        setConnected(true);
        client.subscribe("/topic/risk-updates", (message: IMessage) => {
          const event = JSON.parse(message.body) as RiskEvent;
          const eventZoneId = Number(event.zoneId);
          if (typeof window !== "undefined") {
            (window as Window & { DEBUG_LAST_EVENT?: { zoneId: number; densityScore: number; peopleCount?: number; timestamp: string; receivedAt: number } }).DEBUG_LAST_EVENT = {
              zoneId: eventZoneId,
              densityScore: event.densityScore,
              peopleCount: event.peopleCount,
              timestamp: event.timestamp,
              receivedAt: Date.now(),
            };
          }
          setZones((current) => {
            const next = current.map((zone) =>
              Number(zone.id) === eventZoneId
                ? {
                    ...zone,
                    currentDensity: event.densityScore,
                    currentPeopleCount:
                      event.peopleCount ?? zone.currentPeopleCount ?? 0,
                    currentRiskLevel: event.riskLevel,
                    bottleneckDetected: event.bottleneckDetected ?? zone.bottleneckDetected,
                    simulationActive: event.source === "SIMULATION",
                    lastUpdated: event.timestamp,
                  }
                : zone,
            );
            return next;
          });
          setHealth((current) =>
            current
              ? { ...current, totalRiskEvents: current.totalRiskEvents + 1 }
              : current,
          );
          setLiveTelemetryAtByZone((current) => ({ ...current, [eventZoneId]: event.timestamp }));
          if (eventZoneId === Number(selectedZoneIdRef.current))
            setEvents((current) => [event, ...current].slice(0, 50));
          setHotspotEventsByZone((current) => ({
            ...current,
            [eventZoneId]: [event, ...(current[eventZoneId] || [])].slice(0, 50),
          }));
        });
        client.subscribe("/topic/risk-forecasts", (message: IMessage) => {
          const nextForecast = JSON.parse(message.body) as RiskForecast;
          const selected = nextForecast.zoneId === selectedZoneIdRef.current;
          const currentForecast = forecastRef.current;
          if (!selected) return;
          recordDetailForecast(nextForecast);
          if (nextForecast.lastTelemetryAt) {
            setLiveTelemetryAtByZone((current) => {
              const incomingTime = new Date(nextForecast.lastTelemetryAt!).valueOf();
              const currentTime = new Date(current[nextForecast.zoneId] || 0).valueOf();
              return Number.isFinite(incomingTime) && incomingTime >= currentTime
                ? { ...current, [nextForecast.zoneId]: nextForecast.lastTelemetryAt! }
                : current;
            });
          }
          const committedSnapshotChanged = !currentForecast || currentForecast.analysisGeneratedAt !== nextForecast.analysisGeneratedAt;
          if (committedSnapshotChanged) {
            setForecast(nextForecast);
            setForecastUpdatedAt(Date.now());
          }
        });
      },
      onDisconnect: () => setConnected(false),
      onWebSocketError: () => setConnected(false),
      onStompError: () => setConnected(false),
    });
    client.activate();
    stompRef.current = client;
    return () => {
      client.deactivate();
      stompRef.current = null;
    };
  }, [recordDetailForecast]);
  function takeAction(id: number, zoneId: number | null | undefined, zoneName: string | null | undefined, message: string) {
    const params = new URLSearchParams();
    if (zoneId) params.set("zoneId", String(zoneId));
    params.set("message", `${zoneName || "Venue-wide"} — ${message}`);
    params.set("recommendationId", String(id));
    window.location.assign(`/console/admin?${params.toString()}`);
  }
  if (loading)
    return (
      <AppShell
        user={user}
        title="Dashboard"
        active="Dashboard"
        navItems={navItems}
      >
        <Spinner label="Loading command workspace" />
      </AppShell>
    );
  if (error)
    return (
      <AppShell
        user={user}
        title="Dashboard"
        active="Dashboard"
        navItems={navItems}
      >
        <div className={styles.errorState}>
          <span className={styles.kicker}>BACKEND UNAVAILABLE</span>
          <h2>Command data is offline</h2>
          <p>{error}</p>
          <Button onClick={loadInitial}>Retry connection</Button>
        </div>
      </AppShell>
    );
  return (
    <AppShell
      user={user}
      title="Dashboard"
      subtitle={`${venue?.name || "Venue workspace"} and live safety signals`}
      active="Dashboard"
      navItems={navItems}
      assistantZones={zones.map((zone) => ({ id: zone.id, name: zone.name }))}
    >
      {selectedZone?.simulationActive && <div className={styles.simulationBanner}>SIMULATION MODE · Deterministic staff drill data is active for this zone. It is not live camera telemetry.</div>}
      <div className={styles.connection}>
        <span
          className={connected ? styles.connectedDot : styles.disconnectedDot}
        />
        {connected ? "Live command link" : "WebSocket disconnected · reconnecting"}
        {telemetryStale && <span className={styles.staleNotice}> · Telemetry stale</span>}
      </div>
      <section className={styles.overviewGrid}>
        <div className={styles.focusCard}>
          <div>
            <span className={styles.kicker}>CURRENT OPERATING STATE</span>
            <h2>
              <span style={{ color: overallMeta.color }}>
                {overallMeta.label}
              </span>{" "}
              risk across venue
            </h2>
            <p>
              {zones.length
                ? `${zones.length} monitored zones are reporting current telemetry.`
                : "No zones are registered in the backend yet."}
            </p>
          </div>
          <div className={styles.focusFooter}>
            <span>
              <i
                className={styles.statusDot}
                style={{ background: overallMeta.color }}
              />
              {connected ? "Signals updating live" : "Signals reconnecting"}
            </span>
            <a href="/console/admin/actions">
              Review response actions <Icon name="arrow" />
            </a>
          </div>
        </div>
        <div className={styles.metricGrid}>
          <Metric
            label="Zones monitored"
            value={health?.totalZones ?? zones.length}
            detail="Registered coverage"
          />
          <Metric
            label="People tracked"
            value={totalHeadcount}
            detail="Live CV headcount"
          />
          <Metric
            label="Fresh signals"
            value={freshSignals}
            detail="Updated in the last 30s"
            tone={freshSignals === zones.length && zones.length ? "success" : "danger"}
          />
          <Metric
            label="Zones requiring attention"
            value={zonesRequiringAttention}
            detail="Medium+ current risk"
            tone={zonesRequiringAttention ? "danger" : "success"}
          />
        </div>
      </section>
      <div className={styles.dashboardStack}>
        <Card className={styles.mapCard}>
          <div className={styles.cardHeader}>
            <div>
              <span className={styles.kicker}>VENUE MAP</span>
              <h2>Live coverage</h2>
              <p>Click a zone to update the workspace context.</p>
            </div>
            <span className={styles.liveLabel}>
              <i className={telemetryStale ? styles.disconnectedDot : styles.connectedDot} />
              {telemetryStale ? "STALE" : "LIVE"}
            </span>
          </div>
          <div className={styles.mapWrap}>
            <LeafletVenueMap
              venue={venue}
              zones={zones}
              selectedId={selectedZoneId}
              onSelect={setSelectedZoneId}
            />
          </div>
        </Card>
        <EarlyWarningPanel forecast={detailedForecast} loading={forecastLoading} error={forecastError} now={now} updatedAt={forecastUpdatedAt} stampedeConfidenceStable={stampedeConfidenceStable} />
        <div className={styles.insightGrid}>
          <FlowIntelligencePanel compact forecast={detailedForecast} flowStatus={flowStatus} route={route} graph={routeGraph} now={now} onViewMore={() => setDetailView("flow")} />
          <SelectedZonePanel compact selectedZone={selectedZone} displayedZone={detailedZone} selectedAnalysis={selectedAnalysis} selectedHotspotSummary={selectedHotspotSummary} analysisBottleneck={analysisBottleneck} liveTelemetryAt={liveTelemetryAt} now={now} onViewMore={() => setDetailView("zone")} />
        </div>
        {detailView === "flow" && <DetailModal title="Flow intelligence" onClose={() => setDetailView(undefined)}><FlowIntelligencePanel forecast={detailedForecast} flowStatus={flowStatus} route={route} graph={routeGraph} now={now} /></DetailModal>}
        {detailView === "zone" && <DetailModal title="Selected zone" onClose={() => setDetailView(undefined)}><SelectedZonePanel selectedZone={selectedZone} displayedZone={detailedZone} selectedAnalysis={selectedAnalysis} selectedHotspotSummary={selectedHotspotSummary} analysisBottleneck={analysisBottleneck} liveTelemetryAt={liveTelemetryAt} now={now} /></DetailModal>}
        <div className={styles.lowerGrid}>
          <Card className={styles.zoneTableCard} id="zones">
          <div className={styles.cardHeader}>
            <div>
              <span className={styles.kicker}>ZONE REGISTER</span>
              <h2>Coverage by zone</h2>
              <p>Choose a row to inspect its current signal.</p>
            </div>
            <span className={styles.tableMeta}>{zones.length} zones · Render count: {zoneTableRenderCountRef.current}</span>
          </div>
          <div className={styles.zoneTableHeader}>
            <span>Zone</span>
            <span>Headcount</span>
            <span>Density</span>
            <span>Status</span>
            <span>Last signal</span>
          </div>
          <div className={styles.zoneTable}>
            {zones.length ? (
              zones.map((zone) => (
                <ZoneRow
                  key={zone.id}
                  zone={zone}
                  selected={zone.id === selectedZoneId}
                  onSelect={() => setSelectedZoneId(zone.id)}
                  now={now}
                  lastSignalAt={liveTelemetryAtByZone[zone.id] || zone.lastUpdated}
                  hotspotSummary={summarizeHotspots(hotspotEventsByZone[zone.id], now)}
                />
              ))
            ) : (
              <div className={styles.tableEmpty}>
                No zones are seeded in the backend yet.
              </div>
            )}
          </div>
          </Card>
          <TrendCard zone={selectedZone} events={events} />
        </div>
      </div>
    </AppShell>
  );
}

export default function Page() {
  const [user, setUser] = useState<UserInfo>();
  useEffect(() => {
    const session = readSession();
    if (!session || session.user.role !== "ADMIN") {
      clearSession();
      window.location.replace("/console/login");
      return;
    }
    setUser(session.user);
  }, []);
  if (!user)
    return <main className={styles.loadingPage}>Checking command access</main>;
  return <ConsoleApp user={user} />;
}
