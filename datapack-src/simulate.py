"""Plays the main story through for each road (and each in-story choice) and reports anything that never starts.

A quest starts when something schedules it (flow, courier, loot pool) AND its requirements pass, exactly like
Mebahel's DelayedQuestManager (it retries until canSeeQuest passes). Advancements are assumed earned.
"""
import json, glob, os, sys, itertools

ROOT = sys.argv[1]
Q = {}
for f in glob.glob(f"{ROOT}/data/mebahelrpgrquest/quests/**/*.json", recursive=True):
    if os.path.basename(f).startswith("hag_"):
        Q["mebahelrpgrquest:" + os.path.basename(f)[:-5]] = (f, json.load(open(f)))
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from base_quests import LINES
# Mebahel's own questlines: assume the player finishes each one some time after its act opens
BASE_DONE = {}
for chain, (gate, join, *_rest) in LINES.items():
    BASE_DONE.setdefault("mebahelrpgrquest:hag_" + gate, []).append(f"hag_base_{chain}_done")
COUR = [json.load(open(f)) for f in glob.glob(f"{ROOT}/data/half_a_god/courier_deliveries/*.json")]


def run(choices):
    choices = dict(choices)
    """choices: quest id -> 'spare' | 'kill' for every choice quest."""
    done, traits, pending, order = set(), set(), [], []
    for c in COUR:
        if c["trigger"]["type"] == "advancement":
            pending.append(c["delivery"]["quest_book"])
    progress = True
    while progress:
        progress = False
        for qid in list(pending):
            f, q = Q[qid]
            req = q.get("requires", {})
            if not all(r in done for r in req.get("quests", [])):
                continue
            if not all(t in traits for t in req.get("traits", [])):
                continue
            pending.remove(qid)
            if qid in done:
                continue
            done.add(qid); order.append(qid); progress = True
            branch = choices.get(qid.split(":hag_")[1], "spare") if "choice" in q else None
            traits.update(q["traits"].get("complete", []))
            traits.update(BASE_DONE.get(qid, []))
            if branch:
                traits.update(q["choice"].get("spare" if branch == "spare" else "attack", {}).get("traits", []))
            fl = q.get("flow", {})
            nxt = []
            if branch == "kill" and "on_kill" in fl:
                nxt.append(fl["on_kill"]["delayed_next_quest"]["quest"])
            elif branch == "spare" and "on_spare" in fl:
                nxt.append(fl["on_spare"]["delayed_next_quest"]["quest"])
            elif "delayed_next_quest" in fl:
                nxt.append(fl["delayed_next_quest"]["quest"])
            for c in COUR:
                if c["trigger"].get("quest") == qid and (c["trigger"]["type"] != "quest_spared" or branch == "spare"):
                    if ":hag_" in c["delivery"]["quest_book"]:
                        nxt.append(c["delivery"]["quest_book"])
            if qid.endswith("z3_choir_of_the_whole_god"):
                nxt.append("mebahelrpgrquest:hag_g1_maraals_last_letter")  # Azazel's barrel (good ending)
                nxt.append("mebahelrpgrquest:hag_b1_bow_at_my_feet")
            pending.extend(n for n in nxt if n not in done)
    return done, traits, pending, order


import random, collections
MUST = ["z3_choir_of_the_whole_god", "m7_hand_over_your_weapon", "f2_dragon_slayer", "e5_free_the_end", "w6_the_withered_crown",
        "h9_extinguished", "i8_the_arcane_anvil", "lg5_the_unbreakable_head", "bz10_the_honey_crown", "rc5_saint"][:-1]
choice_qs = [k.split(":hag_")[1] for k, (f, q) in Q.items() if "choice" in q]
seen = collections.Counter(); bad = 0; endings = collections.Counter()
random.seed(1)
for it in range(4000):
    ch = {c: random.choice(["spare", "kill"]) for c in choice_qs}
    if it == 0: ch = {c: "spare" for c in choice_qs}
    if it == 1: ch = {c: "kill" for c in choice_qs}
    done, traits, pending, order = run(ch)
    for d in done: seen[d] += 1
    missing = [m for m in MUST if "mebahelrpgrquest:hag_" + m not in done]
    eps = [d for d in done if ":hag_rep_end_" in d]
    segs = [t for t in traits if t.startswith("hag_route_seg")]
    if "hag_act5_arc_done" not in traits:
        missing.append("act5 arc")
    if missing or len(eps) != 1 or len(segs) != 7:
        bad += 1
        if bad < 5: print("STUCK", missing, eps, sorted(segs), [p.split(':hag_')[1] for p in pending][:6])
    else:
        endings[eps[0].split("rep_end_")[1]] += 1
never = [k.split(":hag_")[1] for k in Q if seen[k] == 0 and not k.split(":hag_")[1].startswith(("sq_", "go0_", "lp", "y", "ru", "duty_", "contract_", "sec_"))]
print("runs: 4000  stuck:", bad, " epilogues:", dict(endings))
print("quests never reached in any run (choice branches the random runs missed are fine):", never)
