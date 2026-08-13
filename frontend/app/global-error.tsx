"use client";

import "./globals.css";
import styles from "./error.module.css";

export default function GlobalError({ reset }: { error: Error & { digest?: string }; reset: () => void }) {
  return <html lang="en"><body><main className={styles.page}><section className={styles.card}><h1>Nirikshan failed to load</h1><p>Refresh the page and check the application logs if the problem continues.</p><button type="button" onClick={() => reset()}>Reload</button></section></main></body></html>;
}
