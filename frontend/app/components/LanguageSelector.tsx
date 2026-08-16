"use client";

import { ChangeEvent } from "react";
import { AI_LANGUAGES, type AiLanguage } from "../lib/aiLanguage";

export default function LanguageSelector({ language, onChange, className = "" }: {
  language: AiLanguage;
  onChange: (language: AiLanguage) => void;
  className?: string;
}) {
  function change(event: ChangeEvent<HTMLSelectElement>) {
    onChange(event.target.value as AiLanguage);
  }

  return <label className={className}>
    <select aria-label="AI response language" value={language} onChange={change}>
      {AI_LANGUAGES.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}
    </select>
  </label>;
}
