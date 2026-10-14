"""Titanium: rare, and one metal for both mods that have it.

- Create: Cosmonautics' titanium ore is seven times rarer: only one chunk in seven gets its veins (they're the same
  veins as before where they do generate).
- Create: The Air War's own titanium ore no longer generates (when Cosmonautics is there): its guns, missiles and tools
  take Cosmonautics' titanium instead. Every Air War recipe that asked for its titanium ingot, nugget or sheet takes any
  titanium ingot, nugget or sheet (the c: tags, which Air War's own are added to, so any already mined still work), and
  an Air War ingot, nugget or sheet crafts straight into Cosmonautics'.
Each file only applies when the mods it's about are installed."""
import glob
import json
import os

from lib import EXTRA

RN, AW = "rocketnautics", "create_the_air_wars"
HERE = os.path.dirname(os.path.abspath(__file__))


def loaded(*mods):
    return [{"type": "neoforge:mod_loaded", "modid": m} for m in mods]


# ---- Cosmonautics: seven times rarer (a rarity filter in front of its own placement)
EXTRA[f"{RN}/worldgen/placed_feature/titanium_ore.json"] = {
    "neoforge:conditions": loaded(RN),
    "feature": f"{RN}:titanium_ore",
    "placement": [
        {"type": "minecraft:rarity_filter", "chance": 7},
        {"type": "minecraft:count", "count": 7},
        {"type": "minecraft:in_square"},
        {"type": "minecraft:height_range", "height": {"type": "minecraft:trapezoid", "max_inclusive": {"absolute": 160},
                                                      "min_inclusive": {"absolute": -64}}},
        {"type": "minecraft:biome"}]}

# ---- The Air War: no ore of its own (only when Cosmonautics' titanium is there to use instead)
for bm in ("titanium_ore_biome_modifier", "deepslatetitaniumore_biome_modifier"):
    EXTRA[f"{AW}/neoforge/biome_modifier/{bm}.json"] = {"neoforge:conditions": loaded(AW, RN), "type": "neoforge:none"}

SAME = {f"{AW}:titaniumingot": "c:ingots/titanium", f"{AW}:titaniumnugget": "c:nuggets/titanium",
        f"{AW}:titaniumsheet": "c:plates/titanium"}
for item, tag in SAME.items():
    EXTRA[f"{tag.split(':')[0]}/tags/item/{tag.split(':')[1]}.json"] = {"replace": False, "values": [{"id": item, "required": False}]}

# its recipes: any titanium will do (not the ones that make its titanium itself: those stay as they were)
BASE = set(SAME) | {f"{AW}:titaniumblock"}


def _ingredients(o, key=None):
    if isinstance(o, dict):
        if key not in ("result", "results") and set(o) == {"item"} and o["item"] in SAME:
            return {"tag": SAME[o["item"]]}
        return {k: (v if k in ("result", "results") else _ingredients(v, k)) for k, v in o.items()}
    if isinstance(o, list):
        return [_ingredients(x, key) for x in o]
    return o


def _results(r):
    out = r.get("result") or r.get("results") or {}
    out = out if isinstance(out, list) else [out]
    return {x.get("id") or x.get("item") for x in out if isinstance(x, dict)}


for f in sorted(glob.glob(os.path.join(HERE, "airwars_recipes", "*.json"))):
    r = json.load(open(f, encoding="utf-8"))
    if _results(r) & BASE:
        continue
    new = _ingredients(r)
    new["neoforge:conditions"] = loaded(AW)
    EXTRA[f"{AW}/recipe/{os.path.basename(f)}"] = new

# an Air War ingot, nugget or sheet crafts straight into Cosmonautics'
for aw, rn in ((f"{AW}:titaniumingot", f"{RN}:titanium_ingot"), (f"{AW}:titaniumnugget", f"{RN}:titanium_nugget"),
               (f"{AW}:titaniumsheet", f"{RN}:titanium_sheet")):
    EXTRA[f"recipe/titanium_from_air_war_{aw.split(':')[1]}.json"] = {
        "neoforge:conditions": loaded(AW, RN), "type": "minecraft:crafting_shapeless", "category": "misc",
        "ingredients": [{"item": aw}], "result": {"id": rn, "count": 1}}
