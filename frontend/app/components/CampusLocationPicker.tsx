"use client";

import { useEffect, useMemo, useState } from "react";
import styles from "./campusLocation.module.css";

export type CampusVenue = { id: number; name: string; description?: string; latitude?: number; longitude?: number; serviceRadiusMeters?: number };
export type CampusPoint = { lat: number; lng: number };
export type VenueSelectionSource = "search" | "nearest" | "default";

const DEFAULT_COVERAGE_RADIUS_METERS = 1000;

export function distanceBetween(a: CampusPoint, b: CampusPoint) {
  const radius = 6371000;
  const radians = (value: number) => value * Math.PI / 180;
  const haversine = Math.sin(radians(b.lat - a.lat) / 2) ** 2
    + Math.cos(radians(a.lat)) * Math.cos(radians(b.lat)) * Math.sin(radians(b.lng - a.lng) / 2) ** 2;
  return Math.round(radius * 2 * Math.atan2(Math.sqrt(haversine), Math.sqrt(1 - haversine)));
}

export function venueIsCovered(venue: CampusVenue | undefined, location: CampusPoint | undefined) {
  if (!venue || venue.latitude === undefined || venue.longitude === undefined || !location) return true;
  return distanceBetween(location, { lat: venue.latitude, lng: venue.longitude }) <= (venue.serviceRadiusMeters || DEFAULT_COVERAGE_RADIUS_METERS);
}

export default function CampusLocationPicker({
  venues,
  selectedVenue,
  location,
  onLocationChange,
  onSelect,
}: {
  venues: CampusVenue[];
  selectedVenue?: CampusVenue;
  location?: CampusPoint;
  onLocationChange: (point: CampusPoint) => void;
  onSelect: (venue: CampusVenue, source: VenueSelectionSource) => void;
}) {
  const [query, setQuery] = useState("");
  const [tracking, setTracking] = useState(true);
  const [locationState, setLocationState] = useState<"locating" | "ready" | "denied" | "unavailable">("locating");

  useEffect(() => {
    if (!tracking) return;
    if (!navigator.geolocation) {
      setLocationState("unavailable");
      return;
    }
    const watchId = navigator.geolocation.watchPosition(
      (position) => {
        onLocationChange({ lat: position.coords.latitude, lng: position.coords.longitude });
        setLocationState("ready");
      },
      () => setLocationState("denied"),
      { enableHighAccuracy: true, maximumAge: 15000, timeout: 10000 },
    );
    return () => navigator.geolocation.clearWatch(watchId);
  }, [onLocationChange, tracking]);

  const matches = useMemo(() => {
    const normalized = query.trim().toLowerCase();
    if (!normalized) return venues.slice(0, 5);
    return venues.filter((venue) => `${venue.name} ${venue.description || ""}`.toLowerCase().includes(normalized)).slice(0, 5);
  }, [query, venues]);

  const nearest = useMemo(() => {
    if (!location) return undefined;
    return venues
      .filter((venue) => venue.latitude !== undefined && venue.longitude !== undefined)
      .map((venue) => ({ venue, distance: distanceBetween(location, { lat: venue.latitude!, lng: venue.longitude! }) }))
      .sort((a, b) => a.distance - b.distance)[0];
  }, [location, venues]);

  const covered = venueIsCovered(selectedVenue, location);
  const status = locationState === "ready"
    ? covered ? "Services available here" : "We don't provide services at your current location yet"
    : locationState === "denied" ? "Location access is off"
      : locationState === "unavailable" ? "Live location is unavailable on this device" : "Finding your location…";

  return <section className={styles.picker} aria-label="Campus and location selector">
    <div className={styles.pickerTopline}><div><span className={styles.kicker}>LOCATION SERVICES</span><strong>{selectedVenue?.name || "Choose your campus"}</strong></div><span className={`${styles.status} ${covered ? styles.statusGood : styles.statusWarn}`}><i />{status}</span></div>
    <div className={styles.searchRow}>
      <label className={styles.searchBox}>
        <span aria-hidden="true">⌕</span>
        <input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Search campus, e.g. Campus-25 or KIIT" aria-label="Search campus" />
      </label>
      <button className={styles.locationButton} type="button" onClick={() => setTracking(true)} disabled={locationState === "locating"}>⌖ <span>Use my location</span></button>
    </div>
    {query && <div className={styles.results} role="listbox" aria-label="Campus search results">{matches.length ? matches.map((venue) => <button key={venue.id} type="button" role="option" onClick={() => { onSelect(venue, "search"); setQuery(""); }}>{venue.name}<small>{venue.description || "Safety services available"}</small></button>) : <p>No supported campus matches that search.</p>}</div>}
    {nearest && venueIsCovered(nearest.venue, location) && nearest.venue.id !== selectedVenue?.id && <button className={styles.nearest} type="button" onClick={() => onSelect(nearest.venue, "nearest")}>Use {nearest.venue.name} · {nearest.distance}m away</button>}
    {!covered && locationState === "ready" && <p className={styles.unavailable}>Move into a supported campus or search for a campus above to view its safety services.</p>}
  </section>;
}
