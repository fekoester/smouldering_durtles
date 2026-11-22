/*
 * Copyright 2019-2020 Ernst Jan Plugge <rmc@dds.nl>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.smouldering_durtles.wk.components;

import android.content.Context;
import android.text.InputType;
import android.util.AttributeSet;
import android.widget.EditText;

import androidx.preference.EditTextPreference;

import com.smouldering_durtles.wk.WkApplication;

import javax.annotation.Nullable;

/**
 * A dummy preference class that isn't actually a preference - it's not clickable and just
 * a way to show a block of text in the preferences screen.
 */
public final class EncryptedEditTextPreference extends EditTextPreference implements EditTextPreference.OnBindEditTextListener {
    private final EncryptedPreferenceDataStore store;

    /**
     * The constructor.
     *
     * @param context Android context
     * @param attrs attributes from XML
     * @param defStyleAttr default style
     * @param defStyleRes default style resource
     */
    public EncryptedEditTextPreference(final Context context, final AttributeSet attrs, final int defStyleAttr, final int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        store = WkApplication.getEncryptedPreferenceDataStore();
        init();
    }

    /**
     * The constructor.
     *
     * @param context Android context
     * @param attrs attributes from XML
     * @param defStyleAttr default style
     */
    public EncryptedEditTextPreference(final Context context, final AttributeSet attrs, final int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        store = WkApplication.getEncryptedPreferenceDataStore();
        init();
    }

    /**
     * The constructor.
     *
     * @param context Android context
     * @param attrs attributes from XML
     */
    public EncryptedEditTextPreference(final Context context, final AttributeSet attrs) {
        super(context, attrs);
        store = WkApplication.getEncryptedPreferenceDataStore();
        init();
    }

    /**
     * The constructor.
     *
     * @param context Android context
     */
    public EncryptedEditTextPreference(final Context context) {
        super(context);
        store = WkApplication.getEncryptedPreferenceDataStore();
        init();
    }

    private void init() {
        setPreferenceDataStore(store);
        setOnBindEditTextListener(this);
        if (isAiKey(getKey())) {
            // Never show an AI API key in the settings list: only whether it is set, and its last 4 characters.
            setSummaryProvider(preference -> {
                final @Nullable String value = getText();
                if (value == null || value.trim().isEmpty()) {
                    return "Not set";
                }
                final String trimmed = value.trim();
                return "Set (…" + trimmed.substring(Math.max(0, trimmed.length() - 4)) + ")";
            });
        }
    }

    private static boolean isAiKey(final @Nullable String key) {
        return key != null && key.startsWith("ai_key_");
    }

    @Override
    public void onBindEditText(final EditText editText) {
        final @Nullable String key = getKey();
        if (key != null && (key.equals("web_password") || isAiKey(key))) {
            editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        }
    }
}
