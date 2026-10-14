"""The Ribbits' Song: a short questline of its own, for the Ribbits mod's frog-folk.

Ribbit villages are never quiet: there's always a band. In this one there isn't. The Bog Hag stole the Golden Maraca
the band keeps the beat on (she says their music curdles her potions) and smashed their instruments on her way out.
Grandmother Pondwhistle, the village's old sorceress, asks for help; Bufo the bandleader needs reed, hide and string to
rebuild the band; the Hag is in her hut on the stilts (spare her, and she promises to brew somewhere else; or don't).
Then the Moonrise Chorus, and the village gives you the Golden Maraca: theirs is the oldest in the swamp, and you've
earned the right to keep the beat.

The mod (Ribbits.java) starts the first quest when you walk into a Ribbit village, and the Ribbits who speak to you are
Ribbits of that village (the quest's Grandmother and Bufo take the place of two who live there)."""
from lib import *
import npc_lines

GRAN = "Grandmother Pondwhistle"
BUFO = "Bufo the Bandleader"
RIBBIT = "ribbits:ribbit"
VILLAGE = "ribbits:ribbit_village"

GRAN_INTRO = [
    ["Why is it so quiet?", "Because the band has nothing to keep time to, dear. Somebody stole our music. Well. Our maraca. "
                            "Same thing, to a frog."],
    ["A maraca?", "The Golden Maraca. Every Ribbit band keeps the beat on one, and ours is the oldest in the swamp. "
                  "Without it the others won't play. We are very particular about rhythm. Ribbit. Excuse me."],
    ["Who took it?", "The Bog Hag. She lives in the hut on the stilts, past the cattails. She says our music curdles her "
                     "potions. We say her potions curdle everything."],
    ["How can I help?", "Bufo will tell you. He leads the band, when there is a band. He's been sulking under a lily pad "
                        "since Tuesday."],
]
BUFO_TALK = [
    ["The Hag took the maraca?", "Took the maraca, smashed the flutes, put her foot through the bongos and called my "
                                 "guitar 'a lute for toads'. I am a toad. That's not the point."],
    ["What do you need?", "Bamboo for flutes, rabbit hide for the drums, string for the guitars. Then we're a band "
                          "again. A band with no beat, but a band."],
    ["And the maraca?", "That's the hard part. She lives alone and she's got a temper and a cauldron. I've got a "
                        "flute. You see the problem."],
]
HAG_TALK = [
    ["Give it back.", "Give WHAT back? Oh, the rattle. Every full moon, all night, that bloody band. Chk-chk-chk. "
                      "Do you know what that does to a delicate brew?"],
    ["They just want to play.", "And I just want to sleep. I've lived in this swamp longer than their grandmothers' "
                                "grandmothers. I was here first."],
    ["Then move.", "...You'd let me go? Just like that? Fine. FINE. Take the damn rattle. There are quieter swamps."],
]

quest("rb1_a_song_gone_quiet", "A Song Gone Quiet",
      "Ribbit villages are never quiet. This one is. Somebody here should be playing something.",
      [talk("meet_pondwhistle", 0, "She pats your hand with a cold, damp, very kind one. 'Go and find Bufo. He'll be "
                                   "glad of the company, though he won't say so.'", entity=RIBBIT, name=GRAN)],
      rewards={"xp": 200},
      nxt=flow("rb2_reed_shell_and_hide", delay=200))
QUESTS["rb1_a_song_gone_quiet"][1]["goals"][0]["_talk"] = GRAN_INTRO

quest("rb2_reed_shell_and_hide", "Reed, Hide and String",
      "The Hag smashed the band's instruments on her way out. Bufo the bandleader needs bamboo for the flutes, rabbit hide "
      "for the drums and string for the guitars.",
      [collect("bamboo", "minecraft:bamboo", 16, 0),
       collect("rabbit_hide", "minecraft:rabbit_hide", 4, 0),
       collect("string", "minecraft:string", 8, 0),
       talk("bring_bufo", 1, "Bufo turns a new flute over in his hands, blows one note, and has to sit down for a "
                             "moment. 'Right,' he says, a bit hoarse. 'Right. Now the maraca.'", entity=RIBBIT, name=BUFO)],
      rewards={"xp": 500, "items": {"minecraft:emerald": 6}},
      nxt=flow("rb3_the_bog_hag", delay=200))
QUESTS["rb2_reed_shell_and_hide"][1]["goals"][3]["_talk"] = BUFO_TALK

quest("rb3_the_bog_hag", "The Bog Hag",
      "The Golden Maraca is in the Bog Hag's hut, on the stilts past the cattails. She has a temper, a cauldron, and "
      "nothing whatever to lose.",
      [reach("the_hut", "minecraft:swamp_hut", 0, "Something in the cauldron is singing to itself. It stops when you come in.",
             radius=12000),
       kill("the_hag", "minecraft:witch", 1, 1)],
      threats=[threat("minecraft:witch", "The Bog Hag", stage=1, hp=90, spare=True, structure="minecraft:swamp_hut",
                      search=12000, smin=2, smax=8, effects=fx(("minecraft:resistance", 0)), talk=HAG_TALK)],
      choice={"attack": {"label": "She's not leaving", "traits": ["hag_rb_hag_killed"]},
              "spare": {"label": "Let her go somewhere quieter", "line": "She packs a sack, three jars and a cat, and stomps "
                                                                         "off into the fog muttering about frogs.",
                        "traits": ["hag_rb_hag_spared"]}},
      rewards={"xp": 900},
      on_kill=flow("rb4_the_moonrise_chorus", delay=200), on_spare=flow("rb4_the_moonrise_chorus", delay=200))

quest("rb4_the_moonrise_chorus", "The Moonrise Chorus",
      "You have the Golden Maraca. Take it home to Grandmother Pondwhistle, and stay for the music.",
      [talk("the_chorus", 0, "The band strikes up the moment she lifts the maraca: flutes, bongos, guitars, a bass made of "
                             "a barrel. Then she puts it in your hands. 'It's yours. We've a spare. Keep the beat for us "
                             "out there, wherever you go.'", entity=RIBBIT, name=GRAN)],
      rewards={"xp": 1500, "items": {"half_a_god:golden_maraca": 1, "minecraft:emerald": 12}},
      traits={"complete": ["hag_rb_chorus"]})

npc_lines.GREET[("rb1_a_song_gone_quiet", GRAN)] = "Oh. A visitor. Mind the lily pads, dear, they're older than you are."
npc_lines.GREET[("rb2_reed_shell_and_hide", BUFO)] = "If you've come for a song, there isn't one. Ribbit."
npc_lines.GREET[("rb4_the_moonrise_chorus", GRAN)] = "Is that...? Oh. Oh, you darling thing. Bufo! BUFO!"

# rumours, wherever there are villagers
for rid, req, line, forbid in (
        ("ribbits_quiet", None, "Went through the frog village in the swamp. No music. Never known it quiet. Gave me the "
                                "creeps, honestly.", "hag_rb1_a_song_gone_quiet_done"),
        ("ribbits_chorus", "hag_rb_chorus", "The frogs in the swamp are playing again. You can hear them three hills "
                                            "away. Somebody's got their rattle back.", None)):
    d = {"id": f"{NS}:{rid}", "weight": 8, "line": line}
    cond = {}
    if req:
        cond["required_traits"] = [req]
    if forbid:
        cond["forbidden_traits"] = [forbid]
    if cond:
        d["conditions"] = cond
    EXTRA[f"rumors/{rid}.json"] = d

# for the mod
TABLE = {"start": qid("rb1_a_song_gone_quiet"), "structure": VILLAGE}


def mod_table():
    return TABLE


import dialogue
dialogue.LANG.update({
    "item.half_a_god.golden_maraca": "The Golden Maraca",
    "item.half_a_god.golden_maraca.lore1": "The oldest beat in the swamp.",
    "item.half_a_god.golden_maraca.lore2": "Shake it (hold use): you and everyone near you move to the rhythm.",
})
dialogue.LANG["subtitles.half_a_god.golden_maraca"] = "Golden Maraca shakes"
