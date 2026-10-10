"""Clearer story text for the main line: every summary says who is asking, why it matters and what to do,
and every 'talk to' NPC explains the job in their own words.

T[quest] = (summary, {talk_goal_id: [[question, answer], ...]}, optional new title)
"""
from lib import QUESTS

T = {
# ---------------------------------------------------------------- PROLOGUE
"the_first_letter": ("A courier brought you a letter from someone called Old Mara. She has been watching you, and she thinks "
    "the village nearby is in danger. Go there and introduce yourself to the village elder.",
    {"listen_to_the_village": [["Old Mara sent me.", "Mara? Then you're the one she wrote about. She said someone would come."],
                               ["What's wrong here?", "Small things. A stranger buying gold. Paint on the chapel. Our bell is gone."],
                               ["How can I help?", "Start with the bell. A village without a bell can't warn itself."]]}),
"p1_the_bell": ("The village elder told you their bell was sold to a smiling stranger who paid 'twice its weight in gold'. "
    "Without a bell the village can't raise the alarm. Bring the smith iron and gold, then hang the new bell.", {}),
"p2_winter_stores": ("With the bell back, the farmer admits the granary is half empty: the same stranger bought their grain too, "
    "and winter is coming. Fill the stores, then tell the farmer. He has been worried about Tam the shepherd.",
    {"tell_the_farmer": [["The granary's full.", "Bless you. We'd have starved by midwinter."],
                         ["You said something about Tam?", "He went up the hill three days ago. His sheep came back without him."],
                         ["I'll look for him.", "Go at dusk. Whatever's up there only comes out at night."]]}),
"p3_the_shepherd": ("Tam the shepherd never came home, and his sheep did. Something walks the hills at night. Wait for dark, "
    "fight off what comes, then bring Tam home and hear what happened to him.",
    {"bring_tam_home": [["Tam, are you hurt?", "Just scared. A man with a golden smile bought my sheep. Then he asked what a person weighs. In gold."],
                        ["What did he look like?", "Smiling. Always smiling. He said someone called 'He' would want to meet me."]]}),
"p4_eye_on_chapel": ("Since Tam's story, the cleric has been frightened: someone painted a golden eye on the chapel door and left "
    "a note on the altar. Ask her what it says, then light the chapel with candles so the village feels safe there again.",
    {"ask_the_cleric": [["Tam's stranger was here, wasn't he?", "I think so. Read this. I can't say it out loud."],
                        ["'Azazel is my light'?", "A demon's name. Old books say he was a god once. People don't paint his eye for fun."],
                        ["What can I do?", "Light the chapel. Eight candles. If they're watching, let them see we're not afraid."]]}),
"p5_raise_the_watch": ("The eye on the chapel means someone is coming, and the village has no way to see them coming. "
    "The mason wants a watchtower so the guards can spot raiders on the hills. Ask him what he needs, then build it.",
    {"ask_the_mason": [["You want a tower?", "Painted eyes, stolen bells, missing sheep. Someone is scouting us. I want to see them first."],
                       ["What do you need?", "Forty-eight stone bricks for the walls and eight ladders to climb it."],
                       ["Where?", "Anywhere near the village with a clear view of the hills. You choose."]]}),
"p6_market_road": ("From the new watchtower, the guards saw pillagers on the market road. The farmer still has to sell his harvest "
    "or the village has no money for winter. Escort his cart: talk to him, fight off the raiders, and meet the market elder.",
    {"load_the_cart": [["Ready to go?", "As I'll ever be. They took the last cart and left the driver in a ditch."],
                       ["Why risk it?", "No market, no money. No money, no winter. Stay close."]],
     "reach_the_market": [["We made it.", "Barely. Look at the chapel here."],
                          ["The same golden eye.", "Painted last week. The same smiling man came through, buying gold."]]}),
"p7_gold_at_double_weight": ("The smiling stranger is back in the village, buying gold and asking about people. "
    "He is the one behind the bell, the sheep, the eyes and the raiders. Hear him out, then let him walk or drive him out with steel.", {}),
"p7s_the_peddlers_gift": ("You let the Peddler walk, and he left you a book of cult scripture. The village elder needs to know what "
    "the village is up against. Show it to her.",
    {"show_the_elder": [["He gave me this.", "The Book of Belial... Burn it. No. Keep it. Know your enemy."],
                        ["What does it mean?", "That they think gold buys a god's favour. People who believe that will do anything for gold."]]}),
"p7b_peddlers_debt": ("You killed the Peddler. His friends found a note on your door: 'He paid for you in advance.' Gold-Sworn "
    "enforcers have come to collect. Kill them before they hurt the village.", {}),
"p8_pillager_moon": ("Whatever you did with the Peddler, the people who paid him are angry. The watchtower guards have seen a war "
    "party gathering. Warn the village, wait for nightfall, and hold the line until the last raider falls.",
    {"warn_the_village": [["A war party is coming.", "We saw the torches from the tower. North road. Lots of them."],
                          ["Get everyone inside.", "Doors barred, bell ready. We're with you."]]}),
"p9_who_pays_in_gold": ("The raiders carried gold they never looted, which means someone paid them to attack. Follow their tracks "
    "to a Pillager Camp (It Takes a Pillage), kill eight pillagers (patrols and outposts count too) and find their ledger in a barrel.", {}),
# ---------------------------------------------------------------- ACT I
"a1_ashford_burns": ("The camp's ledger said 'Ashford. Tonight.' Mara's letter confirms it: the raid on the next village has "
    "already started. Kill the raiders and the Brand-Carrier, then find Reeve Halden in the ruins.",
    {"find_halden": [["Are you Halden?", "Reeve of Ashford. What's left of it."],
                     ["What happened?", "They didn't kill the men. They took them. Fourteen of them. Tied and marched off."],
                     ["I'll find them.", "Then Ashford is yours to call on. Whatever we have left."]]}),
"a2_what_was_left": ("Ashford's homes burned and its men were taken. The families who are left have no food, bedding or fuel. "
    "Bring bread, wool and coal, and give them to the survivors.",
    {"give_to_the_families": [["Bread, wool and coal.", "We'll eat tonight. And sleep warm. Thank you."],
                              ["Did you see where they took the men?", "No. But look, a child found this. A gold coin with an eye on it."]]}),
"a3_roof_and_hearth": ("Halden won't let Ashford die. If the raiders come back, he wants them to find walls, not ashes. "
    "Ask him what to build, then help rebuild the homes.",
    {"ask_halden": [["What do you need?", "Walls and roofs. Ninety-six planks, thirty-two glass panes, four beds."],
                    ["Where?", "Here in Ashford. Anywhere a house burned."]]}),
"a4_refugee_road": ("Two Ashford families are too afraid to stay. They want to move to your village, but the Faithful, the "
    "cult's warriors, are watching the road. Meet the families, protect them on the road, and see them settled.",
    {"meet_the_families": [["You want to leave Ashford?", "Every night we hear them in the trees. We can't stay."],
                           ["I'll take you.", "Just don't let them take us like they took the others."]],
     "arrive_home": [["You're safe now.", "Safe. I'd forgotten what that felt like."],
                     ["Did you see anything on the road?", "The ones who attacked us whispered about 'pipes'. Pipes under the ground."]]}),
"a5_beneath_the_pipes": ("The Faithful talked about pipes, and Mara's note agrees: the cult moves its prisoners through old rusted "
    "pipes (a Cult of Azazel structure). Find the pipes and the keeper's log hidden in a barrel there.", {}),
"a6_believers_house": ("The pipe-keeper's log says the prisoners were moved through a believer house (Cult of Azazel). Find it "
    "and talk to the Preacher who lives there. Zealots will come for him once he's talked: kill them, then read the ledger in "
    "the house's barrel to learn where Ashford's men are now.", {}),
"a7_the_bastille": ("The ledger says Ashford's fourteen men are held in a Bastille, an illager fortress paid for with cult gold "
    "(It Takes a Pillage). Storm it: kill its legioners, archers and skirmishers.", {}),
"a8_ravagers_horn": ("The Bastille's ravagers will run down anyone who escapes. Kill two ravagers and bring back a Ravager Horn "
    "(every ravager drops one), so Halden can hang it over Ashford's gate as proof.", {}),
"a9_open_the_cells": ("You freed the prisoners who were left, but the Bastille's jailers are hunting them on the road home. "
    "Kill the jailers, then make sure the freed men reach a village safely.",
    {"bring_them_home": [["You're free.", "Not all of us. The rest were marched into a purple portal. North. Always north."],
                         ["Who gave the orders?", "Not the jailers. A man in a mansion in the dark forest. They called him the Invoker."]]}),
"a9i_the_invokers_fort": ("The freed men say the Bastille took its orders from the Invoker, an illager sorcerer (Illager Invasion) "
    "who lives in a Woodland Mansion in the dark forest. Find the mansion, cut through its guards and kill him.", {}, "The Invoker's Mansion"),
"a10_burning_door": ("The rest of Ashford's men were taken through a portal into the Nether. Build your own portal and follow them.", {}),
"a11_chains_at_the_fortress": ("The prisoners' trail leads to a Nether fortress. Find it, kill its wither skeletons, and bring "
    "whoever is still chained there back to the Overworld.", {}),
"a12_brother_vael": ("The freed men named the man who runs the cult's Nether trade: Brother Vael, a steward who buys gold, skulls and "
    "people. He is at the fortress. Hear what he has to say, then spare him or kill him.", {}),
"a12s_vaels_map": ("Spared, Vael scratched a map of the cult's gold route into the netherrack. Its gold-runners carry cult money "
    "to the Sacred City. Kill them and the cult's carts run empty for a while.", {}),
"a12k_the_stewards_guard": ("Vael's death did not go unnoticed. His wither-guard wants revenge. Kill them before they reach you.", {}),
"a13_tears_for_a_ward": ("Mara writes that the road ahead is long and you will need healing. She wants ghast tears brought to a cleric, "
    "who will brew regeneration for you.",
    {"deliver_to_the_cleric": [["Mara asked me to bring these.", "Ghast tears! Mara knows her medicine. Give me a night."],
                               ["What will you make?", "Regeneration. Enough to keep you standing when you shouldn't be."]]}),
"a14_night_of_the_faithful": ("You have broken the cult's prison, killed its jailers and robbed its trade. Now it has noticed you. "
    "Tonight the Faithful come for the village you protect. Hold it until dawn.", {}),
"a15_one_of_seven": ("The Faithful are dead and the village survived the night. Tell them it's over, for now.",
    {"tell_the_village": [["It's over.", "For tonight. Thank you. We'd all be gone without you."],
                          ["What did they want?", "One of them screamed something before he died. 'One of seven.' Whatever that means."]]}),
# ---------------------------------------------------------------- ROAD MEETS (Iron's Spells) and ACT II: THE WITHER
"i1_the_priests_warning": ("A village priest writes that the cult's Faithful are casting real spells now. To fight magic you have to "
    "learn it. Build an Inscription Table (Iron's Spells) and gather arcane essence.", {}),
"i2_fire_in_the_tower": ("The priest traced the cult's fire spells to a Pyromancer who sells them. Find his tower in the hills or "
    "forest and kill him.", {}),
"i3_the_evoker_fort": ("The Pyromancer's ledger says the magic came from an Evoker Fort, where an Archevoker taught the cult to cast. "
    "Find the fort and end the lessons.", {}),
"i4_the_scroll_forge": ("With the cult's teachers dead, the priest wants you ready for whatever comes next. Build a Scroll Forge so you "
    "can make spells of your own.", {}),
"w0_two_roads_meet": ("Steel and spell: both roads led to the same name, the Chariot. Tell the elder you're ready for what comes next.",
    {"tell_the_elder": [["Both trails lead to 'the Chariot'.", "Then the Chariot is who we're fighting."],
                        ["What now?", "Go home and rest. A courier is already looking for you."]]}),
"w1_the_skull_market": ("Mara writes that the cult is buying wither skulls: three of them summon the Wither. Go to the Nether, find a "
    "fortress and the cult's price list in a barrel there, and find out who is buying.", {}),
"w2_smite_the_dead": ("The cult wants the Wither. You'll be fighting the dead for a while, and the armourer can forge a weapon "
    "'that hates the dead' if you bring blaze rods and bones.",
    {"visit_the_armourer": [["I brought the makings.", "Good. Steel hates the dead if you teach it to."],
                            ["Why do the dead matter?", "The Wither is made from dead things. So is its army."]]}),
"w3_the_skull_harvest": ("Every wither skeleton you kill is a skull the cult can't harvest. Thin their numbers in the fortress, "
    "and kill the blazes that guard it.", {}),
"w4_hold_the_black_bridge": ("The cult sends its Bone Choir to take the fortress back. Hold the bridge and kill the Choir-Master.", {}),
"w5_a_grave_for_a_nail": ("Mara writes that the Wither is one of seven 'Wardens' the cult wants to control. If the cult raises it, "
    "it will serve them. Raise it yourself, far from any village, and kill it: four soul sand, three wither skulls, then fight.", {}),
"w6_the_withered_crown": ("The Wither is dead. Bring its Nether Star out of the pit. You'll need it for the beacon.", {}),
"w7_a_light_they_cannot_dim": ("Turn the Wither's star into a beacon over your village, so everyone can see the cult did not win.", {}),
"w8_six_remain": ("Mara's last letter had a strange line in it. Read it to someone and see what they make of it.",
    {"show_the_letter": [["Can you read this?", "'The first is done. Six remain... I mean, the hard part remains.'"],
                         ["Six what?", "That's what I'd like to know. Six more Withers?"]]}),
"i5_the_dead_king": ("With the Wither dead, the dead are restless, and the priest has traced it to the Catacombs. Something is "
    "waking under them. Find the Catacombs and kill the Dead King when he rises from his throne.", {}),
"i6_the_mountain_tower": ("The Dead King's crown named him 'Champion of M. Second of seven. Failed.' His journal points to a mountain "
    "tower where a Cryomancer guards the notes of another failed champion. Find the tower and kill the Cryomancer.", {}),
"i7_echo_of_tyros": ("The frozen notes say the god's oldest fire still burns: the Echo of Tyros, in a Nether Citadel. Find the Citadel and "
    "kill two Ancient Knights for Cinder Essence. Craft a Cinderous Soulcaller (4 Cinder Essence, 2 Netherite Scrap, the Echo Shard "
    "from the mountain tower) and ring it at the Citadel's glowing rune to summon Tyros. Then put him out.", {}),
"i8_the_arcane_anvil": ("Two champions failed before you, and both died without magic in their steel. Build an Arcane Anvil so you can "
    "bind spells into your weapons before the End.", {}),
# ---------------------------------------------------------------- ACT III: THE AETHER
"h1_a_light_above_the_pit": ("Villagers saw a beam of light rise from the Wither's pit into the clouds. Mara says to ignore it. "
    "The cleric's grandmother said glowstone and water open a door to heaven. Build an Aether portal.", {}),
"h2_hostile_paradise": ("You are in the Aether, a sky realm that should be full of light and is strangely empty. Gather what you need "
    "to survive: holystone, ambrosium and skyroot.", {}),
"h3_do_you_believe_in_magic": ("The Aether's tools are made at an Altar. Build one and find Zanite for better gear.", {}),
"h4_cockatrice_nights": ("Without whoever used to rule the Aether, its beasts have gone wild. Thin the cockatrices and zephyrs so "
    "you can explore the dungeons.", {}),
"h5_the_bronze_halls": ("The dungeons hold the Aether's history, and maybe the truth about the light from the pit. Find the Bronze "
    "Dungeon, clear its sentries and mimic, and kill the Slider in its heart.", {}),
"h6_like_a_bossaru": ("The Aether's islands are far apart. Build a road of cold aercloud from your portal toward the dungeons so you "
    "never have to jump blind again.", {}, "A Road of Cloud"),
"h7_medals_of_the_silver_court": ("The Silver Dungeon is the Valkyrie Queen's court. She only duels those with ten Victory Medals, "
    "and Valkyries drop one each. Find the dungeon, fight the Valkyries and the Queen, and read the hymn in its barrel.", {}),
"h8_dethroned": ("The Queen knew the truth about the Charioteer and kept it. Build a cairn of holystone bricks for the Valkyries "
    "who died for her.", {}, "A Cairn for the Valkyries"),
"h9_extinguished": ("The hymn says the Sun Spirit threw Azriel the Charioteer and Maraal out of heaven. Find the Gold Dungeon and kill "
    "the Sun Spirit before it does the same to you.", {}),
"h10_fireproof": ("You came back with a name you can't stop thinking about: Maraal. Tell someone what you saw up there.",
    {"tell_a_villager": [["I've been to the sky.", "The sky? You're joking. ...You're not joking."],
                         ["There was a name. Maraal.", "Like Mara? Old Mara? ...A courier came for you while you were gone."]]}),
# ---------------------------------------------------------------- ACT IV: THE END
"e0_the_road_to_the_end": ("Two of Mara's champions failed before you. You've seen their bones. Tell the elder you're going on anyway.",
    {"tell_the_elder": [["I'm going to the End.", "Even after what you found in the catacombs?"],
                        ["Even then.", "Then Mara's courier is already on the road. She always knows."]]}),
"e1_the_librarians_request": ("A librarian who has studied the cult's Old Tongue for thirty years wants to help you find the End. "
    "She needs books and ink to copy what she finds.",
    {"meet_the_librarian": [["You can read the Old Tongue?", "Thirty years. Enough to be frightened properly."],
                            ["How do I reach the End?", "Twelve eyes of the old kind. Find them, and a stronghold. I'll come."]]}),
"e3_the_road_underground": ("With all the eyes found, find a stronghold. The cult wants its library too and has sent Faithful "
    "and a hollow pilgrim to get there first.", {}),
"e4_the_warden_journal": ("The cult was looking for a journal in the stronghold library. Find it in a barrel before they do.", {}),
"e5_free_the_end": ("Through the stronghold's portal is the End. The journal calls its dragon the second Warden. Kill it.", {}),
"e6_edge_of_the_world": ("Before you leave the End, find an End City and find out how far the cult reached.", {}),
"e7_the_nether_is_singing": ("The librarian has finished copying the Warden journal and wants to talk about what it says.",
    {"talk_to_the_librarian": [["What does the journal say?", "That the seven were not monsters. They were wardens. Jailers."],
                               ["Jailers of what?", "It doesn't say. One page is missing."]]}),
# ---------------------------------------------------------------- ACT V: BOSSES' RISE
"s1_cold_hearths": ("Mara writes that five more beasts stir, and asks you to start in the snow. A snowy village is freezing because "
    "its men won't leave the old garrison post. Bring coal and logs and talk to them.",
    {"warm_the_village": [["Coal and logs.", "We'll survive the week. Thank you."],
                          ["Why won't anyone go out?", "Our grandfathers guarded something out there. They never came back. Their ghosts still patrol."]]}),
"s2_the_frozen_garrison": ("The old garrison still patrols the Yeti Hideout in the snowy plains. Find it, lay the frozen soldiers to "
    "rest, kill Skor the Yeti (Bosses' Rise) and read the garrison roll in its barrel.", {}),
"s3_lost_in_the_snow": ("Skor is dead. Tell the snowy village their grandfathers can rest.", {}),
"d1_the_buried_well": ("A desert village filled in its own well 'so the ground would stop breathing'. Their sick need milk, and the "
    "husks need clearing. Then tend the sick.",
    {"tend_the_sick": [["Milk, as you asked.", "It'll draw the poison. These wounds aren't from husks."],
                       ["Then from what?", "Something under the sand. The husks herd towards it."]]}),
"d2_the_nest_road": ("The husks are being herded toward the Sandworm Nest. Kill the Dune Faithful, find the nest and kill Sirok, "
    "the Sandworm.", {}),
"d3_the_beast_below": ("Sirok is dead. Tell the desert village they can dig their well out.", {}),
"c1_nets_and_knives": ("A fishing village won't sail past the black ship anchored offshore. Help them rebuild their dock so they can "
    "fish closer to shore.", {}),
"c2_the_tidecaller": ("Drowned are coming up the beach at night, led by something that calls the tide. Hold the beach.", {}),
"c3_the_black_ship": ("Board the Kraken Ship in the deep ocean. Kill its crossbowman, rook and captain, and the Kraken, Nerakyss, "
    "will rise. Kill it too.", {}),
"c4_terror_of_the_depths": ("Nerakyss is dead. Tell the fishing village the black ship is gone.", {}),
"u1_the_soul_guard": ("In the Nether's wastes or soul sand valleys stands the Underworld Arena. Clear its soul guard and kill Helvar, "
    "the Underworld Knight who holds its gate.", {}),
"u2_helvars_oath": ("Helvar couldn't speak. He left his oath written down in a barrel in the arena. Read it.", {}),
"u3_back_into_hell": ("Leave the Nether. What Helvar wrote is going to follow you.", {}, "Out of the Underworld"),
"f1_the_last_road": ("Mara writes: 'The last one was his once. His mount.' Find the Dragon Tower, fight its skeleton guards, and kill "
    "Ashlord, the Infernal Dragon.", {}),
"f2_dragon_slayer": ("Bring Ashlord's bones home to show the smith. (They're given back.)", {}),
"f3_unbound": ("All seven Wardens are dead. Tell someone.",
    {"tell_a_villager": [["It's done. All seven.", "Seven beasts dead. You'd think we'd feel safer."],
                         ["You don't?", "Why don't we feel safer?"]]}),
# ---------------------------------------------------------------- ACT VI: SONS OF SINS
"v1_blood_in_the_wells": ("Mara says the cult will try to grow its god a new body in blood pits. Before you go, the librarian wants to "
    "show you what's been found in the well water. (Reward: a Sickle of Struggle. Hit creatures with it while carrying empty "
    "glass bottles and you catch their blood.)",
    {"deliver_to_the_librarian": [["Blood in the wells?", "Poured, not spilled. Someone is feeding the ground."],
                                  ["Where from?", "A Sinful Church to the south. Take this sickle. You'll want samples."]]}),
"v2_the_sinful_church": ("The blood comes from a Sinful Church (Sons of Sins). Find it, kill the Devourers born in its blood, and read "
    "the recipe the cult is following in a barrel there.", {}),
"v3_the_midwife": ("The woman who runs the blood pits is at the church. Hear her out, then spare her or end her.", {}),
"v3s_cleansing_the_pits": ("Spared, the Midwife shows you how to sour her blood pits: bury them under soul soil.", {}),
"v3k_the_coven": ("The Midwife's sisters come for you a day later. They want blood for blood.", {}),
"v4_original_sin": ("The pits' failures crawl out at night as Betrayed Guzzlers, bloated things that drink livestock dry. Kill three.", {}),
"v5_jaws_in_the_dark": ("Villagers who drank from the wells are rising at night as Blood-Born. Hold the village until they stop.", {}),
"v6_what_eats_the_dead": ("To forge armour that can stand in front of a god you'll need Ether Ashes. Creatures killed while standing in "
    "blood, by a bone weapon (an Osseous sword or axe), leave ash. The church's blood pools work. Collect eight. (They're given back.)", {}),
"v7_soul_steel": ("Dip iron ingots into blood, or smelt Iron and Ether, to make Soul Steel. Craft a Soul Steel block and place it. "
    "Then craft the Remnant (Sinbuster) armour: the next chapter begins the first time its chestplate catches a heavy blow while you "
    "carry an Urn of ether ashes.", {}),
"v8_sinbuster": ("You wear the Sinbuster now: armour that can 'imprison attacks too brutal for the mortals'. Show the smith.",
    {"show_the_smith": [["What do you think?", "That's not armour. That's a cage you wear."],
                        ["It'll hold.", "Against what? ...Don't tell me."]]}),
"v9_the_wrong_parcel": ("Mara's courier delivered the wrong bundle. Inside is the page torn from the Warden journal. Ask around about "
    "Old Mara.",
    {"ask_about_mara": [["How long has Old Mara lived here?", "She's been old as long as anyone remembers. My grandmother said the same."],
                        ["Where does she live?", "Nobody knows. Her letters just... arrive."]]}),
"v10_maraal": ("She was waiting for you to find the page. Mara is Maraal. Hear her out, then forgive her or reject her.", {}),
"v10f_walking_with_maraal": ("You forgave her. She walks beside you and finally talks about the old days. Rest a night and listen.", {}),
"v10r_alone": ("You rejected her. Her letters have stopped. You'll have to learn the Nether the hard way.", {}),
"v11_the_crimson_guide": ("Ghastlies, the crimson bees of the Nether, know the way to the Nether Mansion. Go below and tame one. "
    "The next chapter starts once one follows you.", {}),
# ---------------------------------------------------------------- ACT VII-VIII
"m1_follow_the_crimson_bee": ("Your Ghastly hums north. Follow it to the Nether Mansion, the Cult of Azazel's Sacred City, and "
    "talk to the Welcomer who greets every pilgrim at its gate.", {}),
"m2_the_cursed_quarries": ("Ashford's men are mining gold for a god in the Mansion's quarries. Kill the Manipulators who drive them.", {}),
"m3_hold_the_quarry_gate": ("The freed prisoners need time to reach your portal. Hold the quarry gate against the piglin wardens.", {}),
"m4_gilded_hunger": ("In the Sacred City, Gilded Golems drink gold and blood. Kill three, then talk to the Doctor who was "
    "watching. He and the Blacksmith will trade with you.", {}),
"m5_do_not_provoke_gehinnom": ("The cult's guidebook says 'best not to provoke' the Gehinnom guardians. You have to, to reach the Lair.", {}),
"m6_the_instruction": ("Reach Azazel's Lair at the end of the Mansion. A barrel there holds the instructions to summon him.", {}),
"m7_hand_over_your_weapon": ("Fight the Divine Chariot Azazel in his Lair. If you hand him a sword or axe he lets you live and you'll "
    "have to come back. Kill him and take his Staff of Manipulation.", {}),
"m9_half_a_soul": ("A letter arrives the moment you're home. Read it to someone.",
    {"read_it_to_someone": [["Listen to this.", "'The head is dead, so the body wakes. A half-soul cannot die.'"],
                            ["Go on.", "'To end him, join his head to his body. Then kill him while he is whole.'"]]}),
"z1_caution": ("The guidebook's trail: 'Pipes. Compass. Pillar. Head. Puzzle. Maze. Caution. Up. God?' Find the maze.", {}),
"z2_up": ("Above the maze waits the head altar, with 'God?' carved into the stone. Find it.", {}),
"z3_choir_of_the_whole_god": ("Everything the cult has left comes to the altar, to stop the reunion or to watch it. Kill them all.", {}),
}

ROAD_INTRO = {"rl1_the_lantern_oath", "ra1_ash_on_your_hands", "rg1_protection_money"}


def apply():
    for k, entry in T.items():
        if k not in QUESTS:
            raise SystemExit(f"story_text: unknown quest {k}")
        q = QUESTS[k][1]
        summary, talks = entry[0], entry[1]
        q["summary"] = summary
        if len(entry) > 2:
            q["title"] = entry[2]
        for gl in q["goals"]:
            if gl["id"] in talks:
                gl["_talk"] = talks[gl["id"]]
        missing = set(talks) - {gl["id"] for gl in q["goals"]}
        if missing:
            raise SystemExit(f"story_text: {k} has no goals {missing}")
