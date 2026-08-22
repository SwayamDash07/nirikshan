import { routeWaypointsByPair } from "./routeWaypoints";
import { riskColors, type RiskLevel, type ZoneMarkerData } from "../components/ZoneRiskMarker";

export type RoutePoint = [number, number];
export type RouteSegment = { positions: [RoutePoint, RoutePoint]; color: string };
type RouteZone = Pick<ZoneMarkerData, "id" | "name" | "latitude" | "longitude" | "currentRiskLevel">;

const MAIN_GATE_ZONE_ID = 1;
const MAIN_GATE_EXIT_ZONE_ID = 6;
const METERS_PER_LATITUDE_DEGREE = 111320;
const COLLISION_DISTANCE_METERS = 65;
const RISK_RANK: Record<RiskLevel, number> = { LOW: 0, MEDIUM: 1, HIGH: 2, CRITICAL: 3 };

function localMeters(point: RoutePoint, origin: RoutePoint): RoutePoint {
  const latitude = origin[0] * Math.PI / 180;
  return [
    (point[0] - origin[0]) * METERS_PER_LATITUDE_DEGREE,
    (point[1] - origin[1]) * METERS_PER_LATITUDE_DEGREE * Math.cos(latitude),
  ];
}

function distanceToSegment(point: RoutePoint, start: RoutePoint, end: RoutePoint) {
  const localPoint = localMeters(point, start);
  const localEnd = localMeters(end, start);
  const lengthSquared = localEnd[0] ** 2 + localEnd[1] ** 2;
  const ratio = lengthSquared === 0 ? 0 : Math.max(0, Math.min(1, (localPoint[0] * localEnd[0] + localPoint[1] * localEnd[1]) / lengthSquared));
  return Math.hypot(localPoint[0] - localEnd[0] * ratio, localPoint[1] - localEnd[1] * ratio);
}

function distanceToPath(point: RoutePoint, path: RoutePoint[]) {
  return path.slice(0, -1).reduce((nearest, start, index) => Math.min(nearest, distanceToSegment(point, start, path[index + 1])), Number.POSITIVE_INFINITY);
}

function segmentColor(start: RoutePoint, end: RoutePoint, zones: RouteZone[]) {
  const nearbyRisk = zones
    .map((zone) => ({ zone, distance: distanceToSegment([zone.latitude, zone.longitude], start, end) }))
    .filter(({ distance }) => distance <= COLLISION_DISTANCE_METERS)
    .sort((left, right) => RISK_RANK[right.zone.currentRiskLevel] - RISK_RANK[left.zone.currentRiskLevel])[0]?.zone.currentRiskLevel;
  return nearbyRisk && RISK_RANK[nearbyRisk] >= RISK_RANK.MEDIUM ? riskColors[nearbyRisk] : "var(--accent)";
}

function colorSegments(path: RoutePoint[], zones: RouteZone[]): RouteSegment[] {
  return path.slice(0, -1).map((start, index) => {
    const end = path[index + 1];
    return { positions: [start, end], color: segmentColor(start, end, zones) };
  });
}

export function buildCampusRoute(zones: RouteZone[]) {
  const mainGate = zones.find((zone) => zone.id === MAIN_GATE_ZONE_ID);
  const mainGateExit = zones.find((zone) => zone.id === MAIN_GATE_EXIT_ZONE_ID);
  if (!mainGate || !mainGateExit) {
    return { path: [] as RoutePoint[], segments: [] as RouteSegment[], blockedIds: new Set<number>(), startName: "Campus", exitName: "Exit unavailable", recommendedExitId: undefined, mutedExitId: undefined, shelterInPlace: false, alternate: false };
  }

  const mainGateBlocked = RISK_RANK[mainGate.currentRiskLevel] >= RISK_RANK.MEDIUM;
  const mainGateExitBlocked = RISK_RANK[mainGateExit.currentRiskLevel] >= RISK_RANK.MEDIUM;
  if (mainGateBlocked && mainGateExitBlocked) {
    return { path: [] as RoutePoint[], segments: [] as RouteSegment[], blockedIds: new Set<number>(), startName: "Campus", exitName: "Shelter in place", recommendedExitId: undefined, mutedExitId: undefined, shelterInPlace: true, alternate: false };
  }

  const recommendedExit = mainGateExitBlocked ? mainGate : mainGateExit;
  const start = recommendedExit.id === MAIN_GATE_EXIT_ZONE_ID
    ? mainGate
    : zones.find((zone) => zone.id !== MAIN_GATE_ZONE_ID && zone.id !== MAIN_GATE_EXIT_ZONE_ID) || mainGateExit;
  const defaultRoadPath: RoutePoint[] = routeWaypointsByPair["1-6"].map(([latitude, longitude]) => [latitude, longitude]);
  const fallbackPath: RoutePoint[] = [[start.latitude, start.longitude], [recommendedExit.latitude, recommendedExit.longitude]];
  const path = recommendedExit.id === MAIN_GATE_EXIT_ZONE_ID ? defaultRoadPath : fallbackPath;
  const blocked = zones
    .filter((zone) => zone.id !== start.id && zone.id !== recommendedExit.id && RISK_RANK[zone.currentRiskLevel] >= RISK_RANK.HIGH)
    .map((zone) => ({ zone, distance: distanceToPath([zone.latitude, zone.longitude], path) }))
    .filter(({ distance }) => distance <= 28)
    .sort((first, second) => first.distance - second.distance || first.zone.id - second.zone.id);

  return {
    path,
    segments: colorSegments(path, zones),
    blockedIds: new Set(blocked.map(({ zone }) => zone.id)),
    startName: start.name,
    exitName: recommendedExit.name,
    recommendedExitId: recommendedExit.id,
    mutedExitId: mainGateExitBlocked ? mainGateExit.id : undefined,
    shelterInPlace: false,
    alternate: mainGateExitBlocked,
  };
}
