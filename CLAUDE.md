# Fitness App — house rules

An app for Android, and later iPhone, that counts calories and tracks workouts in one place. It is
for one person: no sharing, no accounts, no sync.

It is a separate app from the checklist app, built alongside it, and the two are kept apart
wherever possible: they share only the GitHub organisation. Its own repository, its own signing
key, its own data and settings, and no links between the apps. Code or scripts that would be
useful in both are copied, not shared.

## Free, and nothing held back

The point of the app is what other calorie and workout apps do well, without what they get wrong:
features locked behind a subscription, and needing the internet for AI.

- Everything is free and always will be: no subscription, no paywall, no "premium" features, no
  ads, no in-app purchases, no trial.
- When a feature is one that other apps charge for, it is built here as an ordinary feature.
- Logging is made quick in plain ways first: recent and frequent foods first, saved meals,
  copying a meal from another day, and barcodes. AI is an optional extra on top, only on the
  phone (see "On-phone AI").

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
- The user's own food list can be imported from a spreadsheet saved as CSV, through the system
  file dialog, adding to the typed-in foods. **Details (default):** one food per row, with a
  header row naming the columns; comma or semicolon separators, and decimal commas with
  semicolons; per 100 g, per 100 ml or per serving; energy in kcal or kJ; portions like
  "1 slice=30". Nothing is replaced, a food already there by name and brand is skipped, and the
  rows that can't be read are listed with the reason. The app can save a template to fill in.
- The built-in common foods come from the US government's food data (USDA FoodData Central,
  public domain) and are bundled with the app **(default: its basic foods and "as eaten" dishes,
  several thousand in all)**.
- European foods are built in too: France's food table, CIQUAL (Anses, Licence Ouverte / Etalab
  Open Licence 2.0, which needs a credit in the app), with its English names, alongside the USDA
  list.
- Carbs are shown the way EU labels give them, without fibre, for every food, the USDA ones
  included. Typed-in labels can be entered in kJ or kcal, per 100 g (or 100 ml) or per serving.
- Barcode scanning covers as many countries as possible, every EU country included, and the user
  picks theirs.
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
  - A meal, or a whole day, can be copied from another day.
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

- Each meal can have its own calorie target.
- **Details (default):** off until the user turns it on. The day's target is split between
  breakfast, lunch, dinner and snacks (25%, 35%, 30% and 10% to start), changeable in
  percentages or calories, and protein, carbs and fat follow the same split. Each meal shows
  what is left of its own share. Going over in one meal doesn't take from the others' shares;
  the day's total still counts.

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

## Recipes

- Recipes made from several foods, split into servings.
- **Details (default):** a recipe lists its foods and amounts and how many servings it makes,
  and the app works out calories and protein, carbs and fat per serving. It can also be logged
  by weight: the user weighs the finished dish once, then logs it in grams, which allows for
  water lost in cooking. A recipe can have notes for the method. Changing a recipe applies from
  then on; days already logged keep what they were.

## Planning meals ahead

- Meals can be planned for the coming days.
- **Details (default):** foods, saved meals and recipes are put on a future day and meal. A day
  shows its planned calories and protein, carbs and fat against the target, so a plan can be
  balanced before it is eaten. On the day, ticking a planned item logs it as eaten; planned items
  that aren't ticked don't count. A day's or a week's plan can be copied to another.

## Shopping list

- A shopping list made from planned meals.
- **Details (default):** the user picks the days, and the list adds up the same food across
  them. Recipes are broken into their foods. Items are ticked off in the shop, and the user can
  add their own items, like washing-up liquid. Ticked items clear when a new list is made.

## Fasting

- A fasting timer.
- **Details (default):** plans of 16:8, 18:6, 20:4 and 23:1, or the user's own hours. The timer
  shows how long has gone and how long is left, and a notification says when the fast is done.
  Logging food during a fast asks whether to end it. Past fasts are kept, and finishing a planned
  fast counts toward a streak. The app gives no medical advice about fasting.

## Stats and charts

- Detailed stats and charts.
- **Details (default):** week, month and year views with averages of calories and protein,
  carbs and fat; days on target; the foods that give the most calories; weight against the goal,
  with the date it would be reached at the current trend (marked as an estimate); workouts per
  week; the total lifted (sets × reps × weight) per exercise and per muscle group over time;
  personal bests over time; water; and fasts. The charts are drawn by the app itself.

## Keeping going

- Streaks and personal bests. No points or shop, and no link to the checklist app.
- **Streaks (default):** days with food logged; days on target (within 10% of the calorie target
  either side); weeks with the user's chosen number of workouts (3 to start), so rest days don't
  break it; days the water target was reached; days a planned fast was finished.
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
   Then importing the user's own food list from a spreadsheet. (0.1.1)
2. Workouts: weights, cardio, bodyweight and timed, saved workouts, the rest timer, personal
   bests, and calories burned added to the day. (0.2.0)
3. Barcode scanning and the country files. (0.3.0)
4. Streaks, stats and charts, reminders, the privacy lock and the automatic backup. (0.4.0)
5. Recipes, planning meals ahead and the shopping list. (0.5.0)
6. A target for each meal, and the fasting timer. (0.6.0)

## Layout (default)

- Five tabs: Diary, Foods (your foods, saved meals, country files), Workouts, Plan (meals ahead,
  shopping list, recipes, fasting) and Progress (stats and streaks, weight). Settings opens from
  the gear button at the top of the diary.

## More, after 0.6.0

Decided on 2026-11-09: everything below is added.

- **More nutrients:** fibre, sugar, salt and saturated fat, as EU labels list them, with optional
  daily targets **(default: shown on each food and day; targets off until set; salt in g, from
  sodium × 2.5 where a table gives only sodium)**. Foods logged before keep what they had.
- **Favourites:** foods starred by the user, always at the top of the food search.
- **"Same as yesterday":** one tap copies a meal from the day before.
- **Reading a label with the camera:** the phone reads the nutrition numbers off a label and fills
  in the typed-in food form, for the user to check before saving. This is text recognition, not
  guessing a meal, and it runs on the phone **(default: Tesseract, Apache-2.0, through
  Tesseract4Android, with its English data bundled; not Google's ML Kit)**.
- **Body measurements:** waist, hips, chest, arms, thighs and neck, and body-fat % if known, each
  with a chart **(default: any of them, whenever the user likes)**.
- **Progress photos:** kept only in the app's own storage on the phone, behind the privacy lock
  when Progress is locked, never in the export file **(default; the user can save one out
  through the system dialog)**.
- **Sleep and mood:** a note on a day: hours slept and a mood from 1 to 5, with a chart next to
  calories **(default)**.
- **Weekly check-in:** once a week the Progress tab sums up the average weight change against
  the plan and offers a new target if it is off by 50 kcal or more, never changing it by itself
  **(default: on Mondays)**.
- **Your own habits:** the user adds habits ("no sugary drinks", "stretch 10 minutes"), ticks them
  each day, and each gets a streak like the built-in ones.
- **Workout programmes:** a weekly schedule of saved workouts ("Mon: Push, Wed: Pull, Fri: Legs");
  the Workouts tab suggests today's session.
- **Plate calculator:** the plates for each side of the bar for a given weight **(default: 20 kg
  bar; plates 25, 20, 15, 10, 5, 2.5 and 1.25 kg, or 45 lb bar and 45, 35, 25, 10, 5, 2.5 lb;
  both changeable)**.
- **"Try a bit more" hints:** when every set last time hit its reps, the next session suggests a
  little more weight **(default: +2.5 kg or +5 lb)**, or one more rep for bodyweight.
- **Supersets and warm-up sets:** exercises can be grouped to alternate, and a set can be marked
  as a warm-up, which counts in neither personal bests nor the total lifted.
- **Home-screen widget:** today's calories left, water, and buttons to add water or open quick
  add.
- **Steps from the phone:** read from Health Connect, which works offline, if the user allows it;
  shown on the day **(default: steps add calories at weight (kg) × 0.0005 per step, following the
  half-count setting; a logged walk or run is not counted twice: steps during a logged cardio
  workout are left out)**.
- **Theme:** follow the phone, light or dark, and an accent colour.
- **Spreadsheet export:** the diary, weigh-ins, measurements and workouts saved as CSV files
  through the system dialog, for reading elsewhere. The backup file stays the way to move data.

Build order **(default)**: 0.7.0 the food ones (nutrients, favourites, same as yesterday,
reading a label); 0.8.0 body and habits (measurements, photos, sleep and mood, weekly check-in,
own habits); 0.9.0 workouts (programmes, plate calculator, hints, supersets and warm-ups); 0.10.0
the widget, steps, theme and spreadsheet export.

## More, after 0.10.0

Decided on 2026-11-09: everything below is added.

- **Alcohol and caffeine:** counted from foods and drinks, in grams of alcohol (shown as
  standard drinks) and mg of caffeine, with optional daily limits **(default: from the built-in
  food data and typed-in labels; a standard drink is 10 g of alcohol, changeable (14 g in the US,
  8 g as a UK unit); limits off until set)**.
- **Vitamins and minerals:** for built-in foods, from the government tables **(default: iron,
  calcium, vitamin D, vitamin C, potassium, magnesium, vitamin B12, folate, zinc and vitamin A,
  shown as a share of the EU label reference values, which are labelled as such and not as
  personal needs; typed-in foods and country files don't have them)**.
- **Different targets by day:** a calorie target for each weekday, or extra calories on training
  days **(default: both off; a training day is one with a finished workout, or a programme day;
  protein, carbs and fat follow the percentages)**.
- **Diet breaks:** a planned week or two at maintenance calories, then back to the plan **(default:
  maintenance is the worked-out target for keeping the current weight; 1 or 2 weeks)**.
- **Body-fat estimate:** from waist, neck and height (and hips for women), by the US Navy
  formula, always marked as an estimate.
- **Supplements and medication:** a daily tick list with optional reminders at set times. The app
  gives no medical advice about them **(default: on the diary next to the habits; in the backup
  file)**.
- **Cycle tracking:** period start and end days, kept on the phone and behind the lock with
  Progress; the weight chart marks period days, because weight often rises around them
  **(default: off until turned on in Settings; the next period's date is shown as an estimate
  from the average cycle; no fertility predictions and no medical advice)**.
- **Interval timer:** work and rest rounds (HIIT, Tabata) with a buzz at each change **(default:
  presets 20/10 × 8 and 40/20 × 10, or the user's own; the screen stays on while it runs)**.
- **One-rep-max table:** the weights for 1 to 12 reps from the estimated one-rep max, on each
  exercise's personal bests **(default: the same Epley formula as the bests)**.
- **Exercise notes:** the user's own cues per exercise ("elbows in"), shown during the workout.
- **Calendar view:** a month grid, each day coloured by the calorie target and marked with
  workouts **(default: on Progress)**.
- **Year in review:** the year's totals and bests **(default: on Progress, for this year and past
  years)**.
- **Larger text and colour-blind-friendly charts:** a text size setting, and chart colours that
  don't rely on red and green **(default: Okabe–Ito colours when turned on)**.

Build order **(default)**: 0.11.0 food (alcohol and caffeine, vitamins and minerals, targets by
day, diet breaks); 0.12.0 body (body-fat estimate, supplements and medication, cycle tracking);
0.13.0 workouts (interval timer, one-rep-max table, exercise notes); 0.14.0 overview (calendar,
year in review, larger text and colour-blind-friendly charts).

## Keeping it simple

Decided on 2026-11-09: the app must not feel cluttered. Only what the user uses shows; every
other part can be hidden.

- A **Features** page in Settings with a switch for each part. A part that is off has no tab,
  button, diary card or setting anywhere, and nothing logged in it is deleted; turning it back on
  shows it all again. The food diary (calories, protein, carbs and fat) and Settings always stay.
- A new install starts simple, and the Features page suggests the rest under "You might like",
  each with a one-line description.
- **Details (default):** a new install starts with the food diary, saved meals and copying,
  water, weight, workouts, and stats and streaks. Everything else starts off: barcodes and country
  files; reading labels with the camera; recipes, planning ahead and the shopping list; fasting;
  a target for each meal; targets by day and diet breaks; fibre, sugar, saturates and salt;
  alcohol and caffeine; vitamins and minerals; measurements, the body-fat estimate and photos;
  sleep and mood; habits; supplements and medication; cycle tracking; steps; the weekly check-in;
  training extras (the programme, plate calculator, hints, supersets and warm-ups, interval timer
  and exercise notes); and the calendar and year in review. On an update, a part the user has
  already used (has data or settings in) stays on.

## On-phone AI

Decided on 2026-11-09: AI features are added, on these terms.

- Everything works fully without AI. Each AI feature is optional, separate and off until the user
  turns it on, and nothing else in the app depends on it.
- It runs only on the phone. The app still has no internet: a model is a one-time download from
  the Releases page, opened in the app the same way as a country file.
- AI only suggests. The user checks every suggestion before anything is logged or saved.
- No medical advice, and no guessing about the user's body.
- The features: describing a meal in words or by voice ("two eggs and a slice of toast"),
  matched to foods; suggesting foods from a photo of a plate; pasting a recipe's text to make a
  recipe; reading labels better than Tesseract; and a weekly summary in plain words. No camera
  rep counter.
- **Details (default):**
  - The models are files on a "models" Release, each with its licence and credit, using only
    models whose licences let them be passed on (Apache-2.0, MIT or similar): a small text model
    for meals, recipes and the summary (Qwen2.5 1.5B Instruct, run with llama.cpp); a speech model
    for voice (Whisper, run with whisper.cpp); a small vision model for plate photos (SmolVLM);
    and PaddleOCR's mobile models for labels.
  - The model only picks words; the app's own food data gives the numbers. A meal or recipe
    becomes foods from the user's foods and the built-in lists, with amounts, shown as a list to
    tick and change. Plate photos only suggest foods; the amounts are the user's.
  - The weekly summary words the numbers the app has already worked out, and the user can
    read the numbers beside it.
  - On the Features page under "AI on this phone", each with its download size, and a note when
    a phone is likely too slow.
  - Build order: 0.16.0 better label reading; 0.17.0 describing a meal, pasting a recipe and the
    weekly summary; 0.18.0 voice; 0.19.0 plate photos.

## Talking to the owner

- Short and plain. The owner is not an Android developer. Say what changed, what to try, and what
  is not verified.
- "Done" means a Release the owner can install, not a green check.
