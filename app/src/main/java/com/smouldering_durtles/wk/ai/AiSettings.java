package com.smouldering_durtles.wk.ai;

import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

import com.smouldering_durtles.wk.WkApplication;

import javax.annotation.Nullable;

/**
 * Settings for the AI features. API keys live in the encrypted preference store, like the WaniKani token,
 * so they are not part of the settings export and never leave the device except in requests to the provider.
 */
public final class AiSettings {
    private AiSettings() {
        //
    }

    private static SharedPreferences prefs() {
        return PreferenceManager.getDefaultSharedPreferences(WkApplication.getInstance());
    }

    /**
     * The provider chosen in the settings.
     *
     * @return the provider
     */
    public static AiProvider getProvider() {
        return AiProvider.fromPreference(prefs().getString("ai_provider", null));
    }

    /**
     * The API key for a provider, with whitespace removed.
     *
     * @param provider the provider
     * @return the key or null if not set
     */
    public static @Nullable String getApiKey(final AiProvider provider) {
        final @Nullable String key = WkApplication.getEncryptedPreferenceDataStore().getString(provider.getApiKeyPreference(), null);
        if (key == null) {
            return null;
        }
        final String trimmed = key.replaceAll("\\s+", "");
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * The model for a provider; the provider's default if the field is empty.
     *
     * @param provider the provider
     * @return the model name
     */
    public static String getModel(final AiProvider provider) {
        final @Nullable String model = prefs().getString(provider.getModelPreference(), null);
        if (model == null || model.trim().isEmpty()) {
            return provider.getDefaultModel();
        }
        return model.trim();
    }

    /**
     * Number of sentences in a generated story.
     *
     * @return the length
     */
    public static int getStorySentences() {
        final @Nullable String value = prefs().getString("ai_story_length", "8");
        try {
            final int n = Integer.parseInt(value == null ? "8" : value.trim());
            return Math.max(3, Math.min(20, n));
        } catch (final NumberFormatException e) {
            return 8;
        }
    }

    /**
     * Maximum number of the selected subjects passed to one story.
     *
     * @return the number of words
     */
    public static int getStoryMaxWords() {
        return 12;
    }
}
