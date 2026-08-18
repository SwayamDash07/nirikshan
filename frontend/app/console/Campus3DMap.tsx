"use client";

import { useEffect, useRef, useState } from "react";
import * as THREE from "three";
import { OrbitControls } from "three/examples/jsm/controls/OrbitControls.js";
import {
  CAMPUS25_AREAS,
  CAMPUS25_BUILDINGS,
  CAMPUS25_GATES,
  CAMPUS25_ROUTE,
  CAMPUS25_ROUTE_SEGMENTS,
  FLOOR_HEIGHT_METERS,
  validateCampus25Model,
  type CampusBuilding,
  type CampusRoutePoint,
  type EvidenceStatus,
} from "./campus25Route";
import styles from "./campus3d.module.css";

const EARTH_METERS_PER_DEGREE_LATITUDE = 110_540;
const EARTH_METERS_PER_DEGREE_LONGITUDE = 111_320;
const MODEL_CENTER = {
  latitude: CAMPUS25_ROUTE.reduce((sum, point) => sum + point.latitude, 0) / CAMPUS25_ROUTE.length,
  longitude: CAMPUS25_ROUTE.reduce((sum, point) => sum + point.longitude, 0) / CAMPUS25_ROUTE.length,
};

type ReferenceEntry = {
  path: string;
  category: string;
  areas: string[];
  page: number | string | null;
  landmarks: string[];
  notes?: string;
};

type ReferenceManifest = {
  status: string;
  photos: ReferenceEntry[];
  pdfs: ReferenceEntry[];
  landmarks: { name: string; area: string; status: string; sources: string[] }[];
  notes: string[];
};

const EMPTY_MANIFEST: ReferenceManifest = { status: "missing", photos: [], pdfs: [], landmarks: [], notes: [] };
const HIDDEN_MODEL_POINT_NAMES = new Set(["Hostel 25 Gate", "Cafeteria"]);

function project(point: { latitude: number; longitude: number }): THREE.Vector3 {
  const x = (point.longitude - MODEL_CENTER.longitude) * EARTH_METERS_PER_DEGREE_LONGITUDE * Math.cos(MODEL_CENTER.latitude * Math.PI / 180);
  const z = -(point.latitude - MODEL_CENTER.latitude) * EARTH_METERS_PER_DEGREE_LATITUDE;
  return new THREE.Vector3(x, 0, z);
}

function footprintGeometry(footprint: CampusBuilding["footprint"], height: number): THREE.ExtrudeGeometry {
  const shape = new THREE.Shape();
  footprint.forEach(([x, z], index) => {
    const shapeY = -z;
    if (index === 0) shape.moveTo(x, shapeY);
    else shape.lineTo(x, shapeY);
  });
  shape.closePath();
  const geometry = new THREE.ExtrudeGeometry(shape, { depth: height, bevelEnabled: false, curveSegments: 1 });
  geometry.rotateX(-Math.PI / 2);
  geometry.computeVertexNormals();
  return geometry;
}

function makeLabel(scene: THREE.Scene, text: string, position: THREE.Vector3, evidence: EvidenceStatus): void {
  const canvas = document.createElement("canvas");
  canvas.width = 640;
  canvas.height = 96;
  const context = canvas.getContext("2d");
  if (!context) return;
  context.clearRect(0, 0, canvas.width, canvas.height);
  context.fillStyle = evidence === "unverified" ? "rgba(94, 58, 12, 0.94)" : "rgba(13, 21, 29, 0.92)";
  context.beginPath();
  context.roundRect(4, 4, canvas.width - 8, canvas.height - 8, 14);
  context.fill();
  context.font = "700 26px Arial";
  context.fillStyle = evidence === "unverified" ? "#ffd58a" : "#f8fafc";
  context.fillText(text, 22, 61);
  const sprite = new THREE.Sprite(new THREE.SpriteMaterial({ map: new THREE.CanvasTexture(canvas), transparent: true, depthTest: false }));
  sprite.position.copy(position);
  sprite.position.y += 6;
  sprite.scale.set(20, 3, 1);
  sprite.renderOrder = 10;
  scene.add(sprite);
}

function makeBuilding(scene: THREE.Scene, building: CampusBuilding, palette: Record<string, number>): void {
  const height = building.floors * FLOOR_HEIGHT_METERS;
  const location = project(building);
  const material = new THREE.MeshStandardMaterial({
    color: palette[building.kind], roughness: 0.84, metalness: 0.03, transparent: true,
    opacity: building.evidence === "unverified" ? 0.62 : 0.86,
  });
  const geometry = footprintGeometry(building.footprint, height);
  const mesh = new THREE.Mesh(geometry, material);
  mesh.position.copy(location);
  mesh.rotation.y = building.rotation;
  mesh.castShadow = true;
  mesh.receiveShadow = true;
  mesh.userData = { name: building.name, evidence: building.evidence, source: building.source };
  scene.add(mesh);

  const edgeMaterial = new THREE.LineBasicMaterial({ color: building.evidence === "unverified" ? 0xf0b45a : 0xf0f6f8, transparent: true, opacity: 0.66 });
  const edges = new THREE.LineSegments(new THREE.EdgesGeometry(geometry), edgeMaterial);
  edges.position.copy(mesh.position);
  edges.rotation.copy(mesh.rotation);
  scene.add(edges);
  makeLabel(scene, `${building.name}${building.evidence === "unverified" ? " · UNVERIFIED" : ""}`, location, building.evidence);
}

function makePathSegment(scene: THREE.Scene, from: CampusRoutePoint, to: CampusRoutePoint, width: number, kind: "ROAD" | "PATH"): void {
  const start = project(from);
  const end = project(to);
  const vector = end.clone().sub(start);
  const length = vector.length();
  const center = start.clone().add(end).multiplyScalar(0.5);
  const road = new THREE.Mesh(
    new THREE.BoxGeometry(length, 0.18, width),
    new THREE.MeshStandardMaterial({ color: kind === "ROAD" ? 0x536a73 : 0x6b7d72, roughness: 0.98 }),
  );
  road.position.set(center.x, 0.08, center.z);
  road.rotation.y = -Math.atan2(vector.z, vector.x);
  road.receiveShadow = true;
  road.userData = { from: from.name, to: to.name, kind };
  scene.add(road);
}

function makePhotoBackedBoundary(scene: THREE.Scene, from: CampusRoutePoint, to: CampusRoutePoint, width: number): void {
  const start = project(from);
  const end = project(to);
  const vector = end.clone().sub(start);
  const length = vector.length();
  const direction = new THREE.Vector3(vector.x / length, 0, vector.z / length);
  const perpendicular = new THREE.Vector3(-direction.z, 0, direction.x);
  const center = start.clone().add(end).multiplyScalar(0.5);
  const rotation = -Math.atan2(vector.z, vector.x);
  const material = new THREE.MeshStandardMaterial({ color: 0xcfc2b3, roughness: 0.92, transparent: true, opacity: 0.72 });
  const railMaterial = new THREE.MeshStandardMaterial({ color: 0x182126, roughness: 0.7, metalness: 0.12, transparent: true, opacity: 0.82 });
  for (const side of [-1, 1]) {
    const wall = new THREE.Mesh(new THREE.BoxGeometry(length, 0.95, 0.34), material);
    const wallPosition = center.clone().add(perpendicular.clone().multiplyScalar(side * (width / 2 + 1.65)));
    wall.position.set(wallPosition.x, 0.48, wallPosition.z);
    wall.rotation.y = rotation;
    wall.userData = { name: "Photo-backed low wall", evidence: "unverified", source: "attached PDF repeated corridor sequence" };
    scene.add(wall);

    const rail = new THREE.Mesh(new THREE.BoxGeometry(length, 0.12, 0.12), railMaterial);
    rail.position.set(wallPosition.x, 1.45, wallPosition.z);
    rail.rotation.y = rotation;
    scene.add(rail);

    const postCount = Math.max(2, Math.ceil(length / 5) + 1);
    const posts = new THREE.InstancedMesh(new THREE.BoxGeometry(0.12, 1.08, 0.12), railMaterial, postCount);
    const postQuaternion = new THREE.Quaternion().setFromEuler(new THREE.Euler(0, rotation, 0));
    for (let index = 0; index < postCount; index += 1) {
      const distance = postCount === 1 ? 0 : (index / (postCount - 1) - 0.5) * length;
      const postPosition = wallPosition.clone().add(direction.clone().multiplyScalar(distance));
      posts.setMatrixAt(index, new THREE.Matrix4().compose(new THREE.Vector3(postPosition.x, 0.98, postPosition.z), postQuaternion, new THREE.Vector3(1, 1, 1)));
    }
    posts.instanceMatrix.needsUpdate = true;
    posts.castShadow = true;
    scene.add(posts);
  }
}

function makeMarker(scene: THREE.Scene, point: CampusRoutePoint, color: number, radius: number): void {
  const location = project(point);
  const marker = new THREE.Mesh(new THREE.SphereGeometry(radius, 10, 8), new THREE.MeshStandardMaterial({ color, emissive: color, emissiveIntensity: 0.3 }));
  marker.position.set(location.x, radius + 0.45, location.z);
  marker.castShadow = true;
  marker.userData = { name: point.name, evidence: point.evidence, source: point.source };
  scene.add(marker);
  if (point.kind !== "WAYPOINT") makeLabel(scene, point.name, location, point.evidence);
}

function makeGate(scene: THREE.Scene, gate: (typeof CAMPUS25_GATES)[number]): void {
  const location = project(gate);
  const color = gate.kind === "ENTRY" ? 0xf05a67 : gate.kind === "EXIT" ? 0x42d392 : 0x68b5d2;
  const group = new THREE.Group();
  const pillarMaterial = new THREE.MeshStandardMaterial({ color, roughness: 0.72 });
  for (const x of [-2.6, 2.6]) {
    const pillar = new THREE.Mesh(new THREE.BoxGeometry(0.7, 4.5, 0.7), pillarMaterial);
    pillar.position.set(x, 2.25, 0);
    pillar.castShadow = true;
    group.add(pillar);
  }
  const beam = new THREE.Mesh(new THREE.BoxGeometry(5.9, 0.55, 0.7), pillarMaterial);
  beam.position.y = 4.2;
  beam.castShadow = true;
  group.add(beam);
  group.position.set(location.x, 0, location.z);
  group.userData = { name: gate.name, evidence: gate.evidence, source: gate.source };
  scene.add(group);
  makeLabel(scene, `${gate.name}${gate.kind === "EXIT" ? " · EXIT" : ""}`, location, gate.evidence);
}

function makeNorthArrow(scene: THREE.Scene): void {
  scene.add(new THREE.ArrowHelper(new THREE.Vector3(0, 0, -1), new THREE.Vector3(-130, 0.2, 120), 22, 0x8eb7c5, 5, 3));
  makeLabel(scene, "NORTH", new THREE.Vector3(-130, 0, 98), "confirmed");
}

function disposeScene(scene: THREE.Scene): void {
  scene.traverse((object) => {
    const mesh = object as THREE.Mesh;
    if (mesh.geometry) mesh.geometry.dispose();
    const material = mesh.material;
    if (Array.isArray(material)) material.forEach((item) => item.dispose());
    else if (material) {
      const texture = (material as THREE.Material & { map?: THREE.Texture | null }).map;
      if (texture) texture.dispose();
      material.dispose();
    }
  });
}

function referencesForArea(manifest: ReferenceManifest, area: string): ReferenceEntry[] {
  return [...manifest.photos, ...manifest.pdfs].filter((reference) => reference.areas.some((candidate) => candidate.toLowerCase() === area.toLowerCase()));
}

export default function Campus3DMap() {
  const mountRef = useRef<HTMLDivElement>(null);
  const resetViewRef = useRef<(() => void) | null>(null);
  const [ready, setReady] = useState(false);
  const [referenceMode, setReferenceMode] = useState(false);
  const [manifest, setManifest] = useState<ReferenceManifest>(EMPTY_MANIFEST);
  const validationErrors = validateCampus25Model();

  useEffect(() => {
    let active = true;
    fetch("/campus-reference/manifest.json")
      .then((response) => response.ok ? response.json() as Promise<ReferenceManifest> : EMPTY_MANIFEST)
      .then((value) => { if (active) setManifest(value); })
      .catch(() => undefined);
    return () => { active = false; };
  }, []);

  useEffect(() => {
    const mount = mountRef.current;
    if (!mount || validationErrors.length) return;

    const scene = new THREE.Scene();
    scene.background = new THREE.Color(0x0d151d);
    scene.fog = new THREE.Fog(0x0d151d, 300, 560);
    const camera = new THREE.PerspectiveCamera(48, 1, 0.1, 800);
    const renderer = new THREE.WebGLRenderer({ antialias: true, alpha: false, powerPreference: "high-performance" });
    renderer.setPixelRatio(Math.min(window.devicePixelRatio, window.innerWidth < 700 ? 1.25 : 1.75));
    renderer.shadowMap.enabled = true;
    renderer.shadowMap.type = THREE.PCFSoftShadowMap;
    mount.appendChild(renderer.domElement);

    const controls = new OrbitControls(camera, renderer.domElement);
    controls.enableDamping = true;
    controls.maxPolarAngle = Math.PI * 0.48;
    controls.minDistance = 35;
    controls.maxDistance = 440;

    scene.add(new THREE.HemisphereLight(0xdbeafe, 0x17232b, 2.1));
    const sun = new THREE.DirectionalLight(0xffffff, 2.2);
    sun.position.set(-100, 180, 80);
    sun.castShadow = true;
    sun.shadow.mapSize.set(1024, 1024);
    scene.add(sun);

    const ground = new THREE.Mesh(new THREE.PlaneGeometry(460, 460), new THREE.MeshStandardMaterial({ color: 0x16232a, roughness: 0.95 }));
    ground.rotation.x = -Math.PI / 2;
    ground.receiveShadow = true;
    scene.add(ground);
    scene.add(new THREE.GridHelper(460, 46, 0x3a5260, 0x243740));

    for (const segment of CAMPUS25_ROUTE_SEGMENTS) {
      const from = CAMPUS25_ROUTE.find((point) => point.name === segment.from);
      const to = CAMPUS25_ROUTE.find((point) => point.name === segment.to);
      if (from && to) {
        makePathSegment(scene, from, to, segment.widthMeters, segment.kind);
        if (segment.boundaryStyle === "LOW_WALL_BLACK_RAILING") makePhotoBackedBoundary(scene, from, to, segment.widthMeters);
      }
    }
    const routePoints = CAMPUS25_ROUTE.map((point) => { const value = project(point); value.y = 0.32; return value; });
    scene.add(new THREE.Line(new THREE.BufferGeometry().setFromPoints(routePoints), new THREE.LineBasicMaterial({ color: 0x48d8a0 })));

    const palette = { HOSTEL: 0x776ce0, ACADEMIC: 0xd85468, AMENITY: 0xe0aa47 };
    CAMPUS25_BUILDINGS.forEach((building) => makeBuilding(scene, building, palette));
    CAMPUS25_ROUTE.filter((point) => !HIDDEN_MODEL_POINT_NAMES.has(point.name)).forEach((point) => {
      const color = point.kind === "ENTRY" ? 0xf05a67 : point.kind === "EXIT" ? 0x42d392 : point.kind === "ZONE" ? 0x73b9d8 : 0xf0b45a;
      makeMarker(scene, point, color, point.kind === "WAYPOINT" ? 0.8 : point.kind === "LANDMARK" ? 1.35 : 2.2);
    });
    CAMPUS25_GATES.filter((gate) => !HIDDEN_MODEL_POINT_NAMES.has(gate.name)).forEach((gate) => makeGate(scene, gate));
    makeNorthArrow(scene);

    const reset = () => {
      camera.position.set(150, 150, 175);
      controls.target.set(0, 0, 0);
      controls.update();
    };
    resetViewRef.current = reset;
    reset();

    const resize = () => {
      const width = Math.max(1, mount.clientWidth);
      const height = Math.max(360, mount.clientHeight);
      camera.aspect = width / height;
      camera.updateProjectionMatrix();
      renderer.setSize(width, height, false);
    };
    const resizeObserver = new ResizeObserver(resize);
    resizeObserver.observe(mount);
    resize();

    let frame = 0;
    const animate = () => {
      frame = window.requestAnimationFrame(animate);
      controls.update();
      renderer.render(scene, camera);
    };
    animate();
    setReady(true);

    return () => {
      window.cancelAnimationFrame(frame);
      resizeObserver.disconnect();
      resetViewRef.current = null;
      controls.dispose();
      disposeScene(scene);
      renderer.dispose();
      if (mount.contains(renderer.domElement)) mount.removeChild(renderer.domElement);
    };
  }, [validationErrors.length]);

  const areaRows = [
    ...CAMPUS25_BUILDINGS.map((building) => ({ name: building.name, status: building.evidence, source: building.source, refs: referencesForArea(manifest, building.name) })),
    ...CAMPUS25_GATES.filter((gate) => !HIDDEN_MODEL_POINT_NAMES.has(gate.name)).map((gate) => ({ name: gate.name, status: gate.evidence, source: gate.source, refs: referencesForArea(manifest, gate.name) })),
    ...CAMPUS25_AREAS.filter((area) => !HIDDEN_MODEL_POINT_NAMES.has(area.name)).map((area) => ({ name: area.name, status: area.evidence, source: area.source, refs: referencesForArea(manifest, area.name) })),
  ];

  return (
    <div className={styles.sceneShell}>
      <div ref={mountRef} className={styles.scene} role="img" aria-label="Three-dimensional Campus 25 route model from Main Gate through the campus to Main Gate Exit" />
      <div className={styles.sceneToolbar}>
        <span className={styles.orientation}>N ↑</span>
        <button type="button" onClick={() => resetViewRef.current?.()} disabled={!ready}>Reset view</button>
        <button type="button" className={referenceMode ? styles.activeButton : undefined} onClick={() => setReferenceMode((value) => !value)}>
          {referenceMode ? "Close references" : "Reference/debug"}
        </button>
      </div>
      {validationErrors.length > 0 && <div className={styles.validationError} role="alert">3D model validation failed: {validationErrors.join("; ")}</div>}
      {referenceMode && (
        <aside className={styles.referencePanel} aria-label="Campus photo and PDF reference panel">
          <div className={styles.referenceHeader}><strong>Reference/debug mode</strong><span className={manifest.status === "missing" ? styles.unverifiedText : styles.confirmedText}>{manifest.status === "missing" ? "PHOTO/PDF SET MISSING" : "INDEX LOADED"}</span></div>
          <p className={styles.referenceIntro}>Each area lists the evidence currently attached to its model location. Coordinate-only and unverified entries are deliberately not treated as photo confirmation.</p>
          {manifest.notes.map((note) => <p className={styles.referenceNote} key={note}>{note}</p>)}
          <div className={styles.referenceRows}>
            {areaRows.map((row) => (
              <div className={styles.referenceRow} key={row.name}>
                <div><strong>{row.name}</strong><span className={row.status === "unverified" ? styles.unverifiedText : styles.confirmedText}>{row.status}</span></div>
                <small>{row.refs.length ? row.refs.map((reference) => `${reference.path}${reference.page ? ` · p.${reference.page}` : ""}`).join(", ") : `No photo/PDF reference · ${row.source}`}</small>
              </div>
            ))}
          </div>
          <p className={styles.referenceNote}>Unverified model areas: {CAMPUS25_BUILDINGS.filter((building) => building.evidence === "unverified").length} building footprints; walls, trees, security-post placement, restricted boundary, and facade details remain unconfirmed.</p>
        </aside>
      )}
      <div className={styles.sceneLegend}>
        <strong>Campus 25 route model</strong>
        <span><i className={styles.entrySwatch} /> Main Gate entrance</span>
        <span><i className={styles.exitSwatch} /> Main Gate Exit / exits</span>
        <span><i className={styles.routeSwatch} /> Navigable road/path segments</span>
        <span><i className={styles.academicSwatch} /> Academic footprint · unverified</span>
        <small>Hostel and cafeteria structures are intentionally removed. Coordinates remain in route data for connectivity only. Labels marked UNVERIFIED are not photo-confirmed.</small>
      </div>
    </div>
  );
}
