package com.smouldering_durtles.wk.ai;

import javax.annotation.Nullable;

/**
 * The AI services the app can talk to. The user picks one in the settings and enters their own API key for it.
 */
public enum AiProvider {
    OPENAI("OpenAI", "gpt-4.1-mini"),
    ANTHROPIC("Anthropic (Claude)", "claude-opus-5-5"),
    GEMINI("Google Gemini", "gemini-3.8-flash");

    private final String displayName;
    private final String defaultModel;

    AiProvider(final String displayName, final String defaultModel) {
        this.displayName = displayName;
        this.defaultModel = defaultModel;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDefaultModel() {
        return defaultModel;
    }

    /**
     * Key of the (encrypted) preference that holds this provider's API key.
     *
     * @return the preference key
     */
    public String getApiKeyPreference() {
        return "ai_key_" + name().toLowerCase();
    }

    /**
     * Key of the preference that holds the model name for this provider.
     *
     * @return the preference key
     */
    public String getModelPreference() {
        return "ai_model_" + name().toLowerCase();
    }

    /**
     * Parse a stored preference value, falling back to OpenAI for unknown or missing values.
     *
     * @param value the stored value
     * @return the provider
     */
    public static AiProvider fromPreference(final @Nullable String value) {
        if (value != null) {
            for (final AiProvider provider: values()) {
                if (provider.name().equalsIgnoreCase(value.trim())) {
                    return provider;
                }
            }
        }
        return OPENAI;
    }
}
