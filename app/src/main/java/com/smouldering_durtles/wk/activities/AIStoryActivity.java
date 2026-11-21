package com.smouldering_durtles.wk.activities;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.smouldering_durtles.wk.R;
import com.smouldering_durtles.wk.db.model.Subject;

import java.util.ArrayList;
import java.util.List;

public class AIStoryActivity extends AbstractActivity {

    private static final String EXTRA_SUBJECTS = "extra_subjects";

    public static void start(Context context, List<Subject> subjects) {
        Intent intent = new Intent(context, AIStoryActivity.class);
        ArrayList<String> subjectNames = new ArrayList<>();
        for (Subject s : subjects) {
            if (s.getCharacters() != null) {
                subjectNames.add(s.getCharacters());
            }
        }
        intent.putStringArrayListExtra(EXTRA_SUBJECTS, subjectNames);
        context.startActivity(intent);
    }

    public AIStoryActivity() {
        super(R.layout.activity_ai_story, R.menu.generic_options_menu);
    }

    @Override
    protected void onCreateLocal(@Nullable Bundle savedInstanceState) {
        List<String> subjects = getIntent().getStringArrayListExtra(EXTRA_SUBJECTS);
        TextView textView = findViewById(R.id.ai_story_text);

        if (subjects == null || subjects.isEmpty()) {
            textView.setText("No subjects selected for AI Story Mode.");
        } else {
            textView.setText("Generating story for: " + String.join(", ", subjects));
            // TODO: Add ChatGPT integration here
        }
    }

    @Override
    protected void onResumeLocal() {
        // Nothing special yet
    }

    @Override
    protected void onPauseLocal() {
        // Nothing special yet
    }

    @Override
    protected boolean showWithoutApiKey() {
        return false;
    }

    @Override
    protected void enableInteractionLocal() {
        // no interactive controls yet
    }

    @Override
    protected void disableInteractionLocal() {
        // no interactive controls yet
    }


}
