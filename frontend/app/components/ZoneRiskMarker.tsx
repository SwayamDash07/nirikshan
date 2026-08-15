"use client";

import { Fragment, type ReactNode } from "react";
import { CircleMarker, Popup } from "react-leaflet";

export type RiskLevel = "LOW" | "MEDIUM" | "HIGH" | "CRITICAL";
export type ZoneMarkerData = {
  id: number;
  name: string;
  latitude: number;
  longitude: number;
  currentDensity: number;
  currentPeopleCount?: number;
  currentRiskLevel: RiskLevel;
  lastUpdated: string;
};

export const riskColors: Record<RiskLevel, string> = {
  LOW: "var(--risk-low)", MEDIUM: "var(--risk-medium)", HIGH: "var(--risk-high)", CRITICAL: "var(--risk-critical)",
};

type ZoneRiskMarkerProps = {
  zone: ZoneMarkerData;
  maxScale: number;
  riskLevel?: RiskLevel;
  compact?: boolean;
  popup?: ReactNode;
  onSelect?: () => void;
};

export function ZoneRiskMarker({ zone, maxScale, riskLevel = zone.currentRiskLevel, compact = false, popup, onSelect }: ZoneRiskMarkerProps) {
  const color = riskColors[riskLevel];
  const value = zone.currentPeopleCount ?? zone.currentDensity;
  const signal = Math.min(1, Math.max(0, value / Math.max(1, maxScale)));
  const coreRadius = compact ? 2.75 + signal * 4.5 : 3.5 + signal * 6;
  const glowRadius = coreRadius * (compact ? 1.75 : 1.85);
  const eventHandlers = onSelect ? { click: onSelect } : undefined;

  return <Fragment><CircleMarker center={[zone.latitude, zone.longitude]} radius={glowRadius} pathOptions={{ color, fillColor: color, fillOpacity: 0.11, weight: 1, className: "nirikshan-heat-marker" }} eventHandlers={eventHandlers}>{popup && <Popup>{popup}</Popup>}</CircleMarker><CircleMarker center={[zone.latitude, zone.longitude]} radius={coreRadius} pathOptions={{ color, fillColor: color, fillOpacity: 0.96, weight: 1.5, className: "nirikshan-core-marker" }} eventHandlers={eventHandlers} /></Fragment>;
}
