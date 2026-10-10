"""30 villager side quests. Villagers offer them at random once the linked main-story quest is done.
Each one branches off a main quest on Mebahel's quest map."""
from lib import *

SIDE = "side"


def side(id, title, summary, after, goals, offer, ret, done, rewards, **kw):
    req = kw.pop("requires", {})
    req.setdefault("quests", []).append(qid(after))
    return quest(id, title, summary, goals, folder=SIDE, requires=req, intro=offer[0][1] if offer else None,
                 offer=offer, ret=ret, done=done, rewards=rewards, **kw)


# ---------------- Prologue branches ----------------
side("sq_lost_lambs", "Lost Lambs", "Tam's flock scattered the night he went missing. Bring three sheep back to the pen.",
     "p3_the_shepherd", [bring("sheep", "minecraft:sheep", 3, 0, "Tam counts them twice and laughs for the first time in a week.")],
     [["Lost sheep?", "Three of Tam's best. They ran when the man with the golden smile came."], ["I'll bring them.", "Lead them close. Sheep only trust a full belly."]],
     "Any luck with the sheep?", "Tam will sleep tonight. Thank you.", {"xp": 150, "items": {"minecraft:white_wool": 16, "minecraft:emerald": 4}})

side("sq_bells_tongue", "The Bell's Tongue", "The new bell needs a proper clapper. The bellmaker wants copper.",
     "p1_the_bell", [collect("copper", "minecraft:copper_ingot", 12, 0)],
     [["What's wrong with the bell?", "Nothing, if you like the sound of a spoon on a pot."], ["Copper?", "Twelve ingots. A good tongue rings true."]],
     "Copper yet?", "Listen to that. Now it sounds like a warning.", {"xp": 150, "items": {"minecraft:emerald": 6}})

side("sq_night_watch", "Night Watch", "The new watchtower saw torches on the ridge. A raid is coming. Hold the village.",
     "p5_raise_the_watch", [defend_raid("hold_the_village", 0, "The torches go out one by one.")],
     [["Torches?", "On the ridge. Too many for traders."], ["I'll stand watch.", "Then the tower was worth building."]],
     "They're still coming!", "The tower saw them first. That's all a tower is for.",
     {"xp": 400, "items": {"minecraft:iron_sword": {"count": 1, "enchants": {"minecraft:sharpness": 2}}}})

side("sq_market_road", "Safe to Market", "A trader wants an escort to the next village. The road is no safer than it was.",
     "p6_market_road", [escort("walk_with_the_trader", 0, "The trader exhales for the first time since leaving.")],
     [["You need an escort?", "I need a sword between me and that road."], ["Lead on.", "Stay close. If I scream, run toward it."]],
     "Are we there yet?", "Here. Take your share of the profit.", {"xp": 300, "items": {"minecraft:emerald": 12}})

side("sq_gold_collectors", "The Gold Collectors", "Men who bought the Peddler's 'protection' are collecting payment. In blood if not gold.",
     "p7_gold_at_double_weight", [kill("collectors", "minecraft:vindicator", 3, 0)],
     [["Collectors?", "They say the Peddler sold us safety. Now they want the price."], ["Where?", "They come at dusk. Three of them."]],
     "Are they gone?", "No more payments. No more eyes on the door.",
     {"xp": 350, "items": {"minecraft:gold_ingot": 6}},
     threats=[threat("minecraft:vindicator", "Gold Collector", count=3, hp=44, smin=8, smax=18, equipment=eq(mainhand="minecraft:golden_axe"))])

# ---------------- Act I branches ----------------
side("sq_ashford_orphans", "Ashford's Orphans", "Children who lost their parents in the raid need food and warm clothes.",
     "a2_what_was_left", [collect("bread", "minecraft:bread", 24, 0), collect("wool", "minecraft:white_wool", 12, 1)],
     [["How many?", "Seven. The oldest is nine."], ["What do they need?", "Bread. Something warm. Someone to say it will be all right."]],
     "Have you brought anything?", "You can't fix what happened. But this helps.", {"xp": 300, "items": {"minecraft:golden_apple": 1}})

side("sq_halden_records", "Halden's Records", "Halden wants the outpost that bought Ashford's men burned to the ground.",
     "a3_roof_and_hearth", [reach("find_outpost", "minecraft:pillager_outpost", 0, radius=8000), kill("outpost_pillagers", "minecraft:pillager", 8, 1)],
     [["Which outpost?", "The one with gold under its banner. Nearest one, I'd wager."], ["Burned?", "Emptied, then. I'm not a cruel man."]],
     "Is it done?", "Then Ashford's debt is paid in their coin.", {"xp": 450, "items": {"minecraft:diamond": 2}})

side("sq_echoes_in_the_pipes", "Echoes in the Pipes", "A miner swears the cult's pipes are whispering names. Go and listen.",
     "a5_beneath_the_pipes", [reach("find_pipes", "netherman:pipes", 0, "The pipes whisper. One of the names is yours.", radius=30000)],
     [["Whispering?", "Names. Old ones. Ours."], ["I'll go.", "Don't answer them."]],
     "What did you hear?", "...Your name? Then they know you too.", {"xp": 400, "items": {"minecraft:ender_pearl": 4}})

side("sq_ravager_hunt", "Ravager Hunt", "Freed ravagers from the Bastille are trampling the fields.",
     "a8_ravagers_horn", [kill("ravagers", "minecraft:ravager", 3, 0)],
     [["Loose ravagers?", "Three. They flattened the wheat and the fence and Old Pell."], ["Is Pell all right?", "He's flat. But alive."]],
     "Are they down?", "Pell says thank you. Slowly.", {"xp": 500, "items": {"minecraft:saddle": 1, "minecraft:emerald": 10}})

side("sq_bastille_survivors", "Bastille Survivors", "A freed prisoner wants to go home to her own village.",
     "a9_open_the_cells", [escort("take_her_home", 0, "She runs the last stretch. Someone in the village screams her name.")],
     [["Where is home?", "The next village over. If it's still there."], ["I'll take you.", "I won't slow you down. I promise."]],
     "Is it much further?", "I'm home. I'm actually home.", {"xp": 400, "items": {"minecraft:golden_carrot": 12, "minecraft:emerald": 8}})

# ---------------- Act II branches ----------------
side("sq_quartz_for_the_cleric", "Quartz for the Cleric", "The cleric wants Nether quartz to make wards against the Wither's curse.",
     "w1_the_skull_market", [enter_nether("go_below", 0), collect("quartz", "minecraft:quartz", 32, 1)],
     [["Wards?", "Quartz holds a prayer better than glass."], ["How much?", "Thirty-two pieces. Prayers are heavy."]],
     "The quartz?", "Now the chapel will hold.", {"xp": 400, "items": {"minecraft:experience_bottle": 8}})

side("sq_blaze_bounty", "Blaze Bounty", "The smith needs blaze rods to forge Smite-steel for the whole village guard.",
     "w3_the_skull_harvest", [kill("blazes", "minecraft:blaze", 12, 0), collect("rods", "minecraft:blaze_rod", 8, 1)],
     [["For the guard?", "If the dead come, I want every spear to hate them."], ["Rods it is.", "Eight. Bring more if you like burning."]],
     "Rods?", "The forge has never been so hot.", {"xp": 500, "items": {"minecraft:enchanted_book": {"count": 1, "enchants": {"minecraft:smite": 4}}}})

side("sq_beacon_keeper", "The Beacon Keeper", "The village wants to raise the beacon's power. Iron, and lots of it.",
     "w7_a_light_they_cannot_dim", [collect("iron_blocks", "minecraft:iron_block", 9, 0)],
     [["More power?", "The light reaches further. They say the cult hates the light."], ["Iron blocks?", "Nine. The base must be wide."]],
     "The iron?", "Look how far it shines now.", {"xp": 450, "items": {"minecraft:emerald": 16}})

# ---------------- Act III (Aether) branches ----------------
side("sq_sky_amber", "Sky Amber", "The cleric heard of golden amber from the sky trees. She wants it for her medicines.",
     "h2_hostile_paradise", [collect("amber", "aether:golden_amber", 12, 0)],
     [["Sky trees?", "Golden oaks above the clouds. Their sap heals."], ["Twelve pieces?", "Twelve. And don't eat any."]],
     "Any amber?", "It smells like summer.", {"xp": 400, "items": {"minecraft:golden_apple": 2}})

side("sq_swet_trouble", "Swet Trouble", "Swets are sticking to the Aether's wildlife. Clear them.",
     "h4_cockatrice_nights", [kill("blue_swets", "aether:blue_swet", 6, 0), kill("golden_swets", "aether:golden_swet", 6, 0)],
     [["Swets?", "Jelly that eats. You'll know them."], ["Clear them how?", "Repeatedly."]],
     "Clean sky?", "I don't want to know what you're covered in.", {"xp": 450, "items": {"aether:ambrosium_shard": 16}})

side("sq_ambrosium_lamps", "Ambrosium Lamps", "The village wants lamps that burn with heaven's light.",
     "h5_the_bronze_halls", [collect("shards", "aether:ambrosium_shard", 32, 0)],
     [["Heaven's light?", "They say it keeps the dead away."], ["Thirty-two?", "One lamp per house."]],
     "The shards?", "Every house is lit.", {"xp": 450, "items": {"minecraft:lantern": 8, "minecraft:emerald": 10}})

# ---------------- Act IV branches ----------------
side("sq_ender_hunt", "Ender Hunt", "The librarian wants pearls to study the End's 'silence'.",
     "e3_the_road_underground", [kill("endermen", "minecraft:enderman", 20, 0)],
     [["Silence?", "The journal calls the End's Nail 'Silence'. I want to know why."], ["Twenty endermen?", "Pearls go missing. Bring a few extra."]],
     "Pearls?", "Fascinating. They hum.", {"xp": 500, "items": {"minecraft:ender_pearl": 8}})

side("sq_copy_the_journal", "Copy the Journal", "The librarian wants to make copies of the Warden Journal before something happens to it.",
     "e4_the_warden_journal", [collect("books", "minecraft:book", 16, 0), collect("feathers", "minecraft:feather", 16, 1)],
     [["Copies?", "One original is one fire away from none."], ["What do you need?", "Books and quills. Lots."]],
     "Supplies?", "Three copies by morning. One for you.", {"xp": 400, "items": {"minecraft:enchanted_book": {"count": 1, "enchants": {"minecraft:mending": 1}}}})

side("sq_shulker_shells", "Shulker Shells", "The village builder wants shulker shells for storage that never forgets.",
     "e6_edge_of_the_world", [collect("shells", "minecraft:shulker_shell", 6, 0)],
     [["Never forgets?", "A box that keeps everything. I want three."], ["Six shells.", "Six. Please don't fall off the edge of the world."]],
     "Shells?", "Look, a box inside a box!", {"xp": 600, "items": {"minecraft:diamond": 3}})

# ---------------- Act V branches ----------------
side("sq_frost_patrol", "Frost Patrol", "The snowy village wants the strays that drift down from the hideout cleared.",
     "s2_the_frozen_garrison", [kill("strays", "minecraft:stray", 12, 0)],
     [["Strays?", "Arrows from nowhere. Every night."], ["Twelve of them?", "Twelve at least. They don't stop counting."]],
     "Quiet out there?", "We heard nothing last night. It was wonderful.", {"xp": 500, "items": {"minecraft:emerald": 14}})

side("sq_husk_plague", "Husk Plague", "The desert village is still overrun with husks from the nest.",
     "d1_the_buried_well", [kill("husks", "minecraft:husk", 20, 0)],
     [["Still?", "They keep coming out of the sand."], ["Twenty?", "Start with twenty."]],
     "Done?", "The well is safe again.", {"xp": 550, "items": {"minecraft:golden_apple": 2}})

side("sq_drowned_nets", "Drowned Nets", "The fishing village can't cast a net without drowned climbing up it.",
     "c2_the_tidecaller", [kill("drowned", "minecraft:drowned", 15, 0)],
     [["Climbing the nets?", "Grabbing them. Grabbing us."], ["Fifteen.", "Fifteen less, at least."]],
     "Can we fish?", "Fish for dinner. Real fish!", {"xp": 550, "items": {"minecraft:trident": 1}})

side("sq_pirate_hoard", "The Pirate Hoard", "The harbourmaster says the black ship's surviving crew are picking over shipwrecks for its lost cargo. Find a shipwreck and stop them.",
     "c3_the_black_ship", [reach("find_a_wreck", "minecraft:shipwreck", 0, "Fresh footprints in the sand, and a crate already pried open.", radius=8000),
                           kill("wreck_looters", "minecraft:drowned", 6, 1, "The last looter's pockets hold a gold coin stamped with an eye.")],
     [["Still more pirates?", "What's left of the crew. They're looting every wreck on the coast for the Kraken's cargo."], ["Where?", "Any wreck along the shore. Follow the gulls."]],
     "The wrecks?", "Fewer looters on the water. Good.", {"xp": 700, "items": {"minecraft:diamond": 3}},
     threats=[threat("minecraft:drowned", "Wreck Looter", stage=1, count=6, hp=36, structure="minecraft:shipwreck", search=8000,
                     equipment=eq(mainhand="minecraft:iron_sword", head="minecraft:leather_helmet"))])

side("sq_soul_lanterns", "Soul Lanterns", "Villagers want soul lanterns hung for the dead the Underworld Knight guarded.",
     "u1_the_soul_guard", [collect("lanterns", "minecraft:soul_lantern", 12, 0)],
     [["For his dead?", "Someone should light the way for them."], ["Twelve lanterns.", "One for every soul he kept."]],
     "The lanterns?", "They burn blue. Like they're listening.", {"xp": 500, "items": {"minecraft:experience_bottle": 12}})

side("sq_ash_cleanup", "Ash Cleanup", "The Dragon Tower's spawners still pour out Ashlord's skeleton guards, and they wander down to burn the farms. Go back to the tower and thin them out.",
     "f1_the_last_road", [kill("ash_guards", "block_factorys_bosses:dragon_guard_sword", 4, 0)],
     [["Still roaming?", "Burning crops. Burning goats. They come from the tower, the cages inside it keep making more."], ["Four guards.", "Four fewer fires."]],
     "Smoke's gone?", "The plains are green again.", {"xp": 700, "items": {"minecraft:diamond": 3}})

# ---------------- Act VI branches ----------------
side("sq_blood_tithe", "The Blood Tithe", "The cleric wants to study the cult's blood so she can cleanse the wells.",
     "v1_blood_in_the_wells", [collect("blood", "sons_of_sins:bottle_of_blood", 3, 0)],
     [["Study it?", "If I know what it is, I can boil it out."], ["Three bottles.", "Corked. Please."]],
     "The blood?", "...It's warm. It shouldn't be warm.", {"xp": 600, "items": {"minecraft:golden_apple": 2}})

side("sq_grub_hunt", "Guzzler Hunt", "Betrayed Guzzlers, bloated things the Sins left behind, crawl out at night and drink the livestock dry. Hunt them down.",
     "v4_original_sin", [kill("guzzlers", "sons_of_sins:betrayed_guzzler", 4, 0)],
     [["Guzzlers?", "Fat, pale, always thirsty. They come out of the dark anywhere in the Overworld."], ["Four?", "Four we've seen. Kill them and the cows stop dying."]],
     "Gone?", "The wheat will grow now.", {"xp": 650, "items": {"minecraft:emerald": 16}})

side("sq_midwifes_debt", "The Midwife's Debt", "The Midwife you spared sends word: she wants to undo what she poured. She needs ash.",
     "v3_the_midwife", [collect("ashes", "sons_of_sins:ether_ashes", 6, 0)],
     [["She wants to help?", "She says none of them ever spoke. She says she's sorry."], ["Ashes?", "Six. To sweeten the wells she soured. Kill something standing in blood with a bone blade and it leaves ash."]],
     "The ashes?", "She cried when I gave her your answer.", {"xp": 700, "items": {"minecraft:totem_of_undying": 1}},
     requires={"traits": ["midwife_spared"]})

# ---------------- Act VII / VIII branches ----------------
side("sq_pay_the_freed", "Pay the Freed", "The men freed from the Quarries mined gold for a god. They should be paid for it.",
     "m2_the_cursed_quarries", [collect("gold", "minecraft:gold_ingot", 32, 0)],
     [["Pay them?", "They mined a fortune. They came home with nothing."], ["Thirty-two gold.", "A start."]],
     "The gold?", "For the first time, the gold is theirs.", {"xp": 800, "items": {"minecraft:emerald_block": 2}})

side("sq_crimson_honey", "Crimson Honey", "The cleric wants Ghastly honey. She thinks it might heal the cursed.",
     "v11_the_crimson_guide", [collect("honey", "netherman:crimson_honey_bottle", 4, 0)],
     [["Heal the cursed?", "The bees serve the cult. Maybe their honey can unserve it."], ["Four bottles.", "Careful. The bees are protective."]],
     "Honey?", "Sweet. Bitter. Like penance.", {"xp": 800, "items": {"minecraft:enchanted_golden_apple": 1}})

side("sq_the_last_raid", "The Last Raid on Ashford", "The cult's last raiders want to burn Ashford before the end. Hold it one more time.",
     "z1_caution", [defend_raid("hold_ashford", 0, "Ashford stands. It always will now.")],
     [["One more time?", "They know the end is coming. They want us to burn first."], ["Not while I'm here.", "That's what Halden said you'd say."]],
     "They're at the gate!", "Ashford stands.", {"xp": 1500, "items": {"minecraft:diamond_block": 1}})
