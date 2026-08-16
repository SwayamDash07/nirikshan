export type CampusRoutePoint = {
  name: string;
  latitude: number;
  longitude: number;
  kind: "ENTRY" | "EXIT" | "LANDMARK" | "ZONE" | "WAYPOINT";
};

export type CampusBuilding = {
  name: string;
  latitude: number;
  longitude: number;
  floors: number;
  kind: "HOSTEL" | "ACADEMIC" | "AMENITY";
  rotation: number;
  footprint: readonly (readonly [number, number])[];
};

export const CAMPUS25_ROUTE: CampusRoutePoint[] = [
  { name: "Main Gate", latitude: 20.36365256893469, longitude: 85.81628323724554, kind: "ENTRY" },
  { name: "Hostel 25 Gate", latitude: 20.364145031341526, longitude: 85.81619190942068, kind: "ZONE" },
  { name: "Towards A Block", latitude: 20.364047357294904, longitude: 85.81623763969318, kind: "WAYPOINT" },
  { name: "A Block Gate", latitude: 20.364354947887005, longitude: 85.81617608892412, kind: "ZONE" },
  { name: "Between A Block and Cafeteria", latitude: 20.364514183812783, longitude: 85.81610514063314, kind: "WAYPOINT" },
  { name: "Cafeteria", latitude: 20.36461975435873, longitude: 85.81587627107363, kind: "ZONE" },
  { name: "Parking Turn", latitude: 20.364867598005716, longitude: 85.81593615116894, kind: "WAYPOINT" },
  { name: "My Mother My Hero Statue", latitude: 20.365035774135677, longitude: 85.8164769174586, kind: "LANDMARK" },
  { name: "Yoga Sculptures", latitude: 20.364989464786223, longitude: 85.8170696805023, kind: "LANDMARK" },
  { name: "Student Parking", latitude: 20.364801789912022, longitude: 85.81741285910654, kind: "LANDMARK" },
  { name: "Staff Parking", latitude: 20.36462630151151, longitude: 85.81777163673826, kind: "LANDMARK" },
  { name: "Front of B Block", latitude: 20.363943844720538, longitude: 85.81797702395042, kind: "LANDMARK" },
  { name: "C Block Front", latitude: 20.363751293866894, longitude: 85.81769364161813, kind: "LANDMARK" },
  { name: "Straight Towards Exit", latitude: 20.363683047937066, longitude: 85.81693708878603, kind: "WAYPOINT" },
  { name: "Main Gate Exit", latitude: 20.363609927264484, longitude: 85.81632092720116, kind: "EXIT" },
];

// Building centers supplied from the user's Google Maps reference. Footprints and
// rotations are traced from the supplied satellite view; heights use the supplied
// floor-count assumptions because no elevation dataset is available.
export const CAMPUS25_BUILDINGS: readonly CampusBuilding[] = [
  {
    name: "Hostel A",
    latitude: 20.364004968242533,
    longitude: 85.81560664556247,
    floors: 7,
    kind: "HOSTEL",
    rotation: 0.62,
    footprint: [[-10, -22], [10, -22], [10, 22], [-10, 22]],
  },
  {
    name: "Hostel C",
    latitude: 20.363737400831692,
    longitude: 85.81579557546787,
    floors: 7,
    kind: "HOSTEL",
    rotation: 0.62,
    footprint: [[-10, -22], [10, -22], [10, 22], [-10, 22]],
  },
  {
    name: "Hostel B",
    latitude: 20.36363188116186,
    longitude: 85.81489916336352,
    floors: 7,
    kind: "HOSTEL",
    rotation: 0.62,
    footprint: [[-10, -22], [10, -22], [10, 22], [-10, 22]],
  },
  {
    name: "Hostel D",
    latitude: 20.363371850239048,
    longitude: 85.81509613283937,
    floors: 7,
    kind: "HOSTEL",
    rotation: 0.62,
    footprint: [[-10, -22], [10, -22], [10, 22], [-10, 22]],
  },
  {
    name: "Academic Building",
    latitude: 20.364434582551713,
    longitude: 85.81696131318206,
    floors: 4,
    kind: "ACADEMIC",
    rotation: 0.12,
    // One concave footprint keeps the visible wings connected instead of
    // drawing overlapping boxes on top of each other.
    footprint: [
      [-68, -48], [-36, -48], [-36, -60], [8, -60], [8, -48],
      [46, -48], [46, -30], [64, -30], [64, -4], [48, -4],
      [48, 22], [66, 22], [66, 48], [32, 48], [32, 36],
      [-4, 36], [-4, 50], [-36, 50], [-36, 35], [-66, 35],
      [-66, 14], [-52, 14], [-52, -12], [-68, -12],
    ],
  },
  {
    name: "Cafe",
    latitude: 20.364536333134023,
    longitude: 85.81589205031318,
    floors: 2,
    kind: "AMENITY",
    rotation: 0.12,
    footprint: [
      [-24, -16], [0, -16], [0, -28], [24, -28], [24, -6],
      [13, -6], [13, 18], [-9, 18], [-9, 10], [-24, 10],
    ],
  },
];

export const FLOOR_HEIGHT_METERS = 3.2;
