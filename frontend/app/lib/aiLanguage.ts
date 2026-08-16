export type AiLanguage = "en" | "hi" | "or";

export const AI_LANGUAGES: Array<{ value: AiLanguage; label: string }> = [
  { value: "en", label: "EN" },
  { value: "hi", label: "हिंदी" },
  { value: "or", label: "ଓଡ଼ିଆ" },
];

export const AI_LANGUAGE_STORAGE_KEY = "nirikshan.ai-language";
