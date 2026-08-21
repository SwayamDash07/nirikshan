"use client";

import { useMemo } from "react";
import { CircleMarker, MapContainer, Polyline, Popup, TileLayer } from "react-leaflet";
import { riskColors, ZoneRiskMarker, type RiskLevel, type ZoneMarkerData } from "./components/ZoneRiskMarker";
import styles from "./console/console.module.css";

type Zone = ZoneMarkerData & { currentPeopleCount: number };
type Venue = { name: string; latitude?: number; longitude?: number };
type Point = [number, number];
const MAIN_GATE_ZONE_ID = 1;
const MAIN_GATE_EXIT_ZONE_ID = 6;
const METERS_PER_LATITUDE_DEGREE = 111320;
const COLLISION_DISTANCE_METERS = 65;
const RISK_RANK: Record<RiskLevel, number> = { LOW: 0, MEDIUM: 1, HIGH: 2, CRITICAL: 3 };

function formatTime(value: string) {
  const date = new Date(value);
  return Number.isNaN(date.valueOf()) ? "Not available" : date.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit", second: "2-digit" });
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
  const mainGate = zones.find((zone) => zone.id === MAIN_GATE_ZONE_ID);
  const mainGateExit = zones.find((zone) => zone.id === MAIN_GATE_EXIT_ZONE_ID);
  if (!mainGate || !mainGateExit) {
    return { path: [] as Point[], blockedIds: new Set<number>(), startName: "Campus", exitName: "Exit unavailable", recommendedExitId: undefined, mutedExitId: undefined, shelterInPlace: false, alternate: false };
  }

  const mainGateBlocked = RISK_RANK[mainGate.currentRiskLevel] >= RISK_RANK.MEDIUM;
  const mainGateExitBlocked = RISK_RANK[mainGateExit.currentRiskLevel] >= RISK_RANK.MEDIUM;
  if (mainGateBlocked && mainGateExitBlocked) {
    return { path: [] as Point[], blockedIds: new Set<number>(), startName: "Campus", exitName: "Shelter in place", recommendedExitId: undefined, mutedExitId: undefined, shelterInPlace: true, alternate: false };
  }

  const recommendedExit = mainGateExitBlocked ? mainGate : mainGateExit;
  const start = recommendedExit.id === MAIN_GATE_EXIT_ZONE_ID
    ? mainGate
    : zones.find((zone) => zone.id !== MAIN_GATE_ZONE_ID && zone.id !== MAIN_GATE_EXIT_ZONE_ID) || mainGateExit;
  const finish: Point = [recommendedExit.latitude, recommendedExit.longitude];
  const startPoint: Point = [start.latitude, start.longitude];
  const blocked = zones
    .filter((zone) => zone.id !== start.id && zone.id !== recommendedExit.id && RISK_RANK[zone.currentRiskLevel] >= RISK_RANK.HIGH)
    .map((zone) => ({ zone, distance: distanceToSegment([zone.latitude, zone.longitude], startPoint, finish) }))
    .filter(({ distance }) => distance <= 28)
    .sort((first, second) => first.distance - second.distance || first.zone.id - second.zone.id);
  const route: Point[] = [startPoint];
  const line = localMeters(finish, startPoint);
  const length = Math.max(1, Math.hypot(line[0], line[1]));

  blocked.forEach(({ zone }) => {
    const point = localMeters([zone.latitude, zone.longitude], startPoint);
    const side = zone.id % 2 === 0 ? 1 : -1;
    const detourMeters = 34;
    const normal = [-line[1] / length, line[0] / length];
    const detourLocal: Point = [point[0] + normal[0] * detourMeters * side, point[1] + normal[1] * detourMeters * side];
    const latitudeScale = Math.max(0.2, Math.cos(startPoint[0] * Math.PI / 180));
    route.push([
      startPoint[0] + detourLocal[0] / METERS_PER_LATITUDE_DEGREE,
      startPoint[1] + detourLocal[1] / (METERS_PER_LATITUDE_DEGREE * latitudeScale),
    ]);
  });

  route.push(finish);
  return {
    path: route,
    blockedIds: new Set(blocked.map(({ zone }) => zone.id)),
    startName: start.name,
    exitName: recommendedExit.name,
    recommendedExitId: recommendedExit.id,
    mutedExitId: mainGateExitBlocked ? mainGateExit.id : undefined,
    shelterInPlace: false,
    alternate: mainGateExitBlocked,
  };
}

function zonePopup(zone: Zone, blocked: boolean, muted: boolean) {
  return <div className={styles.mapPopup}><span>ZONE {String(zone.id).padStart(2, "0")}</span><strong>{zone.name}</strong>{blocked && <b className={styles.simulationBadge}>ROUTE BYPASS ACTIVE</b>}{muted && <b style={{ color: riskColors[zone.currentRiskLevel] }}>EXIT NOT RECOMMENDED</b>}{zone.simulationActive && <b className={styles.simulationBadge}>SIMULATION MODE</b>}<div><b style={{ color: riskColors[zone.currentRiskLevel] }}>{zone.currentRiskLevel}</b><b>{zone.currentDensity.toFixed(2)} people per m2</b></div><small>Last updated {formatTime(zone.lastUpdated)}</small></div>;
}

export default function LeafletVenueMap({ venue, zones, selectedId, onSelect }: { venue?: Venue; zones: Zone[]; selectedId?: number; onSelect: (id: number) => void }) {
  const center = useMemo<[number, number]>(() => {
    if (venue?.latitude !== undefined && venue.longitude !== undefined) return [venue.latitude, venue.longitude];
    if (zones.length) return [zones.reduce((sum, zone) => sum + zone.latitude, 0) / zones.length, zones.reduce((sum, zone) => sum + zone.longitude, 0) / zones.length];
    return [28.6139, 77.2295];
  }, [venue, zones]);
  const route = useMemo(() => buildRoute(zones), [zones]);

  if (!zones.length) return <div className={styles.emptyPanel}>No mapped zones available yet.</div>;
  const maxHeadcount = Math.max(1, ...zones.map((zone) => zone.currentPeopleCount ?? 0));
  const alternateColor = route.mutedExitId ? riskColors[zones.find((zone) => zone.id === route.mutedExitId)?.currentRiskLevel || "HIGH"] : "var(--risk-medium)";

  return <div className={styles.leafletPanel}>
    <div className={styles.mapTopline}><span className={styles.liveLabel}><span className={styles.connectedDot} /> LIVE</span><span>Live venue telemetry</span><span className={styles.mapCoordinates}>OpenStreetMap, {venue?.name || "Venue map"}</span></div>
    <MapContainer center={center} zoom={16} scrollWheelZoom className={styles.leafletMap}>
      <TileLayer attribution='&copy; OpenStreetMap contributors' url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png" />
      {route.path.length > 1 && <Polyline positions={route.path} pathOptions={{ color: "var(--accent)", weight: 5, opacity: 0.9, lineCap: "round", lineJoin: "round" }} />}
      {zones.map((zone) => {
        const blocked = route.blockedIds.has(zone.id);
        const muted = route.mutedExitId === zone.id;
        if (muted) return <CircleMarker key={zone.id} center={[zone.latitude, zone.longitude]} radius={5} pathOptions={{ color: alternateColor, fillColor: alternateColor, fillOpacity: 0.12, opacity: 0.42, weight: 1, dashArray: "4 3" }} eventHandlers={{ click: () => onSelect(zone.id) }}><Popup>{zonePopup(zone, false, true)}</Popup></CircleMarker>;
        if (blocked) return <CircleMarker key={zone.id} center={[zone.latitude, zone.longitude]} radius={5} pathOptions={{ color: riskColors[zone.currentRiskLevel], fillColor: riskColors[zone.currentRiskLevel], fillOpacity: 0.24, opacity: 0.58, weight: 1 }} eventHandlers={{ click: () => onSelect(zone.id) }}><Popup>{zonePopup(zone, true, false)}</Popup></CircleMarker>;
        return <ZoneRiskMarker key={zone.id} zone={zone} maxScale={maxHeadcount} onSelect={() => onSelect(zone.id)} popup={zonePopup(zone, false, false)} />;
      })}
    </MapContainer>
    {route.shelterInPlace && <div className={styles.forecastNotice} role="status" style={{ border: "1px solid var(--risk-critical)", color: "var(--risk-critical)", background: "var(--risk-critical-soft)" }}><strong>Both exits are currently crowded.</strong> Shelter in place in the nearest building until crowd density clears. Do not attempt to exit through Main Gate or Main Gate Exit.</div>}
    {route.alternate && <div className={styles.forecastNotice} role="status" style={{ border: `1px solid ${alternateColor}`, background: "var(--risk-medium-soft)" }}>Main Gate Exit is crowded. The highlighted route now favors Main Gate.</div>}
    <div className={styles.mapLegend}><span><i style={{ background: riskColors.LOW }} />Normal</span><span><i style={{ background: riskColors.MEDIUM }} />Watch</span><span><i style={{ background: riskColors.HIGH }} />High</span><span><i style={{ background: riskColors.CRITICAL }} />Critical</span><span><i className={styles.simulationLegend} />Simulation</span></div>
    <div className={styles.densityLegend}><strong>Recommended exit</strong><span>{route.shelterInPlace ? "Shelter in place" : `${route.startName} → ${route.exitName}${route.blockedIds.size ? " · bypass active" : route.alternate ? " · alternate exit" : " · primary exit"}`}</span></div>
    <div className={styles.mapScale}><span>OPENSTREETMAP LAYER</span><strong>{zones.length} zones</strong></div>
  </div>;
}
