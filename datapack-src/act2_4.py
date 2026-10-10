from lib import *

# ======================= ACT II: THE WITHERED CROWN (courier after A15) =======================
courier("letter_skulls", "w0_two_roads_meet", "w1_the_skull_market", lo=600, hi=1200)
quest("w1_the_skull_market", "The Skull Market",
      "Mara: 'They are buying wither skulls now. Three make a crown. Find out who is selling.'",
      [enter_nether("go_below", 0),
       reach("find_a_fortress", "minecraft:fortress", 1, radius=12000),
       chest_book("price_list", 2, "minecraft:fortress", "Price List", "hag_price_list", "Price List", "The Skull Market",
                  ["One wither skull: three villagers, or forty gold.",
                   "Buyer: THE CHARIOT. Deliver to the Lair, past the Sacred City.",
                   "Three skulls make a crown. The crown wakes the Nail of Ash."],
                  "The buyer signs himself 'the Chariot'.", radius=12000)],
      book=book("They Are Buying Skulls", "Old Mara", [
          "Three skulls and four blocks of soul sand. That is all it takes to wake the Wither.",
          "If the cult wakes it, they will try to bind it. They will fail, and the world will burn while they fail.\n\nGo into the Nether. Find out who is selling. - M."], color="gray"),
      rewards={"xp": 600, "items": {"minecraft:gold_ingot": 12}},
      nxt=flow("w2_smite_the_dead", dims=OVER))

quest("w2_smite_the_dead", "Smite the Dead",
      "The armourer can forge you a weapon 'that hates the dead', if you bring the makings.",
      [collect("blaze_rods", "minecraft:blaze_rod", 12, 0),
       collect("bones", "minecraft:bone", 32, 1),
       talk("visit_the_armourer", 2, "'Steel hates the dead if you teach it to.'")],
      rewards={"xp": 500, "items": {"minecraft:enchanted_book": {"count": 1, "enchants": {"minecraft:smite": 5}}}},
      nxt=flow("w3_the_skull_harvest", dims=NETHER))

quest("w3_the_skull_harvest", "The Skull Harvest",
      "Every wither skeleton you drop is one the cult cannot harvest. Thin their numbers.",
      [kill("wither_skeletons", "minecraft:wither_skeleton", 25, 0),
       kill("blazes", "minecraft:blaze", 10, 0, "The fortress goes quiet. Then it starts screaming.")],
      rewards={"xp": 900, "items": {"minecraft:netherite_scrap": 1, "minecraft:diamond": 3}},
      nxt=flow("w4_hold_the_black_bridge", dims=NETHER))

quest("w4_hold_the_black_bridge", "Hold the Black Bridge",
      "The cult sends its Bone Choir to take back the fortress. Hold the bridge.",
      [kill("bone_choir", "minecraft:wither_skeleton", 4, 0),
       kill("choir_master", "minecraft:wither_skeleton", 1, 0, "The Choir-Master's skull is cracked down the middle. Useless to them.")],
      threats=[threat("minecraft:wither_skeleton", "Bone Chorister", count=4, hp=80, smin=8, smax=18,
                      equipment=eq(mainhand=("minecraft:stone_sword", {"minecraft:sharpness": 3})), effects=fx(("minecraft:strength", 0))),
               threat("minecraft:wither_skeleton", "The Choir-Master", hp=160, smin=10, smax=18,
                      equipment=eq(mainhand=("minecraft:netherite_sword", {"minecraft:sharpness": 2}), head="minecraft:golden_helmet"),
                      effects=fx(("minecraft:strength", 1), ("minecraft:resistance", 1)))],
      rewards={"xp": 1000, "items": {"minecraft:totem_of_undying": 1}},
      nxt=flow("w5_a_grave_for_a_nail", dims=OVER))

quest("w5_a_grave_for_a_nail", "A Grave for a Nail",
      "Mara: 'Far from any village. Deep. It will try to break out.' Build the summoning and wake the Wither yourself.",
      [place_anywhere("soul_sand", "minecraft:soul_sand", 4, 0),
       place_anywhere("skulls", "minecraft:wither_skeleton_skull", 3, 1, "The ground shakes. Something old opens its eyes."),
       kill("the_wither", "minecraft:wither", 1, 2, "The first Warden falls. Far below, the Nether sky turns red.", threats_only=False)],
      rewards={"xp": 1500, "items": {"minecraft:golden_apple": 3, "minecraft:diamond": 4}},
      nxt=flow("w6_the_withered_crown"))

quest("w6_the_withered_crown", "The Withered Crown",
      "Kill the Wither before it reaches daylight. Where: the pit you built.",
      [collect("nether_star", "minecraft:nether_star", 1, 0, "The star is still warm. (It goes back in your pack. You will need it.)")],
      rewards={"xp": 600, "items": {"minecraft:nether_star": 1}},
      nxt=flow("w7_a_light_they_cannot_dim"))

quest("w7_a_light_they_cannot_dim", "A Light They Cannot Dim",
      "Turn the Wither's star into a beacon over your village.",
      [place_anywhere("beacon", "minecraft:beacon", 1, 0, "Villagers say it is the first good night's sleep in weeks.")],
      rewards={"xp": 600, "items": {"minecraft:emerald": 20}},
      nxt=flow("w8_six_remain"))

quest("w8_six_remain", "Six Remain",
      "A strange note in Mara's last letter. Show it to someone.",
      [talk("show_the_letter", 0, "The villager reads: 'The first is done. Six remain... I mean, the hard part remains. Forgive an old woman's pen.' 'Six what?' they ask.")],
      rewards={"xp": 300, "items": {"minecraft:experience_bottle": 8}})

# ======================= ACT III: THE FALLEN SKY - THE AETHER (courier after W8) =======================
courier("letter_light_above", "w8_six_remain", "h1_a_light_above_the_pit", lo=600, hi=1200)
quest("h1_a_light_above_the_pit", "A Light Above the Pit",
      "Villagers saw a beam of light rise from the Wither's pit into the clouds. Mara says to ignore it. You won't.",
      [place_anywhere("glowstone_frame", "minecraft:glowstone", 10, 0,
                      "Glowstone and water. The cleric's grandmother called it the door to heaven.")],
      book=book("Leave the Sky Alone", "Old Mara", [
          "I hear the villagers saw a light go up from your pit.\n\nIgnore it. There is nothing above the clouds but old, proud things that will not help you.",
          "Go to the End instead. Find a stronghold. The next Warden waits there.\n\nPlease. - M."], color="gray"),
      rewards={"xp": 400, "items": {"minecraft:glowstone": 4}},
      nxt=flow("h2_hostile_paradise", delay=20, dims=AETHER))

quest("h2_hostile_paradise", "Hostile Paradise",
      "Beautiful, and empty. Something is missing from this sky. Gather what you need to survive here.",
      [collect("holystone", "aether:holystone", 32, 0),
       collect("ambrosium", "aether:ambrosium_shard", 24, 1),
       collect("skyroot", "aether:skyroot_log", 16, 2, "You find a chariot wheel half buried in a cloud. Far too large for any cart.")],
      requires={"advancements": ["aether:enter_aether"]},
      rewards={"xp": 600, "items": {"aether:golden_amber": 8}},
      nxt=flow("h3_do_you_believe_in_magic", dims=AETHER))

quest("h3_do_you_believe_in_magic", "Do You Believe in Magic?",
      "Build an Altar and find Zanite.",
      [place_anywhere("altar", "aether:altar", 1, 0),
       collect("zanite", "aether:zanite_gemstone", 4, 1)],
      rewards={"xp": 600, "items": {"aether:zanite_gemstone": 4}},
      nxt=flow("h4_cockatrice_nights", dims=AETHER))

quest("h4_cockatrice_nights", "Cockatrice Nights",
      "The Aether's beasts have gone wild without their old masters. Thin them out.",
      [kill("cockatrices", "aether:cockatrice", 8, 0),
       kill("zephyrs", "aether:zephyr", 6, 0)],
      rewards={"xp": 800, "items": {"aether:golden_amber": 8}},
      nxt=flow("h5_the_bronze_halls", dims=AETHER))

quest("h5_the_bronze_halls", "The Bronze Halls",
      "Break into the Bronze Dungeon.",
      [reach("find_bronze", "aether:bronze_dungeon", 0, radius=12000),
       kill("sentries", "aether:sentry", 4, 1),
       kill("mimics", "aether:mimic", 1, 1, "A carving on the wall: a chariot falling from the sun, and a woman falling beside it."),
       kill("the_slider", "aether:slider", 1, 1, "The Slider grinds to a stop. The halls go quiet for the first time in an age.")],
      rewards={"xp": 900, "items": {"minecraft:diamond": 2}},
      nxt=flow("h6_like_a_bossaru", dims=AETHER))

quest("h6_like_a_bossaru", "Like a Bossaru!",
      "Kill the Slider. Where: the heart of the Bronze Dungeon.",
      [place_anywhere("cloud_road", "aether:cold_aercloud", 16, 0, "A road of cloud, from your portal to the dungeons. You won't have to jump blind again.")],
      rewards={"xp": 900, "items": {"minecraft:golden_apple": 2}},
      nxt=flow("h7_medals_of_the_silver_court", dims=AETHER))

quest("h7_medals_of_the_silver_court", "Medals of the Silver Court",
      "The Valkyrie Queen only duels those who have earned ten Victory Medals from her Valkyries.",
      [reach("find_silver", "aether:silver_dungeon", 0, radius=12000),
       kill("valkyries", "aether:valkyrie", 6, 1, "Ten Victory Medals open the Queen's door. Valkyries drop one each."),
       kill("valkyrie_queen", "aether:valkyrie_queen", 1, 1, "'You carry his stink,' she says as she falls. 'The Charioteer's.'"),
       chest_book("hymn_of_azriel", 2, "aether:silver_dungeon", "The Hymn of Azriel", "hag_hymn_of_azriel",
                  "The Hymn of Azriel", "The Silver Court",
                  ["Azriel, Charioteer of the Sun, drove the Sun across our sky for a thousand years.",
                   "He wanted it for himself. He tried to take it.",
                   "Maraal of the Silver Court rode beside him. She did not stop him.\n\nThey fell together, and the Sun Spirit threw them into the burning deep."],
                  "Maraal. You have never heard that name before. Have you?", radius=12000)],
      rewards={"xp": 1200, "items": {"aether:zanite_gemstone": 6}},
      nxt=flow("h8_dethroned", dims=AETHER))

quest("h8_dethroned", "Dethroned",
      "Kill the Valkyrie Queen. Where: the Silver Dungeon.",
      [place_anywhere("cairn", "aether:holystone_bricks", 16, 0, "A cairn for the Valkyries who died for a Queen who knew the truth and kept it.")],
      rewards={"xp": 1200, "items": {"minecraft:diamond": 4}},
      nxt=flow("h9_extinguished", dims=AETHER))

quest("h9_extinguished", "Extinguished",
      "Kill the Sun Spirit. Where: the Gold Dungeon.",
      [reach("find_gold", "aether:gold_dungeon", 0, radius=15000),
       kill("sun_spirit", "aether:sun_spirit", 1, 1, "The Sun Spirit cast Azriel down. With it gone, nothing in heaven remembers him.")],
      rewards={"xp": 2500, "items": {"minecraft:golden_apple": 4}},
      nxt=flow("h10_fireproof", dims=OVER))

quest("h10_fireproof", "Fireproof",
      "You came back with Phoenix armour and a name you can't stop thinking about. Tell someone what you saw up there.",
      [talk("tell_a_villager", 0, "A courier is already waiting. The handwriting on the letter is shakier than usual.")],
      requires={"advancements": ["aether:gold_dungeon"]},
      rewards={"xp": 500, "items": {"minecraft:experience_bottle": 10}},
      nxt=flow("e0_the_road_to_the_end", retry=400))

# ======================= ACT IV: THE GATE BEYOND (courier after H10) =======================
courier("letter_go_to_the_end", "e0_the_road_to_the_end", "e1_the_librarians_request", lo=400, hi=800)
quest("e1_the_librarians_request", "The Librarian's Request",
      "A librarian who has studied the cult's Old Tongue for thirty years wants to help, for a price.",
      [collect("books", "minecraft:book", 24, 0),
       collect("ink", "minecraft:ink_sac", 8, 1),
       talk("meet_the_librarian", 2, "'Strongholds keep old books. Take me to one.'")],
      book=book("What Did They Tell You?", "Old Mara", [
          "I told you to leave the sky alone. What did they tell you up there?",
          "Never mind. It does not matter. Old proud things tell old proud lies.\n\nGo to the End. A librarian will help you. - M."], color="gray"),
      rewards={"xp": 600, "items": {"minecraft:enchanted_book": {"count": 1, "enchants": {"minecraft:feather_falling": 4}}}},
      nxt=flow("ea1_the_old_eye"))


quest("e3_the_road_underground", "The Road Underground",
      "Find a stronghold. The cult wants its library too.",
      [reach("find_the_stronghold", "minecraft:stronghold", 0, radius=30000000),
       kill("faithful", "minecraft:vindicator", 4, 1),
       kill("witches", "minecraft:witch", 2, 1),
       kill("hollow_pilgrim", "minecraft:enderman", 1, 1, "The Hollow Pilgrim's last word is a name: 'M...'")],
      threats=[threat("minecraft:vindicator", "Faithful Seeker", stage=1, count=4, hp=48, structure="minecraft:stronghold", origin="quest_target", search=30000000,
                      equipment=eq(mainhand=("minecraft:iron_axe", {"minecraft:sharpness": 2}))),
               threat("minecraft:witch", "Faithful Witch", stage=1, count=2, hp=40, structure="minecraft:stronghold", origin="quest_target", search=30000000),
               threat("minecraft:enderman", "The Hollow Pilgrim", stage=1, hp=120, structure="minecraft:stronghold", origin="quest_target", search=30000000,
                      effects=fx(("minecraft:strength", 1)))],
      rewards={"xp": 1200, "items": {"minecraft:golden_apple": 2}},
      nxt=flow("e4_the_warden_journal"))

quest("e4_the_warden_journal", "The Warden Journal",
      "Find the journal the cult was looking for.",
      [chest_book("warden_journal", 0, "minecraft:stronghold", "The Warden Journal", "hag_warden_journal", "The Warden Journal", "M.",
                  ["Seven hold the Nails. Seven keep the halves apart.",
                   "Ash below. Silence beyond. Frost, Sand, Tide. The Gate. The Flame.",
                   "[A page has been torn out here. Recently. The tear is clean.]"],
                  "One page is missing. Someone tore it out, and not long ago.", radius=30000000)],
      rewards={"xp": 900, "items": {"minecraft:experience_bottle": 10}},
      nxt=flow("e5_free_the_end"))

quest("e5_free_the_end", "Free the End",
      "Kill the Ender Dragon. Where: the End, through the stronghold's portal.",
      [kill("the_dragon", "minecraft:ender_dragon", 1, 0,
            "The second Warden falls. That night every villager in the world dreams of a king with no head.")],
      rewards={"xp": 3000, "items": {"minecraft:diamond_chestplate": {"count": 1, "enchants": {"minecraft:protection": 4}}}},
      nxt=flow("e6_edge_of_the_world", dims=END))

quest("e6_edge_of_the_world", "The Edge of the World",
      "Before you leave the End, find out how far the cult reached.",
      [reach("find_end_city", "minecraft:end_city", 0, "A prayer is scratched into the purpur: 'AZAZEL IS MY LIGHT.' They reached the End before you.", radius=8000),
       kill("shulkers", "minecraft:shulker", 8, 1)],
      requires={"advancements": ["minecraft:end/kill_dragon"]},
      rewards={"xp": 1500, "items": {"minecraft:netherite_upgrade_smithing_template": 1}},
      nxt=flow("e7_the_nether_is_singing", dims=OVER))

quest("e7_the_nether_is_singing", "The Nether Is Singing",
      "The librarian has finished copying the journal and wants to talk.",
      [talk("talk_to_the_librarian", 0,
            "The librarian: 'The journal says the seven were not monsters. It says they were...' A courier interrupts with a letter. 'Do not trust old books. Five more beasts stir. Go now. - M.'")],
      rewards={"xp": 600, "items": {"minecraft:emerald": 16}})
