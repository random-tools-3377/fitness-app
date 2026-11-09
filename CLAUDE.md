# Fitness App — house rules

An app for Android, and later iPhone, that counts calories and tracks workouts in one place. It is
for one person: no sharing, no accounts, no sync.

It is a separate app from the checklist app, built alongside it, and the two are kept apart
wherever possible: they share only the GitHub organisation. Its own repository, its own signing
key, its own data and settings, and no links between the apps. Code or scripts that would be
useful in both are copied, not shared.

Designed on 2026-11-09. Plain bullets are the owner's decisions. Bullets marked **(default)** are
suggestions the owner has not overruled and can still change. Anything not written here is not
decided, so ask the owner rather than filling it in.

## This repository is public

Anyone can read the code, the commits, the issues, the Actions logs and the Releases. So none of
them may ever contain the owner's name or username, what the owner said about the app (feedback,
quotes, requests), or any of the owner's own data: weight, body details, targets, foods eaten,
workouts or exports.

- Write "the owner" or "the user".
- Decisions are recorded as decisions, not as who said what.
- Release notes are written for a non-developer and name nobody.
- Commits are authored as `Claude <noreply@anthropic.com>`.

## No internet

- The app has no network access. Same rules as the checklist app **(default)**: the manifest
  removes `INTERNET`, and CI fails the build if `aapt2 dump permissions` on the release APK lists
  any network permission.
- Nothing that would need it: no online food search, cloud backup, sync, analytics, crash
  reporter, update check or Play Services. When a feature would be easier online, offer the
  offline version and ask the owner.
- No phone backup **(default)**: `android:allowBackup="false"` and data-extraction rules that
  exclude everything. The backup is the export file.
- The camera is the only extra permission, for scanning barcodes, asked the first time the user
  scans **(default)**. Scanning uses ZXing (Apache-2.0), which works offline, not Google's
  scanner, which needs Play Services **(default)**.

## Food

- Three ways to find a food: foods the user types in from the label, a built-in list of common
  foods, and barcode scanning.
- Typed-in foods are remembered, with their portions, and so are saved meals.
- The built-in common foods come from the US government's food data (USDA FoodData Central,
  public domain) and are bundled with the app **(default: its basic foods and "as eaten" dishes,
  several thousand in all)**.
- Barcode scanning covers as many countries as possible, and the user picks theirs.
- **How that works with no internet (default):** the app itself stays small. Each country's
  packaged products are a separate file on the Releases page, built from Open Food Facts (its
  database licence, ODbL, needs a credit in the app and in the files). The user downloads the
  countries they want in the phone's browser and opens them in the app, the same way as
  importing a backup. Only products with calories filled in are kept. The US file also takes in
  the US government's branded-food data. A barcode found in no file is typed in once, from the
  label, and then remembered.
- **Details (default):**
  - The day's diary is split into breakfast, lunch, dinner and snacks.
  - Amounts in grams or millilitres, or in the food's own portions ("1 slice", "1 cup").
  - Quick add: just a number of calories, with no food.
  - The most recent and most used foods come first.
- Each food and each day shows calories and also protein, carbs and fat.

## Targets

- The daily calorie target is worked out from age, height, weight, sex and how active the user
  is, plus whether they want to lose, keep or gain weight. The user can always change the number.
- **Details (default):** resting burn by the Mifflin–St Jeor formula, times an activity level
  (from "mostly sitting" to "very active"). Lose or gain at 0.25, 0.5 or 0.75 kg a week, about
  550 kcal a day per 0.5 kg. The app never suggests a target below 1,200 kcal (women) or
  1,500 kcal (men), and says why. When a new weigh-in changes the worked-out target by 50 kcal or
  more, the app offers the new number rather than changing it by itself.
- Protein, carbs and fat get targets too **(default: 25% / 50% / 25% of the calories, which the
  user can change, in percentages or grams)**.
- Water has a daily target **(default: 2 litres, with buttons for a glass of 250 ml and a bottle
  of 500 ml, sizes the user can change)**.

## Body weight

- Weigh-ins, with a chart over time.
- **Details (default):** the chart also shows a 7-day average line, because day-to-day weight
  jumps around. The latest weigh-in is the one used for the target and for calories burned.

## Workouts

- Four kinds: weights (sets, reps and weight), cardio (time and distance, typed in, no GPS),
  bodyweight and timed (reps or seconds), and saved workouts.
- A saved workout, like "Leg day", fills in each exercise from last time.
- A rest timer between sets.
- **Details (default):**
  - A built-in list of common exercises, plus the user's own.
  - The rest timer starts when a set is ticked, 90 seconds to start, changeable per exercise. It
    buzzes and shows a notification if the app is in the background.
  - A workout in progress survives the app being closed.

## Calories burned

- Estimated from logged workouts, using the body weight, and added to the day's calorie allowance.
- **Details (default):** from standard activity values (METs) × body weight × time. Cardio uses
  its speed when a distance is given; weights and bodyweight use the workout's length. Added in
  full, with a Settings choice to count only half, because estimates tend to run high.

## Keeping going

- Streaks and personal bests. No points or shop, and no link to the checklist app.
- **Streaks (default):** days with food logged; days on target (within 10% of the calorie target
  either side); weeks with the user's chosen number of workouts (3 to start), so rest days don't
  break it; days the water target was reached.
- **Personal bests (default):** heaviest weight, most reps at a weight, an estimated one-rep max,
  the longest and the fastest cardio. Beating one is celebrated on the spot.

## Also (default, same as the checklist app)

- Units follow the phone's region, with Settings to switch kg/lb, cm/ft-in, kcal/kJ, ml/fl oz and
  km/miles.
- Export everything to a file and import it back, through the system dialogs. An automatic daily
  backup to a folder the user picks, keeping the last 7.
- A Privacy menu where the user picks what sits behind the phone's own lock (fingerprint, face or
  PIN), and the recent-apps preview is hidden.
- Optional reminders for meals, water, workouts and weigh-ins, scheduled on the phone.

## iPhone (default, same as the checklist app)

- Kotlin Multiplatform from the start: the shared code holds the food maths, targets, calories
  burned, streaks, personal bests and the export format, and CI builds and tests it for iPhone on
  every push. Jetpack Compose for the Android screens, written so they can move to Compose
  Multiplatform.
- The iPhone app is built once there is a way to install it. Each phone keeps its own data.

## Builds and releases

- The app is called Fitness App; the repository is `fitness-app`. Its package name is
  `io.github.randomtools3377.fitness` **(default)**.
- Apart from the name and the key, this section copies the checklist app's rules **(default)**.

- GitHub Actions builds the APK. The owner installs from the Releases page in the phone's browser,
  so every build meant to be tried is a Release with the APK attached.
- **Every release is signed with the same key**, this app's own, not the checklist app's. The key
  is in repository secrets, never in the repo: `SIGNING_KEYSTORE_B64` (a base64 PKCS12 keystore),
  `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD`. The owner keeps a backup
  offline. Never generate another key for this app.
- CI checks the release APK with `apksigner verify --print-certs` against this certificate, so a
  build signed with a debug key never becomes a Release:
  SHA-256 `10:BE:7D:71:15:4D:4D:85:CB:67:82:46:4A:5A:97:B5:F4:56:C2:3B:64:E9:59:6A:B9:81:B5:75:3D:B1:0E:74`.
- Releases come only from pushes to `main`. No `pull_request_target`, and signing secrets never
  go to a workflow that runs code from a pull request.
- CI on every push: the network-permission check, the signature check, and the tests.

Build order **(default)**, each step a Release the owner can try:

1. The food diary: typed-in foods, built-in common foods, saved meals, quick add, the worked-out
   target, protein, carbs and fat, water, body weight, export and import. (0.1.0)
2. Workouts: weights, cardio, bodyweight and timed, saved workouts, the rest timer, personal
   bests, and calories burned added to the day. (0.2.0)
3. Barcode scanning and the country files. (0.3.0)
4. Streaks, charts, reminders, the privacy lock and the automatic backup. (0.4.0)

## Not decided yet

- A home-screen widget; reading steps from the phone (Health Connect, which works offline);
  recipes made from several foods.

## Talking to the owner

- Short and plain. The owner is not an Android developer. Say what changed, what to try, and what
  is not verified.
- "Done" means a Release the owner can install, not a green check.
