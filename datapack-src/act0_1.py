from lib import *

V = "minecraft:villager"

# ======================= THE FIRST LETTER (courier, on first crafting table) =======================
quest("the_first_letter", "A Letter from Old Mara",
      "A stranger's letter, delivered by a courier who would not say who paid him. Find the nearest village and listen.",
      [talk("listen_to_the_village", 0,
            "The villagers greet you like someone they were told to expect.")],
      book=book("A Letter from Old Mara", "Old Mara", [
          "To the one in the new iron armour,\n\nYou do not know me. I know you. I have watched a great many people start with nothing, and you are the first in a long time who started well.",
          "There is a village near you. It is quiet. It will not stay quiet.\n\nGo there. Help them with the small things. The small things are how you learn where the big things are hiding.",
          "Someone is buying gold at twice its worth. Someone is painting eyes on chapel doors.\n\nNotice it. Say nothing yet.\n\nI will write again.\n\n- M."], color="red"),
      rewards={"xp": 50, "items": {"minecraft:bread": 8, "minecraft:torch": 16}},
      nxt=flow("p1_the_bell", delay=40, dims=OVER))
courier("first_letter", None, "the_first_letter", lo=200, hi=600, advancement="half_a_god:full_iron_armor")

# ======================= PROLOGUE: THE QUIET YEAR =======================
quest("p1_the_bell", "The Bell That Won't Ring",
      "The village sold its old bell to a smiling stranger 'for twice its weight in gold'. Bring iron and gold and hang a new one.",
      [collect("iron_for_the_bell", "minecraft:iron_ingot", 6, 0, "The smith takes the iron and frowns. 'Gold too. Old bells were part gold.'"),
       collect("gold_for_the_bell", "minecraft:gold_ingot", 2, 1),
       place("hang_the_bell", "minecraft:bell", 1, 2, "The bell rings. Every villager stops, and listens, and smiles.")],
      rewards={"xp": 120, "items": {"minecraft:emerald": 6, "minecraft:shield": 1}},
      nxt=flow("p2_winter_stores"))

quest("p2_winter_stores", "Winter Stores",
      "The granary is half empty and winter is coming. Fill it. The farmer keeps asking about Tam the shepherd.",
      [collect("wheat", "minecraft:wheat", 64, 0),
       collect("potatoes", "minecraft:potato", 32, 1),
       collect("beef", "minecraft:cooked_beef", 16, 2),
       talk("tell_the_farmer", 3, "'Tam? He went up the hill three days ago. His sheep came back without him.'")],
      rewards={"xp": 160, "items": {"minecraft:bread": 16, "minecraft:golden_carrot": 8}},
      nxt=flow("p3_the_shepherd"))

quest("p3_the_shepherd", "The Shepherd's Path",
      "Find Tam before the night finds you. Something walks the hills after dark.",
      [wait("wait_for_dark", 0, "next_night", "The light goes. The hills start moving."),
       kill("night_walkers", "minecraft:zombie", 10, 1, "Tam crawls out from under a rock ledge, shaking."),
       talk("bring_tam_home", 2, "Tam: 'A man with a golden smile bought my sheep. Then he asked what a person weighs. In gold.'")],
      threats=[threat("minecraft:zombie", "Night-Walker", stage=1, count=10, hp=34,
                      equipment=eq(mainhand="minecraft:iron_shovel", head="minecraft:leather_helmet"), smin=10, smax=22)],
      rewards={"xp": 220, "items": {"minecraft:iron_chestplate": 1}},
      nxt=flow("p4_eye_on_chapel"))

quest("p4_eye_on_chapel", "An Eye on the Chapel",
      "Someone painted a golden eye on the chapel door. The cleric found a note on the altar and will not read it aloud.",
      [talk("ask_the_cleric", 0, "The note: 'AZAZEL IS MY LIGHT AND MY SALVATION.' It is signed in letters nobody can read."),
       place("candles_for_the_chapel", "minecraft:candle", 8, 1, "With the chapel lit, the eye looks less like it is watching.")],
      rewards={"xp": 160, "items": {"minecraft:lantern": 2, "minecraft:experience_bottle": 4}},
      nxt=flow("p5_raise_the_watch"))

quest("p5_raise_the_watch", "Raise the Watch",
      "The mason wants a watchtower. 'For wolves,' he says. Nobody believes him.",
      [talk("ask_the_mason", 0),
       place("tower_stone", "minecraft:stone_bricks", 48, 1),
       place("tower_ladders", "minecraft:ladder", 8, 2, "From the top you can see smoke on the far side of the hills.")],
      rewards={"xp": 220, "items": {"minecraft:crossbow": {"count": 1, "enchants": {"minecraft:quick_charge": 2}}, "minecraft:arrow": 32}},
      nxt=flow("p6_market_road"))

quest("p6_market_road", "The Market Road",
      "The farmer's cart has to reach market. Pillagers have been seen on the road.",
      [talk("load_the_cart", 0, "'Stay close. They took the last cart and left the driver.'"),
       kill("road_pillagers", "minecraft:pillager", 6, 1, "The raiders carry gold coins stamped with an eye."),
       kill("road_captain", "minecraft:vindicator", 1, 1),
       talk("reach_the_market", 2, "The market village has painted a golden eye on its own chapel.")],
      threats=[threat("minecraft:pillager", "Road Raider", stage=1, count=6, hp=30, smin=12, smax=24),
               threat("minecraft:vindicator", "Road Captain", stage=1, hp=60, effects=fx(("minecraft:strength", 0)),
                      equipment=eq(mainhand=("minecraft:iron_axe", {"minecraft:sharpness": 1})),
                      mount={"entity": "minecraft:ravager", "name": "Cart-Breaker", "max_health": 120})],
      rewards={"xp": 280, "items": {"minecraft:emerald": 10, "minecraft:saddle": 1}},
      nxt=flow("p7_gold_at_double_weight"))

# the market is in another village, not the one you set off from
QUESTS["p6_market_road"][1]["goals"][3]["_far"] = True

quest("p7_gold_at_double_weight", "Gold at Double Weight",
      "The smiling stranger is back, buying gold and asking questions. Deal with him: you may spare him, or not.",
      [kill("face_the_peddler", "minecraft:wandering_trader", 1, 0,
            "Whatever you chose, the village will remember it.")],
      threats=[threat("minecraft:wandering_trader", "The Smiling Peddler", hp=60, spare=True, smin=8, smax=16,
                      talk=[["Who are you?", "A merchant. I buy gold. I buy all sorts of things. I pay double."],
                            ["Why the eyes on the chapels?", "So He can see you. He likes to see the ones who are worth something."],
                            ["Who is 'He'?", "Spare me and I will give you His book. Strike me and my friends will come to collect."]],
                      attack_ambushes=[{"entity": "minecraft:vindicator", "name": "Peddler's Bodyguard", "count": 2, "max_health": 50,
                                        "equipment": {"mainhand": "minecraft:iron_axe"}}],
                      spare_handoff={"item": "minecraft:written_book", "name": "The Book of Belial", "count": 1,
                                     "quest_book": {"title": "The Book of Belial", "author": "The Faithful",
                                                    "pages": ["For He commands His angels concerning you, to guard you in all your ways.\n\n- Belial 8:12",
                                                              "Gold is faith made solid. Give your gold and you give your faith. Give enough, and He will give you back your life."]}},
                      target="The Smiling Peddler is waiting near the village."),
               ],
      choice={"attack": {"label": "Drive him out with steel", "traits": ["peddler_slain"]},
              "spare": {"label": "Let him walk", "line": "He bows far too low. 'He will remember your kindness.'", "traits": ["heard_the_word"]}},
      rewards={"xp": 300, "items": {"minecraft:emerald": 8}},
      on_kill=flow("p7b_peddlers_debt", delay=2400), on_spare=flow("p7s_the_peddlers_gift", delay=600))

quest("p7b_peddlers_debt", "The Peddler's Debt",
      "'He paid for you in advance,' the note on your door says. They have come to collect.",
      [kill("gold_sworn", "minecraft:vindicator", 4, 0, "On the last body: a ledger page with your name on it.")],
      threats=[threat("minecraft:vindicator", "Gold-Sworn", count=4, hp=48, smin=8, smax=18,
                      equipment=eq(mainhand=("minecraft:iron_axe", {"minecraft:sharpness": 2}), head="minecraft:golden_helmet"),
                      effects=fx(("minecraft:speed", 0)))],
      rewards={"xp": 260, "items": {"minecraft:iron_axe": {"count": 1, "enchants": {"minecraft:sharpness": 2}}}},
      nxt=flow("p8_pillager_moon"))

quest("p8_pillager_moon", "The Pillager Moon",
      "A war party is coming for the village tonight. Hold the line until the last raider falls.",
      [talk("warn_the_village", 0, "'They're coming from the north road. Lots of them.'"),
       wait("wait_for_the_moon", 1, "next_night"),
       kill("raiders", "minecraft:pillager", 8, 2),
       kill("axemen", "minecraft:vindicator", 4, 2),
       kill("witch_doctor", "minecraft:witch", 1, 2),
       kill("the_beast", "minecraft:ravager", 1, 2, "The raiders carried gold they never looted. Someone paid them to come.")],
      threats=[threat("minecraft:pillager", "Moon Raider", stage=2, count=8, hp=32, smin=14, smax=28),
               threat("minecraft:vindicator", "Moon Axeman", stage=2, count=4, hp=48, smin=14, smax=28,
                      equipment=eq(mainhand=("minecraft:iron_axe", {"minecraft:sharpness": 1}))),
               threat("minecraft:witch", "War-Witch", stage=2, hp=40, smin=16, smax=28),
               threat("minecraft:ravager", "The Moon Beast", stage=2, hp=160, smin=18, smax=30, effects=fx(("minecraft:resistance", 0)))],
      rewards={"xp": 450, "items": {"minecraft:golden_apple": 2, "minecraft:diamond": 2}},
      nxt=flow("p9_who_pays_in_gold"))

quest("p9_who_pays_in_gold", "Who Pays in Gold",
      "Follow the raiders' tracks to their Pillager Camp, find out who paid them, and break the camp.",
      [reach("find_the_camp", "takesapillage:pillager_camp", 0, "Smoke, palisades, and a banner with a golden eye stitched over the illager face."),
       kill("camp_pillagers", "minecraft:pillager", 8, 1),
       chest_book("ledger_of_tithes", 2, "takesapillage:pillager_camp", "Ledger of Tithes", "hag_ledger_of_tithes",
                  "Ledger of Tithes", "Camp Quartermaster",
                  ["Received of the Faithful: 40 gold. For the village by the hill. Burn nothing. Take people.",
                   "Payment owed on delivery to the Sacred City. Souls by weight. The Chariot pays in full.",
                   "Next contract: ASHFORD. Tonight."],
                  "Ashford. Tonight. You have to warn them.")],
      rewards={"xp": 520, "items": {"minecraft:enchanted_book": {"count": 1, "enchants": {"minecraft:protection": 3}}, "minecraft:emerald": 12}})

# ======================= ACT I: ASHFALL ROADS (courier after P9) =======================
courier("letter_ashford", "p9_who_pays_in_gold", "a1_ashford_burns", lo=200, hi=400)
quest("a1_ashford_burns", "Ashford Burns",
      "Mara's second letter: 'Ashford is the next village over. The raid has already started. Go.'",
      [kill("ashford_raiders", "minecraft:pillager", 11, 0, "The Brand-Carrier falls. Reeve Halden crawls out of the burning hall. 'They didn't kill the men. They took them.'"),
       kill("ashford_skirmishers", "minecraft:vindicator", 4, 0),
       talk("find_halden", 1, "Halden: 'Ashford is yours to call on now. What's left of it.'")],
      threats=[threat("minecraft:pillager", "Ashford Raider", count=10, hp=30, structure="minecraft:villages", origin="quest_target", min_dist=200, smin=4, smax=24),
               threat("minecraft:vindicator", "Ashford Skirmisher", count=4, hp=44, structure="minecraft:villages", origin="quest_target", min_dist=200, smin=4, smax=24,
                      equipment=eq(mainhand="minecraft:iron_axe")),
               threat("minecraft:pillager", "The Brand-Carrier", hp=70, structure="minecraft:villages", origin="quest_target", min_dist=200, smin=4, smax=18,
                      equipment=eq(mainhand=("minecraft:bow", {"minecraft:flame": 1, "minecraft:power": 3})))],
      book=book("Ashford Burns", "Old Mara", [
          "The camp's ledger said Ashford. It was right.\n\nThe raid has started. Go now, go fast, and do not let the Brand-Carrier leave alive.",
          "Afterwards, find the reeve. His name is Halden. He is a stubborn man. You will like him.\n\n- M."], color="red"),
      rewards={"xp": 520, "items": {"minecraft:emerald": 12, "minecraft:iron_ingot": 8}},
      nxt=flow("a2_what_was_left"))

# Halden is found in Ashford itself: you are standing in it when the last raider falls
QUESTS["a1_ashford_burns"][1]["goals"][2]["_far"] = False

quest("a2_what_was_left", "What Was Left",
      "Ashford's families lost almost everything. Bring them what they need to get through the week.",
      [collect("bread", "minecraft:bread", 32, 0),
       collect("wool", "minecraft:white_wool", 16, 1),
       collect("coal", "minecraft:coal", 32, 2),
       talk("give_to_the_families", 3, "A child hands you a gold coin stamped with an eye. 'The bad men dropped it.'")],
      rewards={"xp": 300, "items": {"minecraft:golden_apple": 1}},
      nxt=flow("a3_roof_and_hearth"))

quest("a3_roof_and_hearth", "Roof and Hearth",
      "'If they come back, let them find walls.' Help Halden rebuild Ashford.",
      [talk("ask_halden", 0),
       place("planks", "minecraft:oak_planks", 96, 1),
       place("glass", "minecraft:glass_pane", 32, 2),
       place("beds", "minecraft:white_bed", 4, 3, "For the first night since the raid, Ashford sleeps indoors.")],
      rewards={"xp": 380, "items": {"minecraft:diamond": 2, "minecraft:bread": 16}},
      nxt=flow("a4_refugee_road"))

quest("a4_refugee_road", "The Refugee Road",
      "Two Ashford families want to move to your village. The Faithful are watching the road.",
      [talk("meet_the_families", 0, "'We'll follow you. Just don't let them take us like they took the others.'"),
       kill("the_faithful", "minecraft:vindicator", 4, 1, "The Faithful die smiling, whispering in a language made of angles."),
       talk("arrive_home", 2)],
      threats=[threat("minecraft:vindicator", "The Faithful", stage=1, count=4, hp=46, smin=10, smax=22,
                      equipment=eq(mainhand=("minecraft:iron_axe", {"minecraft:sharpness": 1}), head="minecraft:golden_helmet"))],
      rewards={"xp": 340, "items": {"minecraft:iron_chestplate": {"count": 1, "enchants": {"minecraft:protection": 2}}}},
      nxt=flow("a5_beneath_the_pipes"))

quest("a5_beneath_the_pipes", "Beneath the Pipes",
      "Mara's hint, scrawled on the back of the last letter: 'Find the pipes. There are cracks underneath them.'",
      [reach("find_the_pipes", "netherman:pipes", 0, "Old pipes, rusted, going nowhere. Underneath: cracks, and a cold breath coming up.", radius=30000),
       chest_book("pipe_keepers_log", 1, "netherman:pipes", "Pipe-Keeper's Log", "hag_pipe_log", "Pipe-Keeper's Log", "A Believer",
                  ["The pipes carry the prayers down. Do not ask where down is.",
                   "New stock moved to the believer house. Keep them fed. The Sacred City wants them alive."],
                  "The log names a believer house.", radius=30000)],
      rewards={"xp": 380, "items": {"minecraft:ender_pearl": 4}},
      nxt=flow("a6_believers_house"))

quest("a6_believers_house", "The Believer's House",
      "The pipe-keeper's log says the prisoners were moved through a believer house. Find it. A preacher lives there: "
      "make him talk, deal with the zealots who come for him, and find the ledger that says where Ashford's men went.",
      [reach("find_the_house", "netherman:believer_house", 0, radius=30000),
       talk("the_preacher", 1, "The Preacher points at the road. 'They're coming. They know I talked.'",
            entity="netherman:believer_villager", name="The Preacher"),
       kill("believers", "minecraft:vindicator", 4, 2, "The last zealot drops a key on a gold chain."),
       chest_book("ledger_of_ashford", 3, "netherman:believer_house", "Ledger of Ashford", "hag_ledger_ashford", "Ledger of Ashford", "A Believer",
                  ["Ashford men: 14. Held at the BASTILLE until the portal is ready.",
                   "Then through the Nether to the Quarries. Stock for the Quarries. The Chariot wants gold, and gold wants hands."],
                  "Fourteen men, held in a Bastille.", radius=30000)],
      threats=[threat("netherman:believer_villager", "The Preacher", stage=1, spare=False, smin=2, smax=6, hp=40,
                      target_line="You're not one of the faithful. ...You're the one from Ashford.",
                      talk=[["Where are Ashford's men?", "Gone. They stayed one night, chained in my cellar. I prayed over them. That's all I did."],
                            ["Where did they go?", "The Bastille. The ledger is in the barrel. Take it. I won't stop you."],
                            ["Why help me?", "Because they cried at night, and my god never answered them. Maybe you will."]]),
               threat("minecraft:vindicator", "Cult Zealot", stage=2, count=4, hp=48, smin=8, smax=18,
                      equipment=eq(mainhand=("minecraft:iron_axe", {"minecraft:sharpness": 1}), head="minecraft:golden_helmet"))],
      rewards={"xp": 420, "items": {"minecraft:potion": 2}},
      nxt=flow("a7_the_bastille"))

quest("a7_the_bastille", "The Bastille",
      "An illager fortress, paid for in cult gold. Ashford's men are inside. Storm it.",
      [reach("find_the_bastille", "takesapillage:bastille", 0, "Stone walls, a gate, and a golden eye painted over the illager banner.", radius=30000),
       kill("legioners", "takesapillage:legioner", 8, 1),
       kill("archers", "takesapillage:archer", 6, 1),
       kill("skirmishers", "takesapillage:skirmisher", 4, 1, "The cells are half empty. The rest were already taken.")],
      rewards={"xp": 700, "items": {"minecraft:diamond": 3, "minecraft:totem_of_undying": 1}},
      nxt=flow("a8_ravagers_horn"))

quest("a8_ravagers_horn", "The Ravager's Horn",
      "Bring down the Bastille's ravagers and take a horn as proof the beasts are gone.",
      [kill("ravagers", "minecraft:ravager", 2, 0),
       collect("horn", "takesapillage:ravager_horn", 1, 1, "Halden hangs the horn over Ashford's gate.")],
      rewards={"xp": 450, "items": {"minecraft:emerald": 16}},
      nxt=flow("a9_open_the_cells"))

quest("a9_open_the_cells", "Open the Cells",
      "The freed prisoners need to get home, and the Bastille's jailers want them back.",
      [kill("jailers", "minecraft:vindicator", 5, 0),
       talk("bring_them_home", 1, "Back in Ashford, a freed man whispers: 'The rest were marched into a purple portal. North. Always north.'")],
      threats=[threat("minecraft:vindicator", "Bastille Jailer", count=5, hp=50, smin=8, smax=20,
                      equipment=eq(mainhand=("minecraft:iron_axe", {"minecraft:sharpness": 2}), chest="minecraft:chainmail_chestplate"))],
      rewards={"xp": 400, "items": {"minecraft:emerald": 10}},
      nxt=flow("a9i_the_invokers_fort"))

quest("a10_burning_door", "The Burning Door",
      "The others were taken into the Nether. Build a portal and follow them.",
      [place_anywhere("portal_frame", "minecraft:obsidian", 10, 0),
       enter_nether("enter_the_nether", 1, "Heat, ash, and far away, something like singing.")],
      rewards={"xp": 300, "items": {"minecraft:gold_ingot": 8, "minecraft:potion": 1}},
      nxt=flow("a11_chains_at_the_fortress", dims=NETHER))

quest("a11_chains_at_the_fortress", "Chains at the Fortress",
      "The prisoners' trail leads to a Nether fortress. Fight through and free whoever is still there.",
      [reach("find_the_fortress", "minecraft:fortress", 0, radius=12000),
       kill("guards", "minecraft:wither_skeleton", 8, 1, "Three Ashford men are chained in a corridor. 'North,' they say. 'To the city with the eye.'", threats_only=False),
       leave_nether("lead_them_out", 2, "Back home, Ashford counts three more survivors.")],
      rewards={"xp": 600, "items": {"minecraft:emerald": 12, "minecraft:golden_apple": 1}},
      nxt=flow("a12_brother_vael", dims=NETHER))

quest("a12_brother_vael", "Brother Vael",
      "The steward who runs the cult's trade in the Nether is here. He can be spared, or not.",
      [kill("face_vael", "minecraft:evoker", 1, 0, "Whatever happens next, the cult will hear of it.")],
      threats=[threat("minecraft:evoker", "Brother Vael", hp=140, spare=True, structure="minecraft:fortress", origin="quest_target",
                      effects=fx(("minecraft:resistance", 0)), search=12000,
                      talk=[["You run the trade.", "I count. I am good at counting. Gold. Skulls. People."],
                            ["Skulls?", "Wither skulls. Three of them make a crown. The Chariot wants a crown."],
                            ["Why tell me?", "Because you will kill me or you won't, and either way someone should know."]],
                      attack_ambushes=[{"entity": "minecraft:wither_skeleton", "name": "Vael's Guard", "count": 3, "max_health": 60,
                                        "equipment": {"mainhand": "minecraft:stone_sword"}}],
                      spare_handoff={"item": "minecraft:written_book", "name": "Vael's Confession", "count": 1,
                                     "quest_book": {"title": "Vael's Confession", "author": "Brother Vael",
                                                    "pages": ["I was a cartographer once. Then I was hungry. Then I was faithful.",
                                                              "They are buying wither skulls. If the Chariot raises the Wither, it will have a crown and an army.\n\nStop them. I cannot."]}},
                      drop_on_death=[{"type": "item", "item": "minecraft:written_book", "count": 1, "name": "The Steward's Orders",
                                      "quest_book": {"title": "The Steward's Orders", "author": "The Chariot",
                                                     "pages": ["Steward. Buy every wither skull in the Nether. Three for the crown.",
                                                               "The Wither will wake for us, and the old Nail of Ash will break."]}}])],
      choice={"attack": {"label": "End him", "traits": ["vael_slain"]},
              "spare": {"label": "Let him go", "line": "Vael bows. 'I will not forget.'", "traits": ["vael_spared"]}},
      rewards={"xp": 700, "items": {"minecraft:diamond": 3}},
      on_kill=flow("a12k_the_stewards_guard", dims=NETHER), on_spare=flow("a12s_vaels_map", dims=NETHER))

quest("a13_tears_for_a_ward", "Tears for a Ward",
      "Mara asks for ghast tears to brew regeneration 'for the long road'. Bring them to any cleric.",
      [collect("tears", "minecraft:ghast_tear", 4, 0),
       talk("deliver_to_the_cleric", 1, "The cleric brews through the night and hands you a satchel of red bottles.")],
      rewards={"xp": 450, "items": {"minecraft:golden_apple": 2, "minecraft:experience_bottle": 8}},
      nxt=flow("a14_night_of_the_faithful", delay=2400, dims=OVER))

quest("a14_night_of_the_faithful", "Night of the Faithful",
      "The cult has noticed you. Tonight they come for the village you protect.",
      [wait("wait_for_night", 0, "next_night"),
       kill("faithful", "minecraft:vindicator", 6, 1),
       kill("conjurers", "minecraft:evoker", 2, 1),
       kill("ashfords_torch", "minecraft:pillager", 1, 1, "Ashford's Torch falls. In his pack: a map with the Nether marked in gold.")],
      threats=[threat("minecraft:vindicator", "The Faithful", stage=1, count=6, hp=46, smin=12, smax=26,
                      equipment=eq(mainhand=("minecraft:iron_axe", {"minecraft:sharpness": 2}))),
               threat("minecraft:evoker", "Faithful Conjurer", stage=1, count=2, hp=50, smin=16, smax=28),
               threat("minecraft:pillager", "Ashford's Torch", stage=1, hp=100, smin=14, smax=24,
                      equipment=eq(mainhand=("minecraft:crossbow", {"minecraft:piercing": 3, "minecraft:quick_charge": 2})),
                      effects=fx(("minecraft:strength", 1), ("minecraft:resistance", 0)))],
      rewards={"xp": 800, "items": {"minecraft:diamond_sword": {"count": 1, "enchants": {"minecraft:sharpness": 3}}}},
      nxt=flow("a15_one_of_seven"))

quest("a15_one_of_seven", "One of Seven",
      "Tell the village it is over, for tonight.",
      [talk("tell_the_village", 0, "A courier is already waiting at the well with a letter in a hand you recognise.")],
      rewards={"xp": 200, "items": {"minecraft:emerald": 8}},
      nxt=flow("w0_two_roads_meet", retry=400))

# Hidden trigger (no toast, not shown in the advancement screen): a full set of iron armour in your inventory.
# Used only to send the first courier. Same trigger shape as the Aether's "Defying Gravity" (full gravitite set).
EXTRA["advancement/full_iron_armor.json"] = {"criteria": {
    piece: {"trigger": "minecraft:inventory_changed", "conditions": {"items": [{"items": f"minecraft:iron_{piece}"}]}}
    for piece in ("helmet", "chestplate", "leggings", "boots")}}

# ---------------- Prologue outcome: sparing the Peddler ----------------
quest("p7s_the_peddlers_gift", "The Peddler's Gift",
      "You let the Peddler walk. His 'gift' was a book of cult scripture. The village elder wants to see it.",
      [talk("show_the_elder", 0, "The elder reads one page and goes pale. 'Burn it. No. Keep it. Know your enemy.'")],
      rewards={"xp": 250, "items": {"minecraft:emerald": 6, "minecraft:experience_bottle": 4}},
      nxt=flow("p8_pillager_moon"))

# ---------------- Act I outcomes: Brother Vael ----------------
quest("a12s_vaels_map", "Vael's Map",
      "Spared, Vael scratched a map into the netherrack: the cult's supply route. Follow it and break it.",
      [kill("supply_guards", "minecraft:piglin_brute", 3, 0, "The cult's gold carts will run empty for a while.")],
      threats=[threat("minecraft:piglin_brute", "Cult Gold-Runner", count=3, hp=60, smin=10, smax=22,
                      equipment=eq(mainhand=("minecraft:golden_axe", {"minecraft:sharpness": 2})))],
      rewards={"xp": 600, "items": {"minecraft:gold_block": 2}},
      nxt=flow("a13_tears_for_a_ward", dims=OVER))

quest("a12k_the_stewards_guard", "The Steward's Guard",
      "Vael's death did not go unnoticed. His wither-guard wants revenge.",
      [kill("vaels_guard", "minecraft:wither_skeleton", 4, 0, "On the last skull: a gold tag stamped 'PROPERTY OF THE CHARIOT'.")],
      threats=[threat("minecraft:wither_skeleton", "Steward's Guard", count=4, hp=70, smin=8, smax=18,
                      equipment=eq(mainhand=("minecraft:stone_sword", {"minecraft:sharpness": 3})), effects=fx(("minecraft:strength", 0)))],
      rewards={"xp": 600, "items": {"minecraft:wither_skeleton_skull": 1}},
      nxt=flow("a13_tears_for_a_ward", dims=OVER))

# ---------------- Act I: Illager Invasion ----------------
quest("a9i_the_invokers_fort", "The Invoker's Mansion",
      "The Bastille's jailers answered to someone. Their orders came from a Woodland Mansion in the dark forest, and from the Invoker who rules it.",
      [reach("find_the_mansion", "minecraft:mansion", 0, "Banners, cages, and a stairwell humming with spells.", radius=30000000),
       kill("mansion_guard", "minecraft:vindicator", 6, 1),
       kill("the_invoker", "illagerinvasion:invoker", 1, 2, "The Invoker dies cursing 'the Chariot's cheap gold'. The illagers were only ever hired help.")],
      rewards={"xp": 1500, "items": {"minecraft:totem_of_undying": 1, "minecraft:diamond": 4}},
      nxt=flow("a10_burning_door"))
