import json, os, shutil, sys
sys.path.insert(0, os.path.dirname(__file__))
from lib import *
import act0_1, act2_4, act5_6, act7_8, side, forks, green_trade, routes, story_text, bumblezone, legionary  # noqa: registers quests
import base_quests, consequences, dialogue, mod_tables, npc_lines
routes.join_main_story()
consequences.link_after_endings()
base_quests.weave()
story_text.apply()

# v11 -> m1: waits in the Nether until a Ghastly is tamed (netherman:tame_ghastly)
QUESTS["v11_the_crimson_guide"][1]["flow"] = {"delayed_next_quest": flow("m1_follow_the_crimson_bee", dims=NETHER, retry=400)}


# ---- Talk objectives only complete on a villager the quest spawns (Mebahel's "Village Messenger" pattern).
NPC_NAMES = [("halden", "Reeve Halden"), ("librarian", "The Librarian"), ("cleric", "The Cleric"), ("smith", "The Smith"),
             ("armourer", "The Armourer"), ("farmer", "The Farmer"), ("mason", "The Mason"), ("tam", "Tam the Shepherd"),
             ("families", "Ashford Survivor"), ("cart", "The Farmer"), ("market", "Market Elder"), ("mara", "Village Elder")]
NPC_TALK = {
    "Reeve Halden": [["You're Halden?", "Reeve of Ashford. What's left of it. You're the one Mara writes about."],
                     ["You know Mara?", "Everyone knows Old Mara. Nobody knows where she lives."]],
    "The Librarian": [["You study the Old Tongue?", "Thirty years. It is mostly angles and anger."],
                      ["Can you read the cult's books?", "Some. Enough to be frightened properly."]],
    "Village Elder": [["Who are you?", "The one people come to when something is wrong. Lately, that is often."]],
}
def npc_name(goal_id):
    for key, name in NPC_NAMES:
        if key in goal_id:
            return name
    return "Village Elder"

talk_quests = set()
CAST = []          # for the mod: which villager is which character, and where they live
MISSING_GREET = []
DELIVERIES = []
# Where each character lives. Everyone not listed lives in a village of their own (fixed the first time you meet them).
ASHFORD_FOLK = {"Reeve Halden", "Ashford Survivor", "Ashford Elder", "Halden's Son", "Captain of the Watch", "Guard Captain",
                "Freed Ashford Man", "Old Aurel"}
HOME_FOLK = {"The Mason", "The Armourer", "The Smith", "The Cleric", "Tam the Shepherd", "Wren of the Pits"}
SNOW_QUESTS = ("s1_", "s2_", "s3_", "x5_", "x5s_", "x5k_", "rc5_good")
DESERT_QUESTS = ("d1_", "d2_", "d3_")


def place_of(qkey, name):
    if name in ASHFORD_FOLK:
        return "ashford"
    if name in HOME_FOLK:
        return "home"
    if qkey.startswith(SNOW_QUESTS) and name in ("Village Elder", "Ilse"):
        return "snowy_village"
    if qkey.startswith(DESERT_QUESTS) and name == "Village Elder":
        return "desert_village"
    if name in ("Village Elder", "The Farmer"):
        return "home"
    if name in ("Nervous Buyer", "Buyer's Friend"):
        return "buyers_village"
    return "char:" + name


# The freed men at the end of the Bastille road are Ashford's, not a random elder
for gl in QUESTS["a9_open_the_cells"][1]["goals"]:
    if gl.get("talk") == "minecraft:villager":
        gl["name"] = "Freed Ashford Man"

for qkey, (_, q) in QUESTS.items():
    talks = [gl for gl in q["goals"] if gl.get("talk") == "minecraft:villager"]
    if not talks:
        continue
    talk_quests.add(qkey)
    threats = q.setdefault("threats", [])
    for gl in talks:
        name = gl.get("name") or npc_name(gl["id"])
        gl["name"] = name
        far = gl.pop("_far", None)
        if far is None:
            far = not (qkey.startswith(("p", "the_first", "sq_", "gt")))
        greet = npc_lines.GREET.get((qkey, name))
        if not greet:
            MISSING_GREET.append((qkey, name))
            greet = "There you are."
        npc = {"entity": "minecraft:villager", "name": name, "stage": gl["stage"], "structure": "minecraft:villages",
               "search_radius": 8000, "spawn_radius": 10, "spawn_on_accept": True,
               "spare": False, "glow": True, "max_health": 60,
               # If a zombie or raid kills them, Mebahel puts them back (QuestEvents.respawnConfiguredThreatTargetAfterDeath)
               "respawn_on_death": True,
               # iron golems and Guard Villagers leave them alone
               "golem_protected": True,
               "effects": [{"id": "minecraft:resistance", "amplifier": 1, "duration_seconds": 100000, "show_particles": False},
                           {"id": "minecraft:fire_resistance", "amplifier": 0, "duration_seconds": 100000, "show_particles": False}],
               "target_line": greet}
        if far:
            npc["min_structure_distance"] = 300
        own = npc_lines.TALK.get((qkey, name)) or gl.pop("_talk", None)
        gl.pop("_talk", None)
        if own:
            npc["talk"] = own
        elif name in NPC_TALK:
            npc["talk"] = NPC_TALK[name]
        # Anything you were asked to bring is handed over to this person (Mebahel's delivery): the items are taken,
        # unless the quest gives them straight back as its reward (then you're only showing them).
        reward_items = set(q.get("rewards", {}).get("items", {}))
        bring = [x for x in q["goals"] if "collect" in x and x["stage"] < gl["stage"] and x["collect"] not in reward_items]
        if bring:
            npc["delivery_objectives"] = [x["id"] for x in bring]
            npc["delivery_choice_label"] = "Hand over what you brought"
            npc["delivered_line"] = gl.get("say") or "Thank you."
            DELIVERIES.append(qkey)
        place = place_of(qkey, name)
        if place == "snowy_village":
            npc["structure"] = "minecraft:village_snowy"
        elif place == "desert_village":
            npc["structure"] = "minecraft:village_desert"
        threats.append(npc)
        CAST.append({"quest": qid(qkey), "stage": gl["stage"], "name": name, "place": place, "structure": npc["structure"]})
    threat_entities = {t["entity"] for t in threats}
    for gl in q["goals"]:
        if "kill" in gl and "from_threats" not in gl and gl["kill"] not in threat_entities:
            gl["from_threats"] = False

for _, (_, q) in QUESTS.items():
    for t in q.get("threats", []):
        if t.get("structure") and t.get("spawn_origin") == "quest_target":
            del t["spawn_origin"]

for qkey, (_, q) in QUESTS.items():
    for t in q.get("threats", []):
        if t.get("talk") and t["entity"].split(":")[0] not in ("minecraft",) and not t.get("spare"):
            CAST.append({"quest": qid(qkey), "stage": t.get("stage", 0), "name": t["name"], "place": "local", "adopt": t["entity"]})

# Anyone you might spare or talk to is protected from iron golems and Guard Villagers (Mebahel's golem_protected)
for _, (_, q) in QUESTS.items():
    for t in q.get("threats", []):
        if t.get("spare") or t.get("talk"):
            t["golem_protected"] = True

# Threats in a stage after "reach <structure>" spawn around you when you arrive. Mebahel can't place them inside the
# structure unless you're standing within its walls (it needs the exact building), so they spawn near you instead.
STRUCTURE_FIXED = []
for qkey, (_, q) in QUESTS.items():
    reached = {}
    for gl in q["goals"]:
        if "reach" in gl:
            reached[gl["reach"]] = gl["stage"]
    for t in q.get("threats", []):
        s = t.get("structure")
        if s and s in reached and t.get("stage", 0) > reached[s]:
            del t["structure"]
            t.pop("search_radius", None)
            t.pop("min_structure_distance", None)
            t["spawn_origin"] = "player"
            STRUCTURE_FIXED.append(qkey)

# talk steps need a village, so any quest with a talk step starts in the Overworld
for qkey, (_, q) in QUESTS.items():
    fl = q.get("flow", {})
    for d in [fl.get("delayed_next_quest"), fl.get("on_kill", {}).get("delayed_next_quest"), fl.get("on_spare", {}).get("delayed_next_quest")]:
        if d and d["quest"].split(":hag_", 1)[1] in talk_quests:
            d["conditions"]["dimensions"] = OVER

# swap plain potions (they would be water bottles) for something useful
for _, q in QUESTS.values():
    items = q.get("rewards", {}).get("items", {})
    if "minecraft:potion" in items:
        items["minecraft:golden_carrot"] = items.pop("minecraft:potion") * 4

# Link courier letters to the quest before them so the quest map draws one connected story
_senders = {}
for rel, c in EXTRA.items():
    if rel.startswith("courier_deliveries/"):
        _senders[c["delivery"]["quest_book"]] = _senders.get(c["delivery"]["quest_book"], 0) + 1
for rel, c in list(EXTRA.items()):
    # only when a single letter leads here (a book several roads lead to can't wait for all of them)
    if rel.startswith("courier_deliveries/") and c["trigger"]["type"] == "quest_success" and ":hag_" in c["delivery"]["quest_book"] \
            and _senders[c["delivery"]["quest_book"]] == 1:
        book_id = c["delivery"]["quest_book"].split(":hag_", 1)[1]
        QUESTS[book_id][1].setdefault("requires", {}).setdefault("quests", []).append(c["trigger"]["quest"])

# Every questline is part of one story: the main line waits for each arc before moving on
QUESTS["w8_six_remain"][1].setdefault("requires", {}).setdefault("quests", []).append(qid("lg5_the_unbreakable_head"))
QUESTS["h10_fireproof"][1].setdefault("requires", {}).setdefault("quests", []).append(qid("bz10_the_honey_crown"))
QUESTS["f1_the_last_road"][1].setdefault("requires", {}).setdefault("traits", []).append("hag_act5_arc_done")

# Maraal's barrel letter follows the Choir on the map (the book stays readable until then)
QUESTS["g1_maraals_last_letter"][1].setdefault("requires", {})["quests"] = [qid("z3_choir_of_the_whole_god")]

# ======================= RUMORS =======================
R = [
    ("start_gold", None, "A trader's paying double for gold. Double! Nobody pays double for anything honest.", None),
    ("start_tam", None, "Tam's sheep came home without Tam. Sheep don't do that.", None),
    ("start_eye", None, "Someone painted an eye on the chapel door. It was gold paint. Real gold.", None),
    ("peddler_spared", "heard_the_word", "You let the smiling man walk. Folk say he still asks after you. By name.", None),
    ("peddler_slain", "peddler_slain", "They say the gold-man bled gold. I don't believe it. My cousin does.", None),
    ("ashford_quiet", "hag_p9_who_pays_in_gold_done", "Ashford's quiet tonight. Too quiet. Nobody's answered the bell in two days.", None),
    ("ashford_counted", "hag_a1_ashford_burns_done", "The Ashford men weren't killed. They were counted.", None),
    ("bastille_cells", "hag_a7_the_bastille_done", "The Bastille's cells were built for a hundred. Who builds a prison that big for villagers?", None),
    ("ravager_horn", "hag_a8_ravagers_horn_done", "Heard a ravager horn last night. Then nothing. Then cheering.", None),
    ("city_with_eye", "hag_a6_believers_house_done", "There's a city in the Nether with an eye over its gate. Prisoners go in. Gold comes out.", None),
    ("vael_spared", "vael_spared", "A man in a cult robe was seen praying alone on the road. Praying for forgiveness, they say.", None),
    ("vael_slain", "vael_slain", "The cult has a new steward. They say he keeps your name on a list.", None),
    ("red_sky", "hag_w6_the_withered_crown_done", "The Nether sky went red the night the Wither died. Red like something waking.", None),
    ("mara_letters", "hag_w8_six_remain_done", "Old Mara writes more letters than anyone alive. Who's she writing to?", None),
    ("heaven_empty", "hag_h2_hostile_paradise_done", "You've been up there? Above the clouds? My gran said heaven was empty now. Was she right?", None),
    ("who_is_maraal", "hag_h7_medals_of_the_silver_court_done", "Who's Maraal? You keep saying the name in your sleep.", None),
    ("dim_sun", "hag_h9_extinguished_done", "The sun looked dimmer today. Just for a moment.", None),
    ("signed_m", "hag_e4_the_warden_journal_done", "The librarian found a book signed with one letter. M. She won't stop staring at it.", None),
    ("headless_king", "hag_e5_free_the_end_done", "Everyone dreamt of a king with no head last night. Everyone. Even the children.", None),
    ("nether_singing", "hag_e7_the_nether_is_singing_done", "The Nether's singing. You can hear it at the bottom of the well.", None),
    ("garrison_rest", "hag_s2_the_frozen_garrison_done", "The frozen soldiers are gone from the hideout. Somebody finally let them rest.", None),
    ("no_snow", "hag_s3_lost_in_the_snow_done", "No snow fell anywhere today. Not one flake.", None),
    ("tidecaller", "hag_c2_the_tidecaller_done", "The Tidecaller's dead, and the tide still comes in red.", None),
    ("knights_oath", "hag_u2_helvars_oath_done", "A knight's oath was found in the deep places. 'If I fall, the door will open.' Which door?", None),
    ("seven_dead", "hag_f3_unbound_done", "Seven beasts dead. You'd think we'd feel safer. Why don't we feel safer?", None),
    ("blood_wells", "hag_v1_blood_in_the_wells_done", "There's blood in the well water. Not much. Enough.", None),
    ("too_many_hands", "midwife_slain", "Something crawled out of the church the night the midwife died. It had too many hands.", None),
    ("mara_isnt_mara", "hag_v9_the_wrong_parcel_done", "Mara isn't Mara. That's all anyone will say, and they say it quiet.", None),
    ("maraal_forgiven", "maraal_forgiven", "The old woman walks with you now. People say she looks younger every day she's near you.", None),
    ("maraal_rejected", "maraal_rejected", "An old woman sits alone at the crossroads, writing letters she never sends.", None),
    ("crimson_bees", "hag_v11_the_crimson_guide_done", "Crimson bees in the Nether, they say. Follow them and you'll find a city of gold. Don't follow them.", None),
    ("bells_ring", "hag_m7_hand_over_your_weapon_done", "They say the god's head is dead. Then why do the bells still ring by themselves?", None),
    ("god_carved", "hag_z2_up_done", "Someone carved 'God?' into a stone up north. Nobody admits to it.", None),
    ("rep_saint", "hag_ch1_saint", "They say the one who paid for Corwin's grain keeps the roads lit now. Lanterns every hundred paces.", "hag_route_seg2"),
    ("rep_good", "hag_ch2_good", "The Watch says you can be trusted. The Watch doesn't say that about anybody.", "hag_route_seg3"),
    ("rep_waver", "hag_ch3_waver", "Some days you're a hero, some days you're a warning. Which is it today?", "hag_route_seg4"),
    ("rep_fallen", "hag_ch4_fallen", "There's a price on a head round here. You'd know whose.", "hag_route_seg5"),
    ("rep_wicked", "hag_ch5_wicked", "Gold buys anything these days. Even heroes.", "hag_route_seg6"),
    ("rep_climb", "hag_ch6_waver", "I heard you paid Halden's debt. Didn't think you had it in you.", "hag_route_seg7"),
    ("rep_saint_end", "hag_rep_saint", "The Lantern-Bearer walked past my door today. My children waved.", None),
    ("rep_wicked_end", "hag_rep_wicked", "Lock your doors. The Gilded Hand has a new partner.", None),
    ("bees_home", "hag_bz10_the_honey_crown_done", "Wren's bees came home! Hundreds of them. She won't stop smiling.", None),
    ("unbreakable", "hag_lg3_the_unbreakable_done", "Old Aurel's been sleeping through the night. First time since the war, he says.", None),
    ("good_end", "hag_good_ending", "A god died, and the gold in my purse turned back to iron. Good riddance.", None),
    ("bad_end", "hag_sworn", "Lock your doors. The Hand of Azazel walks the roads.", "hag_good_ending"),
]
for rid, req, line, forbid in R:
    d = {"id": f"{NS}:{rid}", "weight": 12, "line": line}
    cond = {}
    if req:
        cond["required_traits"] = [req]
    if forbid:
        cond["forbidden_traits"] = [forbid]
    if cond:
        d["conditions"] = cond
    EXTRA[f"rumors/{rid}.json"] = d

# ======================= BOARD CONTRACTS (added alongside Mebahel's own) =======================
TIERS = ("dangerous", "deadly", "nightmare", "legendary")
def tiers(prefix):
    return {t: f"{NS}:{prefix}_{t}" for t in TIERS}

def reward_table(rolls, rows):
    out = {"rolls": {"min": rolls[0], "max": rolls[1]}, "rewards": []}
    for r in rows:
        kind, rid, w, lo, hi, pk = r
        e = {"type": kind, "weight": w, "base_min": lo, "base_max": hi, "per_kill_min": pk[0], "per_kill_max": pk[1]}
        if kind == "item":
            e["id"] = rid
        out["rewards"].append(e)
    return out

TABLES = {
    "dangerous": ((2, 3), [("xp", None, 6, 80, 140, (10, 18)), ("item", "minecraft:emerald", 7, 8, 16, (0.8, 1.4)),
                            ("item", "minecraft:iron_ingot", 4, 4, 8, (0.3, 0.6)), ("item", "minecraft:golden_carrot", 3, 4, 10, (0.2, 0.4)),
                            ("item", "minecraft:golden_apple", 1, 1, 1, (0, 0.05))]),
    "deadly": ((3, 4), [("xp", None, 7, 180, 300, (30, 50)), ("item", "minecraft:emerald", 6, 16, 32, (1.5, 2.5)),
                         ("item", "minecraft:diamond", 3, 2, 4, (0.2, 0.4)), ("item", "minecraft:blaze_rod", 3, 4, 8, (0.2, 0.5)),
                         ("item", "minecraft:netherite_scrap", 1, 1, 1, (0, 0.08)), ("item", "minecraft:wither_skeleton_skull", 1, 1, 1, (0, 0.05))]),
    "nightmare": ((3, 5), [("xp", None, 7, 320, 500, (50, 80)), ("item", "minecraft:diamond", 5, 3, 6, (0.4, 0.8)),
                            ("item", "minecraft:golden_apple", 3, 2, 3, (0.1, 0.3)), ("item", "block_factorys_bosses:kraken_tooth", 1, 1, 1, (0, 0.1)),
                            ("item", "block_factorys_bosses:dragon_bone", 1, 1, 2, (0, 0.1)), ("item", "minecraft:netherite_scrap", 2, 1, 1, (0, 0.12))]),
    "legendary": ((4, 5), [("xp", None, 7, 500, 800, (80, 120)), ("item", "minecraft:diamond", 5, 5, 8, (0.6, 1.0)),
                            ("item", "sons_of_sins:ether_ashes", 3, 8, 16, (0.5, 1.0)), ("item", "netherman:crimson_arrow", 3, 8, 16, (0.4, 0.8)),
                            ("item", "netherman:faith_part", 1, 1, 2, (0, 0.1)), ("item", "netherman:chance_totem", 1, 1, 1, (0, 0.05)),
                            ("item", "minecraft:enchanted_golden_apple", 1, 1, 1, (0, 0.03))]),
}
for t, (rolls, rows) in TABLES.items():
    EXTRA[f"quest_board_contracts/elites/reward_tables/hag_{t}.json"] = reward_table(rolls, rows)
    EXTRA[f"quest_board_contracts/spawn_groups/reward_tables/hag_{t}.json"] = reward_table(rolls, rows)

def gear(weapon, armour=None, ench=None):
    slots = {"right_hand": [{"id": weapon, "weight": 10, **({"enchantments": [{"id": e, "min_level": lo, "max_level": hi} for e, lo, hi in ench]} if ench else {})}]}
    if armour:
        for slot, item in armour.items():
            slots[slot] = [{"id": item, "weight": 10}]
    return {"slots": slots}

EQUIP = {
    "gold_sworn": gear("minecraft:golden_axe", {"helmet": "minecraft:golden_helmet", "chestplate": "minecraft:chainmail_chestplate"}, [("minecraft:sharpness", 2, 3)]),
    "faithful": gear("minecraft:iron_axe", {"chestplate": "minecraft:iron_chestplate"}, [("minecraft:sharpness", 1, 2)]),
    "bone_choir": gear("minecraft:stone_sword", None, [("minecraft:sharpness", 3, 4)]),
    "frost": gear("minecraft:bow", {"chestplate": "minecraft:leather_chestplate"}, [("minecraft:power", 2, 3), ("minecraft:punch", 1, 1)]),
    "dune": gear("minecraft:iron_sword", {"helmet": "minecraft:golden_helmet", "chestplate": "minecraft:golden_chestplate"}, [("minecraft:fire_aspect", 1, 1)]),
    "tide": gear("minecraft:trident", None, [("minecraft:impaling", 1, 2)]),
    "underworld": gear("minecraft:netherite_sword", {"chestplate": "minecraft:iron_chestplate", "leggings": "minecraft:iron_leggings"}, [("minecraft:sharpness", 1, 2)]),
    "ash": gear("minecraft:bow", {"chestplate": "minecraft:chainmail_chestplate"}, [("minecraft:flame", 1, 1), ("minecraft:power", 3, 4)]),
    "gold_wardens": gear("minecraft:golden_axe", {"helmet": "minecraft:golden_helmet", "chestplate": "minecraft:golden_chestplate"}, [("minecraft:sharpness", 4, 5)]),
}
for name, table in EQUIP.items():
    EXTRA[f"quest_board_contracts/elites/equipment_tables/hag_{name}.json"] = table
    EXTRA[f"quest_board_contracts/spawn_groups/equipment_tables/hag_{name}.json"] = table

def elite_contract(structures, entity, name, hp, eq_table, tier_prefix, minions=None, effects=None, search=8000):
    elites = [{"entity": entity, "min": 1, "max": 1, "name": name, "max_health": hp, "equipment_table": f"{NS}:hag_{eq_table}",
               "effects": [{"id": e, "duration_seconds": 3600, "amplifier": a, "show_particles": False} for e, a in (effects or [])]}]
    if minions:
        ment, mmin, mmax, mname, mhp = minions
        elites.append({"entity": ment, "min": mmin, "max": mmax, "name": mname, "max_health": mhp, "equipment_table": f"{NS}:hag_{eq_table}"})
    return {"structures": structures, "search_radius": search, "spawn_min_radius": 4, "spawn_max_radius": 24,
            "reward_tables": {t: f"{NS}:hag_{tier_prefix}" for t in TIERS}, "elites": elites}

EXTRA["quest_board_contracts/elites/elites.json"] = {"contracts": [
    elite_contract(["minecraft:pillager_outpost"], "minecraft:vindicator", "Gold-Sworn Captain", 90, "gold_sworn", "dangerous",
                   ("minecraft:pillager", 2, 2, "Gold-Sworn Crossbow", 40), [("minecraft:strength", 0)]),
    elite_contract(["takesapillage:bastille"], "minecraft:vindicator", "Bastille Warden", 120, "gold_sworn", "dangerous",
                   ("minecraft:pillager", 2, 3, "Bastille Guard", 40), [("minecraft:strength", 0)]),
    elite_contract(["netherman:pipes", "netherman:believer_house"], "minecraft:evoker", "Cult Cell Leader", 90, "faithful", "deadly",
                   ("minecraft:vindicator", 2, 3, "The Faithful", 50)),
    elite_contract(["minecraft:fortress"], "minecraft:wither_skeleton", "Choir Leader", 100, "bone_choir", "deadly",
                   ("minecraft:wither_skeleton", 2, 2, "Bone Chorister", 60)),
    elite_contract(["block_factorys_bosses:yeti_hideout"], "minecraft:stray", "Frostbitten Sergeant", 80, "frost", "nightmare",
                   ("minecraft:stray", 3, 3, "Frostbitten", 40)),
    elite_contract(["block_factorys_bosses:sandworm_nest"], "minecraft:husk", "Dune Prophet", 90, "dune", "nightmare",
                   ("minecraft:husk", 4, 4, "Dune Faithful", 45), [("minecraft:strength", 0)]),
    elite_contract(["block_factorys_bosses:underworld_arena"], "minecraft:piglin_brute", "Gatebreaker", 120, "underworld", "nightmare",
                   ("minecraft:wither_skeleton", 2, 2, "Gate Watcher", 60)),
    elite_contract(["block_factorys_bosses:dragon_tower"], "minecraft:skeleton", "Ash Archer", 80, "ash", "nightmare",
                   ("minecraft:skeleton", 3, 3, "Ash Bowman", 40), [("minecraft:fire_resistance", 0)]),
    elite_contract(["illagerinvasion:illager_fort"], "minecraft:vindicator", "Fort Warlord", 140, "gold_sworn", "nightmare",
                   ("minecraft:pillager", 3, 3, "Fort Guard", 45), [("minecraft:strength", 1)]),
    elite_contract(["irons_spellbooks:evoker_fort"], "minecraft:evoker", "Rogue Archmage", 110, "faithful", "nightmare",
                   ("minecraft:vindicator", 2, 3, "Magehunter Recruit", 50)),
    elite_contract(["netherman:mansion_nether"], "minecraft:piglin_brute", "Gold-Warden", 150, "gold_wardens", "legendary",
                   ("minecraft:piglin_brute", 2, 2, "Gold-Warden Guard", 80), [("minecraft:resistance", 0)]),
]}

def group(structures, entities, lo, hi, tier_prefix):
    return {"entities": [{"id": e, "weight": w, "equipment_table": f"{NS}:hag_{t}"} for e, w, t in entities],
            "min": lo, "max": hi, "structures": structures, "reward_tables": {t: f"{NS}:hag_{tier_prefix}" for t in TIERS},
            "search_radius": 6000, "ignore_current_structure": True, "min_structure_distance": 128,
            "spawn_min_radius": 0, "spawn_max_radius": 28}

EXTRA["quest_board_contracts/spawn_groups/groups.json"] = {"groups": [
    group(["minecraft:villages"], [("minecraft:vindicator", 6, "faithful"), ("minecraft:evoker", 1, "faithful")], 4, 6, "dangerous"),
    group(["takesapillage:pillager_camp"], [("minecraft:pillager", 6, "gold_sworn"), ("minecraft:vindicator", 2, "gold_sworn")], 4, 6, "dangerous"),
    group(["block_factorys_bosses:kraken_ship"], [("minecraft:drowned", 1, "tide")], 5, 7, "nightmare"),
    group(["sons_of_sins:sinful_church"], [("minecraft:witch", 3, "faithful"), ("minecraft:vindicator", 2, "faithful")], 4, 6, "legendary"),
]}

EXTRA["quest_board_contracts/kill/mobs.json"] = {"mobs": [
    {"id": "minecraft:wither_skeleton", "min": 4, "max": 12},
    {"id": "minecraft:blaze", "min": 4, "max": 10},
    {"id": "minecraft:witch", "min": 2, "max": 6},
    {"id": "takesapillage:legioner", "min": 4, "max": 12},
    {"id": "takesapillage:archer", "min": 4, "max": 10},
    {"id": "takesapillage:skirmisher", "min": 3, "max": 8},
    {"id": "aether:cockatrice", "min": 3, "max": 8},
    {"id": "aether:zephyr", "min": 2, "max": 6},
    {"id": "illagerinvasion:marauder", "min": 3, "max": 8},
    {"id": "illagerinvasion:inquisitor", "min": 2, "max": 5},
    {"id": "illagerinvasion:basher", "min": 2, "max": 6},
    {"id": "illagerinvasion:provoker", "min": 2, "max": 5},
    {"id": "illagerinvasion:sorcerer", "min": 1, "max": 3},
]}

# ======================= PACK =======================
OUT = os.path.join(os.path.dirname(__file__), "out", "Half-a-God")
if os.path.exists(OUT):
    shutil.rmtree(OUT)
os.makedirs(OUT)
json.dump({"pack": {"pack_format": 48, "description": "Half a God - a story campaign for Mebahel's RPG (Cult of Azazel, Bosses' Rise, Sons of Sins, Aether, It Takes a Pillage, Iron's Spells, End Remastered, Illager Invasion, Addictives, Bumblezone, Legionary)"}},
          open(os.path.join(OUT, "pack.mcmeta"), "w"), indent=2)
write_all(OUT)
base_quests.write(OUT)
os.makedirs(os.path.join(OUT, 'data/half_a_god/hag_mod'), exist_ok=True)
json.dump({'lines': base_quests.mod_table()}, open(os.path.join(OUT, 'data/half_a_god/hag_mod/base_lines.json'), 'w'), indent=2)
MOD_ROOT = os.environ.get('HAG_MOD_ROOT', '/home/claude/half-a-god')
mod_tables.write(OUT, MOD_ROOT if os.path.isdir(MOD_ROOT) else None)
import settlement
settlement.write(OUT)
DIM_OF_NS = {"aether": "aether:the_aether"}
NETHER_STRUCTS = {"minecraft:fortress", "minecraft:bastion_remnant", "netherman:mansion_nether", "netherman:pipes",
                  "block_factorys_bosses:underworld_arena", "netherman:gast_chamber"}
qdim = {}
for k, (_, q) in QUESTS.items():
    fl = q.get("flow", {})
    for d in [fl.get("delayed_next_quest"), fl.get("on_kill", {}).get("delayed_next_quest"), fl.get("on_spare", {}).get("delayed_next_quest")]:
        if d and d.get("conditions", {}).get("dimensions"):
            qdim.setdefault(d["quest"], d["conditions"]["dimensions"][0])
MARKERS = {}
for k, (_, q) in QUESTS.items():
    dim = qdim.get(qid(k), "minecraft:overworld")
    for gl in q["goals"]:
        r = gl.get("reach")
        if r in NETHER_STRUCTS:
            dim = "minecraft:the_nether"
        elif r == "minecraft:end_city":
            dim = "minecraft:the_end"
        elif r and r.split(":")[0] in DIM_OF_NS:
            dim = DIM_OF_NS[r.split(":")[0]]
    MARKERS[qid(k)] = {"dim": dim, "title": q["title"]}
for name in base_quests.BASE:
    MARKERS["mebahelrpgrquest:" + name] = {"dim": "minecraft:overworld", "title": base_quests.title_of(name)}
json.dump({'quests': MARKERS}, open(os.path.join(OUT, 'data/half_a_god/hag_mod/markers.json'), 'w'), indent=1)
json.dump({'npcs': CAST}, open(os.path.join(OUT, 'data/half_a_god/hag_mod/cast.json'), 'w'), indent=1)
shutil.copy(os.path.join(os.path.dirname(__file__), "README.md"), os.path.join(OUT, "README.md"))
# the mod ships the same datapack inside its jar as a built-in pack at the top of the pack list
# (minus the load message, which is for the plain datapack)
if os.path.isdir(MOD_ROOT):
    old_data = os.path.join(MOD_ROOT, "src/main/resources/data")
    if os.path.exists(old_data):
        shutil.rmtree(old_data)
    dst = os.path.join(MOD_ROOT, "src/main/resources/story")
    if os.path.exists(dst):
        shutil.rmtree(dst)
    shutil.copytree(os.path.join(OUT, "data"), os.path.join(dst, "data"),
                    ignore=lambda d, names: [n for n in names if n == "function" and (d.endswith("half_a_god") or d.endswith("tags"))])
    json.dump({"pack": {"pack_format": 48, "description": "Half a God story (built into the mod)"}},
              open(os.path.join(dst, "pack.mcmeta"), "w"), indent=2)
print("quests:", len(QUESTS), "extra files:", len(EXTRA))
if MISSING_GREET:
    print("NPCs without a greeting:", MISSING_GREET)
print("quests where villagers now take the items:", len(DELIVERIES))
print("staged threats moved off structures:", sorted(set(STRUCTURE_FIXED)))
