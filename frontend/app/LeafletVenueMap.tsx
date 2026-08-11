"use client";

import { useEffect, useMemo } from "react";
import { CircleMarker, MapContainer, Popup, TileLayer, useMap } from "react-leaflet";
import L from "leaflet";
import "leaflet.heat";
import styles from "./dashboard.module.css";

type RiskLevel = "LOW" | "MEDIUM" | "HIGH" | "CRITICAL";

type Zone = {
  id: number;
  name: string;
  latitude: number;
  longitude: number;
  currentDensity: number;
  currentRiskLevel: RiskLevel;
  lastUpdated: string;
};

type Venue = {
  name: string;
  latitude?: number;
  longitude?: number;
};

const riskColors: Record<RiskLevel, string> = {
  LOW: "#24a26a",
  MEDIUM: "#d69a16",
  HIGH: "#e97825",
  CRITICAL: "#d84b54",
};

function formatTime(value: string) {
  const date = new Date(value);
  return Number.isNaN(date.valueOf()) ? "—" : date.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });
}

function HeatLayer({ zones }: { zones: Zone[] }) {
  const map = useMap();
  const points = useMemo(() => {
    const criticalReference = Math.max(...zones.map((zone) => zone.currentDensity), 6);
    return zones.map((zone) => [zone.latitude, zone.longitude, Math.min(1, Math.pow(zone.currentDensity / criticalReference, 0.82))] as [number, number, number]);
  }, [zones]);

  useEffect(() => {
    const heatLayer = (L as typeof L & { heatLayer: (points: [number, number, number][], options: Record<string, unknown>) => L.Layer }).heatLayer(points, {
      radius: 78,
      blur: 54,
      maxZoom: 17,
      minOpacity: 0.22,
      gradient: { 0.05: "#4e9fca", 0.22: "#2caa72", 0.42: "#d6b13a", 0.68: "#e97825", 1: "#d84b54" },
    });
    heatLayer.addTo(map);
    return () => { map.removeLayer(heatLayer); };
  }, [map, points]);

  return null;
}

export default function LeafletVenueMap({ venue, zones, selectedId, onSelect }: { venue?: Venue; zones: Zone[]; selectedId?: number; onSelect: (id: number) => void }) {
  const center = useMemo<[number, number]>(() => {
    if (venue?.latitude !== undefined && venue.longitude !== undefined) return [venue.latitude, venue.longitude];
    if (zones.length) return [zones.reduce((sum, zone) => sum + zone.latitude, 0) / zones.length, zones.reduce((sum, zone) => sum + zone.longitude, 0) / zones.length];
    return [28.6139, 77.2295];
  }, [venue, zones]);

  if (!zones.length) return <div className={styles.emptyPanel}>No mapped zones available yet.</div>;

  const maxDensity = Math.max(...zones.map((zone) => zone.currentDensity), 1);
  return (
    <div className={styles.leafletPanel}>
      <div className={styles.mapTopline}><span className={styles.liveDot} /> Live venue telemetry <span className={styles.mapCoordinates}>OpenStreetMap · {venue?.name || "Venue map"}</span></div>
      <MapContainer center={center} zoom={16} scrollWheelZoom className={styles.leafletMap}>
        <TileLayer attribution='&copy; OpenStreetMap contributors' url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png" />
        <HeatLayer zones={zones} />
        {zones.map((zone) => {
          const color = riskColors[zone.currentRiskLevel];
          const radius = 8 + Math.min(18, (zone.currentDensity / maxDensity) * 18);
          return (
            <CircleMarker key={zone.id} center={[zone.latitude, zone.longitude]} radius={radius} pathOptions={{ color, fillColor: color, fillOpacity: 0.78, weight: 3, className: zone.currentRiskLevel === "CRITICAL" ? "critical-pulse" : "" }} eventHandlers={{ click: () => onSelect(zone.id) }}>
              <Popup>
                <div className={styles.mapPopup}>
                  <span>ZONE {String(zone.id).padStart(2, "0")}</span>
                  <strong>{zone.name}</strong>
                  <div><b style={{ color }}>● {zone.currentRiskLevel}</b><b>{zone.currentDensity.toFixed(2)} p/m²</b></div>
                  <small>Last updated {formatTime(zone.lastUpdated)}</small>
                </div>
              </Popup>
            </CircleMarker>
          );
        })}
      </MapContainer>
      <div className={styles.mapLegend}><span><i style={{ background: riskColors.LOW }} />Normal</span><span><i style={{ background: riskColors.MEDIUM }} />Watch</span><span><i style={{ background: riskColors.CRITICAL }} />Critical</span></div>
      <div className={styles.densityLegend}><strong>Density heat</strong><span><i className={styles.heatLow} />0–2</span><span><i className={styles.heatMedium} />2–4</span><span><i className={styles.heatHigh} />4–6</span><span><i className={styles.heatCritical} />6+ p/m²</span></div>
      <div className={styles.mapScale}><span>OPENSTREETMAP LAYER</span><strong>{zones.length} zones</strong></div>
    </div>
  );
}
