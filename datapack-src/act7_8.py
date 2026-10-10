from lib import *

NM = "netherman"
# v11 -> m1 is attached in build.py (needs tame_ghastly, in the Nether)

# ======================= ACT VII: THE SACRED CITY =======================
quest("m1_follow_the_crimson_bee", "Follow the Crimson Bee",
      "Your Ghastly hums north. Follow it to the Nether Mansion.",
      [reach("find_the_mansion", f"{NM}:mansion_nether", 0, "Gold everywhere. A Welcomer bows at the gate as if you were expected.", radius=30000),
       talk("the_welcomer", 1, "The Welcomer steps aside. 'The quarries are below. Mind the Manipulators. They don't like visitors.'",
            entity=f"{NM}:welcomer", name="The Welcomer")],
      threats=[threat(f"{NM}:welcomer", "The Welcomer", stage=1, smin=2, smax=6, hp=40,
                      target_line="Welcome, pilgrim, to the Sacred City. Have you come to give, or to receive?",
                      talk=[["I'm looking for prisoners from Ashford.", "Workers. We call them workers here. The quarries are below."],
                            ["They're slaves.", "Words. Here, everything is gold or on its way to becoming gold."],
                            ["And you?", "I welcome. I have welcomed for a very long time. Nobody has ever asked me that."]])],
      requires={"advancements": [f"{NM}:tame_ghastly"]},
      rewards={"xp": 1500, "items": {"minecraft:golden_apple": 2}},
      nxt=flow("m2_the_cursed_quarries", dims=NETHER))

quest("m2_the_cursed_quarries", "The Cursed Quarries",
      "Ashford's men are mining gold for a god. Kill the Manipulators who drive them, and they are free.",
      [kill("manipulators", f"{NM}:manipulator", 6, 0, "Chains fall. Among the freed prisoners: Reeve Halden's son.")],
      requires={"advancements": [f"{NM}:enter_mansion"]},
      rewards={"xp": 2500, "items": {"minecraft:enchanted_book": {"count": 1, "enchants": {"minecraft:fire_protection": 4}}}},
      nxt=flow("m3_hold_the_quarry_gate", dims=NETHER))

quest("m3_hold_the_quarry_gate", "Hold the Quarry Gate",
      "The freed prisoners need time to flee to your portal. The cult's piglin wardens are coming.",
      [kill("gold_wardens", "minecraft:piglin_brute", 4, 0),
       kill("gold_warden_captain", "minecraft:piglin_brute", 1, 0, "The last prisoner runs through the portal. Halden will cry when he sees his son.")],
      threats=[threat("minecraft:piglin_brute", "Gold-Warden", count=4, hp=70, smin=8, smax=18,
                      equipment=eq(mainhand=("minecraft:golden_axe", {"minecraft:sharpness": 4}), head="minecraft:golden_helmet")),
               threat("minecraft:piglin_brute", "Gold-Warden Captain", hp=160, smin=8, smax=16,
                      equipment=eq(mainhand=("minecraft:golden_axe", {"minecraft:sharpness": 5}), chest="minecraft:golden_chestplate"),
                      effects=fx(("minecraft:strength", 1), ("minecraft:resistance", 1)))],
      rewards={"xp": 2500, "items": {"minecraft:totem_of_undying": 1}},
      nxt=flow("m4_gilded_hunger", dims=NETHER))

quest("m4_gilded_hunger", "Gilded Hunger",
      "In the Sacred City, the Gilded Golems drink gold and blood. The Doctor and the Blacksmith watch and say nothing.",
      [kill("gilded_golems", f"{NM}:gilded_golem", 3, 0, "The golems fall. Someone in a white coat is watching you from a doorway."),
       talk("the_doctor", 1, "The Doctor sells potions and the Blacksmith repairs without stripping enchantments. Use them.",
            entity=f"{NM}:doctor", name="The Doctor")],
      threats=[threat(f"{NM}:doctor", "The Doctor", stage=1, smin=2, smax=6, hp=40,
                      target_line="You broke three golems. Do you have any idea what they cost?",
                      talk=[["Who are you?", "The Doctor. I mend the faithful. They pay well, and they hurt often."],
                            ["You serve the cult?", "I serve whoever is bleeding. Today, that might be you. My potions are for sale."],
                            ["What's past the golems?", "Gehinnom's guardians. Then the Lair. Buy something before you go. Please."]])],
      rewards={"xp": 2500, "items": {"minecraft:gold_block": 4}},
      nxt=flow("m5_do_not_provoke_gehinnom", dims=NETHER))

quest("m5_do_not_provoke_gehinnom", "Do Not Provoke Gehinnom",
      "The guidebook says 'best not to provoke'. You must.",
      [kill("gehinnom", f"{NM}:guardian", 2, 0)],
      rewards={"xp": 3000, "items": {"minecraft:netherite_scrap": 2}},
      nxt=flow("m6_the_instruction", dims=NETHER))

quest("m6_the_instruction", "The Instruction",
      "Reach Azazel's Lair at the end of the mansion. The barrel inside holds the instructions to summon him.",
      [g("read_instruction", 0, "Hand over your weapon, he will say. Don't.", collect=f"{NM}:note", amount=1)],
      rewards={"xp": 1500, "items": {f"{NM}:note": 1}},
      nxt=flow("m7_hand_over_your_weapon", dims=NETHER))

quest("m7_hand_over_your_weapon", "Hand Over Your Weapon",
      "Kill the Divine Chariot Azazel. Where: Azazel's Lair, the last zone of the Nether Mansion. If you hand him a sword or axe he will let you live, and you will have to come back.",
      [g("the_chariots_staff", 0,
         "'I'm sorry I failed to meet your expectations, my lord...' He was not talking to you. 'God.' You killed him. Or half of him.",
         collect=f"{NM}:manipulator_stick", amount=1, name="Staff of Manipulation (from his barrel)")],
      rewards={"xp": 8000, "items": {f"{NM}:manipulator_stick": 1, "minecraft:diamond": 10,
                                     "minecraft:netherite_upgrade_smithing_template": 1}},
      nxt=flow("m9_half_a_soul", delay=1200, dims=OVER))

quest("m9_half_a_soul", "Half a Soul Cannot Die",
      "A letter arrives the moment you are home.",
      [talk("read_it_to_someone", 0,
            "'The head is dead, so the body wakes. A half-soul cannot die. To end him you must give him what the cult always wanted: his head, on his body, whole. Then kill him while he is whole.'")],
      rewards={"xp": 2000, "items": {"minecraft:enchanted_golden_apple": 1}},
      nxt=flow("z1_caution", dims=OVER))

# ======================= ACT VIII: WHOLE =======================
quest("z1_caution", "Caution",
      "The guidebook's trail: 'Pipes. Compass. Pillar. Head. Puzzle. Maze. Caution. Up. God?' Find the maze.",
      [reach("find_the_maze", f"{NM}:maze", 0, "Shh... Be careful.", radius=30000)],
      rewards={"xp": 2500, "items": {"minecraft:golden_apple": 3}},
      nxt=flow("z2_up", dims=OVER))

quest("z2_up", "Up",
      "Above the maze waits the head altar. 'God?' is carved into the stone.",
      [reach("find_the_altar", f"{NM}:head_altar", 0, radius=30000)],
      requires={"advancements": [f"{NM}:enter_maze"]},
      rewards={"xp": 2500},
      nxt=flow("z3_choir_of_the_whole_god", dims=OVER))

quest("z3_choir_of_the_whole_god", "The Choir of the Whole God",
      "Everything the cult has left comes to the altar, to stop the reunion or to watch it.",
      [kill("vindicators", "minecraft:vindicator", 6, 0),
       kill("evokers", "minecraft:evoker", 3, 0),
       kill("ravager", "minecraft:ravager", 1, 0),
       kill("choirmaster", "minecraft:evoker", 1, 0,
            "Silence. Then the altar begins to breathe. Join head to body, and he will speak.")],
      threats=[threat("minecraft:vindicator", "Choir of the Whole God", count=6, hp=60, structure=f"{NM}:head_altar", origin="quest_target", search=30000,
                      equipment=eq(mainhand=("minecraft:diamond_axe", {"minecraft:sharpness": 3}), chest="minecraft:golden_chestplate"),
                      effects=fx(("minecraft:strength", 0))),
               threat("minecraft:evoker", "Choir Cantor", count=3, hp=70, structure=f"{NM}:head_altar", origin="quest_target", search=30000),
               threat("minecraft:ravager", "The Altar Beast", hp=220, structure=f"{NM}:head_altar", origin="quest_target", search=30000,
                      effects=fx(("minecraft:resistance", 1))),
               threat("minecraft:evoker", "The Choirmaster", hp=250, structure=f"{NM}:head_altar", origin="quest_target", search=30000,
                      effects=fx(("minecraft:strength", 1), ("minecraft:resistance", 0)))],
      rewards={"xp": 5000, "items": {"minecraft:totem_of_undying": 1}},
      nxt=flow("b1_bow_at_my_feet", delay=40))

# ----- BAD ENDING: only completes if you accept the True Azazel's offer (he leaves you the Cult Scroll)
quest("b1_bow_at_my_feet", "Bow at My Feet",
      "'Approach, beast, and bow at my feet.' This quest only completes if you accept his mercy and take his Cult Scroll. If you kill him instead, ignore it: his barrel holds a letter for you.",
      [g("the_scroll", 0, "'You made the right decision.'", collect=f"{NM}:quota", amount=1, name="Cult Scroll")],
      rewards={"xp": 3000, "items": {f"{NM}:quota": 1, f"{NM}:faith_part": 3}},
      traits={"complete": ["hag_sworn"]},
      nxt=flow("b2_tithe_of_gold", delay=2400))

quest("b2_tithe_of_gold", "Tithe of Gold",
      "Gold is faith made solid. Bring the first tithe.",
      [collect("tithe", "minecraft:gold_ingot", 64, 0, "Somewhere far below, a throne that is no longer empty hums.")],
      rewards={"xp": 2000, "items": {f"{NM}:faith_essence": 1}},
      nxt=flow("b3_the_last_preacher", delay=2400, dims=OVER))

quest("b3_the_last_preacher", "The Last Preacher",
      "He is whole now, so she can finally die. His first command: find Maraal.",
      [kill("maraal", "minecraft:wandering_trader", 1, 0)],
      threats=[threat("minecraft:wandering_trader", "Maraal", hp=300, spare=True, smin=6, smax=12,
                      talk=[["He sent me.", "I know. I made you, and you went home to him. I'm sorry."],
                            ["Any last words?", "Kneel well."]])],
      choice={"attack": {"label": "Obey him", "traits": ["maraal_killed_by_hand"]},
              "spare": {"label": "Let her live forever, half-alive", "line": "'That is crueller than the knife. You learned from the best.'", "traits": ["maraal_left"]}},
      rewards={"xp": 4000, "items": {f"{NM}:faith_essence": 2}},
      traits={"complete": ["hag_hand_of_azazel"]})

# ----- GOOD ENDING: a letter is placed in the True Azazel's loot barrel (see quest_book_loot_pools)
quest("g1_maraals_last_letter", "Maraal's Last Letter",
      "He died whole. His last words, in the Old Tongue: 'Maraal, it's my fault...' Go home. Tell them.",
      [talk("tell_the_village", 0, "Every bell in every village rings at once, and nobody rang them.")],
      book=book("Maraal's Last Letter", "Maraal", [
          "If you are reading this, he is dead, all of him, and so am I. My half-life ended with his.",
          "Whether you forgave me or not, you finished what I could not. I am sorry for Ashford. I am sorry for all of it.",
          "It was never your fault. It was mine.\n\nThank you.\n\n- Maraal, once of the Silver Court"], color="yellow"),
      rewards={"xp": 6000, "items": {"minecraft:enchanted_golden_apple": 2, "minecraft:nether_star": 1}},
      traits={"accept": ["hag_heretic"], "complete": ["hag_good_ending"]},
      nxt=flow("g2_ashford_rebuilt", delay=1200, dims=OVER))

quest("g2_ashford_rebuilt", "Ashford Rebuilt",
      "Halden's son wants to rebuild the hall his father lost. Help him finish it.",
      [talk("find_halden", 0),
       place("stone", "minecraft:stone_bricks", 128, 1),
       place("bell", "minecraft:bell", 1, 2, "Halden's son rings it. For the first time in this story, it is only a bell.")],
      rewards={"xp": 3000, "items": {"minecraft:emerald_block": 4, "minecraft:diamond_block": 2}})

EXTRA["quest_book_loot_pools/azazel_human_barrel.json"] = {
    "table": "netherman:chests/azazel_human_barrel", "drop_chance": 1.0,
    "books": [{"quest": qid("g1_maraals_last_letter"), "weight": 1, "guaranteed": True}]}
