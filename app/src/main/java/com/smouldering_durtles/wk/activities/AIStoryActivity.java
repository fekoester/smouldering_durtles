package com.smouldering_durtles.wk.activities;

import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;
import android.util.TypedValue;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.smouldering_durtles.wk.R;
import com.smouldering_durtles.wk.WkApplication;
import com.smouldering_durtles.wk.ai.AiClient;
import com.smouldering_durtles.wk.ai.AiProvider;
import com.smouldering_durtles.wk.ai.AiRequests;
import com.smouldering_durtles.wk.ai.AiSettings;
import com.smouldering_durtles.wk.ai.AiStory;
import com.smouldering_durtles.wk.api.model.Meaning;
import com.smouldering_durtles.wk.api.model.Reading;
import com.smouldering_durtles.wk.db.model.Subject;
import com.smouldering_durtles.wk.enums.SubjectType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import javax.annotation.Nullable;

import static com.smouldering_durtles.wk.util.ObjectSupport.runAsync;
import static com.smouldering_durtles.wk.util.ObjectSupport.safe;

/**
 * AI story mode: a short Japanese story written by the AI provider chosen in the settings,
 * using the kanji and vocabulary the learner selected in the self-study screen.
 */
public final class AIStoryActivity extends AbstractActivity {
    private static final String EXTRA_CHARACTERS = "ai_story_characters";
    private static final String EXTRA_READINGS = "ai_story_readings";
    private static final String EXTRA_MEANINGS = "ai_story_meanings";
    private static final String EXTRA_KANJI = "ai_story_kanji";
    private static final String STATE_STORY = "ai_story_json";
    private static final String STATE_STATUS = "ai_story_status";

    private final List<AiStory.Word> words = new ArrayList<>();
    private final List<View> detailViews = new ArrayList<>();
    private @Nullable AiStory story = null;
    private boolean allShown = false;
    private boolean generating = false;

    /**
     * Start the activity for a list of subjects. Radicals are skipped: they are not words.
     *
     * @param context the context
     * @param subjects the selected subjects
     * @return the number of usable subjects (0 means the activity was not started)
     */
    public static int start(final Context context, final List<Subject> subjects) {
        final ArrayList<String> characters = new ArrayList<>();
        final ArrayList<String> readings = new ArrayList<>();
        final ArrayList<String> meanings = new ArrayList<>();
        final ArrayList<String> kanji = new ArrayList<>();
        for (final Subject s: subjects) {
            final @Nullable String chars = s.getCharacters();
            if (chars == null || chars.isEmpty() || s.getType() == SubjectType.WANIKANI_RADICAL) {
                continue;
            }
            characters.add(chars);
            readings.add(primaryReading(s));
            meanings.add(primaryMeaning(s));
            kanji.add(s.getType() == SubjectType.WANIKANI_KANJI ? "1" : "0");
        }
        if (characters.isEmpty()) {
            return 0;
        }
        final Intent intent = new Intent(context, AIStoryActivity.class);
        intent.putStringArrayListExtra(EXTRA_CHARACTERS, characters);
        intent.putStringArrayListExtra(EXTRA_READINGS, readings);
        intent.putStringArrayListExtra(EXTRA_MEANINGS, meanings);
        intent.putStringArrayListExtra(EXTRA_KANJI, kanji);
        context.startActivity(intent);
        return characters.size();
    }

    private static String primaryReading(final Subject subject) {
        for (final Reading r: subject.getReadings()) {
            if (r.isPrimary()) {
                final @Nullable String value = r.getValue(false);
                if (value != null) {
                    return value;
                }
            }
        }
        return "";
    }

    private static String primaryMeaning(final Subject subject) {
        for (final Meaning m: subject.getMeanings()) {
            if (m.isPrimary() && m.getMeaning() != null) {
                return m.getMeaning();
            }
        }
        return subject.getOneMeaning();
    }

    public AIStoryActivity() {
        super(R.layout.activity_ai_story, R.menu.generic_options_menu);
    }

    @Override
    protected void onCreateLocal(final @Nullable Bundle savedInstanceState) {
        final Intent intent = getIntent();
        final @Nullable List<String> characters = intent.getStringArrayListExtra(EXTRA_CHARACTERS);
        final @Nullable List<String> readings = intent.getStringArrayListExtra(EXTRA_READINGS);
        final @Nullable List<String> meanings = intent.getStringArrayListExtra(EXTRA_MEANINGS);
        final @Nullable List<String> kanji = intent.getStringArrayListExtra(EXTRA_KANJI);
        if (characters != null) {
            for (int i = 0; i < characters.size(); i++) {
                words.add(new AiStory.Word(characters.get(i),
                        get(readings, i), get(meanings, i), "1".equals(get(kanji, i))));
            }
        }

        final Button toggleAll = findViewById(R.id.ai_story_toggle_all);
        toggleAll.setOnClickListener(v -> safe(() -> setAllShown(!allShown)));
        final Button newStory = findViewById(R.id.ai_story_new);
        newStory.setOnClickListener(v -> safe(this::generate));

        final AiProvider provider = AiSettings.getProvider();
        setWordsLine(null);

        if (words.isEmpty()) {
            setStatus("No kanji or vocabulary selected for AI Story Mode.");
            return;
        }
        if (savedInstanceState != null) {
            final @Nullable AiStory saved = AiStory.fromJson(savedInstanceState.getString(STATE_STORY));
            if (saved != null) {
                showStory(saved);
                return;
            }
            final @Nullable String status = savedInstanceState.getString(STATE_STATUS);
            if (status != null && !status.startsWith("Writing")) {
                setStatus(status);
                return;
            }
        }
        if (AiSettings.getApiKey(provider) == null) {
            setStatus("To use AI Story Mode, open Settings → AI settings, choose a provider and enter your own API key.");
            return;
        }
        generate();
    }

    private static String get(final @Nullable List<String> list, final int i) {
        return list != null && i < list.size() && list.get(i) != null ? list.get(i) : "";
    }

    private void setStatus(final @Nullable String text) {
        final TextView status = findViewById(R.id.ai_story_text);
        status.setText(text == null ? "" : text);
        status.setVisibility(text == null ? View.GONE : View.VISIBLE);
    }

    private void setWordsLine(final @Nullable List<AiStory.Word> chosen) {
        final TextView view = findViewById(R.id.ai_story_words);
        final List<AiStory.Word> list = chosen == null ? words : chosen;
        final StringBuilder sb = new StringBuilder();
        for (final AiStory.Word w: list) {
            if (sb.length() > 0) {
                sb.append("、");
            }
            sb.append(w.characters);
        }
        final String label = chosen == null
                ? String.format(Locale.ROOT, "%d selected: ", words.size())
                : "Words for this story: ";
        view.setText(label + sb);
    }

    private void generate() {
        if (generating || words.isEmpty()) {
            return;
        }
        final AiProvider provider = AiSettings.getProvider();
        if (AiSettings.getApiKey(provider) == null) {
            setStatus("No API key for " + provider.getDisplayName() + ". Add one in Settings → AI settings.");
            return;
        }
        generating = true;
        disableInteraction();
        clearStory();
        setStatus("Writing a story with " + provider.getDisplayName() + " (" + AiSettings.getModel(provider) + ")…");

        final List<AiStory.Word> chosen = new ArrayList<>(words);
        Collections.shuffle(chosen);
        while (chosen.size() > AiSettings.getStoryMaxWords()) {
            chosen.remove(chosen.size() - 1);
        }
        setWordsLine(chosen);
        final int sentences = AiSettings.getStorySentences();

        runAsync(this, () -> {
            int level = 0;
            try {
                level = WkApplication.getDatabase().propertiesDao().getUserLevel();
            } catch (final Exception e) {
                level = 0;
            }
            try {
                final String answer = AiClient.complete(AiStory.systemPrompt(), AiStory.userPrompt(chosen, level, sentences));
                return new Result(AiStory.parse(answer), null);
            } catch (final AiRequests.AiException e) {
                return new Result(null, e.getMessage());
            }
        }, result -> {
            generating = false;
            enableInteraction();
            if (result == null) {
                setStatus("Something went wrong while writing the story. Try again.");
            } else if (result.story != null) {
                showStory(result.story);
            } else {
                setStatus(result.error);
            }
        });
    }

    private static final class Result {
        final @Nullable AiStory story;
        final @Nullable String error;

        Result(final @Nullable AiStory story, final @Nullable String error) {
            this.story = story;
            this.error = error;
        }
    }

    private void clearStory() {
        story = null;
        detailViews.clear();
        allShown = false;
        ((LinearLayout) findViewById(R.id.ai_story_sentences)).removeAllViews();
        findViewById(R.id.ai_story_title).setVisibility(View.GONE);
        findViewById(R.id.ai_story_title_en).setVisibility(View.GONE);
        findViewById(R.id.ai_story_hint).setVisibility(View.GONE);
        ((Button) findViewById(R.id.ai_story_toggle_all)).setText("Show all");
    }

    private void showStory(final AiStory newStory) {
        clearStory();
        story = newStory;
        setStatus(null);

        final TextView title = findViewById(R.id.ai_story_title);
        if (!newStory.title.isEmpty()) {
            title.setText(newStory.title);
            title.setVisibility(View.VISIBLE);
        }
        final TextView titleEn = findViewById(R.id.ai_story_title_en);
        if (!newStory.titleEnglish.isEmpty()) {
            titleEn.setText(newStory.titleEnglish);
            titleEn.setVisibility(View.VISIBLE);
        }
        if (!newStory.used.isEmpty()) {
            final TextView wordsView = findViewById(R.id.ai_story_words);
            wordsView.setText("Used: " + String.join("、", newStory.used));
        }

        final LinearLayout container = findViewById(R.id.ai_story_sentences);
        for (final AiStory.Sentence s: newStory.sentences) {
            final LinearLayout block = new LinearLayout(this);
            block.setOrientation(LinearLayout.VERTICAL);
            block.setPadding(0, dp2px(6), 0, dp2px(6));

            final TextView ja = new TextView(this);
            ja.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
            ja.setText(highlight(s.japanese, newStory.used));
            block.addView(ja);

            final TextView details = new TextView(this);
            details.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            final StringBuilder sb = new StringBuilder();
            if (!s.kana.isEmpty()) {
                sb.append(s.kana);
            }
            if (!s.english.isEmpty()) {
                if (sb.length() > 0) {
                    sb.append('\n');
                }
                sb.append(s.english);
            }
            details.setText(sb.toString());
            details.setAlpha(0.75f);
            details.setVisibility(View.GONE);
            block.addView(details);
            detailViews.add(details);

            block.setOnClickListener(v -> safe(() ->
                    details.setVisibility(details.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE)));
            container.addView(block);
        }
        findViewById(R.id.ai_story_hint).setVisibility(View.VISIBLE);
    }

    private CharSequence highlight(final String sentence, final List<String> targets) {
        final SpannableString text = new SpannableString(sentence);
        final List<String> all = new ArrayList<>(targets);
        for (final AiStory.Word w: words) {
            all.add(w.characters);
        }
        for (final String target: all) {
            if (target.isEmpty()) {
                continue;
            }
            int index = sentence.indexOf(target);
            while (index >= 0) {
                text.setSpan(new UnderlineSpan(), index, index + target.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                text.setSpan(new StyleSpan(Typeface.BOLD), index, index + target.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                index = sentence.indexOf(target, index + target.length());
            }
        }
        return text;
    }

    private void setAllShown(final boolean shown) {
        allShown = shown;
        for (final View v: detailViews) {
            v.setVisibility(shown ? View.VISIBLE : View.GONE);
        }
        ((Button) findViewById(R.id.ai_story_toggle_all)).setText(shown ? "Hide all" : "Show all");
    }

    @Override
    protected void onSaveInstanceState(final Bundle outState) {
        super.onSaveInstanceState(outState);
        safe(() -> {
            if (story != null) {
                outState.putString(STATE_STORY, story.toJson());
            }
            final TextView status = findViewById(R.id.ai_story_text);
            if (status.getVisibility() == View.VISIBLE) {
                outState.putString(STATE_STATUS, status.getText().toString());
            }
        });
    }

    @Override
    protected void onResumeLocal() {
        //
    }

    @Override
    protected void onPauseLocal() {
        //
    }

    @Override
    protected boolean showWithoutApiKey() {
        return false;
    }

    @Override
    protected void enableInteractionLocal() {
        findViewById(R.id.ai_story_new).setEnabled(true);
        findViewById(R.id.ai_story_toggle_all).setEnabled(true);
    }

    @Override
    protected void disableInteractionLocal() {
        findViewById(R.id.ai_story_new).setEnabled(false);
        findViewById(R.id.ai_story_toggle_all).setEnabled(false);
    }
}
