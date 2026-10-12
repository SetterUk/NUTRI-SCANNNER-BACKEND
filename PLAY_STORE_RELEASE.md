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
- [ ] **Deploy the backend** before shipping the new app. The app needs `/api/tracking/sync`
      (meal and weight sync), `DELETE /api/account`, login on `/api/chat`, and the new scan fields
      (verdict bands, score breakdown, alternatives, personal alerts). New tables and columns are
      created automatically on startup (`init_db`).
- [ ] After deploying, scan two or three products you know and check the verdicts look right.
      Products cached before this release are re-fetched from Open Food Facts and re-analysed on
      their next scan (analysis version 3), so the first scan of each is a little slower.
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
- [ ] **Privacy policy URL.** A draft is in `docs/privacy-policy.html`. Replace `[DEVELOPER NAME]`
      and `[CONTACT EMAIL]`, read it against the app once more, then publish the `docs/` folder with
      GitHub Pages (repo Settings → Pages → branch `main`, folder `/docs`).
- [ ] **Account deletion web link.** Draft in `docs/delete-account.html` (replace `[CONTACT EMAIL]`).
      The in-app "Delete account and data" button already exists (Profile screen).
- [ ] **Data safety form.** Declare: personal info (name, email), health and fitness info
      (profile, conditions, meal logs and weight, all stored on our server), app activity
      (scan history), app info and performance (Crashlytics, Firebase Analytics), photos (product
      contributions only). Voice input is converted to text by the phone's speech service; we
      never receive audio. Data is encrypted in transit; users can delete their account in the app
      or via the web page. Chat messages and profile context are sent to Groq for processing.
- [ ] **Health apps declaration.** Declare the app as nutrition/wellness, not a medical device.
      Show a short disclaimer in the app ("General nutrition information, not medical advice").
- [ ] **Target audience: 18+** (or 16+). Avoid the Families programme with health data.
- [ ] **App access for reviewers.** Reviewers can't get past Google sign-in without help.
      Provide a test Google account in "App access".
- [ ] Content rating questionnaire, store listing (icon 512x512, feature graphic 1024x500,
      at least 2 phone screenshots, short and full description).

## 5. Known gaps to fix before or soon after launch

- **NutriBot chat history stays on the phone** (meal and weight logs now sync to the account).
- **Backend Sentry** still runs on the server (`SENTRY_DSN`). Request errors can include personal
  data; keep `send_default_pii` off and mention server error monitoring in the privacy policy if
  you keep it. The app itself now only uses Crashlytics.
- **Alternatives depend on Open Food Facts data for India**, which is thin for some categories
  (biscuits often have no clearly healthier option). The section is hidden when there is nothing
  better, rather than suggesting something that isn't.
- **On-device Gemma** truncates prompts to 1500 characters, which cuts off the rules section.
  Cloud mode (the default) is not affected.
- **Food database values for the 72 added dishes are approximations.** Cross-check against the
  Indian Nutrient Databank (INDB) / IFCT 2017 and expand from there.
- **Test the release build on a real phone** end to end, ideally after installing the previous
  version first so the database migration (v10 → v11) runs on real data: sign in, onboarding,
  Home "Today" card, log food by typing on Home and by chat, delete a meal, log a weight, scan a
  product and tap a healthier pick, check an allergy alert (add "Peanuts" to allergies and scan a
  peanut snack), turn the evening reminder on, reinstall and confirm meals come back, delete account.

## Already fixed in code (this release)

- Scores: products were scored against the wrong food group (only the broadest OFF category was
  kept, so most namkeen used whole-food limits); one five-band verdict that agrees with the
  suggested frequency; plain "why this score" reasons; deterministic explanation when the AI is
  down; allergen matching fixed ("til" matched "lentil"); several scoring bugs (serving-size
  bonus inflation, nut discount on cashew biscuits, glycemic load per 100 g).
- Allergy and diet checks per user on the server (the phone used substring matching that missed
  "Dairy" vs "milk"), applied to verdicts and to healthier picks.
- Scans were never actually personalised: the LangGraph state dropped the user profile.
- Healthier picks: same-category products sold in India, re-scored with our engine.
- Scan cache expires, is cleared on profile or account change, and never stores failed analyses.
- Home is a daily "Today" screen with targets, logging, a next step, a weekly view and weight.
- Meal and weight logs sync to the account (Room v11 migration); different accounts on one phone
  no longer see each other's data.
- Opt-in evening reminder; protein targets aligned with ICMR-NIN 2020.
- One crash reporter (Crashlytics); Room 2.7.2 stable.

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
