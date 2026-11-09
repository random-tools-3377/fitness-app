"""Builds the app's built-in list of common foods (app/src/main/assets/foods.tsv) from two
government food tables:

  - USDA FoodData Central (United States, public domain):
      Foundation Foods      basic foods, with the newest analyses
      SR Legacy             basic foods, the classic USDA reference list
      FNDDS (Survey Foods)  foods and dishes "as eaten", from the national food survey
  - CIQUAL (France, ANSES, Licence Ouverte / Etalab Open Licence 2.0): European foods and dishes,
    with English names.

Run: python3 tools/build-food-list.py [--work DIR] [--out app/src/main/assets]

Files already in the work folder are used as they are; the rest are downloaded. Every food with
calories is kept. Within USDA, when the same name appears more than once, Foundation wins over
SR Legacy, and SR Legacy over FNDDS. CIQUAL foods are all kept, as their names differ in style.

Carbs are given the way EU labels give them: without fibre. CIQUAL already does this. USDA's
"carbohydrate, by difference" includes fibre, so the fibre is taken off; USDA's "carbohydrate,
by summation" (sugars and starch) already leaves it out. Nothing is made up: every number is
from the tables, rounded to one decimal. A value a table gives as "traces", as below a limit
("< 0.5"), or leaves out is written as 0.

Each line of foods.tsv is:

    id <TAB> name <TAB> kcal <TAB> protein <TAB> carbs <TAB> fat <TAB> portions

Nutrients are per 100 g. The id is the source letter and the food's id in that table: F, S or N
and the FoodData Central id, or C and the CIQUAL code. Portions are "label=grams" pairs separated
by "|", for example "1 cup, chopped=128|1 tbsp=8" (CIQUAL has none). Lines starting with "#" are
comments.

The USDA download page is read to find the newest file of each dataset, and data.gouv.fr's
catalogue to find the newest CIQUAL XML file. If one can't be read, the newest known file names
are tried. Any file can be given with --foundation, --sr, --fndds or --ciqual.
"""
import argparse
import io
import json
import os
import re
import sys
import urllib.request
import xml.etree.ElementTree as ET
import zipfile

USER_AGENT = "fitness-app-food-list-builder"

# ---------------------------------------------------------------------------------------------
# USDA FoodData Central

USDA = "https://fdc.nal.usda.gov"
USDA_PAGES = [USDA + "/download-datasets", USDA + "/download-datasets.html"]

USDA_SETS = {
    # key: (file name pattern, known file names newest first, JSON top-level key, letter, title)
    "foundation": (
        r"FoodData_Central_foundation_food_json_[0-9-]+\.zip",
        ["FoodData_Central_foundation_food_json_2026-10-29.zip", "FoodData_Central_foundation_food_json_2025-04-24.zip"],
        "FoundationFoods", "F", "USDA Foundation Foods",
    ),
    "sr": (
        r"FoodData_Central_sr_legacy_food_json_[0-9-]+\.zip",
        ["FoodData_Central_sr_legacy_food_json_2018-04.zip"],
        "SRLegacyFoods", "S", "USDA SR Legacy",
    ),
    "fndds": (
        r"FoodData_Central_survey_food_json_[0-9-]+\.zip",
        ["FoodData_Central_survey_food_json_2024-10-31.zip", "FoodData_Central_survey_food_json_2022-10-28.zip"],
        "SurveyFoods", "N", "USDA FNDDS (Survey Foods)",
    ),
}
USDA_ORDER = ["foundation", "sr", "fndds"]

# FoodData Central nutrient ids, best first.
ENERGY_KCAL = [1008, 2048, 2047]  # Energy; Energy (Atwater specific factors); (general factors)
ENERGY_KJ = [1062]
PROTEIN = [1003]
CARBS_BY_DIFFERENCE = 1005  # includes fibre
CARBS_BY_SUMMATION = 1050  # sugars and starch, no fibre
FIBRE = 1079  # Fiber, total dietary
FAT = [1004, 1085]  # Total lipid (fat); Total fat (NLEA)

MAX_PORTIONS = 8

# ---------------------------------------------------------------------------------------------
# CIQUAL

CIQUAL_DATASET_API = "https://www.data.gouv.fr/api/1/datasets/table-de-composition-nutritionnelle-des-aliments-ciqual/"
CIQUAL_KNOWN = [
    "https://ciqual.anses.fr/cms/sites/default/files/inline-files/XML_2020_07_07.zip",
]
CIQUAL_LETTER = "C"
CIQUAL_TITLE = "CIQUAL (France)"


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


def download(url, path):
    if os.path.exists(path):
        return path
    print("Downloading", url)
    data = fetch(url)
    with open(path + ".part", "wb") as f:
        f.write(data)
    os.replace(path + ".part", path)
    return path


def number(x):
    text = "%.1f" % x
    if text == "-0.0":
        text = "0.0"
    return text[:-2] if text.endswith(".0") else text


def clean(text):
    text = re.sub(r"[\t\r\n|=]+", " ", str(text))
    return re.sub(r" +", " ", text).strip()


# ---- USDA ----


def usda_files_on_page():
    found = {}
    for page in USDA_PAGES:
        try:
            html = fetch(page).decode("utf-8", "replace")
        except Exception as e:
            print("Could not read", page, ":", e)
            continue
        for key, (pattern, _, _, _, _) in USDA_SETS.items():
            names = sorted(set(re.findall(pattern, html)))
            if names and key not in found:
                found[key] = names[-1]
        if len(found) == len(USDA_SETS):
            break
    return found


def usda_choose(key, given, from_page, work):
    if given:
        return given
    pattern = USDA_SETS[key][0]
    local = sorted(n for n in os.listdir(work) if re.fullmatch(pattern, n))
    if local:
        return local[-1]
    if key in from_page:
        return from_page[key]
    for name in USDA_SETS[key][1]:
        if exists(USDA + "/fdc-datasets/" + name):
            return name
    sys.exit("No %s file found. Give its file name with --%s." % (USDA_SETS[key][4], key))


def usda_load(path, top):
    with zipfile.ZipFile(path) as z:
        member = next(n for n in z.namelist() if n.endswith(".json"))
        with z.open(member) as f:
            return json.load(io.TextIOWrapper(f, encoding="utf-8"))[top]


def usda_amounts(food):
    amounts = {}
    for n in food.get("foodNutrients", []):
        info = n.get("nutrient") or {}
        nid = info.get("id")
        amount = n.get("amount")
        if nid is None or amount is None:
            continue
        amounts.setdefault(nid, float(amount))
    return amounts


def first(amounts, ids):
    for nid in ids:
        if nid in amounts:
            return amounts[nid]
    return None


def usda_carbs(amounts):
    """Carbs without fibre, the way EU labels give them."""
    if CARBS_BY_DIFFERENCE in amounts:
        return max(0.0, amounts[CARBS_BY_DIFFERENCE] - amounts.get(FIBRE, 0.0))
    return amounts.get(CARBS_BY_SUMMATION, 0.0)


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


def usda_row(food, letter):
    amounts = usda_amounts(food)
    kcal = first(amounts, ENERGY_KCAL)
    if kcal is None:
        kj = first(amounts, ENERGY_KJ)
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
        number(first(amounts, PROTEIN) or 0.0),
        number(usda_carbs(amounts)),
        number(first(amounts, FAT) or 0.0),
        portions(food),
    ]


def build_usda(args, work):
    from_page = usda_files_on_page()
    print("USDA files on the download page:", from_page or "none found")
    rows, used, names = [], [], set()
    for key in USDA_ORDER:
        _, _, top, letter, title = USDA_SETS[key]
        name = usda_choose(key, getattr(args, key), from_page, work)
        path = download(USDA + "/fdc-datasets/" + name, os.path.join(work, name))
        foods = usda_load(path, top)
        kept = 0
        for food in foods:
            row = usda_row(food, letter)
            if row is None or row[1].lower() in names:
                continue
            names.add(row[1].lower())
            rows.append(row)
            kept += 1
        print("%s (%s): %d foods, %d kept" % (title, name, len(foods), kept))
        used.append((title, name, kept))
    return rows, used


# ---- CIQUAL ----


def ciqual_url(given, work):
    """The newest CIQUAL XML zip: given, already downloaded, from data.gouv.fr, or a known one."""
    if given:
        return given
    local = sorted(n for n in os.listdir(work) if n.lower().startswith("ciqual") and n.lower().endswith(".zip"))
    if local:
        return os.path.join(work, local[-1])
    try:
        info = json.loads(fetch(CIQUAL_DATASET_API).decode("utf-8"))
        candidates = []
        for r in info.get("resources", []):
            url = r.get("url") or ""
            title = r.get("title") or ""
            fmt = (r.get("format") or "").lower()
            print("  CIQUAL resource: %s | %s | %s | %s" % (title, fmt, r.get("last_modified"), url))
            if "xml" in (title + " " + url + " " + fmt).lower() and url.lower().endswith(".zip"):
                candidates.append((r.get("last_modified") or "", url))
        if candidates:
            return sorted(candidates)[-1][1]
    except Exception as e:
        print("Could not read the data.gouv.fr catalogue:", e)
    for url in CIQUAL_KNOWN:
        if exists(url):
            return url
    sys.exit("No CIQUAL file found. Give its address with --ciqual.")


def parse_xml(raw):
    """Reads a CIQUAL XML file into records ({tag: text}), forgiving its stray "&" characters."""
    head = raw[:200].decode("ascii", "replace")
    m = re.search(r'encoding="([^"]+)"', head)
    text = raw.decode(m.group(1) if m else "utf-8", "replace")
    text = re.sub(r"^<\?xml[^>]*\?>", "", text.lstrip("﻿").lstrip())
    text = re.sub(r"&(?!amp;|lt;|gt;|quot;|apos;|#)", "&amp;", text)
    root = ET.fromstring(text)
    records = []
    for rec in root:
        records.append({child.tag.strip().lower(): (child.text or "").strip() for child in rec})
    return records


def ciqual_value(text):
    """A CIQUAL number: "12,5" is 12.5; "traces", "< 0,5" and "-" are 0; none at all is None."""
    t = (text or "").strip().replace(",", ".")
    if t in ("", "-"):
        return None
    if t.lower() == "traces" or t.startswith("<"):
        return 0.0
    try:
        return float(t)
    except ValueError:
        return None


def find_const(consts, *patterns):
    """The CIQUAL constituent code whose English name matches the first pattern that matches one."""
    for pattern in patterns:
        for code, name in consts.items():
            if re.search(pattern, name, re.IGNORECASE):
                return code, name
    sys.exit("CIQUAL: no constituent matching %s" % (patterns,))


def build_ciqual(args, work):
    source = ciqual_url(args.ciqual, work)
    if source.startswith("http"):
        path = download(source, os.path.join(work, "ciqual-" + os.path.basename(source)))
    else:
        path = source
    with zipfile.ZipFile(path) as z:
        members = z.namelist()
        print("CIQUAL files:", members)

        def member(prefix):
            return next(n for n in members if os.path.basename(n).lower().startswith(prefix) and n.lower().endswith(".xml"))

        alim = parse_xml(z.read(member("alim_")))
        alim = [a for a in alim if "alim_code" in a]
        compo = parse_xml(z.read(member("compo_")))
        const = parse_xml(z.read(member("const_")))

    consts = {c["const_code"]: c.get("const_nom_eng", "") for c in const if "const_code" in c}
    kcal = find_const(consts, r"^Energy, Regulation EU No 1169/2011 \(kcal", r"^Energy.*kcal")
    kj = find_const(consts, r"^Energy, Regulation EU No 1169/2011 \(kJ", r"^Energy.*kJ")
    protein = find_const(consts, r"^Protein, N x 6\.25", r"^Protein")
    carbs = find_const(consts, r"^Carbohydrate \(g", r"^Carbohydrate")
    fat = find_const(consts, r"^Fat \(g", r"^Fat")
    for label, (code, name) in [("kcal", kcal), ("kJ", kj), ("protein", protein), ("carbs", carbs), ("fat", fat)]:
        print("CIQUAL %s: %s %s" % (label, code, name))

    wanted = {kcal[0], kj[0], protein[0], carbs[0], fat[0]}
    values = {}
    for c in compo:
        code = c.get("const_code")
        if code in wanted:
            values.setdefault(c.get("alim_code"), {})[code] = ciqual_value(c.get("teneur"))

    rows = []
    for a in alim:
        v = values.get(a["alim_code"], {})
        energy = v.get(kcal[0])
        if energy is None and v.get(kj[0]) is not None:
            energy = v[kj[0]] / 4.184
        name = clean(a.get("alim_nom_eng") or "")
        if energy is None or not name:
            continue
        rows.append([
            CIQUAL_LETTER + a["alim_code"],
            name,
            number(energy),
            number(v.get(protein[0]) or 0.0),
            number(v.get(carbs[0]) or 0.0),
            number(v.get(fat[0]) or 0.0),
            "",
        ])
    name = os.path.basename(path)
    print("%s (%s): %d foods, %d kept" % (CIQUAL_TITLE, name, len(alim), len(rows)))
    return rows, [(CIQUAL_TITLE, name, len(rows))]


# ---------------------------------------------------------------------------------------------


NOTICE = """\
The built-in food list (foods.tsv) comes from two government food tables.

USDA FoodData Central
  U.S. Department of Agriculture, Agricultural Research Service. FoodData Central.
  https://fdc.nal.usda.gov. In the public domain (CC0 1.0). This app is not endorsed by the USDA.

CIQUAL
  Anses. Ciqual French food composition table. https://ciqual.anses.fr
  Under the Licence Ouverte / Etalab Open Licence 2.0
  (https://www.etalab.gouv.fr/licence-ouverte-open-licence/). Only the English food names and
  the energy, protein, carbohydrate and fat values are used. This app is not endorsed by Anses.

The files used:

%s

Changes made: nutrients are per 100 g, rounded to one decimal. Carbs are given without fibre, as
on EU labels: where USDA gives carbohydrate including fibre, the fibre was taken off. A value
given as traces, as below a limit, or left out is shown as 0. Within USDA, where a name appears
in more than one dataset, only the first is kept, in the order above.
"""


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--work", default="build/food-data", help="folder for the downloaded files")
    parser.add_argument("--out", default="app/src/main/assets", help="folder for foods.tsv and its notice")
    for key in USDA_SETS:
        parser.add_argument("--" + key, help="file name of the %s download" % USDA_SETS[key][4])
    parser.add_argument("--ciqual", help="address or path of the CIQUAL XML zip")
    args = parser.parse_args()
    os.makedirs(args.work, exist_ok=True)
    os.makedirs(args.out, exist_ok=True)

    usda_rows, usda_used = build_usda(args, args.work)
    ciqual_rows, ciqual_used = build_ciqual(args, args.work)
    rows = usda_rows + ciqual_rows
    used = usda_used + ciqual_used

    rows.sort(key=lambda r: (r[1].lower(), r[0]))
    target = os.path.join(args.out, "foods.tsv")
    with open(target, "w", encoding="utf-8", newline="\n") as f:
        f.write("# Built-in foods, from USDA FoodData Central (public domain) and CIQUAL (Anses, Licence Ouverte 2.0).\n")
        f.write("# Made by tools/build-food-list.py. See foods-NOTICE.txt.\n")
        for title, name, kept in used:
            f.write("# %s: %s, %d foods\n" % (title, name, kept))
        f.write("# id\tname\tkcal\tprotein\tcarbs\tfat\tportions   (per 100 g; carbs without fibre; portions label=grams)\n")
        for row in rows:
            f.write("\t".join(row) + "\n")
    print(len(rows), "foods written to", target)

    files = "\n".join("  - %s: %s (%d foods)" % u for u in used)
    with open(os.path.join(args.out, "foods-NOTICE.txt"), "w", encoding="utf-8", newline="\n") as f:
        f.write(NOTICE % files)


if __name__ == "__main__":
    main()
