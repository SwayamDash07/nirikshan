"use client";

import { useEffect } from "react";

export default function Error({ error, reset }: { error: Error & { digest?: string }; reset: () => void }) {
  useEffect(() => { console.error("Nirikshan page error", error); }, [error]);
  return <main style={{ minHeight: "100vh", display: "grid", placeItems: "center", padding: 24, fontFamily: "sans-serif" }}><section><h1>Something went wrong</h1><p>The page could not finish loading. Try again or check the backend connection.</p><button onClick={() => reset()}>Try again</button></section></main>;
}
