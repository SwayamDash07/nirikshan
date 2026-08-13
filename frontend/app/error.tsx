"use client";

import { useEffect } from "react";
import styles from "./error.module.css";

export default function Error({ error, reset }: { error: Error & { digest?: string }; reset: () => void }) {
  useEffect(() => { console.error("Nirikshan page error", error); }, [error]);
  return <main className={styles.page}><section className={styles.card}><h1>Something went wrong</h1><p>The page could not finish loading. Try again or check the backend connection.</p><button type="button" onClick={() => reset()}>Try again</button></section></main>;
}
