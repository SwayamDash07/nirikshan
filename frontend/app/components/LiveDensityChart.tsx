"use client";

import styles from "./liveDensity.module.css";

export default function LiveDensityChart({ values }: { values: number[] }) {
  const points = values.slice(-30);
  const maximum = Math.max(...points, 1);
  const coordinates = points.map((value, index) => `${points.length === 1 ? 50 : (index / (points.length - 1)) * 100},${92 - (value / maximum) * 74}`).join(" ");
  const current = points[points.length - 1];
  return <div className={styles.chart} aria-label="Live campus density trend"><div className={styles.chartHeader}><div><span>Density</span><strong>{current === undefined ? "Awaiting signal" : `${current.toFixed(2)} people / m²`}</strong></div><span className={styles.live}><i />LIVE</span></div>{points.length ? <svg viewBox="0 0 100 100" preserveAspectRatio="none" role="img"><line x1="0" y1="92" x2="100" y2="92" />{points.length > 1 ? <polyline points={coordinates} /> : <circle cx="50" cy={92 - (points[0] / maximum) * 74} r="2.5" />}</svg> : <div className={styles.empty}>Waiting for live telemetry<span>Connect a camera feed to populate the trend.</span></div>}<div className={styles.axis}><span>Earlier</span><span>Now</span></div></div>;
}
