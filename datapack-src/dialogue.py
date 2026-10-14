"""Villagers greet you by reputation, talk among themselves about what you did, and your companions have
something to say about the story (Mebahel's ambient dialogue and companion dialogue profiles).

Villager lines carry their own text as a fallback. Companion lines are translation keys (Mebahel requires keys there);
their English text ships in the mod's language file (LANG below). The mod switches a companion between its bright / grey / dark profile as your reputation moves."""
from lib import EXTRA, NS

_n = 0
LANG = {}  # translation key -> English text (written into the mod's assets/half_a_god/lang/en_us.json)


def key(k, text):
    LANG[k] = text
    return k


def _id(prefix):
    global _n
    _n += 1
    return f"hag_{prefix}_{_n}"


def passby(flags, lines, forbid=(), weight=6, cooldown=12000):
    i = _id("passby")
    EXTRA[f"ambient_dialogues/{i}.json"] = {
        "id": i, "type": "player_passby", "required_story_flags": list(flags), "forbidden_story_flags": list(forbid),
        "trigger_radius": 4.5, "weight": weight, "cooldown": cooldown, "max_player_distance": 12,
        "lines": [{"speaker": 0, "look_at": "player", "text": t, "translation_key": key(f"ambient.half_a_god.{i}.{k}", t)}
                  for k, t in enumerate(lines)]}


def chat(flags, lines, forbid=(), weight=8, cooldown=24000):
    i = _id("talk")
    EXTRA[f"ambient_dialogues/{i}.json"] = {
        "id": i, "type": "villager_conversation", "required_story_flags": list(flags), "forbidden_story_flags": list(forbid),
        "weight": weight, "cooldown": cooldown, "max_player_distance": 16,
        "lines": [{"speaker": k % 2, "look_at": 1 - k % 2, "text": t, "translation_key": key(f"ambient.half_a_god.{i}.{k}", t)}
                  for k, t in enumerate(lines)]}


# ---- greetings by where you stand (the mod keeps exactly one hag_tier_* flag on you)
GREET = {
    "saint": ["Lantern-Bearer! Come in, come in, the kettle's on.", "My daughter wants to be you when she grows up. I told her to start with the dishes.",
              "Bless you. I mean it. Bless you.", "Take an apple. Take two. Don't argue.", "The roads are safer since you started walking them."],
    "good": ["Good to see you on the road.", "Halden speaks well of you. He doesn't speak well of anybody.",
             "You helped my cousin in Ashford. I won't forget it.", "Mind the bridge, it's slippery. Stay safe.", "Evening, friend."],
    "waver": ["Oh. It's you.", "Which one of you is it today? The kind one?", "I've heard two stories about you. I don't know which to believe.",
              "Don't take this wrong, but... keep your hands where I can see them.", "My husband says you're a hero. My sister says otherwise."],
    "fallen": ["We don't want trouble.", "Please, just... take what you need and go.", "The children are inside. They'll stay inside.",
               "I didn't see you. I never see you.", "Is it true, what they say about the prisoner?"],
    "wicked": ["Don't hurt anyone. Please.", "Take it. Take all of it.", "The golem won't stop you. We know. We just hope.",
               "Silas's friend. I know who you are.", "Get away from my door."],
}
# Mebahel always prefers story-flagged lines over its own, so greetings are rare: each one at most every three days.
for tier, lines in GREET.items():
    for line in lines:
        passby([f"hag_tier_{tier}"], [line], weight=1, cooldown=72000)

# ---- villagers talk about what you did (story traits double as Mebahel story flags)
# Gossip only while it's news: each line stops once the story has moved on (the "until" trait).
A1, A9, W1, W6, H1, E1, S1, V1, V10, M1, A6, F1 = ("hag_a1_ashford_burns_done", "hag_a9_open_the_cells_done",
    "hag_w1_the_skull_market_done", "hag_w6_the_withered_crown_done", "hag_h1_a_light_above_the_pit_done",
    "hag_e1_the_librarians_request_done", "hag_s1_cold_hearths_done", "hag_v1_blood_in_the_wells_done", "hag_v10_maraal_done",
    "hag_m1_follow_the_crimson_bee_done", "hag_a6_believers_house_done", "hag_f1_the_last_road_done")
CHATS = [
    (["heard_the_word"], ["The smiling man walked out of here alive.", "The hero let him go. Must have had a reason."], A1),
    (["peddler_slain"], ["They drove the gold-man out of the village with steel.", "Good. ...Isn't it?"], A1),
    (["hag_a1_ashford_burns_done"], ["Did you hear about Ashford?", "Burned. All of it. They say the men were taken, not killed."], A9),
    (["hag_a9_open_the_cells_done"], ["Some of the Ashford men are home!", "Not all of them. But some is a lot."], W1),
    (["vael_spared"], ["A cult steward was seen praying on the road.", "Praying to who, though?"], W6),
    (["vael_slain"], ["The cult's steward is dead.", "Then they'll send another. They always do."], W6),
    (["hag_w6_the_withered_crown_done"], ["The Wither's dead. The Wither!", "And the Nether sky went red the same night. I don't like that."], H1),
    (["hag_w7_a_light_they_cannot_dim_done"], ["Have you seen the beacon? You can see it from three villages.", "My mother cried when she saw it."], E1),
    (["hag_h10_fireproof_done"], ["The hero went above the clouds.", "And came back talking about someone called Maraal. Who's Maraal?"], S1),
    (["hag_e5_free_the_end_done"], ["The dragon's dead. The actual dragon.", "And I can't sleep. Everyone dreams of a king with no head."], S1),
    (["hag_f3_unbound_done"], ["Seven beasts dead.", "Then why does it feel like something just woke up?"], V1),
    (["midwife_spared"], ["The midwife from the blood pits is with the cleric now.", "I wouldn't let her near my children. ...But I'd let her near the cleric."], V10),
    (["midwife_slain"], ["They burned the blood pits.", "Good. Burn the church too."], V10),
    (["maraal_forgiven"], ["Old Mara travels with the hero now.", "I always thought she was older than she looked."], M1),
    (["maraal_rejected"], ["Old Mara's stopped writing letters.", "First time in forty years. Something's wrong."], M1),
    (["hag_mercy_x1_the_informant"], ["The hero let that pillager scout go.", "Wick? He'll be back. Maybe that's the point."], A6),
    (["hag_cruelty_x1_the_informant"], ["They hanged the scout at the crossroads.", "My boy saw it. He keeps asking about it."], A6),
    (["hag_mercy_x2_the_bastille_warden"], ["The Bastille warden walked free.", "Gorran? The Ashford men said he was the decent one."], W6),
    (["hag_mercy_x5_the_last_garrison_man"], ["A man came home after ten years in the ice!", "Brann? Ilse's Brann? I'll believe it when I see him."], F1),
    (["gt_kingpin"], ["There's green on the market road again.", "Don't buy it. Don't even look at it. You know whose it is."], V1),
    (["gt_cartel_broken"], ["Blackwater's finished. The Watch nailed the ledger to the gate.", "Every name in it. Even the guards'."], V1),
    (["gt_got_out"], ["I heard the hero testified against Blackwater.", "Against themselves, too. That takes something."], V1),
    (["hag_good_ending"], ["Is it true? The god is dead?", "The gold in my purse turned to iron last night. I think that's a yes."], None),
    (["hag_sworn"], ["Lock the door.", "The Hand walks the roads now. Don't look out the window."], None),
    (["hag_lantern_road_lit"], ["The old road's lit end to end.", "My grandmother walked it at night when she was a girl. I never thought I would."], None),
]
for flags, lines, until in CHATS:
    chat(flags, lines, forbid=[until] if until else [], weight=4, cooldown=36000)

# ---- companion profiles: Dunn (spared in chapter 1), Corwin (the granary), Ansel (chapter 6)
COMMON = {
    "follow": ["Right behind you.", "Lead on."], "wait": ["I'll wait here.", "Don't be long."],
    "set_home": ["This'll do for home.", "Home it is."], "go_home": ["I'll head back.", "See you there."],
    "guard": ["Nobody gets past me.", "I'll watch this spot."], "guard_home": ["I'll keep the home safe."],
    "stop_guard": ["Standing down."], "combat_menu": ["How do you want me to fight?"],
    "passive": ["I'll keep my head down."], "defensive": ["Only if they come at us."], "aggressive": ["Then let's not wait for them."],
    "equipment": ["Got anything better than this?"], "farewell": ["Goodbye, then. Thank you. For all of it."],
}
COMPANIONS = {
    "dunn": ("Dunn the Deserter", "minecraft:pillager", "rc1_", {
        "bright": {"greeting": ["You saved my life. I keep trying to deserve it.", "The Watch let me carry a lantern last night. Me!"],
                   "stories": [("Why did you desert?", "At Ashford a woman looked at me over her child's head. I put the crossbow down. I couldn't pick it up again."),
                               ("Do you miss the camps?", "I miss the people I thought were my friends. I don't miss what we did."),
                               ("What do you think of me?", "I think you're what I wanted to be when I joined up. Before I knew what it meant.")]},
        "grey": {"greeting": ["You spared me. I still don't know why.", "I'll fight beside you. I'm still deciding what you are."],
                 "stories": [("Why did you desert?", "Ashford. I couldn't do it. Don't ask me more than that."),
                             ("What do you think of me?", "Some days I see the one who saved me. Some days I see the camp captains. Try to be the first one.")]},
        "dark": {"greeting": ["You spared me. Then you did... everything else.", "I'm here because I owe you. Don't make me regret paying it."],
                 "stories": [("Why do you stay?", "Because I left once already, and I swore I'd never run again. Even from you."),
                             ("What do you think of me?", "I think you're becoming what I ran from.")]}}),
    "corwin": ("Corwin the Refugee", "minecraft:villager", "r2g_the_granary_thief", {
        "bright": {"greeting": ["You paid for my grain. I'll pay you back if it takes my whole life.", "My children ask about you every night."],
                   "stories": [("Why did you steal?", "Four children and no grain. Ashford was ash. I'd have stolen from a god."),
                               ("Where's your family?", "Safe, in your village. Because of you. I don't know how to say that properly.")]},
        "grey": {"greeting": ["You let me go once. I haven't forgotten.", "I'm still here. Still owe you."],
                 "stories": [("Why did you steal?", "Hunger. It's always hunger."),
                             ("Are you afraid of me?", "A little. You can be kind. You can also be... the other thing.")]},
        "dark": {"greeting": ["I follow because I owe you. Not because I like where we're going.", "My children don't ask about you any more."],
                 "stories": [("Why are you still here?", "Somebody has to remind you that you were kind to me once."),
                             ("What do you want?", "For you to be the person who paid for my grain. Just once more.")]}}),
    "ansel": ("Ansel the Blood-Drinker", "minecraft:villager", "rc6_", {
        "bright": {"greeting": ["Seven days without the blood. Seven. You did that.", "My hands have stopped shaking."],
                   "stories": [("What was it like?", "Like being hungry with your whole soul. And then you sat with me through the worst night."),
                               ("Will you go back?", "Not while you're watching. And I think you'll always be watching. I'm glad.")]},
        "grey": {"greeting": ["Still clean. Still here.", "Some days are harder than others."],
                 "stories": [("Why the blood?", "The cult said it would make me strong. It made me theirs."),
                             ("Are you better?", "Better than I was. Not as good as I'll be.")]},
        "dark": {"greeting": ["You smell like the pits sometimes. Did you know?", "I'm clean. Are you?"],
                 "stories": [("What's wrong?", "You helped me off the blood. Now I watch you take something just as bad. Gold. Fear. Pick one."),
                             ("Why stay?", "Because you sat with me when nobody else would. I'm trying to return the favour.")]}}),
}
for cname, (name, ent, qprefix, tones) in COMPANIONS.items():
    for tone, t in tones.items():
        base = f"companion.half_a_god.{cname}.{tone}"
        resp = {}
        for action, lines in {**COMMON, "greeting": t["greeting"]}.items():
            resp[action] = [key(f"{base}.{action}.{n}", line) for n, line in enumerate(lines, 1)]
        stories = [{"question": key(f"{base}.story.{n}.question", q), "answer": key(f"{base}.story.{n}.answer", a)}
                   for n, (q, a) in enumerate(t["stories"], 1)]
        # only the bright profile matches by name (it's what a newly recruited companion gets); the mod swaps in grey / dark
        EXTRA[f"companion_dialogue_profiles/{cname}_{tone}.json"] = {
            "display_name": key(f"{base}.name", name), "quest_ids": [], "entity_types": [ent] if tone == "bright" else [],
            "entity_names": [name] if tone == "bright" else [], "priority": 10,
            "responses": resp, "stories": stories}

COMPANION_NAMES = {key: v[0] for key, v in COMPANIONS.items()}

# the mod's creative tab
LANG.update({
    "itemGroup.half_a_god": "Half a God",
    "item.half_a_god.tab_icon": "Half a God",
})
