package com.smouldering_durtles.wk.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class AiRequestsTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    public void openAiRequestShape() throws Exception {
        final AiRequests.HttpSpec spec = AiRequests.build(AiProvider.OPENAI, "gpt-4.1-mini", "sk-test", "SYS", "USER");
        assertEquals("https://api.openai.com/v1/chat/completions", spec.url);
        assertEquals("Bearer sk-test", spec.headers.get("Authorization"));
        final JsonNode body = MAPPER.readTree(spec.body);
        assertEquals("gpt-4.1-mini", body.path("model").asText());
        assertEquals("system", body.path("messages").path(0).path("role").asText());
        assertEquals("SYS", body.path("messages").path(0).path("content").asText());
        assertEquals("USER", body.path("messages").path(1).path("content").asText());
        assertEquals("json_object", body.path("response_format").path("type").asText());
    }

    @Test
    public void anthropicRequestShape() throws Exception {
        final AiRequests.HttpSpec spec = AiRequests.build(AiProvider.ANTHROPIC, "claude-opus-5-5", "ak-test", "SYS", "USER");
        assertEquals("https://api.anthropic.com/v1/messages", spec.url);
        assertEquals("ak-test", spec.headers.get("x-api-key"));
        assertEquals(AiRequests.ANTHROPIC_VERSION, spec.headers.get("anthropic-version"));
        assertEquals(AiRequests.ANTHROPIC_FALLBACK_BETA, spec.headers.get("anthropic-beta"));
        assertFalse(spec.headers.containsKey("Authorization"));
        final JsonNode body = MAPPER.readTree(spec.body);
        assertEquals("claude-opus-5-5", body.path("model").asText());
        assertEquals("SYS", body.path("system").asText());
        assertEquals("default", body.path("fallbacks").asText());
        assertTrue(body.path("max_tokens").asInt() > 1000);
        assertEquals(1, body.path("messages").size());
        assertEquals("user", body.path("messages").path(0).path("role").asText());
        // No sampling parameters or thinking config: rejected on current Claude models.
        assertFalse(body.has("temperature"));
        assertFalse(body.has("thinking"));
    }

    @Test
    public void geminiRequestShape() throws Exception {
        final AiRequests.HttpSpec spec = AiRequests.build(AiProvider.GEMINI, "gemini-3.8-flash", "g-test", "SYS", "USER");
        assertEquals("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent", spec.url);
        assertEquals("g-test", spec.headers.get("x-goog-api-key"));
        assertFalse(spec.url.contains("g-test"));
        final JsonNode body = MAPPER.readTree(spec.body);
        assertEquals("SYS", body.path("systemInstruction").path("parts").path(0).path("text").asText());
        assertEquals("USER", body.path("contents").path(0).path("parts").path(0).path("text").asText());
    }

    @Test
    public void parsesEachProvider() throws Exception {
        assertEquals("hi", AiRequests.parseText(AiProvider.OPENAI,
                "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"hi\",\"refusal\":null}}]}"));
        assertEquals("hello", AiRequests.parseText(AiProvider.ANTHROPIC,
                "{\"content\":[{\"type\":\"thinking\",\"thinking\":\"\"},{\"type\":\"text\",\"text\":\"hel\"},"
                        + "{\"type\":\"text\",\"text\":\"lo\"}],\"stop_reason\":\"end_turn\"}"));
        assertEquals("yo", AiRequests.parseText(AiProvider.GEMINI,
                "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"plan\",\"thought\":true},{\"text\":\"yo\"}]},"
                        + "\"finishReason\":\"STOP\"}]}"));
    }

    @Test
    public void anthropicRefusalIsAnError() {
        try {
            AiRequests.parseText(AiProvider.ANTHROPIC, "{\"content\":[],\"stop_reason\":\"refusal\"}");
            fail();
        } catch (final AiRequests.AiException e) {
            assertTrue(e.getMessage().contains("declined"));
        }
    }

    @Test
    public void emptyAnswerIsAnError() {
        for (final AiProvider p: AiProvider.values()) {
            try {
                AiRequests.parseText(p, "{}");
                fail(p.name());
            } catch (final AiRequests.AiException e) {
                // expected
            }
        }
    }

    @Test
    public void errorMessages() {
        assertTrue(AiRequests.describeError(AiProvider.OPENAI, 401,
                "{\"error\":{\"message\":\"Incorrect API key\"}}").contains("rejected the API key"));
        assertTrue(AiRequests.describeError(AiProvider.ANTHROPIC, 404,
                "{\"type\":\"error\",\"error\":{\"type\":\"not_found_error\",\"message\":\"model: x\"}}").contains("model"));
        assertTrue(AiRequests.describeError(AiProvider.GEMINI, 429, "not json").contains("rate limit"));
    }

    @Test
    public void providerFromPreference() {
        assertEquals(AiProvider.ANTHROPIC, AiProvider.fromPreference("ANTHROPIC"));
        assertEquals(AiProvider.GEMINI, AiProvider.fromPreference("gemini"));
        assertEquals(AiProvider.OPENAI, AiProvider.fromPreference(null));
        assertEquals(AiProvider.OPENAI, AiProvider.fromPreference("unknown"));
        assertEquals("ai_key_anthropic", AiProvider.ANTHROPIC.getApiKeyPreference());
    }

    @Test
    public void storyPromptAndParse() throws Exception {
        final String prompt = AiStory.userPrompt(Arrays.asList(
                new AiStory.Word("猫", "ねこ", "cat", false),
                new AiStory.Word("山", "さん", "mountain", true)), 12, 5);
        assertTrue(prompt.contains("WaniKani level 12"));
        assertTrue(prompt.contains("exactly 5 sentences"));
        assertTrue(prompt.contains("猫 (ねこ) = cat [word]"));
        assertTrue(prompt.contains("山 (さん) = mountain [kanji]"));

        final String answer = "```json\n{\"title\":\"猫と山\",\"title_en\":\"The cat and the mountain\","
                + "\"sentences\":[{\"ja\":\"猫が山にいます。\",\"kana\":\"ねこがやまにいます。\",\"en\":\"A cat is on the mountain.\"},"
                + "{\"ja\":\"\",\"kana\":\"\",\"en\":\"skipped\"}],\"used\":[\"猫\",\"山\"]}\n```";
        final AiStory story = AiStory.parse(answer);
        assertEquals("猫と山", story.title);
        assertEquals(1, story.sentences.size());
        assertEquals("ねこがやまにいます。", story.sentences.get(0).kana);
        assertEquals(Arrays.asList("猫", "山"), story.used);

        final AiStory again = AiStory.fromJson(story.toJson());
        assertEquals(story.sentences.get(0).english, again.sentences.get(0).english);
    }

    @Test
    public void storyParseRejectsGarbage() {
        for (final String bad: Arrays.asList("no json here", "{\"sentences\":[]}", "{broken")) {
            try {
                AiStory.parse(bad);
                fail(bad);
            } catch (final AiRequests.AiException e) {
                // expected
            }
        }
        assertEquals(null, AiStory.fromJson(null));
        assertTrue(AiStory.userPrompt(Collections.emptyList(), 0, 3).contains("exactly 3 sentences"));
    }
}
