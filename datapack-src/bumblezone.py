"""The Honey Crown: the Bumblezone arc of the main story (between the Wither and the end of the Aether act).

Starts with a letter the first time you stand within 5 blocks of a bee nest or beehive
(the Bumblezone's own the_bumblezone:teleportation/is_near_beehive advancement).
Bumblezone chapters only start while you are in the Bumblezone dimension.
"""
from lib import *
from routes import say_talk

BZ = "the_bumblezone"
BZ_DIM = [f"{BZ}:the_bumblezone"]
WREN = "Wren the Apiarist"

# Part of the main story: Wren writes right after the Wither falls (Act II -> Act III). The main story waits for
# The Honey Crown before Fireproof (end of the Aether act).
courier("honey_letter", "w8_six_remain", "bz1_a_letter_smelling_of_honey", sender=WREN, lo=1200, hi=2400)

quest("bz1_a_letter_smelling_of_honey", "A Letter Smelling of Honey",
      "Wren, a beekeeper, writes that since the light rose from the Wither's pit her bees have been flying into their nests and never "
      "coming out, and she thinks you can follow them. Go and meet her.",
      [say_talk("meet_wren", 0, WREN, [["You wrote to me?", "You were standing by the nests like you were listening. My bees are vanishing."],
                                        ["Vanishing where?", "Inside. There's a whole world in there, made of honeycomb. My grandmother went once."],
                                        ["How do I get in?", "Throw an ender pearl at a bee nest or a hive. Don't ask me how it works. It just does."]])],
      book=book("A Letter Smelling of Honey", WREN, [
          "You don't know me. I keep bees in the village over the hill, or I did.\n\nThe night the light rose out of the Wither's pit, my bees flew into their nests and didn't come out. Not dead. Just gone.",
          "My grandmother said there's a country inside the comb. A queen. She went once and came back with honey that glowed.\n\nCome and see me. Please.\n\n- Wren"],
          color="yellow"),
      rewards={"xp": 100, "items": {"minecraft:honey_bottle": 2}},
      nxt=flow("bz2_pearls_and_patience"))

quest("bz2_pearls_and_patience", "Pearls and Patience",
      "Wren will give you ender pearls to follow her bees, but first she wants honeycomb to seal her empty hives so new bees "
      "don't vanish too. Shear some bee nests (use a campfire under them so the bees stay calm).",
      [collect("honeycomb", "minecraft:honeycomb", 6, 0),
       say_talk("give_to_wren", 1, WREN, [["Six honeycomb.", "Good. Here, two pearls. One in, and one spare in case you panic."],
                                          ["Anything I should know?", "Never hurt a bee in there. Hit one and the whole hive comes for you."],
                                          ["What am I looking for?", "My bees. And whatever is calling them."]])],
      rewards={"xp": 200, "items": {"minecraft:ender_pearl": 2, "minecraft:honey_bottle": 4}},
      nxt=flow("bz3_into_the_comb", dims=BZ_DIM, retry=400))

quest("bz3_into_the_comb", "Into the Comb",
      "You are inside the Bumblezone, a world of honeycomb, and the air smells of sugar. Wren will want proof. Mine honey crystal "
      "for its shards, and collect pollen puffs from the bees and pollen piles.",
      [collect("crystal_shards", f"{BZ}:honey_crystal_shards", 8, 0, "The shards glow in your hand like Wren's grandmother said."),
       collect("pollen", f"{BZ}:pollen_puff", 4, 1)],
      rewards={"xp": 400, "items": {"minecraft:honey_bottle": 4, f"{BZ}:honey_crystal_shards": 8}},
      nxt=flow("bz4_clogged_channels", dims=BZ_DIM))

quest("bz4_clogged_channels", "Clogged Channels",
      "Slimes have got into the honey channels and the bees can't get through them. Clear them out (slimes only: leave the "
      "bees and honey slimes alone).",
      [kill("slimes", "minecraft:slime", 12, 0, "The channels run gold again. A swarm flies past, humming.")],
      rewards={"xp": 500, "items": {f"{BZ}:bee_bread": 4}},
      nxt=flow("bz5_the_cell_maze", dims=BZ_DIM))

quest("bz5_the_cell_maze", "The Cell Maze",
      "The swarm you freed is flying into a Cell Maze, a labyrinth of wax. Wren's bees might be inside. Find it.",
      [reach("find_the_maze", f"{BZ}:cell_maze", 0, "Wax corridors in every direction, and a sound like a thousand wings.", radius=8000)],
      rewards={"xp": 600, "items": {"minecraft:emerald": 8}},
      nxt=flow("bz6_the_hive_temple", dims=BZ_DIM))

quest("bz6_the_hive_temple", "The Hive Temple",
      "In the maze, Wren's bees were flying one way: toward a Hive Temple. Find it. The bees say a queen lives beyond.",
      [reach("find_the_temple", f"{BZ}:hive_temple", 0, "Royal wax, old carvings of a crowned bee, and a stinger spear on the altar.", radius=8000)],
      rewards={"xp": 700, "items": {"minecraft:experience_bottle": 8}},
      nxt=flow("bz7_roots_that_bite", dims=BZ_DIM))

quest("bz7_roots_that_bite", "Roots That Bite",
      "Rootmin, flower-headed things that spit at anything that walks past, are attacking the Queen's workers in the floral "
      "meadows. Clear a few out of the way.",
      [kill("rootmin", f"{BZ}:rootmin", 3, 0, "The workers come back to the flowers.")],
      rewards={"xp": 800, "items": {"minecraft:golden_carrot": 8}},
      nxt=flow("bz8_audience_with_the_queen", dims=BZ_DIM))

quest("bz8_audience_with_the_queen", "Audience with the Queen",
      "The Bee Queen lives on top of a Throne Pillar. Find her. She doesn't speak, but she trades: hold out an item she wants "
      "and right-click her. Her first trade begins the next chapter.",
      [reach("find_the_throne", f"{BZ}:throne_pillar", 0, "She is enormous, and golden, and she is looking right at you.", radius=12000)],
      rewards={"xp": 900, "items": {"minecraft:honey_block": 4}},
      nxt=flow("bz9_the_queens_desire", dims=BZ_DIM, retry=400))

quest("bz9_the_queens_desire", "The Queen's Desire",
      "You traded with the Queen, and now she has desires for you to fulfil (see 'The Queen's Desire' in your advancements). "
      "Complete any of them and she rewards you with Royal Jelly. Bring one bottle back for Wren. (You keep it.)",
      [collect("royal_jelly", f"{BZ}:royal_jelly_bottle", 1, 0, "Royal Jelly. Wren's grandmother's glowing honey.")],
      requires={"advancements": [f"{BZ}:the_queens_desire/the_beginning"]},
      rewards={"xp": 1500, "items": {f"{BZ}:royal_jelly_bottle": 1, "minecraft:diamond": 2}},
      nxt=flow("bz10_the_honey_crown", retry=400))

quest("bz10_the_honey_crown", "The Honey Crown",
      "Wren's bees are coming home: the Queen was gathering them for her brood, and she let them go for you. Tell Wren.",
      [say_talk("tell_wren", 0, WREN, [["Your bees are coming home.", "I saw! Dozens of them, this morning. What did you do?"],
                                       ["Made friends with a queen.", "...Grandmother would have loved you."],
                                       ["Keep this.", "Royal Jelly? No. You keep it. You earned the Queen. Take my shield instead."]])],
      rewards={"xp": 2000, "items": {f"{BZ}:honey_crystal_shield": 1, "minecraft:emerald": 24}})
