"use client";

import { ReactNode, useEffect, useState } from "react";

export type ThemeMode = "system" | "light" | "dark";

const storageKey = "nirikshan.theme";

function resolveTheme(mode: ThemeMode) {
  if (mode !== "system") return mode;
  return window.matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light";
}

export function ThemeProvider({ children }: { children: ReactNode }) {
  const [mode, setMode] = useState<ThemeMode>("system");

  useEffect(() => {
    const saved = window.localStorage.getItem(storageKey);
    if (saved === "light" || saved === "dark" || saved === "system") setMode(saved);
  }, []);

  useEffect(() => {
    const apply = () => document.documentElement.dataset.theme = resolveTheme(mode);
    apply();
    if (mode !== "system") return undefined;
    const media = window.matchMedia("(prefers-color-scheme: dark)");
    media.addEventListener("change", apply);
    return () => media.removeEventListener("change", apply);
  }, [mode]);

  function changeTheme(next: ThemeMode) {
    setMode(next);
    window.localStorage.setItem(storageKey, next);
  }

  return <ThemeContext.Provider value={{ mode, changeTheme }}>{children}</ThemeContext.Provider>;
}

import { createContext, useContext } from "react";

const ThemeContext = createContext<{ mode: ThemeMode; changeTheme: (mode: ThemeMode) => void } | null>(null);

export function useTheme() {
  const context = useContext(ThemeContext);
  if (!context) throw new Error("useTheme must be used inside ThemeProvider");
  return context;
}
