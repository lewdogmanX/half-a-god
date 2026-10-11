"""The world answering back: Ashford rebuilding, the people you saved moving in, and repeatable work for the Watch
(good) or the Gilded Hand (evil).

Writes:
  hag_mod/settlement.json   settlers and Ashford's stages (the mod places them)
  hag_mod/duties.json       which repeatable quests are Watch duties and which are Hand contracts
  quests/delayed_next_quest/half_a_god/hag_duty_*.json, hag_contract_*.json
                            the repeatable quests themselves. They sit in the delayed folder so Mebahel never hands them
                            out at random; the mod gives them to a village resident to offer, depending on your reputation.
"""
import json
import os

from lib import QUESTS, QNS

# ---------------------------------------------------------------------------------------------------------------
# People you saved, and where they settle. place: home / ashford / snowy_village (the story's own village places).
# when: "good" / "evil" limits them to that side of the scale (they leave if you cross over).
SETTLERS = [
    dict(key="wick", name="Wick the Informant", place="home", trait="hag_payoff_wick_kept_his_word", profession="farmer",
         lines=["I sell bread now. Not patrol times. Bread.",
                "Nell writes. She says I'm a fool for staying. She's not wrong, but I'm staying.",
                "If raiders come, you'll hear it from me first. That's a promise, and I keep those now."]),
    dict(key="gorran", name="Gorran the Warden", place="ashford", trait="hag_payoff_gorran_at_the_gate", profession="weaponsmith",
         lines=["Halden said I could stay if I mend every lock in Ashford. I've mended forty.",
                "My brothers are safe. Because of you. I don't say that easily.",
                "I locked the Bastille's doors. Now I build doors that keep people out instead of in."]),
    dict(key="tomas", name="Tomas of Ashford", place="ashford", trait="hag_payoff_tomas_safe", profession="mason",
         lines=["Father pretends he isn't proud. He's very bad at pretending.",
                "I still dream about the quarries. Gold dust in everything. Then I wake up here.",
                "Every stone I lay in Ashford is one the cult doesn't get."]),
    dict(key="pilgrim", name="The Pilgrim of the Sun", place="home", trait="hag_payoff_pilgrim_blessing", profession="cleric",
         lines=["I said my goodbye to the Sun. You let me. I won't forget it.",
                "The light's different now. Thinner. But it's still light.",
                "I pray for you at dawn. You can stop me if you like. Nobody has yet."]),
    dict(key="brann", name="Brann of the Garrison", place="snowy_village", trait="hag_payoff_brann_home", profession="armorer",
         lines=["Ilse cried for a week. Then she made me fix the roof.",
                "Ten years in the ice. I still count the cold in my fingers every morning.",
                "If the Yeti's kin come down from the peaks, I'll be at the gate. Warmer this time."]),
    dict(key="midwife", name="The Midwife", place="home", trait="hag_midwife_cleric", profession="cleric",
         lines=["The cleric lets me sweep the chapel. It's more than I deserve.",
                "I count the children in this village every morning. All of them. Every one breathes.",
                "None of them ever spoke, down in the pits. These ones never stop. I love it."]),
    dict(key="tobin", name="Tobin the Scavenger", place="home", trait="hag_mercy_2", profession="butcher",
         lines=["My girls ate all winter. On your coin. I don't know how to pay that back.",
                "No more fortresses. I sell rabbits now. Rabbits don't have skulls worth anything.",
                "If you ever need something found, I still know how to find things."]),
    dict(key="ysolde", name="Sister Ysolde", place="home", trait="hag_mercy_3", profession="cleric",
         lines=["I scrubbed the golden eye off the chapel door myself. Took a week.",
                "Forty years I carried that eye. You set it down for me.",
                "If you need judgement, go to Halden. If you need forgiving, come to me."]),
    dict(key="garret", name="Garret of Ashford", place="ashford", trait="hag_mercy_7", profession="mason",
         lines=["Fourteen houses. I've rebuilt six. Halden checks every one.",
                "Nobody here speaks to me. That's fair. I build anyway.",
                "I gave the cult fourteen names. I give Ashford fourteen roofs. It isn't even."]),
    dict(key="pell", name="Pell", place="home", trait="gt_getting_out", profession="farmer",
         lines=["No more Blackwater. I grow wheat now. Just wheat.",
                "The Fixer came by once. Saw you'd been here. Never came back.",
                "Wheat doesn't pay like the green did. I sleep better, though."]),
    dict(key="hollis", name="Hollis of Ashford", place="ashford", trait="hag_a9_open_the_cells_done", profession="farmer",
         lines=["They kept us in the Bastille three weeks. I counted every day on the wall.",
                "My fields went to weeds while I was gone. They're coming back. So am I.",
                "The children follow you around, you know. They think you can't die."]),
    dict(key="edrin", name="Edrin of Ashford", place="ashford", trait="hag_a9_open_the_cells_done", profession="toolsmith",
         lines=["I'm making nails. Thousands of nails. Ashford needs every one.",
                "When the jailers came for us on the road, you were there. I saw.",
                "Halden smiles more now. Don't tell him I said so."]),
    dict(key="captain", name="Captain of the Ashford Watch", place="ashford", trait="hag_m3_hold_the_quarry_gate_done", when="good",
         profession="weaponsmith",
         lines=["The Watch has thirty names now. Thirty! We had three.",
                "Every Watchman swears on Ashford's bell. Some of them swear on your name.",
                "If you're ever in trouble near a village at night, look behind you. We'll be there."]),
    dict(key="tallyman", name="Tallyman of the Gilded Hand", place="ashford", trait="hag_a3_roof_and_hearth_done", when="evil",
         profession="nitwit",
         lines=["Every house rebuilt in Ashford pays the Hand. Every one.",
                "Silas sends his regards. And his ledger. Mostly the ledger.",
                "The Reeve doesn't like me. The Reeve doesn't have to like me. He has to pay."]),
]

# Ashford, as the story goes on. Stage 1 (burned) starts as soon as the story fixes where Ashford is.
ASHFORD_STAGES = [
    (2, "hag_a2_what_was_left_done", "camp"),
    (3, "hag_a3_roof_and_hearth_done", "rebuilding"),
    (4, "hag_a9_open_the_cells_done", "rebuilt"),
    (5, "hag_m3_hold_the_quarry_gate_done", "thriving"),
]

# ---------------------------------------------------------------------------------------------------------------
# Repeatable work. Every duty / contract is a villager quest: take it, do it, come back to the one who gave it.

def _q(title, summary, intro, offer, ret, reward, done, goals, rewards):
    return {
        "title": title, "summary": summary, "sequential_objectives": True,
        "dialogue": {"intro": intro, "offer": offer, "return": ret, "reward": reward, "done": done},
        "goals": goals, "rewards": rewards,
    }


def _kill(id, entity, n):
    return {"id": id, "kill": entity, "amount": n, "mandatory": True, "stage": 0}


def _collect(id, item, n):
    return {"id": id, "collect": item, "amount": n, "mandatory": True, "stage": 0}


def _items(*pairs):
    return {i: c for i, c in pairs}


WATCH = {
    "duty_night_watch": _q(
        "Watch Duty: The Night Watch", "The Watch asks you to walk the walls tonight and thin out the dead.",
        "The Watch is short-handed tonight. The dead keep coming up to the fences.",
        [["What do you need?", "Eight of the dead put back down. Before they find a door."],
         ["Why me?", "Because you've stood for us before. People sleep better when they hear you're on the wall."]],
        "Still walking the walls? Come back when it's quiet.", "Quiet out there now. Take this, from the Watch.",
        "The night was quiet. Thanks to you.",
        [_kill("night_dead", "minecraft:zombie", 8)], {"xp": 120, "items": _items(("minecraft:emerald", 4), ("minecraft:bread", 6))}),
    "duty_clear_the_road": _q(
        "Watch Duty: Clear the Road", "Skeletons are shooting at carts on the road. The Watch wants it clear.",
        "Two carts came in with arrows in the sides this week. Skeletons, on the road at dusk.",
        [["How many?", "Six would make the road safe for a while."],
         ["And then?", "Then the carts get through, and we eat."]],
        "The road still isn't safe.", "The carts came in clean this morning. Here.",
        "The road's been clear since you went out.",
        [_kill("road_skeletons", "minecraft:skeleton", 6)], {"xp": 140, "items": _items(("minecraft:emerald", 5), ("minecraft:arrow", 16))}),
    "duty_spider_season": _q(
        "Watch Duty: Spider Season", "Spiders are nesting near the fields. The farmers won't go out after dark.",
        "The farmers won't go out after dark. Spiders, big ones, in the hedges.",
        [["How many?", "Six. That should scare the rest off."],
         ["Anything else?", "Bring a torch. They hate the light, and so do I, out there."]],
        "The hedges still rustle at night.", "The farmers went out last night and came back. Thank you.",
        "Not a spider in the hedges since.",
        [_kill("field_spiders", "minecraft:spider", 6)], {"xp": 120, "items": _items(("minecraft:emerald", 4), ("minecraft:torch", 16))}),
    "duty_raider_patrol": _q(
        "Watch Duty: Raider Patrol", "Pillagers have been seen watching the village. The Watch wants them gone before they bring friends.",
        "Pillagers on the hill again. Watching. Counting.",
        [["How many are there?", "Four we've seen. There may be more."],
         ["What if there are?", "Then you come back and tell us, and we ring the bell."]],
        "They're still up on the hill.", "No more eyes on the hill. The Watch thanks you.",
        "The hill's been empty since.",
        [_kill("hill_pillagers", "minecraft:pillager", 4)], {"xp": 200, "items": _items(("minecraft:emerald", 7))}),
    "duty_creeper_watch": _q(
        "Watch Duty: Creeper Watch", "Something blew a hole in the baker's wall. The Watch thinks there are more of them about.",
        "Something blew a hole in the baker's wall last night. Green, quiet, and there are more of them.",
        [["Creepers?", "Five of them, at least. Get them before they get to the houses."]],
        "The baker's still sweeping up.", "No more holes. Here's the Watch's thanks.",
        "Not a single new hole in a wall since.",
        [_kill("creepers", "minecraft:creeper", 5)], {"xp": 160, "items": _items(("minecraft:emerald", 5), ("minecraft:gunpowder", 4))}),
    "duty_feed_the_village": _q(
        "Watch Duty: Feed the Village", "The Watch is collecting bread for families who lost their stores.",
        "Some families lost their stores to the last raid. The Watch is collecting bread.",
        [["How much?", "Sixteen loaves would see them through the week."]],
        "The families are still waiting.", "Sixteen loaves. They'll eat tonight. Thank you.",
        "Nobody went hungry this week.",
        [_collect("bread", "minecraft:bread", 16)], {"xp": 100, "items": _items(("minecraft:emerald", 6))}),
    "duty_mend_the_fences": _q(
        "Watch Duty: Mend the Fences", "The village's fences are broken in a dozen places. The Watch needs timber and stone.",
        "The fences are down in a dozen places. If raiders come, they'll walk straight in.",
        [["What do you need?", "Timber and stone. Thirty-two cobblestone, sixteen oak logs."]],
        "We're still short of timber and stone.", "That'll mend every gap. Here, from the Watch.",
        "The fences are whole again.",
        [_collect("stone", "minecraft:cobblestone", 32), _collect("logs", "minecraft:oak_log", 16)],
        {"xp": 120, "items": _items(("minecraft:emerald", 6))}),
}

HAND = {
    "contract_the_tithe": _q(
        "Hand Contract: The Tithe", "The Gilded Hand wants its gold. You're the one who collects it now.",
        "A villager slips you a folded note sealed with a golden hand, and won't meet your eyes.",
        [["What does it say?", "'Sixteen gold ingots for the Hand. Bring them here. Ask no one.' It's signed with a smile."],
         ["Why you?", "Because they told me to give it to you. Please just take it."]],
        "The Hand is still waiting for its gold.", "The villager takes the gold with shaking hands. Your cut is inside the purse.",
        "The Hand has its tithe. For now.",
        [_collect("tithe_gold", "minecraft:gold_ingot", 16)], {"xp": 160, "items": _items(("minecraft:emerald", 14))}),
    "contract_the_bell": _q(
        "Hand Contract: Steal a Bell", "Silas wants a village bell. Not this village's. Anyone's.",
        "A note under your door. Sealed with a golden hand.",
        [["What does Silas want?", "'A bell. From a village that isn't this one. They ring them when we come. I'd like them to stop.'"],
         ["That's cruel.", "'That's business. Bring it here.'"]],
        "Silas is still waiting for his bell.", "The bell disappears into a sack. A heavy purse appears in its place.",
        "Somewhere a village can't ring for help. The Hand is pleased.",
        [_collect("stolen_bell", "minecraft:bell", 1)], {"xp": 200, "items": _items(("minecraft:emerald", 20))}),
    "contract_rivals": _q(
        "Hand Contract: Rivals", "A pillager band has been selling to the cult without paying the Hand its share. Silas wants them gone.",
        "A villager passes you a note sealed with a golden hand.",
        [["Who are they?", "'Pillagers. Selling skulls to the cult without paying my share. Four of them. Make it loud.'"],
         ["And the cult?", "'The cult will understand. Business is business.'"]],
        "Silas's rivals are still in business.", "'Loud, I heard. Good.' The purse is heavy.",
        "The pillagers pay the Hand now. All of them.",
        [_kill("rival_pillagers", "minecraft:pillager", 4)], {"xp": 200, "items": _items(("minecraft:emerald", 14))}),
    "contract_the_farmers_share": _q(
        "Hand Contract: The Farmers' Share", "The Hand takes a share of every harvest. Go and take it.",
        "A sealed note: 'The farmers haven't paid. Take the Hand's share. Don't be gentle.'",
        [["How much?", "'Twenty-four wheat and twelve carrots. They'll say it's all they have. It isn't.'"]],
        "The Hand's share is still in the farmers' barns.", "You hand over the farmers' share. The Hand pays well for food it didn't grow.",
        "The farmers will go hungry this week. The Hand won't.",
        [_collect("wheat", "minecraft:wheat", 24), _collect("carrots", "minecraft:carrot", 12)],
        {"xp": 140, "items": _items(("minecraft:emerald", 12))}),
    "contract_powder": _q(
        "Hand Contract: Powder for the Hand", "The Hand is buying gunpowder. Nobody asks what for.",
        "A note sealed with a golden hand: 'Powder. Sixteen measures. Don't ask.'",
        [["What's it for?", "The villager shrugs. 'Nobody asks. That's why they pay.'"]],
        "The Hand still wants its powder.", "The powder's gone before you've let go of it. Your purse is fuller.",
        "Something will burn soon. Not your problem.",
        [_collect("powder", "minecraft:gunpowder", 16)], {"xp": 140, "items": _items(("minecraft:emerald", 13))}),
    "contract_bones": _q(
        "Hand Contract: Bones for the Choir", "The Bone Choir is buying, and the Hand is selling.",
        "A note sealed with a golden hand: 'The Bone Choir wants bones. Lots of them. They pay the Hand; the Hand pays you.'",
        [["How many?", "'Thirty-two bones. Don't ask what they sing with them.'"]],
        "The Choir is still waiting.", "The sack of bones rattles away into the dark. The Hand pays.",
        "Somewhere, the Bone Choir is singing.",
        [_collect("bones", "minecraft:bone", 32)], {"xp": 120, "items": _items(("minecraft:emerald", 11))}),
}


def produced_traits():
    import mod_tables
    out = set()
    for _, q in QUESTS.values():
        for v in q.get("traits", {}).values():
            out.update(v)
        for side in q.get("choice", {}).values():
            out.update(side.get("traits", []))
    for c in mod_tables.CHOICES:
        for o in c["options"]:
            out.add(o["trait"])
    return out


def write(out):
    known = produced_traits()
    for s in SETTLERS:
        assert s["trait"] in known, f"settler {s['key']}: trait {s['trait']} is never set"
        assert s["place"] in ("home", "ashford", "snowy_village", "desert_village", "buyers_village"), s
    for _, t, _ in ASHFORD_STAGES:
        assert t in known, f"Ashford stage trait {t} is never set"
    d = os.path.join(out, "data/half_a_god/hag_mod")
    os.makedirs(d, exist_ok=True)
    json.dump({"settlers": SETTLERS, "ashford": [{"stage": s, "trait": t, "name": n} for s, t, n in ASHFORD_STAGES]},
              open(os.path.join(d, "settlement.json"), "w", encoding="utf-8"), indent=1, ensure_ascii=False)
    json.dump({"watch": [f"{QNS}:hag_{k}" for k in WATCH], "hand": [f"{QNS}:hag_{k}" for k in HAND]},
              open(os.path.join(d, "duties.json"), "w", encoding="utf-8"), indent=1, ensure_ascii=False)
    qdir = os.path.join(out, "data", QNS, "quests", "delayed_next_quest", "half_a_god")
    os.makedirs(qdir, exist_ok=True)
    for k, q in {**WATCH, **HAND}.items():
        assert k not in QUESTS
        json.dump(q, open(os.path.join(qdir, f"hag_{k}.json"), "w", encoding="utf-8"), indent=2, ensure_ascii=False)
