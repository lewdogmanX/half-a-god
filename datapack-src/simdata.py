"""Tables for the operators' story simulator (/halfagod test story <act> [good|evil|waver|random]).

For every chapter of the map: the main road in order (with which branch each quest is on: after a spare or a kill,
or on the good or bad ending road) and the decisions along the way, plus the side quests with a decision in them.
The mod walks this to make a believable playthrough up to a point, without playing it."""
from lib import QUESTS, qid
import story_map

GOOD_ROAD = ("g1_", "g2_", "et")
BAD_ROAD = ("b1_", "b2_", "b3_", "ha")


def _choice(q):
    c = q.get("choice")
    if not c:
        return None
    return {"spare": list(c.get("spare", {}).get("traits", [])), "kill": list(c.get("attack", {}).get("traits", []))}


def build(goal_text):
    m = story_map.build(goal_text)
    out = []
    for ch in m["chapters"]:
        if ch["id"] == "anytime":
            continue
        nodes = {n["id"]: n for n in ch["nodes"]}
        branch = {}
        for a, b, kind in ch["edges"]:
            # only forks on the main road itself (side quests that rejoin the road after a spare don't split it)
            if kind in ("spare", "kill") and b in nodes and a in nodes and nodes[a]["lane"] == "main":
                branch[b] = (kind, a)
        main = sorted([n for n in ch["nodes"] if n["lane"] == "main"], key=lambda n: (n["col"], n["row"]))
        quests = []
        for n in main:
            k = n["id"].split(":hag_", 1)[1]
            if k not in QUESTS:
                continue
            q = QUESTS[k][1]
            e = {"id": n["id"], "choice": _choice(q)}
            if n["id"] in branch:
                e["branch"], e["from"] = branch[n["id"]]
            if k.startswith(GOOD_ROAD):
                e["road"] = "good"
            elif k.startswith(BAD_ROAD):
                e["road"] = "bad"
            quests.append(e)
        side = []
        for n in ch["nodes"]:
            if n["lane"] in ("main", "mebahel") or ":hag_" not in n["id"]:
                continue
            k = n["id"].split(":hag_", 1)[1]
            if k in QUESTS and k.startswith(("sec_", "duty_", "contract_")):
                continue
            c = _choice(QUESTS[k][1]) if k in QUESTS else None
            if c and (c["spare"] or c["kill"]):
                side.append({"id": n["id"], "choice": c})
        out.append({"id": ch["id"], "name": ch["name"], "subtitle": ch["subtitle"], "quests": quests, "side": side})
    assert len(out) >= 9, [c["id"] for c in out]
    return {"chapters": out}
