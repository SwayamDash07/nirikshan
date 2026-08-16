"use client";

import { useEffect, useRef, useState } from "react";
import * as THREE from "three";
import { OrbitControls } from "three/examples/jsm/controls/OrbitControls.js";
import { CAMPUS25_BUILDINGS, CAMPUS25_ROUTE, FLOOR_HEIGHT_METERS, type CampusBuilding, type CampusRoutePoint } from "./campus25Route";
import styles from "./campus3d.module.css";

const EARTH_METERS_PER_DEGREE_LATITUDE = 110_540;
const EARTH_METERS_PER_DEGREE_LONGITUDE = 111_320;
const MODEL_CENTER = {
  latitude: CAMPUS25_ROUTE.reduce((sum, point) => sum + point.latitude, 0) / CAMPUS25_ROUTE.length,
  longitude: CAMPUS25_ROUTE.reduce((sum, point) => sum + point.longitude, 0) / CAMPUS25_ROUTE.length,
};

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

  const geometry = new THREE.ExtrudeGeometry(shape, {
    depth: height,
    bevelEnabled: false,
    curveSegments: 1,
  });
  // ExtrudeGeometry starts in the XY plane; rotate it so its extrusion is
  // vertical and its footprint uses the local X/Z map plane.
  geometry.rotateX(-Math.PI / 2);
  geometry.computeVertexNormals();
  return geometry;
}

function makeBuilding(
  scene: THREE.Scene,
  building: CampusBuilding,
): void {
  const height = building.floors * FLOOR_HEIGHT_METERS;
  const location = project(building);
  const material = new THREE.MeshStandardMaterial({
    color: building.kind === "HOSTEL" ? 0x776ce0 : building.kind === "ACADEMIC" ? 0xd85468 : 0xe0aa47,
    roughness: 0.78,
    metalness: 0.05,
    transparent: true,
    opacity: 0.84,
  });

  const edgeMaterial = new THREE.LineBasicMaterial({ color: 0xf0f6f8, transparent: true, opacity: 0.5 });
  const geometry = footprintGeometry(building.footprint, height);
  const mesh = new THREE.Mesh(geometry, material);
  mesh.position.set(location.x, 0, location.z);
  mesh.rotation.y = building.rotation;
  mesh.castShadow = true;
  mesh.receiveShadow = true;
  scene.add(mesh);

  const edges = new THREE.LineSegments(new THREE.EdgesGeometry(geometry), edgeMaterial);
  edges.position.copy(mesh.position);
  edges.rotation.copy(mesh.rotation);
  scene.add(edges);
}

function makeMarker(scene: THREE.Scene, point: CampusRoutePoint, color: number, radius: number): void {
  const location = project(point);
  const marker = new THREE.Mesh(
    new THREE.SphereGeometry(radius, 18, 12),
    new THREE.MeshStandardMaterial({ color, emissive: color, emissiveIntensity: 0.35 }),
  );
  marker.position.set(location.x, radius + 1, location.z);
  marker.castShadow = true;
  scene.add(marker);
}

function routePoints(): THREE.Vector3[] {
  return CAMPUS25_ROUTE.map((point) => {
    const location = project(point);
    location.y = 0.65;
    return location;
  });
}

export default function Campus3DMap() {
  const mountRef = useRef<HTMLDivElement>(null);
  const resetViewRef = useRef<(() => void) | null>(null);
  const [ready, setReady] = useState(false);

  useEffect(() => {
    const mount = mountRef.current;
    if (!mount) return;

    const scene = new THREE.Scene();
    scene.background = new THREE.Color(0x0d151d);
    scene.fog = new THREE.Fog(0x0d151d, 260, 520);

    const camera = new THREE.PerspectiveCamera(48, 1, 0.1, 800);
    const renderer = new THREE.WebGLRenderer({ antialias: true, alpha: false });
    renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
    renderer.shadowMap.enabled = true;
    renderer.shadowMap.type = THREE.PCFSoftShadowMap;
    mount.appendChild(renderer.domElement);

    const controls = new OrbitControls(camera, renderer.domElement);
    controls.enableDamping = true;
    controls.maxPolarAngle = Math.PI * 0.48;
    controls.minDistance = 35;
    controls.maxDistance = 440;

    const ambient = new THREE.HemisphereLight(0xdbeafe, 0x17232b, 2.1);
    scene.add(ambient);
    const sun = new THREE.DirectionalLight(0xffffff, 2.2);
    sun.position.set(-100, 180, 80);
    sun.castShadow = true;
    sun.shadow.mapSize.set(2048, 2048);
    scene.add(sun);

    const ground = new THREE.Mesh(
      new THREE.PlaneGeometry(460, 460),
      new THREE.MeshStandardMaterial({ color: 0x16232a, roughness: 0.95, metalness: 0 }),
    );
    ground.rotation.x = -Math.PI / 2;
    ground.receiveShadow = true;
    scene.add(ground);
    scene.add(new THREE.GridHelper(460, 46, 0x3a5260, 0x243740));

    const road = new THREE.Mesh(
      new THREE.TubeGeometry(new THREE.CatmullRomCurve3(routePoints()), 180, 1.35, 8, false),
      new THREE.MeshStandardMaterial({ color: 0x526a73, roughness: 1 }),
    );
    road.position.y = 0.05;
    scene.add(road);

    const route = new THREE.Line(
      new THREE.BufferGeometry().setFromPoints(routePoints()),
      new THREE.LineBasicMaterial({ color: 0x48d8a0, linewidth: 2 }),
    );
    scene.add(route);

    CAMPUS25_BUILDINGS.forEach((building) => makeBuilding(scene, building));
    CAMPUS25_ROUTE.forEach((point) => {
      if (point.kind === "ENTRY") makeMarker(scene, point, 0xf05a67, 3.4);
      else if (point.kind === "EXIT") makeMarker(scene, point, 0x42d392, 3.4);
      else if (point.kind === "ZONE") makeMarker(scene, point, 0x73b9d8, 2.2);
      else if (point.kind === "LANDMARK") makeMarker(scene, point, 0xf0b45a, 1.8);
    });

    const north = new THREE.ArrowHelper(new THREE.Vector3(0, 0, -1), new THREE.Vector3(-130, 0.2, 120), 22, 0x8eb7c5, 5, 3);
    scene.add(north);

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
      renderer.dispose();
      mount.removeChild(renderer.domElement);
    };
  }, []);

  return (
    <div className={styles.sceneShell}>
      <div ref={mountRef} className={styles.scene} role="img" aria-label="Three-dimensional Campus 25 route model from Main Gate through the campus to Main Gate Exit" />
      <div className={styles.sceneToolbar}>
        <span className={styles.orientation}>N ↑</span>
        <button type="button" onClick={() => resetViewRef.current?.()} disabled={!ready}>Reset view</button>
      </div>
      <div className={styles.sceneLegend}>
        <strong>Campus 25 route model</strong>
        <span><i className={styles.entrySwatch} /> Main Gate entrance</span>
        <span><i className={styles.exitSwatch} /> Main Gate Exit</span>
        <span><i className={styles.routeSwatch} /> Route corridor</span>
        <span><i className={styles.hostelSwatch} /> Hostel A–D · 6–7 floors assumed</span>
        <span><i className={styles.academicSwatch} /> Academic building · 4 floors</span>
        <span><i className={styles.cafeSwatch} /> Cafe · 2 floors</span>
        <small>Centers use your supplied coordinates. Footprints are traced from the satellite reference.</small>
      </div>
    </div>
  );
}
