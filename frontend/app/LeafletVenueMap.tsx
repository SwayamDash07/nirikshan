"use client";

import { Fragment, useMemo } from "react";
import { CircleMarker, MapContainer, Popup, TileLayer } from "react-leaflet";
import styles from "./console/console.module.css";

type RiskLevel = "LOW" | "MEDIUM" | "HIGH" | "CRITICAL";

type Zone = {
  id: number;
  name: string;
  latitude: number;
  longitude: number;
  currentDensity: number;
  currentPeopleCount: number;
  currentRiskLevel: RiskLevel;
  lastUpdated: string;
};

type Venue = {
  name: string;
  latitude?: number;
  longitude?: number;
};

const riskColors: Record<RiskLevel, string> = {
  LOW: "var(--risk-low)",
  MEDIUM: "var(--risk-medium)",
  HIGH: "var(--risk-high)",
  CRITICAL: "var(--risk-critical)",
};

function formatTime(value: string) {
  const date = new Date(value);
  return Number.isNaN(date.valueOf()) ? "Not available" : date.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit", second: "2-digit" });
}

export default function LeafletVenueMap({ venue, zones, selectedId, onSelect }: { venue?: Venue; zones: Zone[]; selectedId?: number; onSelect: (id: number) => void }) {
  const center = useMemo<[number, number]>(() => {
    if (venue?.latitude !== undefined && venue.longitude !== undefined) return [venue.latitude, venue.longitude];
    if (zones.length) return [zones.reduce((sum, zone) => sum + zone.latitude, 0) / zones.length, zones.reduce((sum, zone) => sum + zone.longitude, 0) / zones.length];
    return [28.6139, 77.2295];
  }, [venue, zones]);

  if (!zones.length) return <div className={styles.emptyPanel}>No mapped zones available yet.</div>;
  const maxHeadcount = Math.max(1, ...zones.map((zone) => zone.currentPeopleCount ?? 0));

  return (
    <div className={styles.leafletPanel}>
      <div className={styles.mapTopline}><span className={styles.liveDot} /> Live venue telemetry <span className={styles.mapCoordinates}>OpenStreetMap · {venue?.name || "Venue map"}</span></div>
      <MapContainer center={center} zoom={16} scrollWheelZoom className={styles.leafletMap}>
        <TileLayer attribution='&copy; OpenStreetMap contributors' url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png" />
        {zones.map((zone) => {
          const color = riskColors[zone.currentRiskLevel];
          const headcountSignal = Math.min(1, Math.max(0, (zone.currentPeopleCount ?? 0) / maxHeadcount));
          const coreRadius = 3.5 + headcountSignal * 6;
          const glowRadius = coreRadius * 1.85;
          return (
            <Fragment key={zone.id}>
              <CircleMarker center={[zone.latitude, zone.longitude]} radius={glowRadius} pathOptions={{ color, fillColor: color, fillOpacity: 0.11, weight: 1, className: "nirikshan-heat-marker" }} eventHandlers={{ click: () => onSelect(zone.id) }}>
                <Popup>
                  <div className={styles.mapPopup}>
                    <span>ZONE {String(zone.id).padStart(2, "0")}</span>
                    <strong>{zone.name}</strong>
                    <div><b style={{ color }}>{zone.currentRiskLevel}</b><b>{zone.currentDensity.toFixed(2)} people per m2</b></div>
                    <small>Last updated {formatTime(zone.lastUpdated)}</small>
                  </div>
                </Popup>
              </CircleMarker>
              <CircleMarker center={[zone.latitude, zone.longitude]} radius={coreRadius} pathOptions={{ color, fillColor: color, fillOpacity: 0.96, weight: 1.5, className: "nirikshan-core-marker" }} eventHandlers={{ click: () => onSelect(zone.id) }} />
            </Fragment>
          );
        })}
      </MapContainer>
      <div className={styles.mapLegend}><span><i style={{ background: riskColors.LOW }} />Normal</span><span><i style={{ background: riskColors.MEDIUM }} />Watch</span><span><i style={{ background: riskColors.HIGH }} />High</span><span><i style={{ background: riskColors.CRITICAL }} />Critical</span></div>
      <div className={styles.densityLegend}><strong>Marker scale</strong><span>Circle size follows live headcount</span></div>
      <div className={styles.mapScale}><span>OPENSTREETMAP LAYER</span><strong>{zones.length} zones</strong></div>
    </div>
  );
}
