"use client";

import { useMemo } from "react";
import { CircleMarker, MapContainer, Polyline, Popup, TileLayer } from "react-leaflet";
import { buildCampusRoute } from "./config/campusRoute";
import { riskColors, ZoneRiskMarker, type ZoneMarkerData } from "./components/ZoneRiskMarker";
import styles from "./console/console.module.css";

type Zone = ZoneMarkerData & { currentPeopleCount: number };
type Venue = { name: string; latitude?: number; longitude?: number };

function formatTime(value: string) {
  const date = new Date(value);
  return Number.isNaN(date.valueOf()) ? "Not available" : date.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit", second: "2-digit" });
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
  const route = useMemo(() => buildCampusRoute(zones), [zones]);

  if (!zones.length) return <div className={styles.emptyPanel}>No mapped zones available yet.</div>;
  const maxHeadcount = Math.max(1, ...zones.map((zone) => zone.currentPeopleCount ?? 0));
  const alternateColor = route.mutedExitId ? riskColors[zones.find((zone) => zone.id === route.mutedExitId)?.currentRiskLevel || "HIGH"] : "var(--risk-medium)";

  return <div className={styles.leafletPanel}>
    <div className={styles.mapTopline}><span className={styles.liveLabel}><span className={styles.connectedDot} /> LIVE</span><span>Live venue telemetry</span><span className={styles.mapCoordinates}>OpenStreetMap, {venue?.name || "Venue map"}</span></div>
    <MapContainer center={center} zoom={16} scrollWheelZoom className={styles.leafletMap}>
      <TileLayer attribution='&copy; OpenStreetMap contributors' url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png" />
      {route.segments.map((segment, index) => <Polyline key={`route-segment-${index}`} positions={segment.positions} pathOptions={{ color: segment.color, weight: 5, opacity: 0.9, lineCap: "round", lineJoin: "round" }} />)}
      {zones.map((zone) => {
        const blocked = route.blockedIds.has(zone.id);
        const muted = route.mutedExitId === zone.id;
        if (muted) return <CircleMarker key={zone.id} center={[zone.latitude, zone.longitude]} radius={5} pathOptions={{ color: alternateColor, fillColor: alternateColor, fillOpacity: 0.12, opacity: 0.42, weight: 1, dashArray: "4 3" }} eventHandlers={{ click: () => onSelect(zone.id) }}><Popup>{zonePopup(zone, false, true)}</Popup></CircleMarker>;
        if (blocked) return <CircleMarker key={zone.id} center={[zone.latitude, zone.longitude]} radius={5} pathOptions={{ color: riskColors[zone.currentRiskLevel], fillColor: riskColors[zone.currentRiskLevel], fillOpacity: 0.24, opacity: 0.58, weight: 1 }} eventHandlers={{ click: () => onSelect(zone.id) }}><Popup>{zonePopup(zone, true, false)}</Popup></CircleMarker>;
        return <ZoneRiskMarker key={zone.id} zone={zone} maxScale={maxHeadcount} onSelect={() => onSelect(zone.id)} popup={zonePopup(zone, false, false)} />;
      })}
    </MapContainer>
    <div className={styles.mapNotices}>
      {route.shelterInPlace && <div className={`${styles.forecastNotice} ${styles.shelterNotice}`} role="status"><strong>Both exits are currently crowded.</strong> Shelter in place in the nearest building until crowd density clears. Do not attempt to exit through Main Gate or Main Gate Exit.</div>}
      {route.alternate && <div className={`${styles.forecastNotice} ${styles.alternateNotice}`} role="status">Main Gate Exit is crowded. The highlighted route now favors Main Gate.</div>}
    </div>
    <div className={styles.mapFooter}>
      <div className={styles.mapLegend}><span><i style={{ background: riskColors.LOW }} />Normal</span><span><i style={{ background: riskColors.MEDIUM }} />Watch</span><span><i style={{ background: riskColors.HIGH }} />High</span><span><i style={{ background: riskColors.CRITICAL }} />Critical</span><span><i className={styles.simulationLegend} />Simulation</span></div>
      <div className={styles.densityLegend}><strong>Recommended exit</strong><span>{route.shelterInPlace ? "Shelter in place" : `${route.startName} → ${route.exitName}${route.blockedIds.size ? " · bypass active" : route.alternate ? " · alternate exit" : " · primary exit"}`}</span></div>
    </div>
    <div className={styles.mapScale}><span>OPENSTREETMAP LAYER</span><strong>{zones.length} zones</strong></div>
  </div>;
}
