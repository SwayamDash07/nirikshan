export type EvidenceStatus = "confirmed" | "unverified";

export type CampusRoutePoint = {
  name: string;
  latitude: number;
  longitude: number;
  kind: "ENTRY" | "EXIT" | "LANDMARK" | "ZONE" | "WAYPOINT";
  evidence: EvidenceStatus;
  source: string;
};

export type CampusBuilding = {
  name: string;
  latitude: number;
  longitude: number;
  floors: number;
  kind: "HOSTEL" | "ACADEMIC" | "AMENITY";
  rotation: number;
  footprint: readonly (readonly [number, number])[];
  evidence: EvidenceStatus;
  source: string;
  modellingNote: string;
};

export type CampusGate = {
  name: string;
  latitude: number;
  longitude: number;
  kind: "ENTRY" | "EXIT" | "ACCESS";
  evidence: EvidenceStatus;
  source: string;
};

export type CampusArea = {
  name: string;
  latitude: number;
  longitude: number;
  kind: "ZONE" | "RESTRICTED" | "SECURITY";
  evidence: EvidenceStatus;
  source: string;
  modellingNote: string;
};

export type CampusRouteSegment = {
  from: string;
  to: string;
  kind: "ROAD" | "PATH";
  widthMeters: number;
  boundaryStyle: "LOW_WALL_BLACK_RAILING" | "NONE";
  evidence: EvidenceStatus;
  source: string;
};

const EXISTING_COORDINATE_DATA = "existing app/backend coordinate data";
const EXISTING_ROUTE_DATA = "existing Campus 25 route data";

export const CAMPUS25_ROUTE: CampusRoutePoint[] = [
  { name: "Main Gate", latitude: 20.36366814775126, longitude: 85.81626264649513, kind: "ENTRY", evidence: "confirmed", source: "backend zone seed + V15 gate migration" },
  { name: "Hostel 25 Gate", latitude: 20.364145031341526, longitude: 85.81619190942068, kind: "ZONE", evidence: "confirmed", source: EXISTING_COORDINATE_DATA },
  { name: "Towards A Block", latitude: 20.364047357294904, longitude: 85.81623763969318, kind: "WAYPOINT", evidence: "confirmed", source: EXISTING_ROUTE_DATA },
  { name: "A Block Gate", latitude: 20.364354947887005, longitude: 85.81617608892412, kind: "ZONE", evidence: "confirmed", source: "backend zone seed" },
  { name: "Between A Block and Cafeteria", latitude: 20.364514183812783, longitude: 85.81610514063314, kind: "WAYPOINT", evidence: "confirmed", source: EXISTING_ROUTE_DATA },
  { name: "Cafeteria", latitude: 20.36461975435873, longitude: 85.81587627107363, kind: "ZONE", evidence: "confirmed", source: "backend zone seed" },
  { name: "Parking Turn", latitude: 20.364867598005716, longitude: 85.81593615116894, kind: "WAYPOINT", evidence: "confirmed", source: EXISTING_ROUTE_DATA },
  { name: "My Mother My Hero Statue", latitude: 20.365035774135677, longitude: 85.8164769174586, kind: "LANDMARK", evidence: "confirmed", source: EXISTING_ROUTE_DATA },
  { name: "Yoga Sculptures", latitude: 20.364989464786223, longitude: 85.8170696805023, kind: "LANDMARK", evidence: "confirmed", source: EXISTING_ROUTE_DATA },
  { name: "Student Parking", latitude: 20.364801789912022, longitude: 85.81741285910654, kind: "LANDMARK", evidence: "confirmed", source: EXISTING_ROUTE_DATA },
  { name: "Staff Parking", latitude: 20.36462630151151, longitude: 85.81777163673826, kind: "LANDMARK", evidence: "confirmed", source: EXISTING_ROUTE_DATA },
  { name: "Front of B Block", latitude: 20.363943844720538, longitude: 85.81797702395042, kind: "LANDMARK", evidence: "confirmed", source: EXISTING_ROUTE_DATA },
  { name: "C Block Front", latitude: 20.363751293866894, longitude: 85.81769364161813, kind: "LANDMARK", evidence: "confirmed", source: EXISTING_ROUTE_DATA },
  { name: "Straight Towards Exit", latitude: 20.363683047937066, longitude: 85.81693708878603, kind: "WAYPOINT", evidence: "confirmed", source: EXISTING_ROUTE_DATA },
  { name: "Main Gate Exit", latitude: 20.36360968378996, longitude: 85.81631763177884, kind: "EXIT", evidence: "confirmed", source: "backend zone seed + V15 gate migration" },
];

export const CAMPUS25_BUILDINGS: readonly CampusBuilding[] = [
  {
    // User-requested visual alignment nudge: approximately 15 m south toward the lower road arc.
    // The route, gates, and all other supplied coordinates remain unchanged.
    name: "Academic Building", latitude: 20.364298582551713, longitude: 85.81696131318206, floors: 4, kind: "ACADEMIC", rotation: 0.12,
    footprint: [[-68, -48], [-36, -48], [-36, -60], [8, -60], [8, -48], [46, -48], [46, -30], [64, -30], [64, -4], [48, -4], [48, 22], [66, 22], [66, 48], [32, 48], [32, 36], [-4, 36], [-4, 50], [-36, 50], [-36, 35], [-66, 35], [-66, 14], [-52, 14], [-52, -12], [-68, -12]],
    evidence: "unverified", source: "existing model footprint + attached PDF pages 61-89 and 109-242", modellingNote: "Repeated photos support a white/gray four-level facade, blue glazing, vertical fins, landscaped frontage, and Block-A/Block-B arch entrances. Exact footprint and coordinate-to-photo alignment still require a survey or per-photo coordinates.",
  },
];

export const CAMPUS25_GATES: readonly CampusGate[] = [
  { name: "Main Gate", latitude: 20.36366814775126, longitude: 85.81626264649513, kind: "ENTRY", evidence: "confirmed", source: "backend zone seed + V15 gate migration" },
  { name: "Main Gate Exit", latitude: 20.36360968378996, longitude: 85.81631763177884, kind: "EXIT", evidence: "confirmed", source: "backend zone seed + V15 gate migration" },
  { name: "Hostel 25 Gate", latitude: 20.364145031341526, longitude: 85.81619190942068, kind: "ACCESS", evidence: "confirmed", source: "backend zone seed" },
  { name: "A Block Gate", latitude: 20.364354947887005, longitude: 85.81617608892412, kind: "ACCESS", evidence: "confirmed", source: "backend zone seed" },
  { name: "C Block Gate", latitude: 20.36376025781561, longitude: 85.81713519590612, kind: "ACCESS", evidence: "confirmed", source: "backend zone seed + V14 migration" },
];

export const CAMPUS25_AREAS: readonly CampusArea[] = [
  ...CAMPUS25_ROUTE.filter((point) => point.kind === "ZONE").map((point) => ({ name: point.name, latitude: point.latitude, longitude: point.longitude, kind: "ZONE" as const, evidence: point.evidence, source: point.source, modellingNote: "Coordinate-backed operational zone; exact boundary is unverified without a photo, survey, or GIS polygon." })),
  { name: "Restricted area boundary", latitude: 20.3643, longitude: 85.8172, kind: "RESTRICTED", evidence: "unverified", source: "no coordinate supplied", modellingNote: "Not rendered as a physical boundary until an authoritative polygon or gate sequence is provided." },
  { name: "Security post", latitude: 20.36366814775126, longitude: 85.81626264649513, kind: "SECURITY", evidence: "unverified", source: "no security-post coordinate supplied", modellingNote: "Listed at the entrance for operational labeling only; exact post location must be confirmed before placement." },
];

export const CAMPUS25_ROUTE_SEGMENTS: readonly CampusRouteSegment[] = CAMPUS25_ROUTE.slice(0, -1).map((point, index) => ({
  from: point.name,
  to: CAMPUS25_ROUTE[index + 1].name,
  kind: index === 0 || index === CAMPUS25_ROUTE.length - 2 ? "ROAD" : "PATH",
  widthMeters: index === 0 || index === CAMPUS25_ROUTE.length - 2 ? 7 : 4,
  boundaryStyle: index >= 1 && index <= 12 ? "LOW_WALL_BLACK_RAILING" : "NONE",
  evidence: point.evidence === "confirmed" && CAMPUS25_ROUTE[index + 1].evidence === "confirmed" ? "confirmed" : "unverified",
  source: point.source,
}));

export const CAMPUS25_UNVERIFIED_FEATURES = [
  "Photo-derived building facade details",
  "Wall and boundary geometry",
  "Tree locations and species",
  "Security-post locations",
  "Restricted-area polygon",
  "Building entrances other than coordinate-backed gates",
] as const;

export const FLOOR_HEIGHT_METERS = 3.2;

export function validateCampus25Model(): string[] {
  const errors: string[] = [];
  const points = new Set(CAMPUS25_ROUTE.map((point) => point.name));
  const buildings = new Set(CAMPUS25_BUILDINGS.map((building) => building.name));
  const gates = new Set(CAMPUS25_GATES.map((gate) => gate.name));
  const finite = (value: number) => Number.isFinite(value);

  for (const point of CAMPUS25_ROUTE) {
    if (!finite(point.latitude) || !finite(point.longitude)) errors.push(`Invalid route coordinate: ${point.name}`);
  }
  for (const building of CAMPUS25_BUILDINGS) {
    if (!finite(building.latitude) || !finite(building.longitude)) errors.push(`Invalid building coordinate: ${building.name}`);
    if (building.footprint.length < 4) errors.push(`Building footprint is too small: ${building.name}`);
  }
  for (const gate of CAMPUS25_GATES) {
    if (!finite(gate.latitude) || !finite(gate.longitude)) errors.push(`Invalid gate coordinate: ${gate.name}`);
  }
  if (CAMPUS25_ROUTE[0]?.kind !== "ENTRY" || CAMPUS25_ROUTE.at(-1)?.kind !== "EXIT") errors.push("Route must start at an entry and end at an exit");
  for (const segment of CAMPUS25_ROUTE_SEGMENTS) {
    if (!points.has(segment.from) || !points.has(segment.to)) errors.push(`Route segment references an unknown point: ${segment.from} -> ${segment.to}`);
    if (segment.widthMeters <= 0) errors.push(`Route segment has no navigable width: ${segment.from} -> ${segment.to}`);
  }
  for (const gateName of ["Main Gate", "Main Gate Exit"]) {
    if (!gates.has(gateName)) errors.push(`Required gate is missing: ${gateName}`);
  }
  for (const area of CAMPUS25_AREAS.filter((area) => area.kind === "ZONE")) {
    if (!points.has(area.name)) errors.push(`Zone is not aligned to a route point: ${area.name}`);
  }
  if (buildings.size !== CAMPUS25_BUILDINGS.length) errors.push("Building names must be unique");
  return errors;
}
