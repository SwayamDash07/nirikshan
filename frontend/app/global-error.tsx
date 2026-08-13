"use client";

export default function GlobalError({ reset }: { error: Error & { digest?: string }; reset: () => void }) {
  return <html><body><main style={{ padding: 32, fontFamily: "sans-serif" }}><h1>Nirikshan failed to load</h1><p>Refresh the page and check the application logs if the problem continues.</p><button onClick={() => reset()}>Reload</button></main></body></html>;
}
