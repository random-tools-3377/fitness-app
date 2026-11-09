"""Builds the app's built-in list of common foods (app/src/main/assets/foods.tsv) from the US
government's food data, USDA FoodData Central, which is in the public domain.

Run: python3 tools/build-food-list.py [--work DIR] [--out app/src/main/assets]

It downloads three FoodData Central datasets (JSON), unless they are already in the work folder:

  - Foundation Foods      basic foods, with the newest analyses
  - SR Legacy             basic foods, the classic USDA reference list
  - FNDDS (Survey Foods)  foods and dishes "as eaten", from the national food survey

and keeps every food that has calories. When the same name appears more than once, Foundation
wins over SR Legacy, and SR Legacy over FNDDS. Nothing is made up or estimated: every number is
USDA's, rounded to one decimal.

Each line of foods.tsv is:

    id <TAB> name <TAB> kcal <TAB> protein <TAB> carbs <TAB> fat <TAB> portions

Nutrients are per 100 g. The id is the dataset letter (F, S or N) and the FoodData Central id.
Portions are "label=grams" pairs separated by "|", for example "1 cup, chopped=128|1 tbsp=8".
Lines starting with "#" are comments.

The download page is read to find the newest file of each dataset. If it can't be read, the
newest known file names are tried. Any of them can be given with --foundation, --sr, --fndds.
"""
import argparse
import io
import json
import os
import re
import sys
import urllib.request
import zipfile

BASE = "https://fdc.nal.usda.gov"
PAGES = [BASE + "/download-datasets", BASE + "/download-datasets.html"]
USER_AGENT = "fitness-app-food-list-builder"

DATASETS = {
    # key: (file name pattern, known file names newest first, JSON top-level key, letter, title)
    "foundation": (
        r"FoodData_Central_foundation_food_json_[0-9-]+\.zip",
        ["FoodData_Central_foundation_food_json_2025-12-18.zip", "FoodData_Central_foundation_food_json_2025-04-24.zip",
         "FoodData_Central_foundation_food_json_2024-10-31.zip"],
        "FoundationFoods", "F", "Foundation Foods",
    ),
    "sr": (
        r"FoodData_Central_sr_legacy_food_json_[0-9-]+\.zip",
        ["FoodData_Central_sr_legacy_food_json_2018-04.zip"],
        "SRLegacyFoods", "S", "SR Legacy",
    ),
    "fndds": (
        r"FoodData_Central_survey_food_json_[0-9-]+\.zip",
        ["FoodData_Central_survey_food_json_2024-10-31.zip", "FoodData_Central_survey_food_json_2022-10-28.zip"],
        "SurveyFoods", "N", "FNDDS (Survey Foods)",
    ),
}
ORDER = ["foundation", "sr", "fndds"]

# FoodData Central nutrient ids, best first.
ENERGY_KCAL = [1008, 2048, 2047]  # Energy; Energy (Atwater specific factors); (general factors)
ENERGY_KJ = [1062]
PROTEIN = [1003]
CARBS = [1005, 1050]  # Carbohydrate, by difference; by summation
FAT = [1004, 1085]  # Total lipid (fat); Total fat (NLEA)

MAX_PORTIONS = 8


def fetch(url):
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(request, timeout=300) as response:
        return response.read()


def exists(url):
    request = urllib.request.Request(url, method="HEAD", headers={"User-Agent": USER_AGENT})
    try:
        with urllib.request.urlopen(request, timeout=60) as response:
            return response.status == 200
    except Exception:
        return False


def find_files():
    """The newest file of each dataset named on the download page, as {key: name}."""
    found = {}
    for page in PAGES:
        try:
            html = fetch(page).decode("utf-8", "replace")
        except Exception as e:
            print("Could not read", page, ":", e)
            continue
        for key, (pattern, _, _, _, _) in DATASETS.items():
            names = sorted(set(re.findall(pattern, html)))
            if names and key not in found:
                found[key] = names[-1]
        if len(found) == len(DATASETS):
            break
    return found


def download(key, name, work):
    path = os.path.join(work, name)
    if os.path.exists(path):
        return path
    url = BASE + "/fdc-datasets/" + name
    print("Downloading", url)
    data = fetch(url)
    with open(path + ".part", "wb") as f:
        f.write(data)
    os.replace(path + ".part", path)
    return path


def choose(key, given, from_page, work):
    if given:
        return given
    # A file already downloaded into the work folder is used as it is.
    pattern = DATASETS[key][0]
    local = sorted(n for n in os.listdir(work) if re.fullmatch(pattern, n))
    if local:
        return local[-1]
    if key in from_page:
        return from_page[key]
    for name in DATASETS[key][1]:
        if exists(BASE + "/fdc-datasets/" + name):
            return name
    sys.exit("No %s file found. Give its file name with --%s." % (DATASETS[key][4], key))


def load(path, top):
    with zipfile.ZipFile(path) as z:
        member = next(n for n in z.namelist() if n.endswith(".json"))
        with z.open(member) as f:
            return json.load(io.TextIOWrapper(f, encoding="utf-8"))[top]


def nutrient(food, ids):
    amounts = {}
    for n in food.get("foodNutrients", []):
        info = n.get("nutrient") or {}
        nid = info.get("id")
        amount = n.get("amount")
        if nid is None or amount is None:
            continue
        amounts.setdefault(nid, amount)
    for nid in ids:
        if nid in amounts:
            return float(amounts[nid])
    return None


def number(x):
    text = ("%.1f" % x)
    return text[:-2] if text.endswith(".0") else text


def clean(text):
    text = re.sub(r"[\t\r\n|=]+", " ", str(text))
    return re.sub(r" +", " ", text).strip()


def portion_label(p):
    description = (p.get("portionDescription") or "").strip()
    if description:
        lowered = description.lower()
        if lowered.startswith("quantity not specified") or lowered.startswith("guideline amount"):
            return None
        return clean(description)
    parts = []
    amount = p.get("amount")
    if amount is None:
        amount = p.get("value")
    if amount is not None:
        parts.append(number(float(amount)))
    unit = ((p.get("measureUnit") or {}).get("name") or "").strip()
    if unit and unit.lower() not in ("undetermined", "unknown"):
        parts.append(unit)
    modifier = (p.get("modifier") or "").strip()
    # Some files hold a numeric code instead of words in the modifier.
    if modifier and not modifier.isdigit():
        parts.append(modifier)
    if len(parts) <= 1:
        return None
    return clean(" ".join(parts))


def portions(food):
    out = []
    seen = set()
    items = sorted(food.get("foodPortions", []), key=lambda p: p.get("sequenceNumber") or 0)
    for p in items:
        grams = p.get("gramWeight")
        if not grams or float(grams) <= 0:
            continue
        label = portion_label(p)
        if not label or label.lower() in seen:
            continue
        seen.add(label.lower())
        out.append("%s=%s" % (label, number(float(grams))))
        if len(out) >= MAX_PORTIONS:
            break
    return "|".join(out)


def convert(food, letter):
    kcal = nutrient(food, ENERGY_KCAL)
    if kcal is None:
        kj = nutrient(food, ENERGY_KJ)
        if kj is not None:
            kcal = kj / 4.184
    if kcal is None:
        return None
    name = clean(food.get("description", ""))
    if not name:
        return None
    return [
        letter + str(food["fdcId"]),
        name,
        number(kcal),
        number(nutrient(food, PROTEIN) or 0.0),
        number(nutrient(food, CARBS) or 0.0),
        number(nutrient(food, FAT) or 0.0),
        portions(food),
    ]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--work", default="build/usda", help="folder for the downloaded files")
    parser.add_argument("--out", default="app/src/main/assets", help="folder for foods.tsv and its notice")
    for key in DATASETS:
        parser.add_argument("--" + key, help="file name of the %s download" % DATASETS[key][4])
    args = parser.parse_args()
    os.makedirs(args.work, exist_ok=True)
    os.makedirs(args.out, exist_ok=True)

    from_page = find_files()
    print("Files on the download page:", from_page or "none found")

    rows = []
    names = set()
    used = []
    for key in ORDER:
        _, _, top, letter, title = DATASETS[key]
        name = choose(key, getattr(args, key), from_page, args.work)
        path = download(key, name, args.work)
        foods = load(path, top)
        kept = 0
        for food in foods:
            row = convert(food, letter)
            if row is None:
                continue
            k = row[1].lower()
            if k in names:
                continue
            names.add(k)
            rows.append(row)
            kept += 1
        print("%s (%s): %d foods, %d kept" % (title, name, len(foods), kept))
        used.append((title, name, kept))

    rows.sort(key=lambda r: r[1].lower())
    target = os.path.join(args.out, "foods.tsv")
    with open(target, "w", encoding="utf-8", newline="\n") as f:
        f.write("# Built-in foods, from USDA FoodData Central (public domain). Made by tools/build-food-list.py.\n")
        for title, name, kept in used:
            f.write("# %s: %s, %d foods\n" % (title, name, kept))
        f.write("# id\tname\tkcal\tprotein\tcarbs\tfat\tportions   (nutrients per 100 g; portions label=grams)\n")
        for row in rows:
            f.write("\t".join(row) + "\n")
    print(len(rows), "foods written to", target)

    notice = os.path.join(args.out, "foods-NOTICE.txt")
    with open(notice, "w", encoding="utf-8", newline="\n") as f:
        f.write(
            "The built-in food list (foods.tsv) comes from USDA FoodData Central, the food data of the\n"
            "U.S. Department of Agriculture, Agricultural Research Service (https://fdc.nal.usda.gov).\n"
            "FoodData Central is in the public domain (CC0 1.0). The datasets used are:\n\n"
        )
        for title, name, kept in used:
            f.write("  - %s: %s (%d foods)\n" % (title, name, kept))
        f.write(
            "\nU.S. Department of Agriculture, Agricultural Research Service. FoodData Central.\n"
            "Nutrients are per 100 g as published. Where a name appears in more than one dataset, only the\n"
            "first is kept, in the order above. This app is not endorsed by the USDA.\n"
        )


if __name__ == "__main__":
    main()
