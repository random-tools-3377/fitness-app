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
       <TAB> fibre <TAB> sugar <TAB> saturated fat <TAB> salt

The last four, added for 0.7.0, are empty when the table doesn't give them. After them come
alcohol (g), caffeine (mg), iron, calcium, vitamin D, vitamin C, potassium, magnesium, vitamin
B12, folate, zinc and vitamin A (0.11.0; see MICROS for the units), also empty when not given. Salt is worked out
from USDA's sodium (mg × 2.5 ÷ 1000), the way EU labels count it; CIQUAL gives salt itself.

Nutrients are per 100 g. The id is the source letter and the food's id in that table: F, S or N
and the FoodData Central id, or C and the CIQUAL code. Portions are "label=grams" pairs separated
by "|", for example "1 cup, chopped=128|1 tbsp=8" (CIQUAL has none). Lines starting with "#" are
comments.

The USDA download page is read to find the newest file of each dataset, and the data.gouv.fr and
data.europa.eu catalogues to find the newest CIQUAL XML file. If one can't be read, the newest
known file names are tried. If the CIQUAL site itself is down, the Internet Archive's saved copy
of the same official file is tried last. Any file can be given with --foundation, --sr, --fndds or --ciqual.
"""
import argparse
import io
import json
import os
import re
import sys
import time
import urllib.error
import urllib.request
import xml.etree.ElementTree as ET
import zipfile

USER_AGENT = "Mozilla/5.0 (compatible; fitness-app-food-list-builder)"

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
SUGARS = [2000, 1063]  # Sugars, total including NLEA; Sugars, Total NLEA
SAT_FAT = [1258]  # Fatty acids, total saturated
SODIUM_MG = [1093]  # Sodium, Na (mg); salt is sodium x 2.5

# Alcohol, caffeine, and vitamins and minerals (0.11.0), in the order of the app's Micro.COLUMNS:
# (name, USDA nutrient ids best first, CIQUAL constituent name patterns, decimals).
MICROS = [
    ("alcohol", [1018], [r"^Alcohol"], 1),            # g
    ("caffeine", [1057], [r"^Caffeine"], 1),          # mg (CIQUAL has none)
    ("iron", [1089], [r"^Iron"], 2),                  # mg
    ("calcium", [1087], [r"^Calcium"], 1),            # mg
    ("vitD", [1114], [r"^Vitamin D \("], 2),          # µg
    ("vitC", [1162], [r"^Vitamin C"], 1),             # mg
    ("potassium", [1092], [r"^Potassium"], 1),        # mg
    ("magnesium", [1090], [r"^Magnesium"], 1),        # mg
    ("b12", [1178], [r"^Vitamin B12"], 2),            # µg
    ("folate", [1190, 1177], [r"^Vitamin B9", r"Folate"], 1),  # µg (dietary folate equivalents where given)
    ("zinc", [1095], [r"^Zinc"], 2),                  # mg
    ("vitA", [1106], [], 1),                          # µg RAE; CIQUAL: retinol + beta-carotene / 12
]

MAX_PORTIONS = 8

# ---------------------------------------------------------------------------------------------
# CIQUAL

CIQUAL_DATASET_API = "https://www.data.gouv.fr/api/1/datasets/table-de-composition-nutritionnelle-des-aliments-ciqual/"
CIQUAL_KNOWN = [
    "https://ciqual.anses.fr/cms/sites/default/files/inline-files/XML_2020_07_07.zip",
]
# The same dataset in the EU's open data portal, which lists its files too.
CIQUAL_EUROPA_API = "https://data.europa.eu/api/hub/search/datasets/5369a15fa3a729239d2065b7"
# The Internet Archive's saved copy of a file, byte for byte as it was served.
WAYBACK = "https://web.archive.org/web/2024id_/"
CIQUAL_LETTER = "C"
CIQUAL_TITLE = "CIQUAL (France)"


def fetch(url, tries=5):
    """Downloads url, trying again a few times when the server is busy."""
    for attempt in range(1, tries + 1):
        request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT, "Accept": "*/*"})
        try:
            with urllib.request.urlopen(request, timeout=300) as response:
                return response.read()
        except urllib.error.HTTPError as e:
            if e.code not in (429, 500, 502, 503, 504) or attempt == tries:
                raise
            print("  %s answered %d; trying again (%d of %d)" % (url, e.code, attempt + 1, tries))
        except (urllib.error.URLError, TimeoutError) as e:
            if attempt == tries:
                raise
            print("  %s failed (%s); trying again (%d of %d)" % (url, e, attempt + 1, tries))
        time.sleep(15 * attempt)


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


def micro_columns(get):
    """The MICROS columns, from get(name) giving a number or None."""
    out = []
    for name, _, _, places in MICROS:
        x = get(name)
        out.append("" if x is None else ("%.*f" % (places, x)).rstrip("0").rstrip("."))
    return out


def optional(x, places=1):
    """An extra nutrient: its number, or "" when the table doesn't give it. Salt keeps two decimals."""
    if x is None:
        return ""
    return number(x) if places == 1 else ("%.2f" % x).rstrip("0").rstrip(".")


def salt_from_sodium(mg):
    return None if mg is None else mg * 2.5 / 1000.0


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
        optional(amounts.get(FIBRE)),
        optional(first(amounts, SUGARS)),
        optional(first(amounts, SAT_FAT)),
        optional(salt_from_sodium(first(amounts, SODIUM_MG)), 2),
    ] + micro_columns(lambda name: first(amounts, next(m[1] for m in MICROS if m[0] == name)))


def build_usda(args, work):
    def have(key):
        return getattr(args, key) or any(re.fullmatch(USDA_SETS[key][0], n) for n in os.listdir(work))

    from_page = {} if all(have(k) for k in USDA_SETS) else usda_files_on_page()
    print("USDA files on the download page:", from_page or "not read, or none found")
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


CIQUAL_SEARCH_API = "https://www.data.gouv.fr/api/1/datasets/?q=ciqual&page_size=20"


def ciqual_candidates():
    """Every CIQUAL XML zip listed on data.gouv.fr, newest first, then the known addresses."""
    found = []
    for api in (CIQUAL_DATASET_API, CIQUAL_SEARCH_API):
        try:
            info = json.loads(fetch(api, tries=2).decode("utf-8"))
        except Exception as e:
            print("Could not read", api, ":", e)
            continue
        datasets = info.get("data", [info])
        for d in datasets:
            for r in d.get("resources", []):
                url = r.get("url") or ""
                title = r.get("title") or ""
                fmt = (r.get("format") or "").lower()
                print("  CIQUAL resource: %s | %s | %s | %s | %s" % (d.get("title"), title, fmt, r.get("last_modified"), url))
                text = (title + " " + url + " " + fmt).lower()
                if "xml" in text and url.lower().endswith(".zip"):
                    found.append((r.get("last_modified") or "", url))
    found += ciqual_europa()
    urls = [u for _, u in sorted(set(found), reverse=True)]
    for u in CIQUAL_KNOWN:
        if u not in urls:
            urls.append(u)
    return urls + [WAYBACK + u for u in urls if not u.startswith(WAYBACK)]


def ciqual_europa():
    """The CIQUAL XML zips listed on data.europa.eu, as (date, address) pairs."""
    try:
        info = json.loads(fetch(CIQUAL_EUROPA_API, tries=2).decode("utf-8"))
    except Exception as e:
        print("Could not read", CIQUAL_EUROPA_API, ":", e)
        return []
    d = info.get("result", info)

    def text(x):
        if isinstance(x, dict):
            return " ".join(str(v) for v in x.values())
        if isinstance(x, list):
            return " ".join(text(v) for v in x)
        return str(x or "")

    found = []
    for r in d.get("distributions", []) or []:
        title = text(r.get("title"))
        fmt = text(r.get("format"))
        urls = []
        for key in ("download_url", "access_url"):
            v = r.get(key) or []
            urls += v if isinstance(v, list) else [v]
        for url in urls:
            if not isinstance(url, str):
                continue
            print("  CIQUAL on data.europa.eu: %s | %s | %s | %s" % (title, fmt, r.get("modified"), url))
            if "xml" in (title + " " + url + " " + fmt).lower() and url.lower().endswith(".zip"):
                found.append((str(r.get("modified") or ""), url))
    return found


def ciqual_file(given, work):
    """The newest CIQUAL XML zip that can be had: given, already downloaded, or downloaded now."""
    if given and not given.startswith("http"):
        return given
    local = sorted(n for n in os.listdir(work) if n.lower().startswith("ciqual") and n.lower().endswith(".zip"))
    if local:
        return os.path.join(work, local[-1])
    urls = [given] if given else ciqual_candidates()
    for i, url in enumerate(urls):
        path = os.path.join(work, "ciqual-%d-%s" % (i, os.path.basename(url)))
        try:
            download(url, path)
        except Exception as e:
            print("  Could not download", url, ":", e)
            continue
        if zipfile.is_zipfile(path):
            print("CIQUAL file from", url)
            return path
        print("  Not a zip file:", url)
        os.remove(path)
    return None


def parse_xml(raw):
    """Reads a CIQUAL XML file into records ({tag: text}), forgiving stray "&" and "<" characters."""
    head = raw[:200].decode("ascii", "replace")
    m = re.search(r'encoding="([^"]+)"', head)
    text = raw.decode(m.group(1) if m else "utf-8", "replace")
    text = re.sub(r"^<\?xml[^>]*\?>", "", text.lstrip("﻿").lstrip())
    text = re.sub(r"&(?!amp;|lt;|gt;|quot;|apos;|#)", "&amp;", text)
    # Values like "< 0,5" are written with a bare "<", which XML doesn't allow.
    text = re.sub(r"<(?=[\s0-9.,])", "&lt;", text)
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


def find_const_optional(consts, *patterns):
    """Like find_const, but (None, "") when the table has no such constituent."""
    for pattern in patterns:
        for code, name in consts.items():
            if re.search(pattern, name, re.IGNORECASE):
                return code, name
    return None, ""


def find_const(consts, *patterns):
    """The CIQUAL constituent code whose English name matches the first pattern that matches one."""
    for pattern in patterns:
        for code, name in consts.items():
            if re.search(pattern, name, re.IGNORECASE):
                return code, name
    sys.exit("CIQUAL: no constituent matching %s" % (patterns,))


def ciqual_vit_a(v, retinol, beta_carotene):
    """Vitamin A as retinol activity equivalents: retinol plus beta-carotene / 12."""
    r = v.get(retinol) if retinol else None
    b = v.get(beta_carotene) if beta_carotene else None
    if r is None and b is None:
        return None
    return (r or 0.0) + (b or 0.0) / 12.0


def build_ciqual(args, work):
    path = ciqual_file(args.ciqual, work)
    if path is None:
        if args.ciqual_optional:
            print("::warning::CIQUAL could not be downloaded. The list is made without it.")
            return [], []
        sys.exit("No CIQUAL file could be downloaded. Give its address or path with --ciqual.")
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
    fibre = find_const(consts, r"^Fibres", r"^Fib")
    sugars = find_const(consts, r"^Sugars \(g", r"^Sugars")
    sat_fat = find_const(consts, r"^FA saturated", r"saturated")
    salt = find_const(consts, r"^Salt", r"^Sodium chloride")
    extras = [("fibre", fibre), ("sugars", sugars), ("saturated fat", sat_fat), ("salt", salt)]
    for label, (code, name) in [("kcal", kcal), ("kJ", kj), ("protein", protein), ("carbs", carbs), ("fat", fat)] + extras:
        print("CIQUAL %s: %s %s" % (label, code, name))

    micro_codes = {name: find_const_optional(consts, *patterns)[0] if patterns else None for name, _, patterns, _ in MICROS}
    retinol = find_const_optional(consts, r"^Retinol")[0]
    beta_carotene = find_const_optional(consts, r"^Beta-carotene", r"^Beta carotene")[0]
    for name, code in micro_codes.items():
        print("CIQUAL %s: %s %s" % (name, code, consts.get(code, "(none)")))
    print("CIQUAL retinol: %s, beta-carotene: %s" % (retinol, beta_carotene))

    wanted = {kcal[0], kj[0], protein[0], carbs[0], fat[0], fibre[0], sugars[0], sat_fat[0], salt[0], retinol, beta_carotene}
    wanted |= {c for c in micro_codes.values() if c}
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
            optional(v.get(fibre[0])),
            optional(v.get(sugars[0])),
            optional(v.get(sat_fat[0])),
            optional(v.get(salt[0]), 2),
        ] + micro_columns(lambda name: ciqual_vit_a(v, retinol, beta_carotene) if name == "vitA" else (v.get(micro_codes[name]) if micro_codes[name] else None)))
    name = re.sub(r"^ciqual-(\d+-)?", "", os.path.basename(path))
    print("%s (%s): %d foods, %d kept" % (CIQUAL_TITLE, name, len(alim), len(rows)))
    return rows, [(CIQUAL_TITLE, name, len(rows))]


# ---------------------------------------------------------------------------------------------


NOTICE_USDA = """\
USDA FoodData Central
  U.S. Department of Agriculture, Agricultural Research Service. FoodData Central.
  https://fdc.nal.usda.gov. In the public domain (CC0 1.0). This app is not endorsed by the USDA.
"""

NOTICE_CIQUAL = """\
CIQUAL
  Anses. Ciqual French food composition table. https://ciqual.anses.fr
  Under the Licence Ouverte / Etalab Open Licence 2.0
  (https://www.etalab.gouv.fr/licence-ouverte-open-licence/). Only the English food names and
  the energy, protein, carbohydrate, fat, fibre, sugars, saturated fat, salt, alcohol, vitamin
  and mineral values are used. This app is not endorsed by Anses.
"""

NOTICE_END = """
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
    parser.add_argument("--ciqual-optional", action="store_true", help="make the list without CIQUAL if it can't be had")
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
        sources = "USDA FoodData Central (public domain)" + (" and CIQUAL (Anses, Licence Ouverte 2.0)" if ciqual_rows else "")
        f.write("# Built-in foods, from %s.\n" % sources)
        f.write("# Made by tools/build-food-list.py. See foods-NOTICE.txt.\n")
        for title, name, kept in used:
            f.write("# %s: %s, %d foods\n" % (title, name, kept))
        f.write("# id\tname\tkcal\tprotein\tcarbs\tfat\tportions\tfibre\tsugar\tsaturates\tsalt\t"
                + "\t".join(m[0] for m in MICROS)
                + "   (per 100 g; carbs without fibre; portions label=grams; an empty extra is not known)\n")
        for row in rows:
            f.write("\t".join(row) + "\n")
    print(len(rows), "foods written to", target)

    files = "\n".join("  - %s: %s (%d foods)" % u for u in used)
    with open(os.path.join(args.out, "foods-NOTICE.txt"), "w", encoding="utf-8", newline="\n") as f:
        parts = [NOTICE_USDA] + ([NOTICE_CIQUAL] if ciqual_rows else [])
        f.write("The built-in food list (foods.tsv) comes from government food tables.\n\n")
        f.write("\n".join(parts))
        f.write(NOTICE_END % files)


if __name__ == "__main__":
    main()
