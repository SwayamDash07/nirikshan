"use client";

import { ChangeEvent, DragEvent, FormEvent, useCallback, useEffect, useState } from "react";
import styles from "./admin.module.css";

type Zone = { id: number; name: string };
type Venue = { id: number; name: string };
type JobStatus = "PENDING" | "PROCESSING" | "COMPLETE" | "FAILED";
type Job = { id: number; zoneId: number; zoneName: string; videoFilename: string; status: JobStatus; createdAt: string; completedAt?: string; errorMessage?: string; annotatedVideoPath?: string; summaryPath?: string };

const API_BASE = process.env.NEXT_PUBLIC_API_BASE_URL || "http://localhost:8080";
const active = new Set<JobStatus>(["PENDING", "PROCESSING"]);

function formatDate(value: string) { return new Date(value).toLocaleString([], { dateStyle: "medium", timeStyle: "short" }); }

export default function AdminUploadPage() {
  const [zones, setZones] = useState<Zone[]>([]);
  const [jobs, setJobs] = useState<Job[]>([]);
  const [zoneId, setZoneId] = useState<number>();
  const [file, setFile] = useState<File>();
  const [loading, setLoading] = useState(true);
  const [uploading, setUploading] = useState(false);
  const [dragging, setDragging] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");

  const loadJobs = useCallback(async (selectedZoneId?: number) => {
    const suffix = selectedZoneId ? `?zoneId=${selectedZoneId}` : "";
    const response = await fetch(`${API_BASE}/api/jobs${suffix}`, { cache: "no-store" });
    if (!response.ok) throw new Error(`Could not load jobs (${response.status})`);
    setJobs(await response.json());
  }, []);

  useEffect(() => { (async () => {
    try {
      const venuesResponse = await fetch(`${API_BASE}/api/venues`, { cache: "no-store" });
      if (!venuesResponse.ok) throw new Error("Could not reach the backend");
      const venues: Venue[] = await venuesResponse.json();
      if (!venues.length) throw new Error("No venue zones are available yet");
      const zonesResponse = await fetch(`${API_BASE}/api/venues/${venues[0].id}/zones`, { cache: "no-store" });
      if (!zonesResponse.ok) throw new Error("Could not load campus zones");
      const fetchedZones: Zone[] = await zonesResponse.json();
      setZones(fetchedZones); setZoneId(fetchedZones[0]?.id);
      await loadJobs();
    } catch (reason) { setError(reason instanceof Error ? reason.message : "Could not load admin data"); }
    finally { setLoading(false); }
  })(); }, [loadJobs]);

  useEffect(() => { if (!jobs.some((job) => active.has(job.status))) return; const timer = window.setInterval(() => { loadJobs(zoneId).catch(() => undefined); }, 3000); return () => window.clearInterval(timer); }, [jobs, loadJobs, zoneId]);
  useEffect(() => { if (!loading) loadJobs(zoneId).catch((reason) => setError(reason instanceof Error ? reason.message : "Could not load jobs")); }, [zoneId, loading, loadJobs]);

  function selectFile(next?: File) { setFile(next); setNotice(""); if (next) setError(""); }
  function onFileChange(event: ChangeEvent<HTMLInputElement>) { selectFile(event.target.files?.[0]); }
  function onDrop(event: DragEvent<HTMLLabelElement>) { event.preventDefault(); setDragging(false); selectFile(event.dataTransfer.files?.[0]); }

  async function upload(event: FormEvent) {
    event.preventDefault();
    if (!file || !zoneId) return;
    setUploading(true); setError(""); setNotice("");
    try {
      const body = new FormData(); body.append("zoneId", String(zoneId)); body.append("file", file);
      const response = await fetch(`${API_BASE}/api/jobs/upload`, { method: "POST", body });
      const result = await response.json();
      if (!response.ok) throw new Error(result.error || "Upload failed");
      setFile(undefined); setNotice(`Job #${result.id} was queued for processing.`); await loadJobs(zoneId);
    } catch (reason) { setError(reason instanceof Error ? reason.message : "Upload failed"); }
    finally { setUploading(false); }
  }

  if (loading) return <main className={styles.state}>Loading admin workspace…</main>;
  return <main className={styles.page}><header className={styles.header}><a href="/" className={styles.brand}><span>◈</span><div><b>Nirikshan</b><small>OPERATIONS ADMIN</small></div></a><nav><a href="/">Command dashboard</a><a href="/alerts">Citizen alerts</a></nav></header><section className={styles.hero}><span>VIDEO INGESTION</span><h1>Process a campus<br /><em>camera recording.</em></h1><p>Upload a clip, assign its physical zone, and Nirikshan will generate visual evidence and live safety events.</p></section><section className={styles.workspace}><form className={styles.uploadCard} onSubmit={upload}><div className={styles.cardTitle}><div><span>NEW PROCESSING JOB</span><h2>Upload a recording</h2></div><i>Internal tool</i></div><label className={styles.field}>Campus zone<select value={zoneId || ""} onChange={(event) => setZoneId(Number(event.target.value))}>{zones.map((zone) => <option key={zone.id} value={zone.id}>{zone.name}</option>)}</select></label><label className={`${styles.dropzone} ${dragging ? styles.dragging : ""}`} onDragOver={(event) => { event.preventDefault(); setDragging(true); }} onDragLeave={() => setDragging(false)} onDrop={onDrop}><input type="file" accept="video/*,.mp4,.mov,.avi,.mkv" onChange={onFileChange} /><strong>{file ? file.name : "Drop video here"}</strong><span>{file ? `${(file.size / 1024 / 1024).toFixed(1)} MB selected` : "or click to browse for a campus video"}</span></label><button className={styles.uploadButton} disabled={!file || !zoneId || uploading}>{uploading ? "Uploading…" : "Upload & process video"}</button>{notice && <p className={styles.notice}>{notice}</p>}{error && <p className={styles.error}>{error}</p>}</form><aside className={styles.flow}><span>WHAT HAPPENS NEXT</span><ol><li><b>1</b><div><strong>Queue</strong><small>Video is stored per processing job.</small></div></li><li><b>2</b><div><strong>Analyse</strong><small>CV produces events and annotated footage.</small></div></li><li><b>3</b><div><strong>Publish</strong><small>Validated events update the command dashboard.</small></div></li></ol></aside></section><section className={styles.jobs}><div className={styles.jobsHeading}><div><span>PROCESSING HISTORY</span><h2>{zoneId ? zones.find((zone) => zone.id === zoneId)?.name || "Selected zone" : "All zones"}</h2></div><button onClick={() => loadJobs(zoneId).catch((reason) => setError(reason instanceof Error ? reason.message : "Could not refresh jobs"))}>Refresh</button></div>{jobs.length ? <div className={styles.jobList}>{jobs.map((job) => <article className={styles.job} key={job.id}><div className={styles.jobMain}><span className={`${styles.status} ${styles[job.status]}`}>{job.status}</span><div><strong>{job.videoFilename}</strong><small>{job.zoneName} · submitted {formatDate(job.createdAt)}</small>{job.errorMessage && <p className={styles.jobError}>{job.errorMessage}</p>}</div></div><div className={styles.actions}>{job.status === "COMPLETE" ? <><a href={`${API_BASE}${job.annotatedVideoPath}`} target="_blank">Annotated video</a><a href={`${API_BASE}${job.summaryPath}`} target="_blank">Summary report</a></> : <span>{job.status === "PROCESSING" ? "Analysis running…" : job.status === "PENDING" ? "Waiting to start…" : "Processing failed"}</span>}</div></article>)}</div> : <div className={styles.empty}>No processing jobs for this zone yet.</div>}</section></main>;
}
