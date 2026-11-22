package com.smouldering_durtles.wk.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.annotation.Nullable;

/**
 * Builds the HTTP requests for each AI provider and reads their responses.
 * Pure Java (no Android, no network), so it can be unit tested.
 */
public final class AiRequests {
    /** Version of the Anthropic Messages API this code is written against. */
    public static final String ANTHROPIC_VERSION = "2026-10-21";
    /** Beta that lets Anthropic retry a declined request on a fallback model inside the same call. */
    public static final String ANTHROPIC_FALLBACK_BETA = "server-side-fallback-2026-07-01";

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_OUTPUT_TOKENS = 16000;

    private AiRequests() {
        //
    }

    /**
     * A request ready to be sent: URL, headers and JSON body.
     */
    public static final class HttpSpec {
        public final String url;
        public final Map<String, String> headers;
        public final String body;

        HttpSpec(final String url, final Map<String, String> headers, final String body) {
            this.url = url;
            this.headers = headers;
            this.body = body;
        }
    }

    /**
     * Thrown when a provider answers but the answer can't be used.
     */
    public static final class AiException extends Exception {
        private static final long serialVersionUID = 1L;

        public AiException(final String message) {
            super(message);
        }
    }

    /**
     * Build a single-turn request: a system prompt plus one user message.
     *
     * @param provider the provider
     * @param model the model name
     * @param apiKey the user's API key
     * @param system the system prompt
     * @param user the user message
     * @return the request
     */
    public static HttpSpec build(final AiProvider provider, final String model, final String apiKey,
                                 final String system, final String user) throws IOException {
        final Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json");
        final ObjectNode body = MAPPER.createObjectNode();

        switch (provider) {
            case ANTHROPIC: {
                headers.put("x-api-key", apiKey);
                headers.put("anthropic-version", ANTHROPIC_VERSION);
                headers.put("anthropic-beta", ANTHROPIC_FALLBACK_BETA);
                body.put("model", model);
                body.put("max_tokens", MAX_OUTPUT_TOKENS);
                body.put("system", system);
                body.put("fallbacks", "default");
                final ArrayNode messages = body.putArray("messages");
                final ObjectNode message = messages.addObject();
                message.put("role", "user");
                message.put("content", user);
                return new HttpSpec("https://api.anthropic.com/v1/messages", headers, MAPPER.writeValueAsString(body));
            }
            case GEMINI: {
                headers.put("x-goog-api-key", apiKey);
                body.putObject("systemInstruction").putArray("parts").addObject().put("text", system);
                final ObjectNode content = body.putArray("contents").addObject();
                content.put("role", "user");
                content.putArray("parts").addObject().put("text", user);
                body.putObject("generationConfig").put("responseMimeType", "application/json");
                final String url = "https://generativelanguage.googleapis.com/v1beta/models/"
                        + URLEncoder.encode(model, "UTF-8") + ":generateContent";
                return new HttpSpec(url, headers, MAPPER.writeValueAsString(body));
            }
            case OPENAI:
            default: {
                headers.put("Authorization", "Bearer " + apiKey);
                body.put("model", model);
                final ArrayNode messages = body.putArray("messages");
                final ObjectNode systemMessage = messages.addObject();
                systemMessage.put("role", "system");
                systemMessage.put("content", system);
                final ObjectNode userMessage = messages.addObject();
                userMessage.put("role", "user");
                userMessage.put("content", user);
                body.putObject("response_format").put("type", "json_object");
                return new HttpSpec("https://api.openai.com/v1/chat/completions", headers, MAPPER.writeValueAsString(body));
            }
        }
    }

    /**
     * Get the generated text out of a successful response.
     *
     * @param provider the provider
     * @param responseBody the JSON response body
     * @return the text
     * @throws AiException if the response has no usable text
     */
    public static String parseText(final AiProvider provider, final String responseBody) throws AiException {
        final JsonNode root;
        try {
            root = MAPPER.readTree(responseBody);
        } catch (final IOException e) {
            throw new AiException("The AI service sent a response that is not JSON.");
        }
        final StringBuilder sb = new StringBuilder();
        switch (provider) {
            case ANTHROPIC: {
                if ("refusal".equals(root.path("stop_reason").asText())) {
                    throw new AiException("The model declined this request. Try again or pick other words.");
                }
                // Only text blocks; thinking blocks and fallback markers are skipped.
                for (final JsonNode block: root.path("content")) {
                    if ("text".equals(block.path("type").asText())) {
                        sb.append(block.path("text").asText());
                    }
                }
                if ("max_tokens".equals(root.path("stop_reason").asText()) && sb.length() == 0) {
                    throw new AiException("The answer was cut off before any text was written.");
                }
                break;
            }
            case GEMINI: {
                final JsonNode candidate = root.path("candidates").path(0);
                for (final JsonNode part: candidate.path("content").path("parts")) {
                    if (!part.path("thought").asBoolean(false)) {
                        sb.append(part.path("text").asText());
                    }
                }
                if (sb.length() == 0) {
                    final String reason = candidate.path("finishReason").asText(
                            root.path("promptFeedback").path("blockReason").asText(""));
                    if (!reason.isEmpty()) {
                        throw new AiException("Gemini returned no text (reason: " + reason + ").");
                    }
                }
                break;
            }
            case OPENAI:
            default: {
                final JsonNode message = root.path("choices").path(0).path("message");
                if (message.hasNonNull("refusal") && !message.path("refusal").asText().isEmpty()) {
                    throw new AiException("The model declined this request: " + message.path("refusal").asText());
                }
                sb.append(message.path("content").asText(""));
                break;
            }
        }
        if (sb.length() == 0) {
            throw new AiException("The AI service returned an empty answer.");
        }
        return sb.toString();
    }

    /**
     * Turn an error response into a short message for the user. Never includes the API key.
     *
     * @param provider the provider
     * @param httpCode the HTTP status code
     * @param responseBody the response body, if any
     * @return the message
     */
    public static String describeError(final AiProvider provider, final int httpCode, final @Nullable String responseBody) {
        String detail = "";
        if (responseBody != null) {
            try {
                final JsonNode root = MAPPER.readTree(responseBody);
                final JsonNode error = root.path("error");
                detail = error.isTextual() ? error.asText() : error.path("message").asText("");
            } catch (final IOException e) {
                detail = "";
            }
        }
        final String prefix;
        if (httpCode == 401 || httpCode == 403) {
            prefix = provider.getDisplayName() + " rejected the API key";
        } else if (httpCode == 404) {
            prefix = provider.getDisplayName() + " does not know this model (check the model name in the AI settings)";
        } else if (httpCode == 429) {
            prefix = provider.getDisplayName() + " rate limit or quota reached";
        } else if (httpCode >= 500) {
            prefix = provider.getDisplayName() + " server error, try again later";
        } else {
            prefix = provider.getDisplayName() + " error " + httpCode;
        }
        if (detail.length() > 300) {
            detail = detail.substring(0, 300) + "…";
        }
        return detail.isEmpty() ? prefix + "." : prefix + ": " + detail;
    }
}
