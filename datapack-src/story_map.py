"""Layout for the Half a God story map (assets/half_a_god/story/map.json).

The story is split into chapters (one per act). Inside a chapter, quests sit in horizontal lanes (the main story,
each side arc, the reputation road, Mebahel's questlines, side quests) and in columns by story order, so you can see
at a glance what comes before what and what runs alongside it."""
import re
from collections import defaultdict
from lib import QUESTS, EXTRA, qid
import base_quests
import margin_notes

CHAPTERS = [
    ("prologue", "Prologue", "The Bell That Won't Ring"),
    ("act1", "Act I", "Ashford Burns"),
    ("act2", "Act II", "The Withered Crown"),
    ("act3", "Act III", "Hostile Paradise"),
    ("act4", "Act IV", "The Edge of the World"),
    ("act5", "Act V", "The Seven Wardens"),
    ("act6", "Act VI", "The Sons of Sins"),
    ("act7", "Act VII", "The Sacred City"),
    ("end", "The End", "Whole"),
    ("after", "After", "What Comes Next"),
    ("anytime", "Anytime", "Lost Pages and Rewards"),
]

# (prefix, chapter, lane). Longest matching prefix wins; a one-letter prefix must be followed by a digit.
RULES = [
    ("the_first_letter", "prologue", "main"), ("p", "prologue", "main"), ("x1", "prologue", "choices"),
    ("a", "act1", "main"), ("i1_", "act1", "arcane"), ("i2_", "act1", "arcane"), ("i3_", "act1", "arcane"), ("i4_", "act1", "arcane"),
    ("w0_", "act1", "main"),
    ("r1_", "act1", "road"), ("r2", "act1", "road"), ("rc1_", "act1", "road"), ("x2", "act1", "choices"),
    ("w", "act2", "main"), ("lg", "act2", "legionary"), ("rc2_", "act2", "road"), ("x3", "act2", "choices"),
    ("h", "act3", "main"), ("bz", "act3", "bumblezone"), ("i5_", "act3", "arcane"), ("i6_", "act3", "arcane"),
    ("i7_", "act3", "arcane"), ("i8_", "act3", "arcane"), ("rc3_", "act3", "road"), ("x4", "act3", "choices"),
    ("e", "act4", "main"), ("ea", "act4", "eyes"), ("eb", "act4", "eyes"), ("rc4_", "act4", "road"), ("y1_", "act4", "choices"),
    ("y2_", "act4", "choices"),
    ("s", "act5", "main"), ("d", "act5", "main"), ("c", "act5", "main"), ("u", "act5", "main"), ("f", "act5", "main"),
    ("gt", "act5", "underworld"), ("go", "act5", "underworld"), ("br", "act5", "underworld"), ("tm", "act5", "underworld"),
    ("rc5_", "act5", "road"), ("x5", "act5", "choices"),
    ("v", "act6", "main"), ("rc6_", "act6", "road"), ("y4_", "act6", "choices"), ("y5_", "act6", "choices"),
    ("m", "act7", "main"), ("rc7_", "act7", "road"), ("y3_", "act7", "choices"),
    ("z", "end", "main"), ("g1_", "end", "main"), ("g2_", "end", "main"), ("b1_", "end", "main"), ("b2_", "end", "main"),
    ("b3_", "end", "main"), ("rep_end", "end", "road"),
    ("et", "after", "main"), ("ha", "after", "main"),
    ("lp", "anytime", "pages"), ("ru", "anytime", "choices"), ("sec_", "anytime", "secrets"),
]
LANES = [
    ("main", "Main story", 0xF2C14E), ("arcane", "The Arcane Road", 0x9B7FE0), ("eyes", "The Eye Hunt", 0x6FC7C0),
    ("underworld", "The Blackwater Cartel", 0x5FAF5F), ("bumblezone", "The Honey Crown", 0xF0B030),
    ("legionary", "The Golden Legion", 0xE0A040), ("road", "The Reputation Road", 0x8FD68A),
    ("choices", "Choices That Come Back", 0xE07F6F), ("secrets", "Secrets", 0x9B6AD0), ("pages", "Lost Pages", 0xB8B2A6),
    ("mebahel", "Mebahel's Questlines", 0x7FB8F0), ("side", "Side Quests", 0xA8A296),
]
GATE_CHAPTER = {"the_first_letter": "prologue", "a1_ashford_burns": "act1", "w1_the_skull_market": "act2",
                "h1_a_light_above_the_pit": "act3", "e1_the_librarians_request": "act4", "s1_cold_hearths": "act5",
                "v1_blood_in_the_wells": "act6", "m1_follow_the_crimson_bee": "act7"}


def _rule(k):
    best = None
    for p, ch, lane in RULES:
        if k == p or (k.startswith(p) and len(k) > len(p) and (len(p) > 1 or k[len(p)].isdigit())):
            if best is None or len(p) > best[0]:
                best = (len(p), ch, lane)
    return (best[1], best[2]) if best else (None, None)


def _icon(q, fallback):
    if q.get("map", {}).get("icon"):
        return q["map"]["icon"]
    if "quest_book" in q:
        return "minecraft:writable_book"
    for gl in q.get("goals", []):
        if "kill" in gl: return "minecraft:iron_sword"
        if "talk" in gl: return "minecraft:emerald"
        if "reach" in gl: return "minecraft:compass"
        if "collect" in gl: return "minecraft:chest"
        if "place" in gl: return "minecraft:bricks"
        if "break" in gl: return "minecraft:iron_pickaxe"
        if gl.get("type") == "read_book": return "minecraft:book"
        if gl.get("type") == "wait_time": return "minecraft:clock"
        if "escort" in gl: return "minecraft:lead"
    return fallback


def _text(v):
    if isinstance(v, dict):
        return v.get("fallback", "")
    return v or ""


def build(goal_text):
    nodes = {}   # id -> dict
    edges = set()

    def add_edge(a, b, kind="next"):
        if a and b and a != b:
            edges.add((a, b, kind))

    # ---- the story's own quests
    for k, (_, q) in QUESTS.items():
        i = qid(k)
        ch, lane = _rule(k)
        if k.startswith("sq_"):
            lane = "side"
        nodes[i] = {"id": i, "title": q["title"], "chapter": ch, "lane": lane, "summary": q["summary"],
                    "goals": [goal_text(gl) for gl in q["goals"]], "stages": [gl.get("stage", 0) for gl in q["goals"]],
                    "icon": _icon(q, "minecraft:paper"), "choice": "choice" in q}
        if lane == "main":
            # what you scribbled beside it: (good, wavering, evil), picked by your reputation when you finished it
            assert k in margin_notes.N, f"main-story quest {k} has no margin note"
            nodes[i]["note"] = list(margin_notes.N[k])
            assert all(len(x) <= 46 for x in margin_notes.N[k]), f"margin note too long: {k}"
        elif k in margin_notes.N:
            raise SystemExit(f"margin note for {k}, which isn't on the main road")
        fl = q.get("flow", {})
        for kind, d in (("next", fl.get("delayed_next_quest")), ("spare", fl.get("on_spare", {}).get("delayed_next_quest")),
                        ("kill", fl.get("on_kill", {}).get("delayed_next_quest"))):
            if d:
                add_edge(i, d["quest"], kind)
        for r in q.get("requires", {}).get("quests", []):
            add_edge(r, i, "needs")
    for rel, c in EXTRA.items():
        if rel.startswith("courier_deliveries/") and c["trigger"].get("quest"):
            add_edge(c["trigger"]["quest"], c["delivery"]["quest_book"], "letter")

    # "A Way Out" is sent by the mod partway through the Green Trade
    add_edge(qid("gt4_the_hash_market"), qid("go0_a_way_out"), "letter")

    # ---- Mebahel's questlines, in the chapter whose act they belong to
    import base_graph
    succ = base_graph.graph()
    for chain, (gate, join, starters, dones, books) in base_quests.LINES.items():
        ch = GATE_CHAPTER.get(gate, "anytime")
        for m in base_quests.CHAINS.get(chain, []):
            i = "mebahelrpgrquest:" + m
            q = base_quests.BASE[m][1]
            nodes[i] = {"id": i, "title": base_quests.title_of(m), "chapter": ch, "lane": "mebahel",
                        "summary": _text(q.get("summary")), "goals": [], "stages": [], "icon": _icon(q, "minecraft:paper"),
                        "choice": False, "line": base_quests.NAMES[chain]}
            for s in succ.get(m, ()):
                add_edge(i, "mebahelrpgrquest:" + s, "next")
        for s in starters + books:
            add_edge(qid(gate), "mebahelrpgrquest:" + s, "unlocks")
            edges.discard((qid(gate), "mebahelrpgrquest:" + s, "letter"))

    # ---- quests without a rule take the chapter of whatever leads to them
    preds = defaultdict(list)
    for a, b, _ in edges:
        preds[b].append(a)
    for _ in range(6):
        for i, n in nodes.items():
            if n["chapter"]:
                continue
            for p in preds.get(i, []):
                if p in nodes and nodes[p]["chapter"]:
                    n["chapter"] = nodes[p]["chapter"]
                    break
    for n in nodes.values():
        n["chapter"] = n["chapter"] or "anytime"
        n["lane"] = n["lane"] or "side"

    # ---- columns: longest path from the start, then squeezed per chapter so every chapter starts at column 0
    rank = {}
    order = list(nodes)
    for _ in range(60):
        changed = False
        for a, b, _k in edges:
            if a in nodes and b in nodes:
                r = rank.get(a, 0) + 1
                if r > rank.get(b, 0) and r < 400:
                    rank[b] = r
                    changed = True
        if not changed:
            break
    chapters = []
    for cid, name, sub in CHAPTERS:
        members = [n for n in nodes.values() if n["chapter"] == cid]
        if not members:
            continue
        # per-lane order: the lane's own longest path within the chapter keeps lanes compact
        cols = sorted({rank.get(n["id"], 0) for n in members})
        col_of = {r: ix for ix, r in enumerate(cols)}
        lanes_used = []
        placed = []
        for lane_id, lane_name, color in LANES:
            ln = [n for n in members if n["lane"] == lane_id]
            if not ln:
                continue
            if lane_id == "mebahel":
                # each questline gets its own row, left to right in its own order
                lines = defaultdict(list)
                for n in ln:
                    lines[n["line"]].append(n)
                rows = 0
                for line_name in sorted(lines):
                    seq = sorted(lines[line_name], key=lambda n: (rank.get(n["id"], 0), n["id"]))
                    lcols = sorted({rank.get(n["id"], 0) for n in seq})
                    sub_rows = defaultdict(int)
                    for n in seq:
                        c = lcols.index(rank.get(n["id"], 0))
                        n2 = dict(n, col=c, row=rows + sub_rows[c])
                        sub_rows[c] += 1
                        placed.append(n2)
                    rows += max(sub_rows.values())
                lanes_used.append({"id": lane_id, "name": lane_name, "color": color, "rows": rows})
                continue
            # squeeze this lane's columns into the chapter's column grid; a quest keeps the row of the quest before it
            # (so each branch reads as its own track), otherwise takes the first free row in its column
            ln.sort(key=lambda n: (rank.get(n["id"], 0), n["id"]))
            taken = set()
            row_of = {}
            in_lane = {n["id"] for n in ln}
            for n in ln:
                c = col_of[rank.get(n["id"], 0)]
                want = None
                for p in sorted(preds.get(n["id"], [])):
                    if p in in_lane and p in row_of and (c, row_of[p]) not in taken:
                        want = row_of[p]
                        break
                if want is None:
                    want = 0
                    while (c, want) in taken:
                        want += 1
                taken.add((c, want))
                row_of[n["id"]] = want
                placed.append(dict(n, col=c, row=want))
            lanes_used.append({"id": lane_id, "name": lane_name, "color": color, "rows": max(r for _, r in taken) + 1})
        if cid == "anytime":
            # nothing leads to these in order; lay each lane out in a single row
            for lane in lanes_used:
                seq = [p for p in placed if p["lane"] == lane["id"]]
                seq.sort(key=lambda p: [int(x) if x.isdigit() else x for x in re.split(r"(\d+)", p["id"])])
                for ix, p in enumerate(seq):
                    p["col"], p["row"] = ix, 0
                lane["rows"] = 1
        # columns are shared by all lanes except Mebahel's, which runs on its own; compress empty columns away
        used_cols = sorted({p["col"] for p in placed if p["lane"] != "mebahel"})
        remap = {c: ix for ix, c in enumerate(used_cols)}
        for p in placed:
            if p["lane"] != "mebahel":
                p["col"] = remap[p["col"]]
        ids = {p["id"] for p in placed}
        chapters.append({
            "id": cid, "name": name, "subtitle": sub, "lanes": lanes_used,
            "nodes": [dict({k: p[k] for k in ("id", "title", "lane", "col", "row", "summary", "goals", "stages", "icon", "choice") if k in p},
                           group=p.get("line", ""), **({"note": p["note"]} if "note" in p else {})) for p in placed],
            "edges": sorted([[a, b, k] for a, b, k in edges if a in ids and b in ids]),
        })
    return {"chapters": chapters}
