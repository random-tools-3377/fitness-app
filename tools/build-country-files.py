"""Builds the country files for barcode scanning: one file of packaged products per country,
for the user to download from the Releases page and open in the app.

Run: python3 tools/build-country-files.py [--work DIR] [--out DIR] [--min-products N]

Sources:
  - Open Food Facts (https://world.openfoodfacts.org), the full product export. Its database is
    under the Open Database License (ODbL 1.0) and its contents under the Database Contents
    License; each file credits it.
  - For the United States file, also USDA FoodData Central's Branded Foods (public domain).

Each product with calories filled in is kept, in every country Open Food Facts says it is sold
in. Each output file, products-<country>.tsv.gz, holds these lines after a few "#" comment lines:

    barcode <TAB> name <TAB> brand <TAB> kcal <TAB> protein <TAB> carbs <TAB> fat <TAB> serving g <TAB> serving label
            <TAB> fibre <TAB> sugar <TAB> saturated fat <TAB> salt   (the last four from 0.7.0; empty if not known)

Nutrients are per 100 g (or 100 ml for drinks, as labelled). Carbs are given without fibre, as on
EU labels: for products in the United States file, whose labels count fibre in the carbs, the
fibre is taken off when it is known. Barcodes are written as 13 digits (UPC-A gets a leading 0).
Every EU country gets a file, however few products it has; other countries need --min-products.
"""
import argparse
import csv
import gzip
import io
import json
import os
import re
import sys
import time
import urllib.error
import urllib.request
import zipfile

USER_AGENT = "Mozilla/5.0 (compatible; fitness-app-country-files-builder)"
OFF_URL = "https://static.openfoodfacts.org/data/en.openfoodfacts.org.products.csv.gz"
USDA = "https://fdc.nal.usda.gov"
USDA_PAGES = [USDA + "/download-datasets", USDA + "/download-datasets.html"]
USDA_BRANDED = r"FoodData_Central_branded_food_csv_[0-9-]+\.zip"

EU = [
    "austria", "belgium", "bulgaria", "croatia", "cyprus", "czech-republic", "denmark", "estonia", "finland", "france",
    "germany", "greece", "hungary", "ireland", "italy", "latvia", "lithuania", "luxembourg", "malta", "netherlands",
    "poland", "portugal", "romania", "slovakia", "slovenia", "spain", "sweden",
]
US = "united-states"


def fetch_to(url, path, tries=5):
    if os.path.exists(path):
        return path
    for attempt in range(1, tries + 1):
        try:
            print("Downloading", url, flush=True)
            request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
            with urllib.request.urlopen(request, timeout=600) as r, open(path + ".part", "wb") as f:
                while True:
                    chunk = r.read(1 << 20)
                    if not chunk:
                        break
                    f.write(chunk)
            os.replace(path + ".part", path)
            return path
        except (urllib.error.URLError, TimeoutError, ConnectionError) as e:
            if attempt == tries:
                raise
            print("  failed (%s); trying again" % e, flush=True)
            time.sleep(20 * attempt)


def number(x):
    text = "%.1f" % x
    if text == "-0.0":
        text = "0.0"
    return text[:-2] if text.endswith(".0") else text


def clean(text):
    text = re.sub(r"[\t\r\n]+", " ", str(text or ""))
    return re.sub(r" +", " ", text).strip()


def value(text):
    try:
        x = float(text)
    except (TypeError, ValueError):
        return None
    if x != x or x < 0 or x > 5000:
        return None
    return x


def barcode(code):
    digits = re.sub(r"\D", "", code or "")
    if len(digits) < 8 or len(digits) > 14:
        return None
    if len(digits) == 12:
        digits = "0" + digits
    if len(digits) == 14 and digits.startswith("0"):
        digits = digits[1:]
    return digits


def sentence_case(text):
    """USDA's branded names are often in capitals: "CHOCOLATE CHIP COOKIES" becomes "Chocolate chip cookies"."""
    if text and text.upper() == text and any(c.isalpha() for c in text):
        text = text.lower()
        return text[0].upper() + text[1:]
    return text


def optional(x, places=1):
    """An extra nutrient: its number, or "" when not known. Salt keeps two decimals."""
    if x is None:
        return ""
    return number(x) if places == 1 else ("%.2f" % x).rstrip("0").rstrip(".")


def plausible(kcal, protein, carbs, fat):
    if kcal is None or kcal > 950:
        return False
    return protein + carbs + fat <= 105


# ---- Open Food Facts ----


def read_off(path, us_fibre):
    """Yields (countries, row) for each product with calories, from the Open Food Facts export."""
    csv.field_size_limit(1 << 30)
    with gzip.open(path, "rt", encoding="utf-8", errors="replace", newline="") as f:
        reader = csv.reader(f, delimiter="\t", quoting=csv.QUOTE_NONE)
        header = next(reader)
        col = {name: i for i, name in enumerate(header)}
        need = ["code", "product_name", "brands", "countries_tags", "energy-kcal_100g", "energy_100g",
                "proteins_100g", "carbohydrates_100g", "fat_100g", "fiber_100g", "serving_quantity", "serving_size",
                "sugars_100g", "saturated-fat_100g", "salt_100g"]
        missing = [n for n in need if n not in col]
        if missing:
            sys.exit("The Open Food Facts export has no columns %s" % missing)
        n = 0
        for row in reader:
            n += 1
            if n % 500000 == 0:
                print("  %d products read" % n, flush=True)
            if len(row) < len(header):
                continue
            get = lambda name: row[col[name]]
            code = barcode(get("code"))
            name = clean(get("product_name"))
            if not code or not name:
                continue
            kcal = value(get("energy-kcal_100g"))
            if kcal is None:
                kj = value(get("energy_100g"))
                kcal = kj / 4.184 if kj is not None else None
            protein = value(get("proteins_100g")) or 0.0
            carbs = value(get("carbohydrates_100g")) or 0.0
            fat = value(get("fat_100g")) or 0.0
            if not plausible(kcal, protein, carbs, fat):
                continue
            countries = [c[3:] for c in get("countries_tags").split(",") if c.startswith("en:")]
            if not countries:
                continue
            serving_g = value(get("serving_quantity"))
            label = clean(get("serving_size"))[:40]
            brand = clean(get("brands").split(",")[0])[:60]
            fibre_known = value(get("fiber_100g"))
            fibre = fibre_known or 0.0
            base = [code, name[:120], brand, number(kcal), number(protein), None, number(fat),
                    number(serving_g) if serving_g and serving_g < 5000 else "", label if serving_g else "",
                    optional(fibre_known), optional(value(get("sugars_100g"))),
                    optional(value(get("saturated-fat_100g"))), optional(value(get("salt_100g")), 2)]
            yield countries, base, carbs, fibre


# ---- USDA Branded Foods (United States) ----


def usda_branded_file(work, given):
    if given:
        return given
    local = sorted(n for n in os.listdir(work) if re.fullmatch(USDA_BRANDED, n))
    if local:
        return local[-1]
    for page in USDA_PAGES:
        try:
            request = urllib.request.Request(page, headers={"User-Agent": USER_AGENT})
            html = urllib.request.urlopen(request, timeout=120).read().decode("utf-8", "replace")
        except Exception as e:
            print("Could not read", page, ":", e)
            continue
        names = sorted(set(re.findall(USDA_BRANDED, html)))
        if names:
            return names[-1]
    sys.exit("No USDA Branded Foods file found. Give its name with --usda-branded.")


def read_usda_branded(path):
    """Yields US rows from USDA's Branded Foods, carbs without fibre."""
    nutrients = {1008: 0, 1003: 1, 1005: 2, 1004: 3, 1079: 4, 2000: 5, 1258: 6, 1093: 7}
    with zipfile.ZipFile(path) as z:
        def member(name):
            return next(n for n in z.namelist() if n.endswith("/" + name) or n == name)

        def rows(name):
            with z.open(member(name)) as f:
                yield from csv.DictReader(io.TextIOWrapper(f, encoding="utf-8", errors="replace", newline=""))

        values = {}
        for r in rows("food_nutrient.csv"):
            try:
                nid = int(r["nutrient_id"])
            except (KeyError, ValueError):
                continue
            if nid in nutrients:
                v = value(r.get("amount"))
                if v is not None:
                    values.setdefault(r["fdc_id"], [None] * 8)[nutrients[nid]] = v
        print("  USDA branded nutrients for %d foods" % len(values), flush=True)
        names = {r["fdc_id"]: r.get("description", "") for r in rows("food.csv")}
        for r in rows("branded_food.csv"):
            v = values.get(r["fdc_id"])
            code = barcode(r.get("gtin_upc"))
            if not v or v[0] is None or not code:
                continue
            kcal, protein, carbs, fat, fibre = v[0], v[1] or 0.0, v[2] or 0.0, v[3] or 0.0, v[4] or 0.0
            if not plausible(kcal, protein, carbs, fat):
                continue
            name = sentence_case(clean(names.get(r["fdc_id"], "")))
            if not name:
                continue
            brand = sentence_case(clean(r.get("brand_name") or r.get("brand_owner") or ""))[:60]
            unit = (r.get("serving_size_unit") or "").lower()
            size = value(r.get("serving_size"))
            serving_g = size if size and unit in ("g", "grm", "ml", "mlt") else None
            label = clean(r.get("household_serving_fulltext"))[:40]
            sodium = v[7]
            yield [code, name[:120], brand, number(kcal), number(protein), number(max(0.0, carbs - fibre)), number(fat),
                   number(serving_g) if serving_g else "", label if serving_g else "",
                   optional(v[4]), optional(v[5]), optional(v[6]), optional(None if sodium is None else sodium * 2.5 / 1000.0, 2)]


# ---- Writing ----

HEADER = (
    "# Fitness App country file: packaged products sold in {country}, for barcode scanning. Made {date} by tools/build-country-files.py.\n"
    "# Product data from Open Food Facts (https://world.openfoodfacts.org), contributors of Open Food Facts,\n"
    "# under the Open Database License (ODbL 1.0, https://opendatacommons.org/licenses/odbl/1-0/);\n"
    "# individual contents under the Database Contents License (https://opendatacommons.org/licenses/dbcl/1-0/).\n"
    "{usda}"
    "# barcode\tname\tbrand\tkcal\tprotein\tcarbs\tfat\tserving g\tserving label\tfibre\tsugar\tsaturates\tsalt"
    "   (per 100 g or 100 ml; carbs without fibre; an empty extra is not known)\n"
)


def title(country):
    return " ".join(w.capitalize() for w in country.split("-"))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--work", default="build/country-data")
    parser.add_argument("--out", default="build/country-files")
    parser.add_argument("--min-products", type=int, default=1000)
    parser.add_argument("--off", help="path of the Open Food Facts export, already downloaded")
    parser.add_argument("--usda-branded", help="file name of USDA's Branded Foods CSV zip")
    parser.add_argument("--no-usda", action="store_true", help="leave out USDA's Branded Foods")
    args = parser.parse_args()
    os.makedirs(args.work, exist_ok=True)
    os.makedirs(args.out, exist_ok=True)
    date = time.strftime("%Y-%m-%d")

    off = args.off or fetch_to(OFF_URL, os.path.join(args.work, "openfoodfacts-products.csv.gz"))
    by_country = {}
    for countries, base, carbs, fibre in read_off(off, True):
        for c in countries:
            row = list(base)
            # US labels count fibre in the carbs; EU labels don't.
            row[5] = number(max(0.0, carbs - fibre)) if c == US else number(carbs)
            by_country.setdefault(c, {})[row[0]] = row
    print("Open Food Facts: %d countries" % len(by_country), flush=True)

    usda_note = ""
    if not args.no_usda:
        name = usda_branded_file(args.work, args.usda_branded)
        path = fetch_to(USDA + "/fdc-datasets/" + name, os.path.join(args.work, name))
        us = by_country.setdefault(US, {})
        before = len(us)
        for row in read_usda_branded(path):
            # USDA's label data comes from the makers, so it wins over a copy typed in by hand.
            us[row[0]] = row
        print("USDA Branded Foods (%s): United States now %d products, was %d" % (name, len(us), before), flush=True)
        usda_note = "# Also, for the United States: USDA FoodData Central, Branded Foods (%s), public domain.\n" % name

    written = []
    for country, products in sorted(by_country.items()):
        if country not in EU and len(products) < args.min_products:
            continue
        if not re.fullmatch(r"[a-z0-9-]+", country):
            continue
        path = os.path.join(args.out, "products-%s.tsv.gz" % country)
        with gzip.open(path, "wt", encoding="utf-8", newline="\n", compresslevel=9) as f:
            f.write(HEADER.format(country=title(country), date=date, usda=usda_note if country == US else ""))
            for code in sorted(products):
                f.write("\t".join(products[code]) + "\n")
        written.append((country, len(products), os.path.getsize(path)))
    for eu in EU:
        if eu not in by_country:
            print("::warning::No products at all for %s" % title(eu))
    with open(os.path.join(args.out, "countries.json"), "w") as f:
        json.dump([{"country": c, "name": title(c), "products": n, "bytes": b} for c, n, b in written], f, indent=1)
    print("%d country files written" % len(written))
    for c, n, b in written:
        print("  %-28s %8d products  %6.1f MB" % (title(c), n, b / 1e6))


if __name__ == "__main__":
    main()
