from lib import *

BFB = "block_factorys_bosses"

# ======================= ACT V: THE FIVE WHO ROSE (courier after E7) =======================
courier("letter_five_beasts", "e7_the_nether_is_singing", "s1_cold_hearths", lo=600, hi=1200)
# ---- Snow: the Nail of Frost
quest("s1_cold_hearths", "Cold Hearths",
      "A snowy village is freezing. Its soldiers marched into the storm a century ago and still patrol it.",
      [collect("coal", "minecraft:coal", 64, 0),
       collect("logs", "minecraft:spruce_log", 48, 1),
       talk("warm_the_village", 2, "'Our grandfathers guarded something out there. They never told us what.'")],
      book=book("Five More Beasts", "Old Mara", [
          "Five more stir: one in the snow, one under the sand, one in the deep sea, one in the burning deep, and one in a tower of fire.",
          "Start with the snow. Help the people there first. They have earned it.\n\n- M."], color="blue"),
      rewards={"xp": 900, "items": {"minecraft:leather_chestplate": {"count": 1, "enchants": {"minecraft:protection": 3}}}},
      nxt=flow("s2_the_frozen_garrison"))

quest("s2_the_frozen_garrison", "The Frozen Garrison",
      "Find the Yeti Hideout in the snowy plains and lay the old garrison to rest.",
      [reach("find_the_hideout", f"{BFB}:yeti_hideout", 0, radius=30000),
       kill("frozen_garrison", "minecraft:stray", 8, 1, "The last of the garrison lowers its bow, as if relieved."),
       kill("skor", f"{BFB}:yeti", 1, 1, "The third Warden falls. No snow falls anywhere in the world for a day.", threats_only=False),
       chest_book("garrison_roll", 2, f"{BFB}:yeti_hideout", "Garrison Roll", "hag_garrison_roll", "Garrison Roll", "Sergeant Halm",
                  ["We guard the Nail of Frost. We do not leave our post.",
                   "If the beast wakes, the Nail goes with it. Do not wake the beast.\n\nWhoever reads this: please, do not wake the beast."],
                  "They were not guarding the village from the beast. They were guarding the beast.", radius=30000)],
      threats=[threat("minecraft:stray", "Frozen Garrison", stage=1, count=8, hp=40, structure=f"{BFB}:yeti_hideout", search=30000,
                      equipment=eq(mainhand=("minecraft:bow", {"minecraft:power": 2}), head="minecraft:iron_helmet"))],
      rewards={"xp": 1200, "items": {"minecraft:enchanted_book": {"count": 1, "enchants": {"minecraft:frost_walker": 2}}}},
      nxt=flow("s3_lost_in_the_snow"))

quest("s3_lost_in_the_snow", "Lost in the Snow",
      "Kill Skor, the Yeti. Where: the Yeti Hideout, snowy plains.",
      [talk("tell_the_snow_village", 0, "'No snow. Not one flake. Grandfather said that would happen if the beast ever died.'", name="Village Elder")],
      rewards={"xp": 3000, "items": {"minecraft:diamond": 5}},
      nxt=flow("d1_the_buried_well", dims=OVER))

# ---- Desert: the Nail of Sand
quest("d1_the_buried_well", "The Buried Well",
      "A desert village filled in its own well 'so the ground would stop breathing'. Their sick need help.",
      [collect("milk", "minecraft:milk_bucket", 6, 0),
       kill("husks", "minecraft:husk", 12, 1),
       talk("tend_the_sick", 2, "The sick have poison wounds no husk could give.")],
      requires={"advancements": [f"{BFB}:kill_yeti"]},
      rewards={"xp": 900, "items": {"minecraft:golden_apple": 2}},
      nxt=flow("d2_the_nest_road"))

quest("d2_the_nest_road", "The Nest Road",
      "Follow the herded husks toward the Sandworm Nest. The Dune Faithful guard the way.",
      [kill("dune_faithful", "minecraft:husk", 5, 0),
       reach("find_the_nest", f"{BFB}:sandworm_nest", 1, "The ground opened like a mouth that had waited.", radius=30000),
       kill("sirok", f"{BFB}:sandworm", 1, 2, "The fourth Warden falls.", threats_only=False)],
      threats=[threat("minecraft:husk", "Dune Faithful", count=5, hp=50, effects=fx(("minecraft:strength", 0), ("minecraft:speed", 0)),
                      equipment=eq(mainhand=("minecraft:iron_sword", {"minecraft:fire_aspect": 1})))],
      rewards={"xp": 1100, "items": {"minecraft:ender_pearl": 6}},
      nxt=flow("d3_the_beast_below"))

quest("d3_the_beast_below", "The Beast Below",
      "Kill Sirok, the Sandworm. Where: the Sandworm Nest, desert.",
      [talk("tell_the_desert_village", 0, "The desert village digs its well back out. The water comes up clean.", name="Village Elder")],
      rewards={"xp": 3000, "items": {"minecraft:diamond": 5}},
      nxt=flow("c1_nets_and_knives", dims=OVER))

# ---- Coast: the Nail of Tide
quest("c1_nets_and_knives", "Nets and Knives",
      "A fishing village won't sail past the black ship. Help them rebuild their dock.",
      [collect("cod", "minecraft:cod", 32, 0),
       collect("salmon", "minecraft:salmon", 16, 1),
       place("dock", "minecraft:oak_fence", 24, 2, "'There's a ship out there that never moves. Something under it does.'")],
      requires={"advancements": [f"{BFB}:kill_sandworm"]},
      rewards={"xp": 900, "items": {"minecraft:fishing_rod": {"count": 1, "enchants": {"minecraft:luck_of_the_sea": 3}}}},
      nxt=flow("c2_the_tidecaller"))

quest("c2_the_tidecaller", "The Tidecaller",
      "Drowned are coming up the beach at night, led by something that calls the tide.",
      [wait("wait_for_night", 0, "next_night"),
       kill("drowned", "minecraft:drowned", 8, 1),
       kill("tidecaller", "minecraft:drowned", 1, 1, "Her pockets are full of cult gold.")],
      threats=[threat("minecraft:drowned", "Drowned Raider", stage=1, count=8, hp=40, allow_water_spawn=True, smin=8, smax=20),
               threat("minecraft:drowned", "The Tidecaller", stage=1, hp=120, allow_water_spawn=True, smin=8, smax=18,
                      equipment=eq(mainhand=("minecraft:trident", {"minecraft:impaling": 3})), effects=fx(("minecraft:strength", 1)))],
      rewards={"xp": 1100, "items": {"minecraft:crossbow": {"count": 1, "enchants": {"minecraft:multishot": 1}}}},
      nxt=flow("c3_the_black_ship"))

quest("c3_the_black_ship", "The Black Ship",
      "Board the Kraken Ship in the deep ocean and kill its crew and captain.",
      [reach("find_the_ship", f"{BFB}:kraken_ship", 0, radius=30000, rng=64),
       kill("crossbow_pirates", f"{BFB}:crossbow_pirate", 1, 1),
       kill("pirate_rooks", f"{BFB}:pirate_rook", 1, 1),
       kill("pirate_captain", f"{BFB}:pirate_captain", 1, 1, "The captain's log: 'Cargo for the Eye. One Nail's worth.' Then the sky goes black and the sea starts to rise."),
       kill("nerakyss", f"{BFB}:kraken", 1, 2, "The fifth Warden falls. The sea goes flat as glass for a whole day.")],
      rewards={"xp": 1600, "items": {"minecraft:enchanted_book": {"count": 1, "enchants": {"minecraft:respiration": 3}}}},
      nxt=flow("c4_terror_of_the_depths"))

quest("c4_terror_of_the_depths", "Terror of the Depths",
      "Kill Nerakyss, the Kraken. Where: the Kraken Ship, deep ocean.",
      [talk("tell_the_harbour", 0, "'The black ship's sinking. Lads are rowing out to watch. First time anyone's dared in forty years.'", name="Harbourmaster")],
      rewards={"xp": 3200, "items": {"minecraft:diamond": 6}},
      nxt=flow("u1_the_soul_guard", dims=NETHER))

# ---- Underworld (Nether): the Nail of the Gate
quest("u1_the_soul_guard", "The Soul Guard",
      "In the Nether's nether wastes or soul sand valleys stands the Underworld Arena. Clear its guard. Their bones carry the arena key.",
      [reach("find_the_arena", f"{BFB}:underworld_arena", 0, radius=20000),
       kill("soul_skeletons", f"{BFB}:soul_skeleton", 10, 1),
       kill("wither_knights", f"{BFB}:soul_knight_wither_skeleton", 3, 1),
       kill("helvar", f"{BFB}:underworld_knight", 1, 1, "The sixth Warden falls. Somewhere far below, a door unlocks.")],
      requires={"advancements": [f"{BFB}:kill_kraken"]},
      rewards={"xp": 1600, "items": {"minecraft:enchanted_book": {"count": 1, "enchants": {"minecraft:protection": 4}}}},
      nxt=flow("u2_helvars_oath", dims=NETHER))

quest("u2_helvars_oath", "Helvar's Oath",
      "The knight in the arena cannot speak. He wrote it down instead.",
      [chest_book("oath", 0, f"{BFB}:underworld_arena", "Helvar's Oath", "hag_helvars_oath", "Helvar's Oath", "Helvar",
                  ["I hold the Gate. I was set here by the one who took his head.",
                   "If I fall, the door I kept shut will open.\n\nIf you are reading this, you have killed five of us already.\n\nStop."],
                  "Five of us already. What does he mean, 'us'?", radius=20000)],
      rewards={"xp": 800, "items": {"minecraft:golden_apple": 3}},
      nxt=flow("u3_back_into_hell", dims=NETHER))

quest("u3_back_into_hell", "Back Into Hell",
      "Kill Helvar, the Underworld Knight. Where: the Underworld Arena, Nether.",
      [leave_nether("climb_out", 0, "Daylight. You did not realise how much you had missed it.")],
      rewards={"xp": 3500, "items": {"minecraft:netherite_scrap": 2}},
      nxt=flow("f1_the_last_road", dims=OVER))

# ---- Fire: the Nail of Flame
quest("f1_the_last_road", "The Last Road",
      "Mara: 'The last one was his once. His mount. Be careful.' Then, quickly: '...a monster's mount, I mean.'",
      [reach("find_the_tower", f"{BFB}:dragon_tower", 0, radius=30000),
       kill("ash_guards", f"{BFB}:dragon_guard_sword", 3, 1),
       kill("ash_shooters", f"{BFB}:flaming_skeleton_guard_fireball", 3, 1, "Their bones carry Ancient Trial Keys."),
       kill("ashlord", f"{BFB}:infernal_dragon", 1, 1, "The seventh Warden falls. All seven Nails are broken.")],
      requires={"advancements": [f"{BFB}:kill_yeti", f"{BFB}:kill_sandworm", f"{BFB}:kill_kraken", f"{BFB}:kill_underworld_knight"]},
      rewards={"xp": 1800, "items": {"minecraft:golden_apple": 3}},
      nxt=flow("f2_dragon_slayer"))

quest("f2_dragon_slayer", "Dragon Slayer",
      "Kill Ashlord, the Infernal Dragon. Where: the Dragon Tower.",
      [collect("dragon_bones", f"{BFB}:dragon_bone", 8, 0, "The smith turns a bone over in his hands. 'Nothing in the world is this hard. Nothing alive.' (The bones go back in your pack.)")],
      rewards={"xp": 4000, "items": {"minecraft:diamond": 8, f"{BFB}:dragon_bone": 8}},
      nxt=flow("f3_unbound", dims=OVER))

quest("f3_unbound", "Unbound",
      "It is done. All seven. Tell someone.",
      [talk("tell_a_villager", 0, "'Seven beasts dead. You'd think we'd feel safer. Why don't we feel safer?'")],
      requires={"advancements": [f"{BFB}:kill_all_bosses"]},
      rewards={"xp": 1000, "items": {"minecraft:enchanted_golden_apple": 1}})

# ======================= ACT VI: CHILDREN OF SIN (courier after F3) =======================
SOS = "sons_of_sins"
courier("letter_blood_pits", "f3_unbound", "v1_blood_in_the_wells", lo=1200, hi=2400)
quest("v1_blood_in_the_wells", "Blood in the Wells",
      "Mara: 'Rest a night. Then come find me where the blood pits are. I will explain everything.' Before you go, the librarian wants a sample.",
      [talk("deliver_to_the_librarian", 0, "'This blood was poured, not spilled. Someone is feeding the ground.'")],
      book=book("Where the Blood Pits Are", "Old Mara", [
          "It is done. All seven.\n\nNow the cult will try the only road left to it: if it cannot free his body, it will grow him a new one.",
          "They pour blood into pits and throw dead flesh into it. What crawls out, they call sons.\n\nCome. I will explain everything. - M."], color="red"),
      rewards={"xp": 1200, "items": {f"{SOS}:sickle_of_struggle": 1}},
      nxt=flow("v2_the_sinful_church"))

quest("v2_the_sinful_church", "The Sinful Church",
      "Find the Sinful Church and the recipe the cult is following.",
      [reach("find_the_church", f"{SOS}:sinful_church", 0, radius=30000),
       kill("devourers", f"{SOS}:devourer", 2, 1, "Things with too many teeth, born in the church's blood. They never spoke."),
       chest_book("recipe", 2, f"{SOS}:sinful_church", "The Midwife's Recipe", "hag_midwife_recipe", "The Midwife's Recipe", "The Midwife",
                  ["Carcass into blood. Pray.",
                   "If it lives, it is a son.\nIf it speaks, it is Him.\n\nNone have spoken yet. Pour more."],
                  "None have spoken yet.", radius=30000)],
      rewards={"xp": 1200, "items": {"minecraft:experience_bottle": 12}},
      nxt=flow("v3_the_midwife"))

quest("v3_the_midwife", "The Midwife",
      "The woman who runs the blood pits is here. Spare her, or not.",
      [kill("the_midwife", "minecraft:witch", 1, 0)],
      threats=[threat("minecraft:witch", "The Midwife", hp=150, spare=True, structure=f"{SOS}:sinful_church", origin="quest_target", search=30000,
                      effects=fx(("minecraft:resistance", 0)),
                      talk=[["What are you making?", "A body. He needs a body. The old one is sealed, so we grow him a new one."],
                            ["Has it worked?", "Not once. They live. They never speak. They just... eat."],
                            ["Then stop.", "And do what? Pray to an empty throne?"]],
                      attack_ambushes=[{"entity": "minecraft:witch", "name": "Coven Sister", "count": 3, "max_health": 40}])],
      choice={"attack": {"label": "End the blood pits", "traits": ["midwife_slain"]},
              "spare": {"label": "Let her go", "line": "'None of them ever spoke. Remember that.'", "traits": ["midwife_spared"]}},
      rewards={"xp": 1600, "items": {f"{SOS}:ether_ashes": 8}},
      on_kill=flow("v3k_the_coven", delay=12000), on_spare=flow("v3s_cleansing_the_pits"))

quest("v4_original_sin", "Original Sin",
      "To kill the cult's vessels you must wake one yourself: throw a Flesh Carcass into blood. Then kill a Butcher.",
      [kill("guzzlers", f"{SOS}:betrayed_guzzler", 3, 0, "A failed vessel. It wears a cult wreath.")],
      rewards={"xp": 2200, "items": {"minecraft:diamond": 4}},
      nxt=flow("v5_jaws_in_the_dark"))

quest("v5_jaws_in_the_dark", "Jaws in the Dark",
      "Wake and kill a Wistiver. Where: woken by the blood ritual.",
      [wait("wait_for_night", 0, "next_night", "Something wet climbs out of the well."),
       kill("blood_born", "minecraft:zombie", 8, 1, "They were villagers once. The blood kept them moving.")],
      threats=[threat("minecraft:zombie", "Blood-Born", stage=1, count=8, hp=40, smin=10, smax=22,
                      equipment=eq(head="minecraft:leather_helmet"), effects=fx(("minecraft:speed", 0)))],
      rewards={"xp": 2200, "items": {"minecraft:golden_apple": 3}},
      nxt=flow("v6_what_eats_the_dead"))

quest("v6_what_eats_the_dead", "What Eats the Dead",
      "Wake and kill a Devourer. Where: woken by the blood ritual.",
      [collect("ashes", f"{SOS}:ether_ashes", 8, 0, "None of them spoke. None of them were Him. (The ashes go back in your pack.)")],
      rewards={"xp": 2200, "items": {"minecraft:totem_of_undying": 1, f"{SOS}:ether_ashes": 8}},
      nxt=flow("v7_soul_steel"))

quest("v7_soul_steel", "Soul Steel",
      "Forge Soul Steel from what is left of the failures, and set a block of it as proof.",
      [place_anywhere("soul_steel_block", f"{SOS}:soul_steel_block", 1, 0, "Remarkable resistance. You will need it.")],
      rewards={"xp": 1600, "items": {"minecraft:diamond": 4}},
      nxt=flow("v8_sinbuster", retry=400))

quest("v8_sinbuster", "Sinbuster",
      "You wear the Sinbuster now: armour that can 'imprison attacks too brutal for the mortals'. Now you can stand in front of a god.",
      [talk("show_the_smith", 0, "The smith won't touch it. 'That's not armour. That's a cage you wear.'")],
      requires={"advancements": [f"{SOS}:sinbuster_armor"]},
      rewards={"xp": 2000, "items": {"minecraft:enchanted_golden_apple": 1}})

courier("wrong_parcel", "v8_sinbuster", "v9_the_wrong_parcel", sender="Old Mara", lo=1200, hi=2400)
quest("v9_the_wrong_parcel", "The Wrong Parcel",
      "Mara's courier delivered the wrong bundle. Inside is the page torn from the Warden journal.",
      [talk("ask_about_mara", 0, "'Old Mara? She's been old as long as anyone remembers. My grandmother said the same.'")],
      book=book("The Torn Page", "Maraal", [
          "Seven Nails I drove, and seven keepers I bound.\n\nAsh: the Wither. Silence: the Dragon. Frost: Skor. Sand: Sirok. Tide: Nerakyss. The Gate: Helvar. The Flame: Ashlord, who was his.",
          "Kill the keepers and the Nails break, and the halves may find each other.\n\nNever let anyone kill them all.",
          "- Maraal, first Preacher of Azriel, who took his head."], color="purple"),
      rewards={"xp": 1500, "items": {"minecraft:experience_bottle": 8}},
      nxt=flow("v10_maraal", delay=600, dims=OVER))

quest("v10_maraal", "Maraal",
      "She was waiting for you to find the page. Hear her out. Then forgive her, or don't.",
      [kill("maraal", "minecraft:wandering_trader", 1, 0)],
      threats=[threat("minecraft:wandering_trader", "Maraal", hp=300, spare=True, effects=fx(("minecraft:resistance", 1)), smin=6, smax=12,
                      recruit_companion=True,
                      talk=[["You tore the page.", "Yes. I knew you would stop if you read it."],
                            ["You took his head.", "On his own throne. My hand, my blade, my god."],
                            ["Why use me?", "A half-soul cannot die, and I cannot lift a blade to him again. I needed someone who could."],
                            ["The Wardens?", "Seven Nails I drove myself. I sent you to pull them out, one by one. Seven Nails for one champion."],
                            ["Ashford burned.", "Because I needed you angry. I am sorry. I would do it again."],
                            ["And now?", "Now there is only the head, and then the body. Will you finish it with me, or without me?"]])],
      choice={"attack": {"label": "Reject her", "traits": ["maraal_rejected"]},
              "spare": {"label": "Forgive her", "line": "'Then let us go and kill a god.'", "traits": ["maraal_forgiven"]}},
      rewards={"xp": 2500, "items": {"netherman:chance_totem": 1}},
      on_kill=flow("v10r_alone"), on_spare=flow("v10f_walking_with_maraal"))

quest("v11_the_crimson_guide", "The Crimson Guide",
      "Ghastlies, the crimson bees of the Nether, 'can show the way to the Nether Mansion'. Go below and tame one.",
      [enter_nether("go_below", 0, "Tame a Ghastly. The next chapter begins when one follows you.")],
      rewards={"xp": 1200, "items": {"netherman:crimson_honey_bottle": 2}})

# ---------------- Act VI outcomes ----------------
quest("v3s_cleansing_the_pits", "Cleansing the Pits",
      "Spared, the Midwife shows you how to sour her own blood pits: bury them under soul soil and ash.",
      [place_anywhere("soul_soil", "minecraft:soul_soil", 16, 0, "The pit hisses, bubbles, and goes still.")],
      rewards={"xp": 1200, "items": {f"{SOS}:ether_ashes": 8}},
      nxt=flow("v4_original_sin"))

quest("v3k_the_coven", "The Coven",
      "The Midwife's sisters come for you a day later. They want blood for blood.",
      [kill("coven", "minecraft:witch", 4, 0, "Among the coven's things: a recipe. 'If it speaks, it is Him.' None ever spoke.")],
      threats=[threat("minecraft:witch", "Coven Sister", count=4, hp=60, smin=8, smax=18, effects=fx(("minecraft:resistance", 0)))],
      rewards={"xp": 1400, "items": {"minecraft:golden_apple": 3}},
      nxt=flow("v4_original_sin"))

quest("v10f_walking_with_maraal", "Walking with Maraal",
      "You forgave her. She walks beside you now and finally talks about the old days: the Silver Court, the Sun, the fall.",
      [wait("a_night_of_stories", 0, "next_day", "'He was kind, once. That is the worst part.'")],
      rewards={"xp": 1500, "items": {"minecraft:golden_apple": 2}},
      nxt=flow("v11_the_crimson_guide"))

quest("v10r_alone", "Alone",
      "You rejected her. She is gone, and the letters have stopped. You will have to find the Nether Mansion by yourself.",
      [kill("ghasts", "minecraft:ghast", 3, 0, "Without her letters, you learn the Nether the hard way.")],
      rewards={"xp": 1500, "items": {"minecraft:fire_charge": 8}},
      nxt=flow("v11_the_crimson_guide"))
