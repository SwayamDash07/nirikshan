"use client";

import { useMemo } from "react";
import { CircleMarker, MapContainer, Popup, TileLayer } from "react-leaflet";
import styles from "./citizen.module.css";

type RiskLevel = "LOW" | "MEDIUM" | "HIGH" | "CRITICAL";
type Zone = { id: number; name: string; latitude: number; longitude: number; currentDensity: number; currentRiskLevel: RiskLevel; lastUpdated: string };
type Alert = { zoneId: number; severity: RiskLevel; message: string };

const colors: Record<RiskLevel, string> = { LOW: "#2caa72", MEDIUM: "#d69a16", HIGH: "#e97825", CRITICAL: "#d84b54" };

export default function CitizenMiniMap({ zones, alerts, location }: { zones: Zone[]; alerts: Alert[]; location?: { lat: number; lng: number } }) {
  const center = useMemo<[number, number]>(() => location ? [location.lat, location.lng] : zones.length ? [zones.reduce((sum, zone) => sum + zone.latitude, 0) / zones.length, zones.reduce((sum, zone) => sum + zone.longitude, 0) / zones.length] : [20.3641, 85.8163], [location, zones]);
  const maxDensity = Math.max(...zones.map((zone) => zone.currentDensity), 1);
  return <div className={styles.miniMap}><MapContainer center={center} zoom={17} scrollWheelZoom={false} className={styles.mapFrame}><TileLayer attribution='&copy; OpenStreetMap contributors' url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png" />{location && <CircleMarker center={[location.lat, location.lng]} radius={8} pathOptions={{ color: "#2164d7", fillColor: "#2164d7", fillOpacity: 1, weight: 4 }}><Popup>You are here</Popup></CircleMarker>}{zones.map((zone) => { const active = alerts.find((alert) => alert.zoneId === zone.id); const level = active?.severity || zone.currentRiskLevel; const color = colors[level]; return <CircleMarker key={zone.id} center={[zone.latitude, zone.longitude]} radius={7 + Math.min(10, (zone.currentDensity / maxDensity) * 10)} pathOptions={{ color, fillColor: color, fillOpacity: .75, weight: 2, className: level === "CRITICAL" ? "critical-pulse" : "" }}><Popup><strong>{zone.name}</strong><br />{level} · {zone.currentDensity.toFixed(2)} p/m²</Popup></CircleMarker>; })}</MapContainer><div className={styles.mapCaption}><span><i className={styles.userDot} />Your location</span><span><i className={styles.zoneDot} />Campus zones</span></div></div>;
}
