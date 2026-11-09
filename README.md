# Fitness App

An Android app that counts calories and tracks workouts in one place. It is for one person: no
accounts, no sharing, no sync, and no internet.

The design and the house rules are in [CLAUDE.md](CLAUDE.md).

## Installing

Open the [Releases page](../../releases) in the phone's browser, download the newest APK, open it
and allow installing from the browser if Android asks. Each new version installs over the last one
and keeps the data.

## Barcode scanning

Scanning works offline from a file of packaged products for your country, which you add once. The
files are on the [Country files](../../releases/tag/countries) Release, built monthly from Open
Food Facts (ODbL) by [tools/build-country-files.py](tools/build-country-files.py).

## Food data

The built-in foods come from USDA FoodData Central (public domain) and the ANSES-CIQUAL French food
composition table (Licence Ouverte / Etalab Open Licence 2.0). See
[app/src/main/assets/foods-NOTICE.txt](app/src/main/assets/foods-NOTICE.txt). The list is built by
[tools/build-food-list.py](tools/build-food-list.py), which the "Food list" workflow runs.
