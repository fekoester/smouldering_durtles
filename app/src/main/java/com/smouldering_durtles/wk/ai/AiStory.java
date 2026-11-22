package com.smouldering_durtles.wk.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import javax.annotation.Nullable;

/**
 * A short graded-reader story built from the learner's WaniKani subjects: the prompt that asks for it,
 * and the parsed result. Pure Java so it can be unit tested.
 */
public final class AiStory {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** One target word or kanji from the learner's selection. */
    public static final class Word {
        public final String characters;
        public final String reading;
        public final String meaning;
        public final boolean kanji;

        public Word(final String characters, final String reading, final String meaning, final boolean kanji) {
            this.characters = characters;
            this.reading = reading;
            this.meaning = meaning;
            this.kanji = kanji;
        }
    }

    /** One sentence: Japanese, the same sentence in kana, and an English translation. */
    public static final class Sentence {
        public final String japanese;
        public final String kana;
        public final String english;

        public Sentence(final String japanese, final String kana, final String english) {
            this.japanese = japanese;
            this.kana = kana;
            this.english = english;
        }
    }

    public final String title;
    public final String titleEnglish;
    public final List<Sentence> sentences;
    public final List<String> used;

    private AiStory(final String title, final String titleEnglish, final List<Sentence> sentences, final List<String> used) {
        this.title = title;
        this.titleEnglish = titleEnglish;
        this.sentences = Collections.unmodifiableList(sentences);
        this.used = Collections.unmodifiableList(used);
    }

    /**
     * The system prompt.
     *
     * @return the prompt
     */
    public static String systemPrompt() {
        return "You write short graded-reader stories for someone learning Japanese with WaniKani. "
                + "Write natural, correct Japanese that a learner at the given level can read, with a small plot "
                + "(a beginning, a problem, an ending). Reply with exactly one JSON object and nothing else: "
                + "no markdown, no code fences, no comments.";
    }

    /**
     * The user prompt for a story.
     *
     * @param words the target words (already limited and shuffled)
     * @param userLevel the learner's WaniKani level (1-60), or 0 if unknown
     * @param sentenceCount number of sentences to write
     * @return the prompt
     */
    public static String userPrompt(final List<Word> words, final int userLevel, final int sentenceCount) {
        final StringBuilder sb = new StringBuilder();
        if (userLevel > 0) {
            sb.append(String.format(Locale.ROOT, "Learner: WaniKani level %d of 60.%n", userLevel));
        }
        final String grammar = userLevel >= 35 ? "JLPT N3" : userLevel >= 15 ? "JLPT N4" : "JLPT N5";
        sb.append(String.format(Locale.ROOT, "Write a story of exactly %d sentences. Keep grammar at about %s level.%n",
                sentenceCount, grammar));
        sb.append("Target words (use each at least once where it fits naturally; kanji may appear inside words):\n");
        for (final Word w: words) {
            sb.append("- ").append(w.characters);
            if (!w.reading.isEmpty()) {
                sb.append(" (").append(w.reading).append(')');
            }
            if (!w.meaning.isEmpty()) {
                sb.append(" = ").append(w.meaning);
            }
            sb.append(w.kanji ? " [kanji]" : " [word]").append('\n');
        }
        sb.append("Write the target words in kanji. Write other words with kanji only if they are very common; "
                + "otherwise use kana.\n");
        sb.append("JSON format:\n"
                + "{\"title\": \"Japanese title\", \"title_en\": \"English title\", "
                + "\"sentences\": [{\"ja\": \"Japanese sentence\", \"kana\": \"the same sentence written fully in hiragana/katakana\", "
                + "\"en\": \"natural English translation\"}], "
                + "\"used\": [\"each target word you actually used, exactly as listed\"]}");
        return sb.toString();
    }

    /**
     * Parse the model's answer. Tolerates code fences and text around the JSON object.
     *
     * @param text the model output
     * @return the story
     * @throws AiRequests.AiException if no story can be read
     */
    public static AiStory parse(final String text) throws AiRequests.AiException {
        final int start = text.indexOf('{');
        final int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new AiRequests.AiException("The AI answer did not contain a story.");
        }
        final JsonNode root;
        try {
            root = MAPPER.readTree(text.substring(start, end + 1));
        } catch (final IOException e) {
            throw new AiRequests.AiException("The AI answer was not valid JSON. Try again.");
        }
        final List<Sentence> sentences = new ArrayList<>();
        for (final JsonNode s: root.path("sentences")) {
            final String ja = s.path("ja").asText("").trim();
            if (!ja.isEmpty()) {
                sentences.add(new Sentence(ja, s.path("kana").asText("").trim(), s.path("en").asText("").trim()));
            }
        }
        if (sentences.isEmpty()) {
            throw new AiRequests.AiException("The AI answer had no sentences. Try again.");
        }
        final List<String> used = new ArrayList<>();
        for (final JsonNode u: root.path("used")) {
            final String w = u.asText("").trim();
            if (!w.isEmpty()) {
                used.add(w);
            }
        }
        return new AiStory(root.path("title").asText("").trim(), root.path("title_en").asText("").trim(), sentences, used);
    }

    /**
     * Serialize for saving across screen rotation.
     *
     * @return JSON
     */
    public String toJson() {
        final ObjectNode root = MAPPER.createObjectNode();
        root.put("title", title);
        root.put("title_en", titleEnglish);
        final ArrayNode arr = root.putArray("sentences");
        for (final Sentence s: sentences) {
            final ObjectNode n = arr.addObject();
            n.put("ja", s.japanese);
            n.put("kana", s.kana);
            n.put("en", s.english);
        }
        final ArrayNode u = root.putArray("used");
        for (final String w: used) {
            u.add(w);
        }
        try {
            return MAPPER.writeValueAsString(root);
        } catch (final IOException e) {
            return "{}";
        }
    }

    /**
     * Read back a story saved with toJson().
     *
     * @param json the saved JSON
     * @return the story or null
     */
    public static @Nullable AiStory fromJson(final @Nullable String json) {
        if (json == null) {
            return null;
        }
        try {
            return parse(json);
        } catch (final AiRequests.AiException e) {
            return null;
        }
    }
}
