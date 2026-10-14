import json, os, glob, re, sys, collections

ROOT = sys.argv[1]
S = sys.argv[2]  # scratchpad with extracted jars
NS = "half_a_god"
errs, warns = [], []

known = set(json.load(open(os.path.join(S, "build/known_ids.json"))))
def has(kind, rid):
    return f"{kind}:{rid}" in known

# modded structures / advancements straight from the jars
structs, advs = set(), set()
for f in glob.glob(f"{S}/**/data/*/worldgen/structure/*.json", recursive=True) + glob.glob(f"{ROOT}/data/*/worldgen/structure/*.json"):
    ns = f.split("/data/")[1].split("/")[0]
    structs.add(f"{ns}:{os.path.basename(f)[:-5]}")
for f in glob.glob(f"{S}/**/data/*/advancement/**/*.json", recursive=True):
    ns, rest = f.split("/data/")[1].split("/advancement/")
    advs.add(f"{ns}:{rest[:-5]}")
structs |= {"minecraft:villages", "minecraft:fortress", "minecraft:stronghold", "minecraft:end_city", "minecraft:pillager_outpost", "minecraft:mansion", "minecraft:shipwreck", "minecraft:monument", "minecraft:bastion_remnant", "minecraft:ancient_city", "minecraft:end_city", "minecraft:jungle_pyramid", "minecraft:buried_treasure", "minecraft:igloo", "minecraft:ocean_ruin_cold", "minecraft:trail_ruins", "minecraft:ruined_portal", "minecraft:village_snowy", "minecraft:village_desert", "minecraft:swamp_hut", "minecraft:desert_pyramid", "ribbits:ribbit_village"}
advs |= {"minecraft:story/root", "minecraft:story/smelt_iron", "minecraft:end/kill_dragon", "minecraft:nether/find_bastion"}
for f in glob.glob(f"{ROOT}/data/*/advancement/**/*.json", recursive=True):
    ns, rest = f.split("/data/")[1].split("/advancement/")
    advs.add(f"{ns}:{rest[:-5]}")
MOD_NS = {"aether", "netherman", "block_factorys_bosses", "sons_of_sins", "takesapillage", "irons_spellbooks", "endrem", "illagerinvasion", "addictives", "the_bumblezone", "legionary"}

# Cult of Azazel residents who can be spoken to (the mod lets the quest's one replace the resident already there)
TALKERS = {"netherman:believer_villager", "netherman:welcomer", "netherman:doctor"}


def check_id(kind, rid, where):
    if not isinstance(rid, str) or ":" not in rid:
        errs.append(f"{where}: bad id {rid!r}"); return
    ns = rid.split(":")[0]
    if ns in MOD_NS:
        kinds = [kind] if kind != "itemblock" else ["item", "block"]
        if not any(has(k, rid) for k in kinds):
            errs.append(f"{where}: unknown {kind} {rid}")

quests = {}
base = {}  # Mebahel's own quests, overridden in place (checked separately below)
for f in glob.glob(f"{ROOT}/data/mebahelrpgrquest/quests/**/*.json", recursive=True):
    q = json.load(open(f))
    qid = f"mebahelrpgrquest:{os.path.basename(f)[:-5]}"
    if not os.path.basename(f).startswith("hag_"):
        base[qid] = (f, q)
        continue
    if qid in quests: errs.append(f"duplicate {qid}")
    quests[qid] = (f, q)

edges = collections.defaultdict(set)
for qid, (f, q) in quests.items():
    w = qid
    for k in ("title", "summary", "goals", "rewards"):
        if k not in q: errs.append(f"{w}: missing {k}")
    stages = []
    for gl in q.get("goals", []):
        gid = gl.get("id"); stages.append(gl.get("stage"))
        t = gl.get("type")
        if "kill" in gl: check_id("entity", gl["kill"], f"{w}/{gid}")
        if "talk" in gl: check_id("entity", gl["talk"], f"{w}/{gid}")
        if "collect" in gl: check_id("itemblock", gl["collect"], f"{w}/{gid}")
        if "place" in gl: check_id("block", gl["place"], f"{w}/{gid}")
        if "reach" in gl:
            if gl["reach"] not in structs: errs.append(f"{w}/{gid}: unknown structure {gl['reach']}")
        if t == "read_book":
            if gl.get("structure") not in structs: errs.append(f"{w}/{gid}: book structure {gl.get('structure')}")
            if not gl.get("quest_book", {}).get("pages"): errs.append(f"{w}/{gid}: book has no pages")
        if t == "wait_time" and gl.get("wait_until") not in ("next_day", "next_night"): errs.append(f"{w}/{gid}: wait_until")
        if not any(k in gl for k in ("kill", "talk", "collect", "place", "break", "reach", "escort", "bring")) and t not in ("read_book", "wait_time", "enter_nether", "leave_nether", "defend_raid"):
            errs.append(f"{w}/{gid}: goal has no type")
    if stages and sorted(set(stages)) != list(range(max(stages) + 1)):
        errs.append(f"{w}: stage gap {stages}")
    for th in q.get("threats", []):
        check_id("entity", th["entity"], f"{w}/threat")
        if th["entity"].split(":")[0] in MOD_NS and th["entity"] not in TALKERS: errs.append(f"{w}: modded entity spawned as threat {th['entity']}")
        if th["entity"] in TALKERS and (th.get("spare") or not th.get("talk")): errs.append(f"{w}: modded talker must be talk-only")
        if th.get("structure") and th["structure"] not in structs: errs.append(f"{w}: threat structure {th['structure']}")
        if th.get("stage", 0) not in stages: errs.append(f"{w}: threat stage {th.get('stage')} has no goals")
        for a in th.get("attack_ambushes", []):
            if a["entity"].split(":")[0] in MOD_NS: errs.append(f"{w}: modded ambush")
    if any(t.get("spare") for t in q.get("threats", [])) and "choice" not in q:
        warns.append(f"{w}: spare threat without choice labels")
    for item in q.get("rewards", {}).get("items", {}):
        check_id("itemblock", item, f"{w}/reward")
    for a in q.get("requires", {}).get("advancements", []):
        if a not in advs: errs.append(f"{w}: unknown advancement {a}")
    fl = q.get("flow", {})
    for key, d in [("default", fl.get("delayed_next_quest")), ("kill", fl.get("on_kill", {}).get("delayed_next_quest")),
                   ("spare", fl.get("on_spare", {}).get("delayed_next_quest"))]:
        if d:
            if d["quest"] not in quests: errs.append(f"{w}: flow {key} -> missing {d['quest']}")
            edges[qid].add(d["quest"])
    if "quest_book" in q and "/questbooks/" not in f: errs.append(f"{w}: book quest outside questbooks/")
    is_side = "/quests/side/" in f
    if "quest_book" not in q and "/delayed_next_quest/" not in f and not is_side: errs.append(f"{w}: non-book quest outside delayed folder (would be offered randomly)")
    if is_side and not q.get("requires", {}).get("quests"): errs.append(f"{w}: side quest not linked to the main story")
    for r in q.get("requires", {}).get("quests", []):
        if r not in quests and r.split(":")[1] not in [os.path.basename(x)[:-5] for x in glob.glob(f"{ROOT}/data/mebahelrpgrquest/quests/**/*.json", recursive=True)]: errs.append(f"{w}: requires missing quest {r}")
        edges[r].add(qid)
    for gl in q.get("goals", []):
        if gl.get("talk"):
            if not any(t["entity"] == gl["talk"] and t.get("stage", 0) == gl["stage"] and not t.get("spare") for t in q.get("threats", [])):
                errs.append(f"{w}/{gl['id']}: talk step has no quest-spawned villager")
        if "bring" in gl: check_id("entity", gl["bring"], f"{w}/{gl['id']}")

# couriers
for f in glob.glob(f"{ROOT}/data/{NS}/courier_deliveries/*.json"):
    c = json.load(open(f))
    b = c["delivery"]["quest_book"]
    if b in base:
        if "quest_book" not in base[b][1]: errs.append(f"courier {f}: {b} is not a book quest")
        continue
    if b not in quests or "quest_book" not in quests[b][1]: errs.append(f"courier {f}: book {b} is not a book quest")
    tr = c["trigger"]
    if tr["type"] == "quest_spared":
        if tr["quest"] not in quests or "choice" not in quests[tr["quest"]][1]: errs.append(f"courier {f}: spared trigger on a quest without a choice")
        edges[tr["quest"]].add(b)
    elif tr["type"] == "quest_success":
        src = tr["quest"]
        if src not in quests: errs.append(f"courier trigger missing {src}")
        elif quests[src][1].get("flow"): warns.append(f"fork: {src} continues and also sends a courier")
        edges[src].add(b)
    elif tr["type"] == "custom":
        edges["START"].add(b)  # sent by the Half a God mod
    elif tr["type"] == "advancement":
        if tr["advancement"] not in advs: errs.append(f"courier advancement {tr['advancement']}")
        edges["START"].add(b)

# loot pool
for f in glob.glob(f"{ROOT}/data/{NS}/quest_book_loot_pools/*.json"):
    p = json.load(open(f))
    for b in p["books"]:
        if b["quest"] not in quests: errs.append(f"loot pool book {b['quest']} missing")
        edges["AZAZEL_BARREL"].add(b["quest"])

# repeatable Watch duties and Hand contracts: offered by village residents through the mod
_duties = json.load(open(f"{ROOT}/data/half_a_god/hag_mod/duties.json"))
for _d in _duties["watch"] + _duties["hand"]:
    if _d not in quests: errs.append(f"duty {_d} missing")
    edges["START"].add(_d)

# hidden questlines: the mod starts the first quest of each when it's found
for _l in json.load(open(f"{ROOT}/data/half_a_god/hag_mod/secrets.json"))["lines"]:
    if _l["first"] not in quests: errs.append(f"secret {_l['key']}: first quest {_l['first']} missing")
    edges["START"].add(_l["first"])

# the moon storyline: the mod starts its first quest when you walk into an observatory
_m = json.load(open(f"{ROOT}/data/half_a_god/hag_mod/moon.json"))
if _m["start"] not in quests: errs.append(f"moon: first quest {_m['start']} missing")
edges["START"].add(_m["start"])
_r = json.load(open(f"{ROOT}/data/half_a_god/hag_mod/ribbits.json"))
if _r["start"] not in quests: errs.append(f"ribbits: first quest {_r['start']} missing")
edges["START"].add(_r["start"])

# reachability
seen, stack = set(), ["START", "AZAZEL_BARREL"]
while stack:
    n = stack.pop()
    for m in edges.get(n, ()):
        if m not in seen:
            seen.add(m); stack.append(m)
for q in quests:
    if q not in seen: errs.append(f"unreachable quest {q}")

# rumors
for f in glob.glob(f"{ROOT}/data/{NS}/rumors/*.json"):
    r = json.load(open(f))
    for t in r.get("conditions", {}).get("required_traits", []):
        produced = any(f'"{t}"' in json.dumps(q[1]) for q in quests.values())
        if not produced: errs.append(f"rumor {f}: trait {t} never set")

# contracts
for f in glob.glob(f"{ROOT}/data/{NS}/quest_board_contracts/**/*.json", recursive=True):
    d = json.load(open(f))
    txt = json.dumps(d)
    for rid in re.findall(r'"([a-z_]+:[a-z_/]+)"', txt):
        ns = rid.split(":")[0]
        if ns in MOD_NS and not (has("item", rid) or has("entity", rid) or rid in structs):
            errs.append(f"{f}: unknown {rid}")
    for ref in re.findall(rf'"({NS}:hag_[a-z_]+)"', txt):
        name = ref.split(":")[1]
        if not glob.glob(f"{ROOT}/data/{NS}/quest_board_contracts/*/*/{name}.json"):
            errs.append(f"{f}: table {ref} missing")

# Mebahel's quests: same file set as the original jar, each line unlocked by a story quest and closed by a trait
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import base_quests as _bq
_orig = {os.path.relpath(x, _bq.SRC) for x in glob.glob(os.path.join(_bq.SRC, "**", "*.json"), recursive=True)}
_ours = {os.path.relpath(f, f"{ROOT}/data/mebahelrpgrquest/quests") for f, _ in base.values()}
if _orig != _ours: errs.append(f"base quest files differ from Mebahel's: {sorted(_orig ^ _ours)[:5]}")
for chain, (gate, join, starters, dones, books) in _bq.LINES.items():
    done = f"hag_base_{chain}_done"
    if not any(done in json.dumps(q) for _, q in base.values()): errs.append(f"base line {chain}: nothing sets {done}")
    if done not in quests[f"mebahelrpgrquest:hag_{join}"][1].get("requires", {}).get("traits", []): errs.append(f"base line {chain}: {join} does not wait for it")
    for s_ in starters:
        q = base[f"mebahelrpgrquest:{s_}"][1]
        req = q.get("required_completed_quests", q.get("requires", {}).get("quests", []))
        if f"mebahelrpgrquest:hag_{gate}" not in req: errs.append(f"base line {chain}: {s_} is not gated by {gate}")
for qid_, (f, q) in base.items():
    if "quest_discovered:" in json.dumps(q.get("requires", {})) + json.dumps(q.get("required_traits", [])): errs.append(f"{qid_}: still needs a rumour to unlock")

print(f"quests={len(quests)} base={len(base)} reachable={len(seen & set(quests))} edges={sum(len(v) for v in edges.values())}")
print("ERRORS:", len(errs)); [print("  ", e) for e in errs]
print("WARNINGS:", len(warns)); [print("  ", w) for w in warns]

# quest-book rules from QuestBookManager.parse
BOOK_COLORS = {"blue", "brown", "gray", "green", "purple", "red", "yellow"}
berr = []
for qid, (f, q) in quests.items():
    b = q.get("quest_book")
    if b:
        if b.get("color", "brown") not in BOOK_COLORS: berr.append(f"{qid}: unsupported book color {b.get('color')}")
        if not (b.get("title") and b.get("author") and any(p.strip() for p in b.get("pages", []))): berr.append(f"{qid}: book needs title/author/pages")
print("BOOK ERRORS:", len(berr)); [print("  ", e) for e in berr]

# ---- Natural-spawn audit: every kill that is NOT a quest-spawned threat must be on this list, checked against
# the mod's own spawn code/structures. (entity: (max amount per quest, where it really comes from))
NATURAL = {
    "aether:aechor_plant": (6, "Aether surface spawns"), "aether:blue_swet": (8, "Aether surface"), "aether:golden_swet": (8, "Aether surface"),
    "aether:cockatrice": (10, "Aether darkness spawns"), "aether:zephyr": (8, "Aether sky spawns"),
    "aether:mimic": (1, "bronze/silver chests, 40% each"), "aether:sentry": (4, "bronze dungeon trapped stone"),
    "aether:slider": (1, "bronze boss room"), "aether:sun_spirit": (1, "gold boss room"), "aether:valkyrie": (8, "silver trapped angelic stone"),
    "aether:valkyrie_queen": (1, "silver boss room"),
    "block_factorys_bosses:crossbow_pirate": (1, "KrakenSpawner: one per ship"), "block_factorys_bosses:pirate_rook": (1, "one per ship"),
    "block_factorys_bosses:pirate_captain": (1, "one per ship"), "block_factorys_bosses:kraken": (1, "after the 3 pirates"),
    "block_factorys_bosses:dragon_guard_sword": (4, "dragon tower spawners (random type)"),
    "block_factorys_bosses:flaming_skeleton_guard_fireball": (4, "dragon tower spawners (random type)"),
    "block_factorys_bosses:infernal_dragon": (1, "dragon tower boss spawner"), "block_factorys_bosses:sandworm": (1, "nest"),
    "block_factorys_bosses:soul_knight_wither_skeleton": (4, "arena spawners + spawn override"),
    "block_factorys_bosses:soul_skeleton": (12, "arena spawners"), "block_factorys_bosses:underworld_knight": (1, "arena"),
    "block_factorys_bosses:yeti": (1, "yeti arena"),
    "illagerinvasion:invoker": (1, "woodland mansion 'invoker' marker"), "illagerinvasion:sorcerer": (1, "sorcerer hut"),
    "irons_spellbooks:archevoker": (1, "evoker fort tower"), "irons_spellbooks:citadel_keeper": (2, "citadel spawners"),
    "irons_spellbooks:cryomancer": (1, "mountain tower: one"), "irons_spellbooks:dead_king": (1, "catacombs corpse"),
    "irons_spellbooks:fire_boss": (1, "Cinderous Soulcaller at the citadel rune"), "irons_spellbooks:pyromancer": (1, "pyromancer tower"),
    "legionary:unbreakable": (1, "bastion arena tomb"),
    "netherman:gilded_golem": (3, "mansion raw-gold spawners"), "netherman:guardian": (2, "mansion cave spawners"),
    "netherman:manipulator": (6, "mansion gilded-blackstone spawners"),
    "sons_of_sins:betrayed_guzzler": (4, "overworld biome spawn"), "sons_of_sins:devourer": (2, "sinful church: three"),
    "takesapillage:archer": (6, "bastille"), "takesapillage:legioner": (8, "bastille soldier pool"), "takesapillage:skirmisher": (4, "bastille"),
    "the_bumblezone:rootmin": (4, "floral meadow monsters"),
}
nat_err = []
for qid_, (f, q) in quests.items():
    th = {t["entity"] for t in q.get("threats", [])}
    for gl in q.get("goals", []):
        e = gl.get("kill")
        if not e or e.split(":")[0] == "minecraft":
            continue
        if gl.get("from_threats") is False or e not in th:
            if e not in NATURAL:
                nat_err.append(f"{qid_}: natural kill of {e} is not audited")
            elif gl.get("amount", 1) > NATURAL[e][0]:
                nat_err.append(f"{qid_}: {gl.get('amount')} x {e} but only {NATURAL[e][0]} can be relied on ({NATURAL[e][1]})")
print("NATURAL-SPAWN ERRORS:", len(nat_err)); [print("  ", e) for e in nat_err]
