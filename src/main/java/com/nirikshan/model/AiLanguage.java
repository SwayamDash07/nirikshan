package com.nirikshan.model;

import java.util.Locale;

public enum AiLanguage {
    EN("en", "English"),
    HI("hi", "Hindi"),
    OR("or", "Odia");

    private final String code;
    private final String displayName;

    AiLanguage(String code, String displayName) {
        this.code = code;
        this.displayName = displayName;
    }

    public String code() { return code; }
    public String displayName() { return displayName; }

    public static AiLanguage fromCode(String value) {
        if (value == null || value.isBlank()) return EN;
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (AiLanguage language : values()) {
            if (language.code.equals(normalized)) return language;
        }
        throw new IllegalArgumentException("language must be one of: en, hi, or");
    }
}
