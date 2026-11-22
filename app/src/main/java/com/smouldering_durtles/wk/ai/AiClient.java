package com.smouldering_durtles.wk.ai;

import com.smouldering_durtles.wk.BuildConfig;
import com.smouldering_durtles.wk.Identification;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Sends one prompt to the provider chosen in the settings. Call from a background thread.
 */
public final class AiClient {
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private static @Nullable OkHttpClient client = null;

    private AiClient() {
        //
    }

    private static synchronized OkHttpClient client() {
        if (client == null) {
            client = new OkHttpClient.Builder()
                    .connectTimeout(20, TimeUnit.SECONDS)
                    .writeTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(180, TimeUnit.SECONDS)
                    .callTimeout(240, TimeUnit.SECONDS)
                    .build();
        }
        return client;
    }

    /**
     * Ask the configured provider.
     *
     * @param system the system prompt
     * @param user the user message
     * @return the generated text
     * @throws AiRequests.AiException with a user-readable message on any failure
     */
    public static String complete(final String system, final String user) throws AiRequests.AiException {
        final AiProvider provider = AiSettings.getProvider();
        final @Nullable String apiKey = AiSettings.getApiKey(provider);
        if (apiKey == null || apiKey.isEmpty()) {
            throw new AiRequests.AiException("No API key for " + provider.getDisplayName()
                    + ". Add one in Settings → AI settings.");
        }
        final AiRequests.HttpSpec spec;
        try {
            spec = AiRequests.build(provider, AiSettings.getModel(provider), apiKey, system, user);
        } catch (final IOException e) {
            throw new AiRequests.AiException("Could not build the request.");
        }

        final Request.Builder builder = new Request.Builder()
                .url(spec.url)
                .header("User-Agent", Identification.APP_NAME_UA + "/" + BuildConfig.VERSION_NAME)
                .post(RequestBody.create(spec.body, JSON));
        for (final Map.Entry<String, String> header: spec.headers.entrySet()) {
            builder.header(header.getKey(), header.getValue());
        }

        try (final Response response = client().newCall(builder.build()).execute()) {
            final @Nullable ResponseBody body = response.body();
            final @Nullable String text = body == null ? null : body.string();
            if (!response.isSuccessful()) {
                throw new AiRequests.AiException(AiRequests.describeError(provider, response.code(), text));
            }
            if (text == null) {
                throw new AiRequests.AiException("The AI service sent an empty response.");
            }
            return AiRequests.parseText(provider, text);
        } catch (final IOException e) {
            throw new AiRequests.AiException("Could not reach " + provider.getDisplayName() + " (" + e.getClass().getSimpleName() + ").");
        }
    }
}
