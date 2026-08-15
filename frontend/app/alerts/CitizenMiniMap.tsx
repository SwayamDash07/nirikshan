"use client";

import { useMemo, useState } from "react";
import { CircleMarker, MapContainer, Popup, TileLayer } from "react-leaflet";
import { riskColors, ZoneRiskMarker, type RiskLevel, type ZoneMarkerData } from "../components/ZoneRiskMarker";
import styles from "./citizen.module.css";

type Zone = ZoneMarkerData;
type Alert = { zoneId: number; severity: RiskLevel; message: string };

export default function CitizenMiniMap({ zones, alerts, location }: { zones: Zone[]; alerts: Alert[]; location?: { lat: number; lng: number } }) {
  const center = useMemo<[number, number]>(() => location ? [location.lat, location.lng] : zones.length ? [zones.reduce((sum, zone) => sum + zone.latitude, 0) / zones.length, zones.reduce((sum, zone) => sum + zone.longitude, 0) / zones.length] : [20.3641, 85.8163], [location, zones]);
  const maxScale = Math.max(...zones.map((zone) => zone.currentPeopleCount ?? zone.currentDensity), 1);
  const [mapKey] = useState(() => `customer-map-${Math.random().toString(36).slice(2)}`);
  return <div className={styles.miniMap}><MapContainer key={mapKey} center={center} zoom={17} scrollWheelZoom={false} className={styles.mapFrame}><TileLayer attribution='&copy; OpenStreetMap contributors' url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png" />{location && <CircleMarker center={[location.lat, location.lng]} radius={8} pathOptions={{ color: "var(--primary)", fillColor: "var(--primary)", fillOpacity: 1, weight: 4 }}><Popup>You are here</Popup></CircleMarker>}{zones.map((zone) => { const active = alerts.find((alert) => alert.zoneId === zone.id); const level = active?.severity || zone.currentRiskLevel; return <ZoneRiskMarker key={zone.id} zone={zone} maxScale={maxScale} compact riskLevel={level} popup={<><strong>{zone.name}</strong><br />{level}, {zone.currentDensity.toFixed(2)} people per m2</>} />; })}</MapContainer><div className={styles.mapCaption}><span><i className={styles.userDot} />Your location</span><span><i className={styles.zoneDot} style={{ background: riskColors.LOW }} />Campus zones</span></div></div>;
}
