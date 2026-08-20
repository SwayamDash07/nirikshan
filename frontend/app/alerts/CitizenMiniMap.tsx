"use client";

import { useMemo, useState } from "react";
import { CircleMarker, MapContainer, Popup, TileLayer } from "react-leaflet";
import { riskColors, ZoneRiskMarker, type RiskLevel, type ZoneMarkerData } from "../components/ZoneRiskMarker";
import styles from "./citizen.module.css";

type Zone = ZoneMarkerData;
type Alert = { zoneId: number; severity: RiskLevel; message: string };
type Point = [number, number];

const METERS_PER_LATITUDE_DEGREE = 111320;
const COLLISION_DISTANCE_METERS = 65;

function distanceMeters(first: Point, second: Point) {
  const latitude = ((first[0] + second[0]) / 2) * Math.PI / 180;
  const north = (second[0] - first[0]) * METERS_PER_LATITUDE_DEGREE;
  const east = (second[1] - first[1]) * METERS_PER_LATITUDE_DEGREE * Math.cos(latitude);
  return Math.hypot(north, east);
}

function spreadZones(zones: Zone[]) {
  const positions = new Map<number, Point>();
  const assigned = new Set<number>();
  zones.forEach((zone) => {
    if (assigned.has(zone.id)) return;
    const groupIds = new Set<number>([zone.id]);
    let expanded = true;
    while (expanded) {
      expanded = false;
      zones.forEach((candidate) => {
        if (groupIds.has(candidate.id)) return;
        const closeToGroup = zones.some((member) => groupIds.has(member.id) && distanceMeters([candidate.latitude, candidate.longitude], [member.latitude, member.longitude]) <= COLLISION_DISTANCE_METERS);
        if (closeToGroup) {
          groupIds.add(candidate.id);
          expanded = true;
        }
      });
    }
    const group = zones.filter((candidate) => groupIds.has(candidate.id));
    const latitude = group.reduce((sum, candidate) => sum + candidate.latitude, 0) / group.length;
    const longitude = group.reduce((sum, candidate) => sum + candidate.longitude, 0) / group.length;
    const spreadRadius = 14 + group.length * 4;
    group.slice().sort((first, second) => first.id - second.id).forEach((candidate, index) => {
      const angle = group.length === 1 ? 0 : (index / group.length) * Math.PI * 2 - Math.PI / 2;
      const longitudeScale = Math.max(0.2, Math.cos(latitude * Math.PI / 180));
      positions.set(candidate.id, [
        latitude + (Math.sin(angle) * spreadRadius) / METERS_PER_LATITUDE_DEGREE,
        longitude + (Math.cos(angle) * spreadRadius) / (METERS_PER_LATITUDE_DEGREE * longitudeScale),
      ]);
      assigned.add(candidate.id);
    });
  });
  return zones.map((zone) => ({ zone, position: positions.get(zone.id) || [zone.latitude, zone.longitude] as Point }));
}

export default function CitizenMiniMap({ zones, alerts, location }: { zones: Zone[]; alerts: Alert[]; location?: { lat: number; lng: number } }) {
  const center = useMemo<[number, number]>(() => location ? [location.lat, location.lng] : zones.length ? [zones.reduce((sum, zone) => sum + zone.latitude, 0) / zones.length, zones.reduce((sum, zone) => sum + zone.longitude, 0) / zones.length] : [20.3641, 85.8163], [location, zones]);
  const maxScale = Math.max(...zones.map((zone) => zone.currentPeopleCount ?? zone.currentDensity), 1);
  const positionedZones = useMemo(() => spreadZones(zones), [zones]);
  const [mapKey] = useState(() => `customer-map-${Math.random().toString(36).slice(2)}`);
  return <div className={styles.miniMap}><MapContainer key={mapKey} center={center} zoom={17} scrollWheelZoom={false} className={styles.mapFrame}><TileLayer attribution='&copy; OpenStreetMap contributors' url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png" />{location && <CircleMarker center={[location.lat, location.lng]} radius={8} pathOptions={{ color: "var(--primary)", fillColor: "var(--primary)", fillOpacity: 1, weight: 4 }}><Popup>You are here</Popup></CircleMarker>}{positionedZones.map(({ zone, position }) => { const active = alerts.find((alert) => alert.zoneId === zone.id); const level = active?.severity || zone.currentRiskLevel; return <ZoneRiskMarker key={zone.id} zone={{ ...zone, latitude: position[0], longitude: position[1] }} maxScale={maxScale} compact riskLevel={level} popup={<><strong>{zone.name}</strong><br />{level}, {zone.currentDensity.toFixed(2)} people per m2</>} />; })}</MapContainer><div className={styles.mapCaption}><span><i className={styles.userDot} />Your location</span><span><i className={styles.zoneDot} style={{ background: riskColors.LOW }} />Campus zones</span></div></div>;
}
