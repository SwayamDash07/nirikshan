import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const routePath = path.join(root, "app", "console", "campus25Route.ts");
const mapPath = path.join(root, "app", "console", "Campus3DMap.tsx");
const manifestPath = path.join(root, "public", "campus-reference", "manifest.json");
const route = fs.readFileSync(routePath, "utf8");
const map = fs.readFileSync(mapPath, "utf8");
const manifest = JSON.parse(fs.readFileSync(manifestPath, "utf8"));
const errors = [];

function requireText(file, text, message) {
  if (!file.includes(text)) errors.push(message);
}

for (const name of ["Academic Building"]) {
  requireText(route, `name: "${name}"`, `building coordinate/model entry missing: ${name}`);
}
const buildingBlock = route.split("export const CAMPUS25_BUILDINGS")[1]?.split("export const CAMPUS25_GATES")[0] ?? "";
for (const removedName of ["Hostel A", "Hostel B", "Hostel C", "Hostel D", "Cafe"]) {
  if (buildingBlock.includes(`name: "${removedName}"`)) errors.push(`removed building is still present in the 3D building model: ${removedName}`);
}

for (const [name, latitude, longitude] of [
  ["Main Gate", "20.36366814775126", "85.81626264649513"],
  ["Main Gate Exit", "20.36360968378996", "85.81631763177884"],
  ["C Block Gate", "20.36376025781561", "85.81713519590612"],
]) {
  requireText(route, `name: "${name}"`, `gate entry missing: ${name}`);
  requireText(route, latitude, `gate latitude mismatch: ${name}`);
  requireText(route, longitude, `gate longitude mismatch: ${name}`);
}

for (const name of ["Cafeteria", "A Block Gate", "Hostel 25 Gate", "Main Gate Exit"]) {
  requireText(route, `name: "${name}"`, `zone/exit alignment entry missing: ${name}`);
}

requireText(route, "export const CAMPUS25_ROUTE_SEGMENTS", "route connectivity graph is missing");
requireText(route, "validateCampus25Model", "runtime model validation is missing");
requireText(map, "CAMPUS25_ROUTE_SEGMENTS", "3D scene is not consuming route segments");
requireText(map, "CAMPUS25_GATES.filter", "3D scene is not rendering gate locations");
requireText(map, "fetch(\"/campus-reference/manifest.json\")", "reference/debug manifest loading is missing");
requireText(map, "window.innerWidth < 700 ? 1.25 : 1.75", "mobile/desktop pixel-ratio performance cap is missing");
requireText(map, "shadow.mapSize.set(1024, 1024)", "shadow-map performance cap is missing");
requireText(map, "new THREE.InstancedMesh", "repeated wall railing geometry is not instanced");
if (map.includes("TubeGeometry")) errors.push("route must use explicit navigable segments instead of an opaque TubeGeometry");

for (const category of ["building", "entrance", "pathway", "road", "landmark", "viewpoint"]) {
  if (!Array.isArray(manifest.groups?.[category])) errors.push(`photo organization category missing: ${category}`);
}
if (!Array.isArray(manifest.photos) || !Array.isArray(manifest.pdfs)) errors.push("photo/PDF manifest arrays are missing");
if (manifest.pageCount !== 242) errors.push("attached PDF page audit must cover all 242 pages");

if (errors.length) {
  console.error(errors.map((error) => `FAIL: ${error}`).join("\n"));
  process.exitCode = 1;
} else {
  console.log("Campus model validation passed: building/gate coordinates, route connectivity, exits, zones, references, loading hooks, and performance caps are present.");
}
