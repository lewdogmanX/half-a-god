"""The Golden Legion: a short optional questline for Legionary, separate from the main story.

Part of the main story: a letter arrives after The Skull Market (Act II).
"""
from lib import *
from routes import say_talk

LG = "legionary"
AUREL = "Old Aurel"

# Part of the main story (Act II): Aurel writes once you start hunting skulls in the Nether. The main story waits
# for The Unbreakable Head before Six Remain.
courier("golden_legion", "w1_the_skull_market", "lg1_the_golden_legion", sender=AUREL, lo=1200, hi=2400)

quest("lg1_the_golden_legion", "The Golden Legion",
      "Old Aurel, a retired soldier of the Ashford Watch, heard you are hunting in the Nether. He fought piglins in his youth and has a story about "
      "a warrior of theirs who could not die. Go and hear it.",
      [say_talk("hear_aurel", 0, AUREL, [["You wanted to see me?", "You've been in the Nether. Then you've seen the bastions, and what piglins worship: gold."],
                                          ["What's the Golden Legion?", "Their army, long ago. Their champion was the Unbreakable. A furnace for a heart."],
                                          ["Is he still alive?", "He sleeps in a golden tomb in an arena in the Nether wastes. Only netherite wakes him."],
                                          ["Why now?", "Because the cult wants his furnace-heart to forge the Wither a crown. Get to him first."],
                                          ["Why tell me?", "Because I tried, forty years ago, and ran. Someone should finish it."]])],
      book=book("The Golden Legion", AUREL, [
          "They tell me you are hunting wither skulls in the Nether. So did I, once, and I found something worse.\n\nCome and see an old soldier. I have a story nobody believes.",
          "- Aurel, late of the Ashford Watch"], color="brown"),
      rewards={"xp": 200, "items": {"minecraft:golden_carrot": 8}},
      nxt=flow("lg2_ghosts_of_the_legion", dims=NETHER, retry=400))

quest("lg2_ghosts_of_the_legion", "Ghosts of the Legion",
      "Aurel says the Legion's last soldiers still guard the bastions: the piglin brutes. Prove you can stand against them "
      "before you face their champion.",
      [reach("find_a_bastion", "minecraft:bastion_remnant", 0, "Gold and blackstone, and the snort of something big.", radius=12000),
       kill("brutes", "minecraft:piglin_brute", 3, 1, "The last brute falls clutching a gold medallion: a golden fist.")],
      rewards={"xp": 800, "items": {"minecraft:gold_block": 2}},
      nxt=flow("lg3_the_unbreakable", dims=NETHER))

quest("lg3_the_unbreakable", "The Unbreakable",
      "Find the Bastion Arena in the Nether wastes (Legionary). The Unbreakable sleeps in its golden tomb. Strike him with a "
      "netherite pickaxe to wake him, then break him.",
      [reach("find_the_arena", f"{LG}:bastion_arena", 0, "Golden chains, golden lanterns, and a tomb that breathes.", radius=20000),
       kill("the_unbreakable", f"{LG}:unbreakable", 1, 1, "The furnace in his chest goes out. He was breakable after all.")],
      rewards={"xp": 3000, "items": {"minecraft:netherite_scrap": 2}},
      nxt=flow("lg4_the_thermal_heart", retry=400))

quest("lg4_the_thermal_heart", "The Thermal Heart",
      "The Unbreakable always drops his heart, a Thermal Stone. Bring it home and show Aurel. (You keep it.)",
      [collect("thermal_stone", f"{LG}:thermal_stone", 1, 0, "Still warm."),
       say_talk("show_aurel", 1, AUREL, [["His heart.", "...Forty years I've had nightmares about that thing. Thank you."],
                                          ["What now?", "Piglins say his head turns up in the next bastion treasure opened after he dies."]])],
      rewards={"xp": 1200, "items": {f"{LG}:thermal_stone": 1, "minecraft:emerald": 16}},
      nxt=flow("lg5_the_unbreakable_head", retry=400))

quest("lg5_the_unbreakable_head", "The Unbreakable Head",
      "Aurel says the first bastion treasure chest opened after the Unbreakable's death holds his head. Find it: it will be the "
      "proudest trophy in any hall. (You keep it.)",
      [collect("the_head", f"{LG}:unbreakable_head", 1, 0, "A golden helm with a dead furnace behind the visor.")],
      rewards={"xp": 2000, "items": {f"{LG}:unbreakable_head": 1, "minecraft:enchanted_golden_apple": 1, "minecraft:diamond": 4}})
