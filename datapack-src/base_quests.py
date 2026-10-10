"""Mebahel's own 89 quests, woven into the story.

Each questline is copied at its original path (so it overrides Mebahel's file) with three changes:
  - its first quest only unlocks once the story reaches the right act (requires.quests: the act's gate quest),
    and the old 'discover it by rumour first' lock is removed;
  - the quests where the line can end set hag_base_<line>_done;
  - the act's last main-story quest waits for every line of that act (requires.traits).
Letters (quest books) arrive by courier when the act begins. Everything else about Mebahel's quests
(their writing, choices, rewards and villager hand-offs) is untouched.
The mod spawns a quest-giver villager for a line's first quest when you're in a village and haven't found one.
"""
import json, os, glob, copy
from lib import QUESTS, EXTRA, qid, courier

# Mebahel's quest files (CC0). In the repo they live next to this script; in the scratch build, beside the extracted jar.
SRC = os.path.join(os.path.dirname(os.path.abspath(__file__)), "mebahel-quests")
if not os.path.isdir(SRC):
    SRC = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "meb", "data", "mebahelrpgrquest", "quests")

# chain -> (gate quest, join quest, starter quests, done-points, book letters to deliver)
LINES = {
    # ---- Prologue / Act I: settling in, then defending villages
    "village_arrival": ("the_first_letter", "w0_two_roads_meet", ["village_arrival_1_clock"], ["village_arrival_3_fireplace_supplies"], []),
    "village_new_home": ("the_first_letter", "w0_two_roads_meet", ["village_new_home"], ["village_new_home"], []),
    "village_pig_enclosure": ("the_first_letter", "w0_two_roads_meet", ["village_pig_enclosure"], ["village_pig_enclosure"], []),
    "kill_10_skeletons": ("the_first_letter", "w0_two_roads_meet", ["kill_10_skeletons"], ["kill_10_skeletons"], []),
    "kill_10_zombies": ("the_first_letter", "w0_two_roads_meet", ["kill_10_zombies"], ["kill_10_zombies"], []),
    "kill_5_creepers": ("the_first_letter", "w0_two_roads_meet", ["kill_5_creepers"], ["kill_5_creepers"], []),
    "delivery": ("the_first_letter", "w0_two_roads_meet", ["delivery_cobblestone_oak_log_other_village"], ["delivery_cobblestone_oak_log_other_village"], []),
    "most_important_cow": ("the_first_letter", "w0_two_roads_meet", ["most_important_cow_chain_1_an_audience_with_her_majesty"],
                           ["most_important_cow_chain_bargain_5_a_quiet_road", "most_important_cow_chain_refuse_5_long_live_the_queen"], []),
    "village_defense": ("a1_ashford_burns", "w0_two_roads_meet", ["village_defense_chain_1_iron_for_the_watch", "village_defense_chain_4_the_road_remembers"],
                        ["village_defense_chain_6_the_second_bell"], []),
    "no_banner": ("a1_ashford_burns", "w0_two_roads_meet", ["no_banner_vindicator_chain_1_bread_at_the_shrine"],
                  ["no_banner_vindicator_chain_kill_3_ashes_without_witness", "no_banner_vindicator_chain_spare_5_oath_without_banner"], []),
    "witch": ("a1_ashford_burns", "w0_two_roads_meet", ["witch_chain_1_hut_threat"], ["witch_chain_4_evoker_conspiracy"], []),
    # ---- Act II: the Wither and the Nether
    "ash_court": ("w1_the_skull_market", "w8_six_remain", ["ash_court_chain_1_bones_beneath_the_village"], ["ash_court_chain_8_the_last_debt"], []),
    "armed_zombie": ("w1_the_skull_market", "w8_six_remain", ["armed_zombie_chain_1_ruined_portal"],
                     ["armed_zombie_chain_kill_3_ashes_at_the_frame", "armed_zombie_chain_spare_4_oath_of_ash"], []),
    "blaze_mineshaft": ("w1_the_skull_market", "w8_six_remain", ["blaze_mineshaft_threat"], ["blaze_mineshaft_threat"], []),
    "smith_last_heat": ("w1_the_skull_market", "w8_six_remain", ["smith_last_heat_chain_1_coals_for_the_cold_forge"], ["smith_last_heat_chain_3_the_burnt_smith"], []),
    # ---- Act III: the Aether
    "blackthorn": ("h1_a_light_above_the_pit", "h10_fireproof", ["blackthorn_chain_1_the_courier_who_never_returned"], ["blackthorn_chain_1_the_courier_who_never_returned"], []),
    "sculk": ("h1_a_light_above_the_pit", "h10_fireproof", ["sculk_mineshaft_investigation"], ["sculk_mineshaft_investigation"], []),
    "treasure_map": ("h1_a_light_above_the_pit", "h10_fireproof", [], ["questbook_a_very_reasonable_treasure_map"], ["questbook_a_very_reasonable_treasure_map"]),
    # ---- Act IV: the End
    "fourth_member": ("e1_the_librarians_request", "e7_the_nether_is_singing", [], ["questbook_the_fourth_member"], ["questbook_the_fourth_member"]),
    "house_owes_money": ("e1_the_librarians_request", "e7_the_nether_is_singing", [], ["questbook_the_house_that_owes_me_money"], ["questbook_the_house_that_owes_me_money"]),
    # ---- Act V: Bosses' Rise (coast, desert, snow)
    "false_hero": ("s1_cold_hearths", "f1_the_last_road", ["false_hero_chain_1_a_hero_trophy"],
                   ["false_hero_chain_kill_3_convenient_silence", "false_hero_chain_spare_4_the_false_hero"], []),
    "captains_share": ("s1_cold_hearths", "f1_the_last_road", [], ["questbook_the_captains_share"], ["questbook_the_captains_share"]),
    "return_the_ship": ("s1_cold_hearths", "f1_the_last_road", [], ["questbook_please_return_the_ship"], ["questbook_please_return_the_ship"]),
    "undertaker": ("s1_cold_hearths", "f1_the_last_road", ["undertaker_chain_1_graves_without_names"], ["undertaker_chain_4_not_currently_available"], []),
    # ---- Act VI: the Sons of Sins
    "mad_scientist": ("v1_blood_in_the_wells", "v8_sinbuster", ["mad_scientist_chain_1_a_cure_for_the_common_death"],
                      ["mad_scientist_chain_kill_5_the_grand_demonstration", "mad_scientist_chain_spare_5_the_grand_demonstration"], []),
    "funeral": ("v1_blood_in_the_wells", "v8_sinbuster", [], ["questbook_instructions_for_my_own_funeral"], ["questbook_instructions_for_my_own_funeral"]),
    "came_back_warm": ("v1_blood_in_the_wells", "v8_sinbuster", [], ["questbook_the_man_who_came_back_warm"], ["questbook_the_man_who_came_back_warm"]),
    "buried_himself": ("v1_blood_in_the_wells", "v8_sinbuster", [], ["questbook_the_man_who_buried_himself"], ["questbook_the_man_who_buried_himself"]),
    # ---- Act VII: the Nether Mansion
    "zombie_disguise": ("m1_follow_the_crimson_bee", "m6_the_instruction", ["zombie_disguise_friend"], ["zombie_disguise_friend"], []),
    "ash_merchant": ("m1_follow_the_crimson_bee", "m6_the_instruction", [], ["questbook_the_ash_merchant"], ["questbook_the_ash_merchant"]),
}

NAMES = {"village_arrival": "Settling In", "village_new_home": "A New Home", "village_pig_enclosure": "The Pig Pen",
         "kill_10_skeletons": "Bones at Night", "kill_10_zombies": "The Restless Dead", "kill_5_creepers": "Quiet Fields",
         "delivery": "The Neighbour's Order", "most_important_cow": "The Most Important Cow", "village_defense": "The Village Watch",
         "no_banner": "The Man With No Banner", "witch": "The Witch of the Old Hut", "ash_court": "The Ash Court",
         "armed_zombie": "The Knight at the Portal", "blaze_mineshaft": "Fire in the Mine", "smith_last_heat": "The Smith's Last Heat",
         "blackthorn": "The Blackthorn House", "sculk": "The Listening Mine", "treasure_map": "A Very Reasonable Treasure Map",
         "fourth_member": "The Fourth Member", "house_owes_money": "The House That Owes Me Money", "false_hero": "The False Hero",
         "captains_share": "The Captain's Share", "return_the_ship": "Please Return the Ship", "undertaker": "The Undertaker of Red Sand",
         "mad_scientist": "A Cure for the Common Death", "funeral": "Instructions for My Own Funeral",
         "came_back_warm": "The Man Who Came Back Warm", "buried_himself": "The Man Who Buried Himself",
         "zombie_disguise": "A Friend in Disguise", "ash_merchant": "The Ash Merchant"}

ACT_NAME = {"w0_two_roads_meet": "Prologue and Act I", "w8_six_remain": "Act II", "h10_fireproof": "Act III",
            "e7_the_nether_is_singing": "Act IV", "f1_the_last_road": "Act V", "v8_sinbuster": "Act VI", "m6_the_instruction": "Act VII"}

BASE = {}      # quest basename -> (relative path, json)
BASE_META = {}  # quest basename -> chain


def load():
    for f in glob.glob(os.path.join(SRC, "**", "*.json"), recursive=True):
        rel = os.path.relpath(f, SRC)
        BASE[os.path.basename(f)[:-5]] = (rel, json.load(open(f, encoding="utf-8-sig")))


def _list(q, top, parent, key):
    """Mebahel reads a top-level key first and only falls back to the nested one, so edit whichever it will use."""
    if top in q:
        return q[top]
    p = q.setdefault(parent, {})
    if not isinstance(p, dict):
        p = q[parent] = {}
    return p.setdefault(key, [])


def _add(lst, v):
    if v not in lst:
        lst.append(v)


def _strip_discovery(q):
    for holder in (q, q.get("requires", {})):
        for k in ("required_traits", "traits"):
            if holder is q and k == "traits":
                continue
            if isinstance(holder.get(k), list):
                holder[k] = [t for t in holder[k] if not t.startswith("quest_discovered:")]
                if not holder[k]:
                    del holder[k]
    if "requires" in q and not q["requires"]:
        del q["requires"]


def _branches(q, target):
    """Which outcome of q leads on to target: 'spare', 'attack' or 'any'."""
    found = set()
    def walk(o, path):
        if isinstance(o, dict):
            for k, v in o.items():
                if k in ("requires", "required_completed_quests"):
                    continue
                walk(v, path + [k])
        elif isinstance(o, list):
            for x in o:
                walk(x, path)
        elif o == "mebahelrpgrquest:" + target:
            p = "/".join(path)
            if "on_spare" in p or "mercy" in p or "/spare/" in "/" + p + "/":
                found.add("spare")
            elif "on_kill" in p or "/attack/" in "/" + p + "/":
                found.add("attack")
            else:
                found.add("any")
    walk(q, [])
    if not found:
        found.add("any")  # a later quest that requires this one
    return found


CHAINS = {}   # chain -> sorted member basenames
DONE_AT = {}  # chain -> quests (and outcomes) that close it


def weave():
    import base_graph
    load()
    succ, chains = base_graph.chains()
    for chain, (gate, join, starters, dones, books) in LINES.items():
        members, terminals = chains[chain]
        CHAINS[chain] = sorted(members)
        done = f"hag_base_{chain}_done"
        closes = []
        for m in members:
            _strip_discovery(BASE[m][1])
        for s in starters:
            q = BASE[s][1]
            req = q["required_completed_quests"] if "required_completed_quests" in q else q.setdefault("requires", {}).setdefault("quests", [])
            _add(req, qid(gate))
        # the line closes at the chosen quest, at every quest that leads nowhere, and on any outcome that ends it early
        for d in sorted(set(dones) | set(terminals)):
            _add(_list(BASE[d][1], "traits_on_complete", "traits", "complete"), done)
            closes.append(d)
        for m in members:
            q = BASE[m][1]
            outs = [s for s in succ[m] if s in members]
            if not outs or m in dones:
                continue
            kinds = set()
            for o in outs:
                kinds |= _branches(q, o)
            if "any" in kinds:
                continue
            has_spare_choice = bool(q.get("traits", {}).get("spare") if isinstance(q.get("traits"), dict) else False) or "traits_on_spare" in q \
                or "spare" in json.dumps(q.get("threat", {})) or "spare" in json.dumps(q.get("threats", []))
            if not has_spare_choice:
                continue
            for outcome, top in (("spare", "traits_on_spare"), ("attack", "traits_on_attack")):
                if outcome not in kinds:
                    _add(_list(q, top, "traits", outcome), done)
                    closes.append(f"{m} ({'spared' if outcome == 'spare' else 'fought'})")
        DONE_AT[chain] = closes
        for b in books:
            courier(f"base_{chain}", gate, b, sender="A stranger", lo=2400, hi=6000)
        _add(QUESTS[join][1].setdefault("requires", {}).setdefault("traits", []), done)
        for q in members:
            BASE_META[q] = chain
    # the courier helper uses qid() which adds hag_; base books keep their own ids
    for rel, d in list(EXTRA.items()):
        if rel.startswith("courier_deliveries/base_"):
            d["delivery"]["quest_book"] = d["delivery"]["quest_book"].replace(":hag_", ":")


def write(root):
    for name, (rel, q) in BASE.items():
        p = os.path.join(root, "data", "mebahelrpgrquest", "quests", rel)
        os.makedirs(os.path.dirname(p), exist_ok=True)
        json.dump(q, open(p, "w", encoding="utf-8"), indent=2, ensure_ascii=False)


def title_of(name):
    t = BASE[name][1].get("title", name)
    if isinstance(t, dict):
        t = t.get("fallback", name)
    return t


def mod_table():
    """For the mod: each line's members, how it starts, and when it unlocks."""
    out = []
    for chain, (gate, join, starters, dones, books) in LINES.items():
        out.append({"chain": chain, "gate": qid(gate), "join": qid(join), "act": ACT_NAME[join],
                    "title": NAMES[chain],
                    "starters": [f"mebahelrpgrquest:{s}" for s in starters],
                    "books": [f"mebahelrpgrquest:{b}" for b in books],
                    "members": [f"mebahelrpgrquest:{m}" for m in CHAINS[chain]],
                    "placeable": [f"mebahelrpgrquest:{m}" for m in CHAINS[chain]
                                  if not BASE[m][0].startswith(("delayed_next_quest", "questbooks"))],
                    "done_trait": f"hag_base_{chain}_done"})
    return out
