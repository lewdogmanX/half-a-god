"""Helpers that emit Mebahel's RPG quest JSON in the compact format its own quests use."""
import json, os

NS = "half_a_god"
QNS = "mebahelrpgrquest"  # the quest map only draws quests in this namespace
QUESTS = {}      # id -> (folder, dict)
EXTRA = {}       # relative path -> dict
# other mods' namespaces the pack writes into (as they are, not under half_a_god/)
OTHER_NS = ("minecraft/", "mebahelrpgrquest/", "sons_of_sins/", "netherman/", "rocketnautics/")


def qid(x):
    return x if ":" in x else f"{QNS}:hag_{x}"


def flow(next_id, delay=60, dims=None, silent=False, combat_free=True, retry=200):
    d = {"quest": qid(next_id), "delay_ticks": delay, "silent_start": silent, "retry_delay_ticks": retry,
         "conditions": {"player_not_in_combat": combat_free}}
    if dims:
        d["conditions"]["dimensions"] = dims
    return d


NETHER = ["minecraft:the_nether"]
OVER = ["minecraft:overworld"]
AETHER = ["aether:the_aether"]
END = ["minecraft:the_end"]


def quest(id, title, summary, goals, *, folder="delayed_next_quest", intro=None, done=None, threats=None,
          choice=None, rewards=None, nxt=None, on_kill=None, on_spare=None, requires=None, traits=None,
          icon=None, book=None, recruit=False, offer=None, ret=None, already=None, nxt_villager=None):
    q = {"title": title, "summary": summary}
    if icon:
        q["map"] = {"icon": icon}
    q["sequential_objectives"] = True
    if requires:
        q["requires"] = requires
    dlg = {}
    if intro:
        dlg["intro"] = intro
    if done:
        dlg["reward"] = done
    if offer:
        dlg["offer"] = offer
    if ret:
        dlg["return"] = ret
    if already:
        dlg["done"] = already
    if dlg:
        q["dialogue"] = dlg
    q["goals"] = goals
    if threats:
        q["threats"] = threats
    if choice:
        q["choice"] = choice
    q["rewards"] = rewards or {"xp": 100}
    fl = {}
    if nxt_villager:
        fl["next"] = qid(nxt_villager)
    if nxt:
        fl["delayed_next_quest"] = nxt
    if on_kill:
        fl["on_kill"] = {"delayed_next_quest": on_kill}
    if on_spare:
        fl["on_spare"] = {"delayed_next_quest": on_spare}
    if fl:
        q["flow"] = fl
    tr = {"complete": [f"hag_{id}_done"]}
    if traits:
        for k, v in traits.items():
            tr.setdefault(k, [])
            tr[k] = tr[k] + v
    q["traits"] = tr
    if recruit:
        q["recruit_companion"] = True
    if book:
        q["quest_book"] = book
        folder = "questbooks"
    if id in QUESTS:
        raise SystemExit(f"duplicate quest {id}")
    QUESTS[id] = (folder, q)
    return q


def book(title, author, pages, color="red"):
    return {"title": title, "author": author, "color": color, "pages": pages, "auto_claim_rewards": True}


# ---- goals -------------------------------------------------------------
def g(id, stage, say=None, **kw):
    d = {"id": id}
    d.update(kw)
    d["stage"] = stage
    d["auto_advance"] = True
    if say:
        d["say"] = say
    return d


def kill(id, entity, n, stage, say=None, threats_only=None, **kw):
    d = g(id, stage, say, kill=entity, amount=n, **kw)
    if threats_only is not None:
        d["from_threats"] = threats_only
    return d


def collect(id, item, n, stage, say=None, name=None):
    d = g(id, stage, say, collect=item, amount=n)
    if name:
        d["name"] = name
    return d


def place(id, block, n, stage, say=None, rng=64):
    return g(id, stage, say, place=block, amount=n, range=rng)


def place_anywhere(id, block, n, stage, say=None):
    return g(id, stage, say, place=block, amount=n, range=30000000)


def breakb(id, block, n, stage, say=None, rng=64):
    return g(id, stage, say, **{"break": block}, amount=n, range=rng)


def reach(id, structure, stage, say=None, radius=20000, rng=48):
    return g(id, stage, say, reach=structure, radius=radius, range=rng)


def talk(id, stage, say=None, entity="minecraft:villager", name=None):
    d = g(id, stage, say, talk=entity, amount=1)
    if name:
        d["name"] = name
    return d


def wait(id, stage, until="next_day", say=None):
    return g(id, stage, say, type="wait_time", wait_until=until)


def enter_nether(id, stage, say=None):
    return g(id, stage, say, type="enter_nether")


def chest_book(id, stage, structure, item_name, tag, title, author, pages, say=None, radius=20000):
    return g(id, stage, say, type="read_book", item="minecraft:written_book", amount=1, structure=structure,
             radius=radius, in_chest=True, container="minecraft:barrel", name=item_name, tag=tag,
             quest_book={"title": title, "author": author, "pages": pages})


def escort(id, stage, say=None, structure="minecraft:villages", radius=3500, rng=40):
    return g(id, stage, say, escort=structure, radius=radius, range=rng)


def defend_raid(id, stage, say=None):
    return g(id, stage, say, type="defend_raid", amount=1)


def bring(id, entity, n, stage, say=None, rng=24):
    return g(id, stage, say, bring=entity, amount=n, range=rng, threat_target_only=False)


def leave_nether(id, stage, say=None):
    return g(id, stage, say, type="leave_nether")


# ---- threats -----------------------------------------------------------
def eq(**slots):
    out = {}
    for slot, v in slots.items():
        if isinstance(v, tuple):
            item, ench = v
            out[slot] = {"item": item, "enchants": ench}
        else:
            out[slot] = v
    return out


def fx(*pairs):
    return [{"id": e, "amplifier": a, "duration_seconds": 3600, "show_particles": False} for e, a in pairs]


def threat(entity, name, *, stage=0, hp=None, equipment=None, effects=None, structure=None, origin="player",
           count=1, spare=False, glow=True, smin=6, smax=16, min_dist=None, search=20000, **kw):
    t = {"entity": entity, "name": name, "stage": stage, "spawn_on_accept": True, "spare": spare, "glow": glow,
         "spawn_origin": origin, "spawn_min_radius": smin, "spawn_max_radius": smax}
    if count > 1:
        t["count"] = count
    if structure:
        t["structure"] = structure
        t["search_radius"] = search
    if min_dist:
        t["min_structure_distance"] = min_dist
    if hp:
        t["max_health"] = hp
    if equipment:
        t["equipment"] = equipment
    if effects:
        t["effects"] = effects
    t.update(kw)
    return t


def courier(id, after_quest, book_quest, sender="Old Mara", lo=1200, hi=2400, trigger="quest_success",
            advancement=None):
    trig = {"type": trigger}
    if advancement:
        trig = {"type": "advancement", "advancement": advancement}
    else:
        trig["quest"] = qid(after_quest)
    EXTRA[f"courier_deliveries/{id}.json"] = {
        "id": f"{NS}:{id}", "trigger": trig, "delay": {"min_ticks": lo, "max_ticks": hi},
        "delivery": {"type": "courier", "sender": sender, "quest_book": qid(book_quest)}}


def write_all(root):
    for id, (folder, q) in QUESTS.items():
        p = os.path.join(root, "data", QNS, "quests", folder, "half_a_god", f"hag_{id}.json")
        os.makedirs(os.path.dirname(p), exist_ok=True)
        json.dump(q, open(p, "w", encoding="utf-8"), indent=2, ensure_ascii=False)
    for rel, d in EXTRA.items():
        p = os.path.join(root, "data", rel if rel.startswith(OTHER_NS) else os.path.join(NS, rel))
        os.makedirs(os.path.dirname(p), exist_ok=True)
        json.dump(d, open(p, "w", encoding="utf-8"), indent=2, ensure_ascii=False)
