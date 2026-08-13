"use client";

import { FormEvent, useEffect, useState } from "react";
import { api, readSession, saveSession, type Session } from "../../lib/auth";

export default function ConsoleLogin() {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [working, setWorking] = useState(false);
  useEffect(() => { if (readSession()?.user.role === "ADMIN") window.location.replace("/console"); }, []);
  async function login(event: FormEvent) {
    event.preventDefault(); setWorking(true); setError("");
    try {
      const session = await api<Session>("/api/auth/login", { method: "POST", body: JSON.stringify({ email, password }) });
      if (session.user.role !== "ADMIN") throw new Error("This account is not permitted to access the command console.");
      saveSession(session); window.location.replace("/console");
    } catch (err) { setError(err instanceof Error ? err.message : "Login failed"); }
    finally { setWorking(false); }
  }
  return <main style={{ minHeight: "100vh", display: "grid", placeItems: "center", background: "#edf3f8", padding: 20 }}><form onSubmit={login} style={{ width: "min(100%, 390px)", padding: 30, background: "white", borderRadius: 18, boxShadow: "0 18px 45px #16365b20" }}><p style={{ color: "#2471d9", fontSize: 11, fontWeight: 800, letterSpacing: ".12em" }}>NIRIKSHAN / RESTRICTED</p><h1 style={{ margin: "8px 0", color: "#16365b" }}>Command console</h1><p style={{ color: "#64788e", fontSize: 13, lineHeight: 1.5 }}>Sign in with an administrator account.</p><label style={label}>Email<input style={input} value={email} type="email" onChange={(e) => setEmail(e.target.value)} required /></label><label style={label}>Password<input style={input} value={password} type="password" onChange={(e) => setPassword(e.target.value)} required /></label>{error && <p style={{ color: "#c8424d", fontSize: 12 }}>{error}</p>}<button disabled={working} style={button}>{working ? "Signing in…" : "Sign in"}</button></form></main>;
}
const label = { display: "grid", gap: 6, marginTop: 16, color: "#3b5570", fontSize: 12, fontWeight: 700 } as const;
const input = { padding: "12px", border: "1px solid #d7e2eb", borderRadius: 9, fontSize: 14 } as const;
const button = { width: "100%", marginTop: 22, padding: "13px", border: 0, borderRadius: 9, color: "white", background: "#2164d7", fontWeight: 800, cursor: "pointer" } as const;
