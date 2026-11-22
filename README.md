# Smouldering Durtles

Smouldering Durtles is an Android app for WaniKani, forked from the existing codebase of Flaming Durtles, the popular open source client that has fallen into disprear. I'm Joeni over on the site and took on updating the application to support changes in WaniKani's API, in the hopes that Android users will continue to have access.

The following is the original information regarding licencing and sharing:

It's set up as an Android project with Google's default gradle set up.
Use Android Studio or the Gradle command line to build it.

[<img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png"
     alt="Get it on F-Droid"
     height="80">](https://f-droid.org/packages/com.smouldering_durtles.wk/)
[<img src="https://play.google.com/intl/en_us/badges/images/generic/en-play-badge.png"
     alt="Get it on Google Play"
     height="80">](https://play.google.com/store/apps/details?id=com.smouldering_durtles.wk)

Or download the latest APK from the [Releases Section](https://github.com/jerryhcooke/smouldering_durtles/releases/latest).

## AI Story Mode (this fork, branch `ai-integration/initial-setup`)

Self-study quiz → fill in the search form → **AI Story Mode**: an AI writes a short Japanese story with up to 12 of
the selected kanji and vocabulary words (radicals are skipped), at grammar matched to your WaniKani level. Tap a sentence to
see its kana reading and English translation; target words are underlined; **New story** picks another set of words.

- **Provider:** Settings → AI settings → OpenAI, Anthropic (Claude) or Google Gemini. Each has its own key and an
  editable model field (defaults: `gpt-4.1-mini`, `claude-opus-5-5`, `gemini-3.8-flash`). Story length: 5, 8 or 12 sentences.
- **Keys:** you enter your own API key in the app. Keys are stored in the encrypted preference store (like the
  WaniKani token), are masked in the UI, and are not part of the settings export. No key is in this repository.
- **Data sent:** only the chosen words with their reading and meaning, plus your level, to the selected provider.
- **Code:** `app/src/main/java/com/smouldering_durtles/wk/ai/` (`AiProvider`, `AiSettings`, `AiRequests` for the three
  request/response formats, `AiClient` on OkHttp, `AiStory` prompt and parser) and `activities/AIStoryActivity.java`.
  Anthropic: Messages API `2026-10-21` with server-side refusal fallback; OpenAI: Chat Completions with JSON mode;
  Gemini: `generateContent` with JSON output.
- **Tests:** `./gradlew testDebugUnitTest --tests 'com.smouldering_durtles.wk.ai.*'` (request shapes, parsing, errors, story parser).

## Preparing to build the code

Before you can build this code, you will have to replace the existing two files containing identification
information for the app with your own versions. This is because the open source license covering this app's 
code does not cover the name I gave the app, and it also doesn't cover my name. See the file LICENSE.md for details.

- Remove any existing Indentification.java file from app/src/main/java/com.smouldering_durtles.wk
- Copy the file app/Identification.java.sample.txt to app/src/main/java/com.smouldering_durtles.wk
- Name the copy Identification.java
- Edit the file to supply your own identification for the app
- Remove any existing strings.xml file from app/src/res/values
- Copy the file app/strings.xml.sample.txt to app/src/main/res/values
- Name the copy strings.xml
- Edit the file to supply your own identification for the app

After this, you are ready to build the code.

