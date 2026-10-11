# Play Store Release Checklist

What still has to happen before HealthHeat can go live, in order. Code-side fixes
already in the repo are listed at the end for reference.

## 1. Decide these first (they are permanent)

- [ ] **Application ID.** `com.example.healthheatv2` cannot be published; Play rejects
      `com.example.*`. Pick something you own, e.g. `com.healthheat.app`, and change
      `applicationId` in `frontend_android/app/build.gradle.kts`. It can never change after
      the first upload. Also add the new package to the Firebase project and download a new
      `google-services.json`, or Google sign-in will fail.
- [ ] **App name and listing language.** The code says HealthHeat, the decks say NutriScanner.

## 2. Backend (do before any tester installs the app)

- [ ] **Set `SECRET_KEY` on Render.** The code no longer has a fallback key, so the server will
      not start without it. Generate one with
      `python -c "import secrets; print(secrets.token_urlsafe(48))"`.
      If the server was running on the old built-in key, every existing login token becomes
      invalid and users must sign in again. That is expected.
- [ ] **Deploy the backend** before shipping the new app. `/api/chat` now requires login, and
      the app needs `DELETE /api/account`.
- [ ] **Hosting that doesn't sleep.** Render's free tier sleeps after ~15 minutes idle, so the
      first scan after a quiet period waits 30-60 s for the server to boot. This is the biggest
      cause of "scanning is slow". Options: a paid always-on instance, or another host with an
      always-on free tier. `keep_alive.py` pinging the server is a stopgap only.
- [ ] Rotate the Groq keys if they were ever committed or shared.

## 3. Signing

1. Create an upload key once and **back it up somewhere safe** (losing it means you can't ship updates
   without a Play support reset):
   ```
   keytool -genkeypair -v -keystore upload-keystore.jks -alias upload -keyalg RSA -keysize 2048 -validity 10000
   ```
2. Create `frontend_android/keystore.properties` (already in `.gitignore`):
   ```
   storeFile=upload-keystore.jks
   storePassword=...
   keyAlias=upload
   keyPassword=...
   ```
3. Build the bundle: `./gradlew :app:bundleRelease` → `app/build/outputs/bundle/release/app-release.aab`.
4. In Play Console, enable **Play App Signing** (default for new apps) and upload the `.aab`.
5. Add the **SHA-1 of the Play app signing key** (Play Console → App integrity) to Firebase,
   otherwise Google sign-in breaks for Play-installed builds.
6. Increase `versionCode` for every upload.

## 4. Play Console requirements

- [ ] **Closed testing first.** New *personal* developer accounts must run a closed test with
      at least 12 opted-in testers for 14 consecutive days before production access is granted.
      Start this as early as possible. It is the longest step.
- [ ] **Privacy policy URL** (mandatory, and especially for health data). It must cover: Google
      sign-in (name, email), profile data (age, sex, height, weight, diet, allergies, health
      conditions, medical notes), meal logs, chat messages sent to the AI provider (Groq),
      crash reporting (Firebase Crashlytics and Sentry), camera use, microphone use for voice
      input, how long data is kept, and how to delete it. Host it on a public page (GitHub Pages
      works).
- [ ] **Account deletion web link.** The in-app "Delete account and data" button is done
      (Profile screen). Play also requires a web page where users can request deletion without
      the app (a simple form or email address works).
- [ ] **Data safety form.** Declare: personal info (name, email), health and fitness info,
      app activity, app info and performance (crash logs), photos (product contributions), audio
      (if voice input leaves the device). State data is encrypted in transit and users can request
      deletion.
- [ ] **Health apps declaration.** Declare the app as nutrition/wellness, not a medical device.
      Show a short disclaimer in the app ("General nutrition information, not medical advice").
- [ ] **Target audience: 18+** (or 16+). Avoid the Families programme with health data.
- [ ] **App access for reviewers.** Reviewers can't get past Google sign-in without help.
      Provide a test Google account in "App access".
- [ ] Content rating questionnaire, store listing (icon 512x512, feature graphic 1024x500,
      at least 2 phone screenshots, short and full description).

## 5. Known gaps to fix before or soon after launch

- **Meal logs and chat are device-only and not tied to the account.** If a different person signs
  in on the same phone they see the previous user's logs, and reinstalling loses everything.
  Next step: sync meal logs to the backend per user.
- **Two crash reporters** (Crashlytics and Sentry). Keep one; both must be declared in Data safety.
- **Room is on an alpha version** (`2.7.0-alpha13`). Move to a stable release.
- **On-device Gemma** truncates prompts to 1500 characters, which cuts off the rules section.
  Cloud mode (the default) is not affected.
- **Food database values for the 72 added dishes are approximations.** Cross-check against the
  Indian Nutrient Databank (INDB) / IFCT 2017 and expand from there.
- **Test the release build on a real phone** end to end: sign in, onboarding, scan, NutriBot,
  log food by chat and by the dashboard box, delete a meal, delete account.

## Already fixed in code (this release)

- Profile answers now actually drive targets: gender, activity level and goal were stored in a
  different vocabulary than the calculators expected, so everyone got "moderate" activity and
  maintenance calories, and NutriBot described every woman as male.
- Health-condition chips feed the ICMR rules (previously never collected); old free-text answers
  are parsed so existing users benefit without re-editing.
- Meal logging: multi-item parsing ("2 roti, 1 bowl dal"), grams and household units, 140-item food
  database (was 68 raw ingredients), honest "estimated" labelling instead of a silent fake 220 kcal,
  a Today's Meals list with delete, and correct OpenFoodFacts units (kJ vs kcal, per-100 g keys).
- NutriBot: sees today's meals, all conditions, real multi-turn history; good answers are no longer
  replaced by canned text; suggestions target the user's actual biggest gap.
- Backend: personalized scan verdicts (including allergy warnings) were cached on the product and
  shown to every later user; now cached per user. `/api/chat` requires login. Medical notes are saved.
  Account deletion endpoint added.
- Database migrations replace the destructive fallback that would have wiped every user's data on
  each schema change.
- Release build: `targetSdk 36`, R8 keep rules for Retrofit/Gson, signing via `keystore.properties`.
