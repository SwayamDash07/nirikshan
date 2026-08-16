"use client";

import { createContext, ReactNode, useContext } from "react";
import type { AiLanguage } from "./aiLanguage";

export type AiLanguageContextValue = {
  language: AiLanguage;
  setLanguage: (language: AiLanguage) => void;
};

export const AiLanguageContext = createContext<AiLanguageContextValue>({
  language: "en",
  setLanguage: () => undefined,
});

export function useAiLanguage() { return useContext(AiLanguageContext); }

export function AiLanguageProvider({ value, children }: { value: AiLanguageContextValue; children: ReactNode }) {
  return <AiLanguageContext.Provider value={value}>{children}</AiLanguageContext.Provider>;
}
