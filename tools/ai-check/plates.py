"""Fetches a few photos of meals from Wikimedia Commons, for the AI check only (never kept)."""
import json, os, sys, urllib.parse, urllib.request

out = sys.argv[1]
os.makedirs(out, exist_ok=True)
UA = {"User-Agent": "fitness-app-ai-check/1.0 (GitHub Actions test)"}
for i, query in enumerate(["full breakfast plate", "spaghetti bolognese plate", "salad bowl food", "fish and chips plate"]):
    url = "https://commons.wikimedia.org/w/api.php?" + urllib.parse.urlencode({
        "action": "query", "format": "json", "generator": "search", "gsrsearch": query + " filetype:bitmap",
        "gsrnamespace": 6, "gsrlimit": 3, "prop": "imageinfo", "iiprop": "url", "iiurlwidth": 800,
    })
    try:
        pages = json.load(urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=60))["query"]["pages"]
        page = sorted(pages.values(), key=lambda p: p.get("index", 0))[0]
        thumb = page["imageinfo"][0]["thumburl"]
        data = urllib.request.urlopen(urllib.request.Request(thumb, headers=UA), timeout=60).read()
        name = f"{i + 1}-{query.split()[0]}.jpg"
        open(os.path.join(out, name), "wb").write(data)
        print(name, "=", page["title"])
    except Exception as e:  # a photo that can't be fetched is just left out
        print("no photo for", query, e)
