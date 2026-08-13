"use client";

import { useTheme } from "./ThemeProvider";
import styles from "./theme.module.css";

function SunIcon() {
  return <svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="12" r="4" /><path d="M12 2v2M12 20v2M4.93 4.93l1.42 1.42M17.65 17.65l1.42 1.42M2 12h2M20 12h2M4.93 19.07l1.42-1.42M17.65 6.35l1.42-1.42" /></svg>;
}

function MoonIcon() {
  return <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M20.5 15.4A8.5 8.5 0 0 1 8.6 3.5 8.5 8.5 0 1 0 20.5 15.4Z" /></svg>;
}

export default function ThemeToggle() {
  const { mode, changeTheme } = useTheme();
  const next = mode === "dark" ? "light" : mode === "light" ? "system" : "dark";
  const label = mode === "system" ? "System theme" : mode === "dark" ? "Dark theme" : "Light theme";
  return <button className={styles.toggle} type="button" onClick={() => changeTheme(next)} aria-label={`${label}. Switch to ${next} theme`} title={`${label}. Switch to ${next} theme`}><span className={styles.toggleIcon}>{mode === "dark" ? <MoonIcon /> : <SunIcon />}</span><span>{mode === "system" ? "System" : mode === "dark" ? "Dark" : "Light"}</span></button>;
}
