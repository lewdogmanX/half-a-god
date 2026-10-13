"""The Silver King: a storyline for Create: Cosmonautics.

Two gods drove the sky. Azriel carried the sun by day; Iskar, the Silver King, carried the moon by night, and between
them sat the Silver Court, whose herald, Maraal, rode at dusk and dawn. When the Sun Spirit threw Maraal down, Iskar
watched and did not move (the moon does not leave its path). Azriel jumped, and broke in two. The half that hated the
sky, Azazel, never forgave the one who only watched: when his cult was strong enough he drove the burning chariot up one
last time, straight into the moon. The first eclipse. Iskar burned on his own throne with his sword raised, the one blade
ever made to cut the sun, and never swung it. The Faithful's golden eye is that moment: a black moon in a ring of fire.

- The Observatory: rare, on mountain peaks. Walk in and Hester Quill, an astronomer everyone thinks is mad, tells you
  what she's seen: the man in the moon has a face because he's real. She teaches you to fly, one step at a time
  (titanium, a thruster, a Sputnik probe in orbit, a helmet and air).
- The moon: the Faithful's wrecked golden rocket and its logbook; then Iskar's corpse across the great crater, the
  moon-mad Faithful still chipping at him, and their Eclipse Priest. When the priest falls, Iskar's last light rises from
  his mouth, and you choose: take the Eclipse, or lay him to rest with it.
- Come home alive: the Eclipse only answers to the living. Die before you're back on the overworld and it goes home to
  his hand (you'll have to fly back for it). Bring it home and it's yours, and the True Azazel knows it when he sees it.

The mod (Moon.java) starts the first quest when you walk into an observatory, keeps the moon's people breathing (they
haven't needed air for a long time), gives you the Eclipse and takes it back if you die, and makes the finale notice."""
from lib import *
import npc_lines

MOON = ["rocketnautics:moon"]
SPACE = ["rocketnautics:deep_space"]
RN = "rocketnautics"
NM = "netherman"
HESTER = "Hester Quill"

# ======================= the places =======================
LUNAR = ["rocketnautics:lunar_maria", "rocketnautics:lunar_highlands", "rocketnautics:lunar_megaregolith"]
EXTRA["tags/worldgen/biome/has_observatory.json"] = {"values": ["#minecraft:is_mountain"]}
EXTRA["tags/worldgen/biome/has_moon_relics.json"] = {"values": [{"id": b, "required": False} for b in LUNAR]}


def _structure(name, biomes, sink, adapt, spacing, separation, salt, pool_size=1):
    EXTRA[f"worldgen/structure/{name}.json"] = {
        "type": "minecraft:jigsaw", "biomes": f"#{NS}:{biomes}", "step": "surface_structures", "spawn_overrides": {},
        "terrain_adaptation": adapt, "start_pool": f"{NS}:{name}", "size": pool_size,
        "start_height": {"absolute": sink}, "project_start_to_heightmap": "WORLD_SURFACE_WG",
        "max_distance_from_center": 96, "use_expansion_hack": False}
    EXTRA[f"worldgen/template_pool/{name}.json"] = {
        "name": f"{NS}:{name}", "fallback": "minecraft:empty",
        "elements": [{"weight": 1, "element": {"element_type": "minecraft:single_pool_element", "location": f"{NS}:{name}",
                                               "projection": "rigid", "processors": "minecraft:empty"}}]}
    EXTRA[f"worldgen/structure_set/{name}.json"] = {
        "structures": [{"structure": f"{NS}:{name}", "weight": 1}],
        "placement": {"type": "minecraft:random_spread", "spacing": spacing, "separation": separation, "salt": salt}}


# (each sinks so its y = 0 lands on the first air above the ground. The observatory lets the terrain
#  fill in under it on a slope; the moon builds bring their own ground and crater, and leave the terrain alone)
import moon_builds
_structure("observatory", "has_observatory", moon_builds.start_height("observatory"), "beard_thin", 56, 24, 418726391)
_structure("faithful_wreck", "has_moon_relics", moon_builds.start_height("faithful_wreck"), "none", 20, 8, 902113457)
_structure("iskars_rest", "has_moon_relics", moon_builds.start_height("iskars_rest"), "none", 32, 12, 177553129)

# ======================= the story =======================
HESTER_INTRO = [
    ["Who are you?", "Hester Quill. Astronomer. The valley calls me the Mad Woman of the Peak, which is fair, but incomplete."],
    ["What are you looking at?", "The moon. Thirty years of the moon. Do you know it isn't a light in the sky? It's a rock, "
                                 "a world, going round ours. Closer than anyone thinks. You could stand on it."],
    ["Stand on it?", "With fire under you and air in a jar, yes. The overworld isn't all there is. It isn't even most of what there is."],
    ["What did you see?", "A face. Everyone calls it the man in the moon. Through this glass, on a clear full moon, he has "
                          "hands. And shoulders. And something across his chest that catches the sun like a blade."],
    ["The golden eye.", "You've noticed it too? The Faithful's eye: a black disc in a ring of gold. That's not an eye. "
                        "That's an eclipse. The moon in front of the sun. They're praying to something that happened up there."],
]
HESTER_PROBE = [
    ["What did the probe see?", "Him. Clear as you. A body across the great crater, pale as chalk, burned black where the "
                                "sun hit him, and a sword in his hand. And a little gold wreck in a crater nearby. Someone tried before you."],
    ["Who is he?", "The old songs have a name I never understood until tonight. Iskar. The Silver King, who drove the "
                   "moon by night the way the Chariot drove the sun by day. The songs say the Chariot killed him. I thought it was a metaphor."],
    ["Why go?", "Because the Faithful went, and they don't go anywhere for nothing. Because he's been lying there with "
                "nobody to bury him for a thousand years. And because that sword was made for one thing, and the thing it was made for is still out there."],
    ["What do I need?", "A helmet that seals, and air to put in it. And fuel for the way home. Nobody ever plans for the "
                        "way home. Promise me you will."],
]

quest("mn1_the_woman_who_watches_the_moon", "The Woman Who Watches the Moon",
      "An observatory on the peak, a brass telescope pointed at the sky, and a woman who hasn't looked away from it in thirty years.",
      [talk("meet_hester", 0, "She doesn't look up from the eyepiece. 'You'll want to sit down. Everyone does, the first time.'", name=HESTER)],
      rewards={"xp": 300, "items": {"minecraft:spyglass": 1}},
      nxt=flow("mn2_a_metal_for_the_sky", delay=200))
QUESTS["mn1_the_woman_who_watches_the_moon"][1]["goals"][0]["_talk"] = HESTER_INTRO

quest("mn2_a_metal_for_the_sky", "A Metal for the Sky",
      "Hester says nothing made of iron will survive the climb. Titanium will: it's in these mountains, high up on the peaks "
      "or deep under them. Smelt some and bring her eight ingots to prove the hills have enough for a rocket.",
      [collect("titanium", f"{RN}:titanium_ingot", 8, 0),
       talk("show_hester", 1, "She weighs an ingot in her hand like it's the first one she's ever held. 'Light. Strong. Enough "
                               "of these and you could climb out of the sky.'", name=HESTER)],
      rewards={"xp": 600, "items": {"minecraft:emerald": 12}},
      nxt=flow("mn3_fire_under_glass", delay=200))

quest("mn3_fire_under_glass", "Fire Under Glass",
      "Before anyone sits on top of a fire, Hester wants to see one. Build a Rocket Thruster (Create: Cosmonautics) and set it "
      "down on solid ground. It drinks lava. Keep your eyebrows away from the business end.",
      [place_anywhere("a_test_stand", f"{RN}:rocket_thruster", 1, 0,
                      "It roars like the Nether's been let out of a bottle. Somewhere on the peak, a woman is cheering.")],
      rewards={"xp": 800, "items": {"minecraft:lava_bucket": 1}},
      nxt=flow("mn4_a_little_moon_of_our_own", delay=600))

quest("mn4_a_little_moon_of_our_own", "A Little Moon of Our Own",
      "Hester's dream: a Sputnik Probe, a little metal moon of our own, going round the world and looking down. Build one. "
      "Then build something that can carry it up, and carry it up.",
      [collect("the_probe", f"{RN}:sputnik", 1, 0, "It's small. It beeps. Hester has already named it.")],
      rewards={"xp": 600},
      nxt=flow("mn5_above_the_sky", delay=40, dims=SPACE, retry=100))

quest("mn5_above_the_sky", "Above the Sky",
      "You made it. The world is a ball under you, blue and green and very small. Set the probe free up here.",
      [place_anywhere("set_it_free", f"{RN}:sputnik", 1, 0,
                      "It drifts away from you, turning, catching the sun. Beep. Beep. Down on the peak, Hester will be hearing it.")],
      rewards={"xp": 1500, "items": {"minecraft:experience_bottle": 6}},
      nxt=flow("mn6_what_the_probe_saw", delay=200))

quest("mn6_what_the_probe_saw", "What the Probe Saw",
      "Hester has been at the receiver since the probe went up. She's seen him now. Go and hear it, then get ready to go: a "
      "Space Helmet, and a Jetpack to keep air in it.",
      [talk("hear_her", 0, "She's crying a little, and doesn't seem to notice. 'He's real. Thirty years, and he's real.'", name=HESTER),
       collect("a_helmet", f"{RN}:space_helmet", 1, 1),
       collect("air", f"{RN}:jetpack", 1, 1, "Helmet, air, fuel for the way home. Hester makes you say it back to her twice.")],
      rewards={"xp": 1200, "items": {"minecraft:golden_carrot": 16}},
      nxt=flow("mn7_the_silver_sea", delay=40, dims=MOON, retry=100))
QUESTS["mn6_what_the_probe_saw"][1]["goals"][0]["_talk"] = HESTER_PROBE

quest("mn7_the_silver_sea", "The Silver Sea",
      "Grey dust, black sky, the world hanging over you like a lantern. Somewhere near, the Faithful's wreck. Find it, and "
      "find out what they came for.",
      [reach("the_wreck", f"{NS}:faithful_wreck", 0, "A golden rocket, broken in two in a crater, and its pilgrims still "
                                                      "kneeling beside it in their gold. They've been kneeling a long time.", radius=4000),
       chest_book("the_log", 1, f"{NS}:faithful_wreck", "The Ascension's Log", "hag_ascension_log", "The Ascension's Log",
                  "Brother Ascendant", [
                      "Day 1. We rose on the Chariot's fire. Praise him. The world is so small from here. Brother Cail wept.",
                      "Day 3. The moon. Grey and silent. Two brothers have stopped breathing. The Priest says breath is a "
                      "habit of the flesh, and that we will lose it, as we lose everything that is not faith.",
                      "Day 9. We have found the Silver King. He is so much bigger than the hymns. The blade is in his hand. "
                      "We are commanded to break it, so no hand may ever raise it against our god again.",
                      "Day 40. We cannot break it. It does not break. The Priest says we will keep trying until we are "
                      "dust. I think we are dust already. I think we have been for days."],
                  "The last entry is in a different hand: 'Whoever reads this: he is still holding it.'", radius=4000)],
      rewards={"xp": 2000, "items": {"minecraft:gold_block": 2}},
      nxt=flow("mn8_the_man_in_the_moon", delay=200, dims=MOON))

quest("mn8_the_man_in_the_moon", "The Man in the Moon",
      "Iskar, the Silver King, lies across the great crater with his sword in his hand. The Faithful who didn't die are still "
      "there, chipping at him. They don't breathe any more. They don't need to.",
      [reach("iskars_rest", f"{NS}:iskars_rest", 0, "He's vast. Pale stone, burned black and gold where the sun went through "
                                                      "him. A silver crown. His mouth is open, as if he died mid-word.", radius=5000),
       kill("the_moon_mad", "minecraft:husk", 6, 1),
       kill("the_eclipse_priest", "minecraft:evoker", 1, 2, "The Priest's gold mask cracks in half. Under it, nothing at all. "
                                                           "Then a light rises out of Iskar's open mouth: small, silver, cold."),
       kill("the_last_light", "minecraft:allay", 1, 3)],
      threats=[threat("minecraft:husk", "Moon-Mad Faithful", stage=1, count=6, hp=60, smin=6, smax=16,
                      equipment=eq(head="minecraft:golden_helmet", chest="minecraft:golden_chestplate"),
                      effects=fx(("minecraft:strength", 1))),
               threat("minecraft:evoker", "The Eclipse Priest", stage=2, hp=420, smin=8, smax=16,
                      equipment=eq(head=("minecraft:golden_helmet", {"minecraft:protection": 4})),
                      effects=fx(("minecraft:resistance", 1), ("minecraft:strength", 1), ("minecraft:speed", 0))),
               threat("minecraft:allay", "Iskar's Last Light", stage=3, hp=8, spare=True, smin=2, smax=5,
                      talk=[["Iskar?", "What's left. A little light, in the mouth of a dead king. You came a long way to see a corpse."],
                            ["The blade.", "The Eclipse. I made it to cut the sun, and I lifted it, and I was too slow. "
                                           "It has been waiting a thousand years for a faster hand."],
                            ["Maraal.", "My herald. I watched her fall and I did not move. I have had a long time to be sorry."],
                            ["What should I do?", "Take the blade, and finish what I started. Or put it back in my hand and "
                                                  "let me be buried at last. Either way, little half-god, I am so tired."]])],
      choice={"attack": {"label": "Take the Eclipse", "traits": ["hag_iskar_blade"]},
              "spare": {"label": "Lay him to rest with it", "line": "The light sinks back into him like a sigh. The dust settles "
                                                                    "over his face. For the first time in a thousand years, the moon is quiet.",
                        "traits": ["hag_iskar_rest"]}},
      rewards={"xp": 5000, "items": {"minecraft:experience_bottle": 16}},
      on_kill=flow("mn9_come_home_alive", delay=20, dims=MOON), on_spare=flow("mn9r_rest", delay=1200))

quest("mn9_come_home_alive", "Come Home Alive",
      "The Eclipse is cold in your hand, and heavier than it looks. It only answers to the living: die anywhere before your "
      "feet touch the overworld again and it goes home to his hand. Hester made you promise. Fuel for the way home.",
      [collect("the_eclipse", f"{NS}:eclipse", 1, 0)],
      rewards={"xp": 1000},
      nxt=flow("mn10_the_silver_kings_blade", delay=100))

quest("mn10_the_silver_kings_blade", "The Silver King's Blade",
      "You're home, and alive, and the Eclipse is yours. Hester will want to see it. She'll want to see you, mostly.",
      [collect("still_yours", f"{NS}:eclipse", 1, 0),
       talk("show_hester", 1, "She doesn't touch it. She just looks at it, and then at the sky, for a long time. 'He'd be glad "
                              "it was you,' she says, as if she knew him.", name=HESTER)],
      rewards={"xp": 3000, "items": {"minecraft:diamond": 4}},
      traits={"complete": ["hag_eclipse_home"]})
QUESTS["mn10_the_silver_kings_blade"][1]["goals"][0]["_keep"] = True   # she only looks at it

quest("mn9r_rest", "Rest",
      "You put the Eclipse back in his hand and left him to the dust. Tell Hester what you found, and what you did.",
      [talk("tell_hester", 0, "'You buried a god,' she says. 'With his sword. Like a king.' She writes it down in the big book, "
                              "slowly, the way people write things they want to last.", name=HESTER)],
      rewards={"xp": 4000, "items": {"minecraft:diamond": 6, "minecraft:emerald_block": 2}},
      traits={"complete": ["hag_iskar_rested"]})

# what Hester says when you walk up to her
npc_lines.GREET[("mn1_the_woman_who_watches_the_moon", HESTER)] = "Shh. Don't move. ...There. He blinked. No. No, that was a cloud."
npc_lines.GREET[("mn2_a_metal_for_the_sky", HESTER)] = "Titanium? Show me. Show me!"
npc_lines.GREET[("mn6_what_the_probe_saw", HESTER)] = "Come here. Come here and look at what your little moon sent back."
npc_lines.GREET[("mn10_the_silver_kings_blade", HESTER)] = "You came back. Nobody ever comes back. Let me see you. Let me see it."
npc_lines.GREET[("mn9r_rest", HESTER)] = "You came back. Your hands are empty. Tell me."

# rumours: someone in the hills watches the moon
for rid, req, line, forbid in (
        ("moon_watcher", None, "There's a woman up on the peaks who watches the moon all night through a brass tube as long as a "
                               "cart. Says it's a world. Says you could walk on it. Mad as a box of frogs.", "hag_mn1_the_woman_who_watches_the_moon_done"),
        ("moon_face", "hag_mn6_what_the_probe_saw_done", "My gran always said the man in the moon was watching us. "
                                                          "Lately I've started to wonder if he's sad.", None),
        ("moon_home", "hag_eclipse_home", "They say somebody went up to the moon and came back with a black sword. "
                                          "They say the moon's been a little brighter since.", None),
        ("moon_rest", "hag_iskar_rested", "The moon looked different last night. Peaceful. Like something up there finally went to sleep.", None)):
    d = {"id": f"{NS}:{rid}", "weight": 10, "line": line}
    cond = {}
    if req:
        cond["required_traits"] = [req]
    if forbid:
        cond["forbidden_traits"] = [forbid]
    if cond:
        d["conditions"] = cond
    EXTRA[f"rumors/{rid}.json"] = d

# for the mod
TABLE = {
    "start": qid("mn1_the_woman_who_watches_the_moon"),
    "structure": f"{NS}:observatory",
    "iskar": f"{NS}:iskars_rest",
    "moon": "rocketnautics:moon",
    "breathing": ["Moon-Mad Faithful", "The Eclipse Priest", "Iskar's Last Light"],
    "take_trait": "hag_iskar_blade",
    "rest_trait": "hag_iskar_rested",
    "carry_quest": qid("mn9_come_home_alive"),
    "given": "The Eclipse comes out of his hand as if he let go. It's cold, and heavier than it looks, and it hums when you face the sun.",
    "lost": "Your hand opens as you die. Far above, on the moon, the Eclipse is back in Iskar's hand.",
    "again": "The Eclipse is in his hand again. It lets you take it, again. Come home alive this time.",
    "home": "Your feet are on the overworld. The Eclipse is quiet in your hand now. It's yours.",
    "rest_rep": 15,
    "azazel_sees": "THAT BLADE. I watched it rise against me once. I watched it fall.",
    # (the game test checks the templates load at these sizes)
    "sizes": {n: moon_builds.size(n) for n in ("observatory", "faithful_wreck", "iskars_rest")},
}


def mod_table():
    return TABLE

# the Eclipse's name and words (the mod's own language file)
import dialogue
dialogue.LANG.update({
    "item.half_a_god.eclipse": "The Eclipse",
    "item.half_a_god.eclipse.lore1": "Iskar's sword, made to cut the sun.",
    "item.half_a_god.eclipse.lore2": "Twice as deadly to the Chariot and to things of fire.",
    "item.half_a_god.eclipse.lore3": "Stronger by night, in the Nether, and on the moon.",
    "item.half_a_god.eclipse.lore4": "Use: the sky goes dark around you (every 3 minutes).",
})

# the Eclipse takes sword enchantments (in a tag of its own, so other mods' entries are untouched)
for _tag in ("swords", "enchantable/sword", "enchantable/sharp_weapon", "enchantable/weapon", "enchantable/fire_aspect",
             "enchantable/vanishing"):
    EXTRA[f"minecraft/tags/item/{_tag}.json"] = {"replace": False, "values": [{"id": f"{NS}:eclipse", "required": False}]}
