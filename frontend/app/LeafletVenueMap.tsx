"use client";

import { useMemo } from "react";
import { CircleMarker, MapContainer, Polyline, Popup, TileLayer } from "react-leaflet";
import { riskColors, ZoneRiskMarker, type ZoneMarkerData } from "./components/ZoneRiskMarker";
import styles from "./console/console.module.css";

type Zone = ZoneMarkerData & { currentPeopleCount: number };
type Venue = { name: string; latitude?: number; longitude?: number };
type Point = [number, number];
type PositionedZone = { zone: Zone; position: Point };

const METERS_PER_LATITUDE_DEGREE = 111320;
const COLLISION_DISTANCE_METERS = 65;
const RISK_RANK: Record<Zone["currentRiskLevel"], number> = { LOW: 0, MEDIUM: 1, HIGH: 2, CRITICAL: 3 };

function formatTime(value: string) {
  const date = new Date(value);
  return Number.isNaN(date.valueOf()) ? "Not available" : date.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit", second: "2-digit" });
}

function distanceMeters(first: Point, second: Point) {
  const latitude = ((first[0] + second[0]) / 2) * Math.PI / 180;
  const north = (second[0] - first[0]) * METERS_PER_LATITUDE_DEGREE;
  const east = (second[1] - first[1]) * METERS_PER_LATITUDE_DEGREE * Math.cos(latitude);
  return Math.hypot(north, east);
}

function spreadZones(zones: Zone[]): PositionedZone[] {
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

    group
      .slice()
      .sort((first, second) => first.id - second.id)
      .forEach((candidate, index) => {
        const angle = group.length === 1 ? 0 : (index / group.length) * Math.PI * 2 - Math.PI / 2;
        const longitudeScale = Math.max(0.2, Math.cos(latitude * Math.PI / 180));
        positions.set(candidate.id, [
          latitude + (Math.sin(angle) * spreadRadius) / METERS_PER_LATITUDE_DEGREE,
          longitude + (Math.cos(angle) * spreadRadius) / (METERS_PER_LATITUDE_DEGREE * longitudeScale),
        ]);
        assigned.add(candidate.id);
      });
  });

  return zones.map((zone) => ({ zone, position: positions.get(zone.id) || [zone.latitude, zone.longitude] }));
}

function localMeters(point: Point, origin: Point): Point {
  const latitude = origin[0] * Math.PI / 180;
  return [
    (point[0] - origin[0]) * METERS_PER_LATITUDE_DEGREE,
    (point[1] - origin[1]) * METERS_PER_LATITUDE_DEGREE * Math.cos(latitude),
  ];
}

function distanceToSegment(point: Point, start: Point, end: Point) {
  const localPoint = localMeters(point, start);
  const localEnd = localMeters(end, start);
  const lengthSquared = localEnd[0] ** 2 + localEnd[1] ** 2;
  const ratio = lengthSquared === 0 ? 0 : Math.max(0, Math.min(1, (localPoint[0] * localEnd[0] + localPoint[1] * localEnd[1]) / lengthSquared));
  return Math.hypot(localPoint[0] - localEnd[0] * ratio, localPoint[1] - localEnd[1] * ratio);
}

function buildRoute(zones: Zone[]) {
  const entry = zones.find((zone) => /^(main gate|campus gate a)$/i.test(zone.name)) || zones[0];
  const exit = zones.find((zone) => /^(c block gate|main gate exit|campus gate c)$/i.test(zone.name)) || zones[zones.length - 1];
  if (!entry || !exit) return { path: [] as Point[], blockedIds: new Set<number>(), entryName: "Campus Gate A", exitName: "Campus Gate C" };

  const start: Point = [entry.latitude, entry.longitude];
  const finish: Point = [exit.latitude, exit.longitude];
  const blocked = zones
    .filter((zone) => zone.id !== entry.id && zone.id !== exit.id && RISK_RANK[zone.currentRiskLevel] >= RISK_RANK.HIGH)
    .map((zone) => ({ zone, distance: distanceToSegment([zone.latitude, zone.longitude], start, finish) }))
    .filter(({ distance }) => distance <= 28)
    .sort((first, second) => first.distance - second.distance || first.zone.id - second.zone.id);
  const route: Point[] = [start];
  const line = localMeters(finish, start);
  const length = Math.max(1, Math.hypot(line[0], line[1]));

  blocked.forEach(({ zone }) => {
    const point = localMeters([zone.latitude, zone.longitude], start);
    const side = zone.id % 2 === 0 ? 1 : -1;
    const detourMeters = 34;
    const normal = [-line[1] / length, line[0] / length];
    const detourLocal: Point = [point[0] + normal[0] * detourMeters * side, point[1] + normal[1] * detourMeters * side];
    const latitudeScale = Math.max(0.2, Math.cos(start[0] * Math.PI / 180));
    route.push([
      start[0] + detourLocal[0] / METERS_PER_LATITUDE_DEGREE,
      start[1] + detourLocal[1] / (METERS_PER_LATITUDE_DEGREE * latitudeScale),
    ]);
  });

  route.push(finish);
  return { path: route, blockedIds: new Set(blocked.map(({ zone }) => zone.id)), entryName: "Campus Gate A", exitName: "Campus Gate C" };
}

function zonePopup(zone: Zone, blocked: boolean) {
  return <div className={styles.mapPopup}><span>ZONE {String(zone.id).padStart(2, "0")}</span><strong>{zone.name}</strong>{blocked && <b className={styles.simulationBadge}>ROUTE BYPASS ACTIVE</b>}{zone.simulationActive && <b className={styles.simulationBadge}>SIMULATION MODE</b>}<div><b style={{ color: riskColors[zone.currentRiskLevel] }}>{zone.currentRiskLevel}</b><b>{zone.currentDensity.toFixed(2)} people per m2</b></div><small>Last updated {formatTime(zone.lastUpdated)}</small></div>;
}

export default function LeafletVenueMap({ venue, zones, selectedId, onSelect }: { venue?: Venue; zones: Zone[]; selectedId?: number; onSelect: (id: number) => void }) {
  const center = useMemo<[number, number]>(() => {
    if (venue?.latitude !== undefined && venue.longitude !== undefined) return [venue.latitude, venue.longitude];
    if (zones.length) return [zones.reduce((sum, zone) => sum + zone.latitude, 0) / zones.length, zones.reduce((sum, zone) => sum + zone.longitude, 0) / zones.length];
    return [28.6139, 77.2295];
  }, [venue, zones]);
  const positionedZones = useMemo(() => spreadZones(zones), [zones]);
  const route = useMemo(() => buildRoute(zones), [zones]);

  if (!zones.length) return <div className={styles.emptyPanel}>No mapped zones available yet.</div>;
  const maxHeadcount = Math.max(1, ...zones.map((zone) => zone.currentPeopleCount ?? 0));

  return <div className={styles.leafletPanel}>
    <div className={styles.mapTopline}><span className={styles.liveLabel}><span className={styles.connectedDot} /> LIVE</span><span>Live venue telemetry</span><span className={styles.mapCoordinates}>OpenStreetMap, {venue?.name || "Venue map"}</span></div>
    <MapContainer center={center} zoom={16} scrollWheelZoom className={styles.leafletMap}>
      <TileLayer attribution='&copy; OpenStreetMap contributors' url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png" />
      {route.path.length > 1 && <Polyline positions={route.path} pathOptions={{ color: "var(--primary)", weight: 5, opacity: 0.9, lineCap: "round", lineJoin: "round" }} />}
      {positionedZones.map(({ zone, position }) => {
        const blocked = route.blockedIds.has(zone.id);
        if (blocked) return <CircleMarker key={zone.id} center={position} radius={5} pathOptions={{ color: riskColors[zone.currentRiskLevel], fillColor: riskColors[zone.currentRiskLevel], fillOpacity: 0.24, opacity: 0.58, weight: 1 }} eventHandlers={{ click: () => onSelect(zone.id) }}><Popup>{zonePopup(zone, true)}</Popup></CircleMarker>;
        return <ZoneRiskMarker key={zone.id} zone={{ ...zone, latitude: position[0], longitude: position[1] }} maxScale={maxHeadcount} onSelect={() => onSelect(zone.id)} popup={zonePopup(zone, false)} />;
      })}
    </MapContainer>
    <div className={styles.mapLegend}><span><i style={{ background: riskColors.LOW }} />Normal</span><span><i style={{ background: riskColors.MEDIUM }} />Watch</span><span><i style={{ background: riskColors.HIGH }} />High</span><span><i style={{ background: riskColors.CRITICAL }} />Critical</span><span><i className={styles.simulationLegend} />Simulation</span></div>
    <div className={styles.densityLegend}><strong>Route</strong><span>{route.entryName} → {route.exitName}{route.blockedIds.size ? " · bypass active" : " · default path"}</span></div>
    <div className={styles.mapScale}><span>OPENSTREETMAP LAYER</span><strong>{zones.length} zones</strong></div>
  </div>;
}
