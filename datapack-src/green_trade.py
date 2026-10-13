"""Act V's underworld arc, decided by where you stand on the reputation scale.

The Blackwater Cartel's drug money pays a third of everything to the cult. How you deal with that depends on you:
  - Fallen / Wicked: The Green Trade. You join the trade from the inside and climb to King of the Valley.
    Halfway up, a witness gives you one chance to get out (Getting Out); silence him and you go all the way.
  - Wavering: A Generous Offer. A Blackwater recruiter makes you an offer. Take it (The Green Trade) or refuse it
    hard (The Blackwater Raid).
  - Saint / Good: The Blackwater Raid. You help the Watch tear the cartel down and get medicine to the valley.
The letter arrives when your Act V reputation chapter ends; the main story waits (The Last Road) until the arc is done."""
from lib import *

AD = "addictives"
II = "illagerinvasion"
FIX = "The Fixer"

quest("gt1_a_note_in_the_stash", "A Note in the Stash",
      "Mara writes that the Blackwater Cartel's drug money pays the cult's keepers in the snow, the sand and the sea. The only way to "
      "their Don is from inside. Grow a sample of weed (Create: Addictives) and find the Fixer, who is hiring growers.",
      [collect("sample", f"{AD}:weed", 16, 0),
       talk("meet_the_fixer", 1, "The Fixer weighs the sample in one hand and you in the other. 'You'll do.'", name=FIX)],
      book=book("A Note in the Stash", "Old Mara", [
          "Forgive me for this one.\n\nThe Blackwater Cartel pays the cult a third of everything. That money keeps the beasts' keepers fed. To reach their Don you must become one of them.\n\nThis note was in one of their stashes. - M.",
          "If you found this, you know what the seeds are for.",
          "The valley pays well for green. Grow some. Bring me a sample. Ask for the Fixer in any village two hills over.",
          "Don't bring the guards. Don't bring the priest. Don't bring your conscience."], color="green"),
      rewards={"xp": 300, "items": {"minecraft:emerald": 16}},
      nxt=flow("gt2_seeds_of_the_trade"))
QUESTS["gt1_a_note_in_the_stash"][1]["goals"][1]["_far"] = True
FIXER_TALK = [["Who are you?", "Nobody. That's the job. Nobody knows the Fixer, and the Fixer knows every fucker in the valley."],
              ["How'd you get into this?", "My dad grew wheat. The wheat failed and the cult's tithe didn't. You do the maths."],
              ["Can I trust you?", "Fuck, no. But you can trust that I like money, and right now you make me money. Same thing, mostly."]]
QUESTS["gt1_a_note_in_the_stash"][1]["goals"][1]["_talk"] = FIXER_TALK

quest("gt2_seeds_of_the_trade", "Seeds of the Trade",
      "The Fixer wants a real operation, not a windowsill. Plant a field and fill an order.",
      [place_anywhere("plant_the_field", f"{AD}:weed_crop", 16, 0),
       collect("first_order", f"{AD}:weed", 32, 1)],
      rewards={"xp": 500, "items": {"minecraft:emerald": 24}},
      nxt=flow("gt3_first_customers"))

quest("gt3_first_customers", "First Customers",
      "A nervous buyer in another village wants rolled product, delivered by hand.",
      [collect("joints", f"{AD}:joint", 8, 0),
       talk("the_buyer", 1, "He pays in emeralds and won't meet your eyes. 'Same time next week?'", name="Nervous Buyer")],
      rewards={"xp": 500, "items": {"minecraft:emerald": 32}},
      nxt=flow("gt4_the_hash_market"))
QUESTS["gt3_first_customers"][1]["goals"][1]["_far"] = True

quest("gt4_the_hash_market", "The Hash Market",
      "Hash moves faster and pays better. Set up your Create machines and fill the Fixer's order.",
      [collect("hash", f"{AD}:hash", 16, 0),
       talk("deliver_to_the_fixer", 1, "'Now we're talking. You've got a knack for this, which should worry you.'", name=FIX)],
      rewards={"xp": 800, "items": {"minecraft:emerald": 48}},
      nxt=flow("gt5_blackwater_wants_a_word", delay=2400))

quest("gt5_blackwater_wants_a_word", "Blackwater Wants a Word",
      "The Blackwater Cartel runs this valley, and Blackwater is run by illagers. They've noticed a new grower.",
      [kill("enforcers", "minecraft:vindicator", 4, 0, "The last one gasps: 'Don Vasco says... this is a courtesy.'")],
      threats=[threat("minecraft:vindicator", "Blackwater Enforcer", count=4, hp=56, smin=10, smax=20,
                      equipment=eq(mainhand=("minecraft:iron_axe", {"minecraft:sharpness": 2}), head="minecraft:leather_helmet"),
                      effects=fx(("minecraft:strength", 0)))],
      rewards={"xp": 900, "items": {"minecraft:emerald": 24}},
      nxt=flow("gt6_the_coca_fields"))

quest("gt6_the_coca_fields", "The Coca Fields",
      "The Fixer wants to move into white. Plant coca and bring in a harvest.",
      [place_anywhere("plant_coca", f"{AD}:coca_crop", 16, 0),
       collect("leaves", f"{AD}:coca_leaf", 32, 1)],
      rewards={"xp": 800, "items": {"minecraft:emerald": 40}},
      nxt=flow("gt7_white_powder_money"))

quest("gt7_white_powder_money", "White Powder Money",
      "Process the leaves and bring the Fixer his first big order.",
      [collect("product", f"{AD}:cocaine", 16, 0),
       talk("deliver", 1, "The Fixer counts emeralds into your hands until they spill. 'This is what the valley pays.'", name=FIX)],
      rewards={"xp": 1200, "items": {"minecraft:emerald": 64, "minecraft:emerald_block": 2}},
      nxt=flow("gt8_a_customer_goes_under"))

quest("gt8_a_customer_goes_under", "A Customer Goes Under",
      "The nervous buyer's friend comes running: he took too much of what you sold and isn't waking up. Bring Narcan, fast. "
      "Afterwards his friend has something to say to you, and what you do about it decides whether you're still in this trade.",
      [collect("narcan", f"{AD}:narcan", 1, 0),
       talk("save_him", 1, "He gasps awake. His friend won't look at you. 'He'll live. Will the next one?'", name="Buyer's Friend"),
       kill("the_witness", "minecraft:villager", 1, 2,
            "'I'm going to the Watch. I'll tell them everything, your name first. Unless you stop me.'")],
      threats=[threat("minecraft:villager", "Pell, the Buyer's Friend", stage=2, hp=30, spare=True, smin=3, smax=6,
                      talk=[["You're going to the Watch?", "Tonight. Halden will listen. He listens to everybody."],
                            ["And if I go with you?", "Then you'd be the first grower who ever did. They'd go easy on you. Maybe."],
                            ["And if I don't?", "Then you'd better be quicker than me. I'm not much of a runner."]])],
      choice={"spare": {"label": "Go with him to the Watch (get out)",
                        "line": "Pell lets out a breath. 'Come on, then. Before you change your mind.'", "traits": ["gt_getting_out"]},
              "attack": {"label": "Silence him (stay in)", "traits": ["gt_silenced_the_witness"]}},
      rewards={"xp": 1000, "items": {"minecraft:golden_apple": 2}},
      traits={"complete": ["gt_saved_a_buyer"]},
      on_spare=flow("go1_burn_it_down", delay=1200), on_kill=flow("gt9_turf_war"))
QUESTS["gt8_a_customer_goes_under"][1]["goals"][1]["_far"] = True

quest("gt9_turf_war", "Turf War",
      "Blackwater is done being polite. Their crew is hitting your fields tonight.",
      [wait("wait_for_dark", 0, "next_night"),
       kill("crew", "minecraft:vindicator", 6, 1),
       kill("shooters", "minecraft:pillager", 3, 1),
       kill("lieutenant", "minecraft:evoker", 1, 1, "The Lieutenant's ring has an address: a sorcerer's hut in the dark forest.")],
      threats=[threat("minecraft:vindicator", "Blackwater Crew", stage=1, count=6, hp=56, smin=12, smax=26,
                      equipment=eq(mainhand=("minecraft:iron_axe", {"minecraft:sharpness": 2}))),
               threat("minecraft:pillager", "Blackwater Shooter", stage=1, count=3, hp=40, smin=14, smax=28,
                      equipment=eq(mainhand=("minecraft:crossbow", {"minecraft:piercing": 2}))),
               threat("minecraft:evoker", "Blackwater Lieutenant", stage=1, hp=140, smin=14, smax=24,
                      effects=fx(("minecraft:resistance", 0), ("minecraft:strength", 0)))],
      rewards={"xp": 1800, "items": {"minecraft:emerald": 48, "minecraft:totem_of_undying": 1}},
      nxt=flow("gt10_the_sorcerers_books"))

quest("gt10_the_sorcerers_books", "The Sorcerer's Books",
      "Blackwater's accounts are kept by an illager Sorcerer in a dark-forest hut. Take the books.",
      [reach("find_the_hut", f"{II}:sorcerer_hut", 0, radius=30000),
       kill("sorcerer", f"{II}:sorcerer", 1, 1),
       chest_book("ledger", 2, f"{II}:sorcerer_hut", "Blackwater Ledger", "hag_blackwater_ledger", "Blackwater Ledger", "The Sorcerer",
                  ["Weekly takings: 3,000 emeralds. Tithe to the Chariot: one third.",
                   "Don Vasco holds court in the Labyrinth. Bring tribute or bring an army."],
                  "A third of everything goes to the Chariot. Even the cartel pays the cult.", radius=30000)],
      rewards={"xp": 2000, "items": {"minecraft:emerald": 64}},
      nxt=flow("gt11_crystal"))

quest("gt11_crystal", "Crystal",
      "The Fixer wants the one product Blackwater can't make: crystal. Nobody in the valley makes it purer.",
      [collect("crystal", f"{AD}:meth", 16, 0),
       talk("deliver", 1, "The Fixer goes quiet, then very rich. 'With this, we don't need Blackwater. We need Blackwater gone.'", name=FIX)],
      rewards={"xp": 2000, "items": {"minecraft:emerald_block": 6}},
      nxt=flow("gt11b_purity", delay=2400))

quest("gt11b_purity", "Purity",
      "Blackwater has a cook of its own: a witch in a swamp hut they call the Chemist, who cuts her crystal with chalk and "
      "lye. Buyers are going blind on it, and blaming yours. The Fixer wants her gone, and he wants the valley to know "
      "whose product is clean: put her out of business, then bring him a bigger batch than she ever made.",
      [reach("find_the_cook", "minecraft:swamp_hut", 0, radius=12000),
       kill("the_chemist", "minecraft:witch", 1, 1, "Her cauldron is full of something grey that smokes. Chalk, lye, and worse. "
                                                    "Her notebook says 'Blackwater pays by the pound, not by the buyer.'"),
       collect("a_clean_batch", f"{AD}:meth", 32, 2),
       talk("deliver_the_batch", 3, "The Fixer holds a crystal up to the light and laughs like a child. 'Clean. Fucking "
                                    "clean. Now every junkie in the valley knows whose name to ask for. Yours.'", name=FIX)],
      threats=[threat("minecraft:witch", "Blackwater's Chemist", stage=1, hp=120, structure="minecraft:swamp_hut", search=12000,
                      smin=2, smax=8, effects=fx(("minecraft:resistance", 0))),
               threat("minecraft:vindicator", "The Chemist's Guard", stage=1, count=3, hp=56, structure="minecraft:swamp_hut",
                      search=12000, smin=4, smax=12, equipment=eq(mainhand=("minecraft:iron_axe", {"minecraft:sharpness": 2})))],
      rewards={"xp": 2200, "items": {"minecraft:emerald_block": 6}},
      traits={"complete": ["gt_clean_crystal"]},
      nxt=flow("gt12_don_vasco"))

quest("gt12_don_vasco", "Don Vasco",
      "Don Vasco, head of Blackwater, waits in the Labyrinth. Make him a partner, or make him an example.",
      [reach("find_the_labyrinth", f"{II}:labyrinth", 0, radius=30000),
       kill("don_vasco", "minecraft:evoker", 1, 1)],
      threats=[threat("minecraft:evoker", "Don Vasco", stage=1, hp=260, spare=True, structure=f"{II}:labyrinth", search=30000,
                      effects=fx(("minecraft:resistance", 1), ("minecraft:strength", 1)),
                      talk=[["Don Vasco.", "The famous grower. Sit. Nobody fights in my house unless I say so."],
                            ["Your crew hit my fields.", "And you burned my lieutenant. We are even. Now we talk business."],
                            ["What are you offering?", "Half the valley. Together we stop paying the Chariot its third."]],
                      attack_ambushes=[{"entity": "minecraft:vindicator", "name": "Vasco's Bodyguard", "count": 4, "max_health": 70,
                                        "equipment": {"mainhand": "minecraft:diamond_axe"}}])],
      choice={"attack": {"label": "Make him an example", "traits": ["gt_war_won"]},
              "spare": {"label": "Shake his hand", "line": "'Partners, then. The Chariot will not like this.'", "traits": ["gt_partners"]}},
      rewards={"xp": 3000, "items": {"minecraft:emerald_block": 4}},
      on_kill=flow("gt13w_remnants", delay=2400), on_spare=flow("gt13p_the_big_shipment"))

quest("gt13w_remnants", "Remnants",
      "Blackwater's survivors want revenge for their Don. Finish the war.",
      [kill("remnants", "minecraft:vindicator", 8, 0, "Blackwater is finished. The valley is yours.")],
      threats=[threat("minecraft:vindicator", "Blackwater Remnant", count=8, hp=60, smin=10, smax=24,
                      equipment=eq(mainhand=("minecraft:diamond_axe", {"minecraft:sharpness": 2})))],
      rewards={"xp": 3000, "items": {"minecraft:emerald_block": 8}},
      nxt=flow("gt14_king_of_the_valley"))

quest("gt13p_the_big_shipment", "The Big Shipment",
      "With Blackwater as partners, the first joint shipment goes out. Fill your half.",
      [collect("cocaine", f"{AD}:cocaine", 32, 0),
       collect("hash", f"{AD}:hash", 32, 1),
       talk("hand_over", 2, "Don Vasco's men load the wagons. 'Pleasure doing business.'", name=FIX)],
      rewards={"xp": 3000, "items": {"minecraft:emerald_block": 10}},
      nxt=flow("gt14_king_of_the_valley"))

quest("gt14_king_of_the_valley", "King of the Valley",
      "You run the valley's green trade now. Show the Fixer what you've built.",
      [talk("the_fixer", 0, "'Every week, the money comes. Every week, someone pays for it. Try not to count those.'", name=FIX)],
      rewards={"xp": 5000, "items": {"minecraft:emerald_block": 16, "minecraft:diamond_block": 2}},
      traits={"complete": ["gt_kingpin", "hag_act5_arc_done"]})

# ---- the Fixer's price (a coda, by courier, a while after you're king): he sells your name to the Watch
courier("gt_nothing_personal", "gt14_king_of_the_valley", "gt15_the_fixers_price", sender="The Fixer", lo=12000, hi=24000)
quest("gt15_the_fixers_price", "The Fixer's Price",
      "The Fixer is gone, and so is half the strongbox. He left a letter: the Watch offered him a clean name for yours, and "
      "he took it. He's hiding with hired crossbows at a pillager outpost until the Watch comes to collect him. "
      "Get there first. Then decide what a man like that is worth.",
      [reach("find_the_fixer", "minecraft:pillager_outpost", 0, radius=12000),
       kill("the_crossbows", "minecraft:pillager", 4, 1),
       kill("the_fixer", "minecraft:villager", 1, 2, "He doesn't run. 'I'd have done the same to you,' he says. 'You know I would. "
                                                      "You'd have done it first, if you'd thought of it.'")],
      threats=[threat("minecraft:pillager", "The Fixer's Hired Crossbow", stage=1, count=4, hp=48, structure="minecraft:pillager_outpost",
                      search=12000, smin=4, smax=14, equipment=eq(mainhand=("minecraft:crossbow", {"minecraft:quick_charge": 2}))),
               threat("minecraft:villager", "The Fixer", stage=2, hp=40, spare=True, structure="minecraft:pillager_outpost",
                      search=12000, smin=2, smax=6,
                      talk=[["Why?", "Because the Watch was coming for one of us, and I've always liked me better. Nothing personal."],
                            ["After everything I built.", "You built it on my name and my buyers. I just took my half back early."],
                            ["They'll hang you anyway.", "No they fucking won't. I'm a witness now. Witnesses get to live. Ask Pell. Oh. Right."]])],
      choice={"attack": {"label": "Nobody sells me", "traits": ["gt_fixer_killed"]},
              "spare": {"label": "Let him run to the Watch", "line": "He backs away, then runs, and doesn't look back. "
                                                                         "He'll tell them everything. He was always good at talking.",
                        "traits": ["gt_fixer_spared"]}},
      book=book("Nothing Personal", "The Fixer", [
          "You'll have noticed the strongbox.\n\nI took my half. Call it a finder's fee: I found you, after all.",
          "The Watch came to me last week. They know everything except your name, and they offered me my whole life back "
          "for it. A clean name. No more looking over my shoulder.",
          "I said yes. Of course I said yes. You'd have said yes.\n\nNothing personal. It never was.\n\n- F."], color="green"),
      rewards={"xp": 2500, "items": {"minecraft:emerald_block": 8}},
      traits={"accept": ["gt_fixer_talked"]})
QUESTS["gt15_the_fixers_price"][1]["dialogue"] = {"reward": "The outpost is quiet. Somewhere, the Watch is writing your name down anyway."}

# =====================================================================================================
# GETTING OUT (you spared the witness, or the mod saw you climb back to Good mid-trade)
# =====================================================================================================
quest("go0_a_way_out", "A Way Out",
      "Reeve Halden knows what you've been growing. He also knows you've been doing good lately. He's offering one way out.",
      [talk("halden", 0, "'I won't pretend I'm not disappointed. But you came back. Let's finish this properly.'", name="Reeve Halden")],
      book=book("A Way Out", "Reeve Halden", [
          "I know about the fields.\n\nI also know about the things you've done since. People talk about both.",
          "Come and see me. There's a way out of the Blackwater trade that doesn't end in a ditch. It will cost you your fields "
          "and your profit, and it will be worth it.\n\n- Halden"], color="green"),
      rewards={"xp": 500},
      nxt=flow("go1_burn_it_down", delay=600))

quest("go1_burn_it_down", "Burn It Down",
      "Getting out starts with the fields. The Watch needs to see them gone, every plant, so nobody can ever say you kept a "
      "stash for later. Tear up sixteen of your weed plants (plant more first if you've harvested them), then tell Halden it's done.",
      [breakb("tear_up_the_crop", f"{AD}:weed_crop", 16, 0, "The field is bare. It feels lighter than it should.", rng=30000000),
       talk("tell_halden", 1, "'Good. Now they'll come for you. Stay close to the village tonight.'", name="Reeve Halden")],
      rewards={"xp": 800},
      traits={"complete": ["gt_fields_burned"]},
      nxt=flow("go2_blackwater_sends_regards", delay=2400))

quest("go2_blackwater_sends_regards", "Blackwater Sends Its Regards",
      "Blackwater doesn't let growers retire. Their crew is coming tonight to remind you whose you are. Make sure they "
      "never deliver that message to anyone again.",
      [wait("wait_for_dark", 0, "next_night"),
       kill("crew", "minecraft:vindicator", 6, 1, "The last enforcer runs. Blackwater knows now: you're out, and you're expensive.")],
      threats=[threat("minecraft:vindicator", "Blackwater Collector", stage=1, count=6, hp=56, smin=12, smax=24,
                      equipment=eq(mainhand=("minecraft:iron_axe", {"minecraft:sharpness": 2})))],
      rewards={"xp": 1500, "items": {"minecraft:emerald": 24}},
      nxt=flow("go3_testimony"))

quest("go3_testimony", "Testimony",
      "Halden can't touch Blackwater without proof. Their books are kept by an illager Sorcerer in a dark-forest hut. "
      "Take the ledger and put it in Halden's hands. Your name is in it too. That's the price of getting out.",
      [reach("find_the_hut", f"{II}:sorcerer_hut", 0, radius=30000),
       kill("sorcerer", f"{II}:sorcerer", 1, 1),
       chest_book("ledger", 2, f"{II}:sorcerer_hut", "Blackwater Ledger", "hag_blackwater_ledger_out", "Blackwater Ledger", "The Sorcerer",
                  ["Weekly takings: 3,000 emeralds. Tithe to the Chariot: one third.",
                   "New grower (see page 9). Promising. Watch him."],
                  "There's your name on page nine. You take the book anyway.", radius=30000),
       talk("give_halden_the_ledger", 3, "Halden reads page nine, then closes the book. 'You'll testify. And then you'll be free.'",
            name="Reeve Halden")],
      rewards={"xp": 3000, "items": {"minecraft:emerald": 32, "minecraft:golden_apple": 2}},
      traits={"complete": ["gt_got_out", "hag_act5_arc_done"]})

# =====================================================================================================
# A GENEROUS OFFER (Wavering): the trade comes to you
# =====================================================================================================
quest("tm1_a_generous_offer", "A Generous Offer",
      "A letter with a green wax seal: someone wants to meet you on the road. Blackwater has heard you're the kind of hero "
      "who can be talked to. Hear the offer. Take it, or refuse it in a way they'll remember.",
      [kill("the_recruiter", "minecraft:vindicator", 1, 0, "The recruiter waits for your answer, smiling.")],
      threats=[threat("minecraft:vindicator", "Blackwater Recruiter", hp=80, spare=True, smin=6, smax=12,
                      talk=[["What's the offer?", "Grow for us. Sell for us. Get rich. We already pay the cult; you'd just be paying yourself."],
                            ["Why me?", "Because you're not a saint. Saints don't get letters from us. You did."],
                            ["And if I say no?", "Then we're enemies, and you'd better be very good at it."]])],
      choice={"spare": {"label": "Take the job", "line": "'Wise. The Fixer will find you. Bring a sample.'", "traits": ["gt_took_the_offer"]},
              "attack": {"label": "Refuse. Hard.", "traits": ["gt_refused_the_offer"]}},
      book=book("A Generous Offer", "A friend in the valley", [
          "We've been watching you.\n\nYou are not like the other heroes. You understand that the world is not clean.",
          "Meet our man on the road and hear what we have to offer. You'll find him near wherever you read this.\n\n- B."], color="green"),
      rewards={"xp": 400},
      on_spare=flow("gt1b_the_recruiters_terms", delay=1200), on_kill=flow("br2_the_supply_line", delay=2400))

quest("gt1b_the_recruiters_terms", "The Recruiter's Terms",
      "You said yes. Blackwater wants proof you can grow: sixteen weed (Create: Addictives), handed to the Fixer.",
      [collect("sample", f"{AD}:weed", 16, 0),
       talk("meet_the_fixer", 1, "The Fixer weighs the sample in one hand and you in the other. 'You'll do.'", name=FIX)],
      rewards={"xp": 300, "items": {"minecraft:emerald": 16}},
      nxt=flow("gt2_seeds_of_the_trade"))
QUESTS["gt1b_the_recruiters_terms"][1]["goals"][1]["_far"] = True
QUESTS["gt1b_the_recruiters_terms"][1]["goals"][1]["_talk"] = FIXER_TALK

# =====================================================================================================
# THE BLACKWATER RAID (Saint / Good)
# =====================================================================================================
WATCH = "Captain of the Watch"
quest("br1_the_watch_needs_eyes", "The Watch Needs Eyes",
      "Halden writes that the Blackwater Cartel is poisoning the valley and paying a third of every emerald to the cult. "
      "The Watch can't get close. You can. Meet the Captain of the Watch.",
      [talk("the_captain", 0, None, name=WATCH)],
      book=book("The Watch Needs Eyes", "Reeve Halden", [
          "There's a cartel in the valley called Blackwater. They sell weed, hash, worse. People are dying of it.",
          "And a third of every emerald they make goes to the Chariot. Every sale keeps the cult's beasts fed.",
          "The Watch has a captain who wants them gone and can't get close. You can. Go and see her.\n\n- Halden"], color="blue"),
      rewards={"xp": 400},
      nxt=flow("br2_the_supply_line", delay=1200))
QUESTS["br1_the_watch_needs_eyes"][1]["goals"][0]["_talk"] = [
    ["What does the Watch need?", "Their supply line runs through a pillager outpost. Break it and their runners starve."],
    ["Why not send the Watch?", "Because the Watch is twelve farmers with spears. You're you."]]
QUESTS["br1_the_watch_needs_eyes"][1]["goals"][0]["_far"] = True

quest("br2_the_supply_line", "The Supply Line",
      "Blackwater moves product through a pillager outpost. Break the runners there and take their manifest.",
      [reach("find_the_outpost", "minecraft:pillager_outpost", 0, radius=12000),
       kill("runners", "minecraft:vindicator", 6, 1, "The runners are down. Their manifest names a sorcerer in a dark forest.")],
      threats=[threat("minecraft:vindicator", "Blackwater Runner", stage=1, count=6, hp=56, structure="minecraft:pillager_outpost",
                      search=12000, smin=4, smax=14, equipment=eq(mainhand=("minecraft:iron_axe", {"minecraft:sharpness": 2})))],
      rewards={"xp": 1500, "items": {"minecraft:emerald": 24, "minecraft:crossbow": 1}},
      nxt=flow("br3_the_bookkeeper"))

quest("br3_the_bookkeeper", "The Bookkeeper",
      "The manifest leads to Blackwater's bookkeeper: an illager Sorcerer in a dark-forest hut. Take his ledger. With it, "
      "the Watch can name every buyer, seller and bribed guard.",
      [reach("find_the_hut", f"{II}:sorcerer_hut", 0, radius=30000),
       kill("sorcerer", f"{II}:sorcerer", 1, 1),
       chest_book("ledger", 2, f"{II}:sorcerer_hut", "Blackwater Ledger", "hag_blackwater_ledger_raid", "Blackwater Ledger", "The Sorcerer",
                  ["Weekly takings: 3,000 emeralds. Tithe to the Chariot: one third.",
                   "Don Vasco holds court in the Labyrinth. Bring tribute or bring an army."],
                  "A third of everything goes to the Chariot. Even the cartel pays the cult.", radius=30000)],
      rewards={"xp": 2000, "items": {"minecraft:emerald": 32}},
      nxt=flow("br4_medicine_for_the_valley"))

quest("br4_medicine_for_the_valley", "Medicine for the Valley",
      "The ledger lists the villages Blackwater sells to, and the cleric says three people there are already sick from it. "
      "Make Narcan (Create: Addictives) and bring it to the cleric before the raid.",
      [collect("narcan", f"{AD}:narcan", 3, 0),
       talk("the_cleric", 1, "'Three doses. Three people who wake up tomorrow. Thank you.'", name="The Cleric")],
      rewards={"xp": 1200, "items": {"minecraft:golden_apple": 2}},
      traits={"complete": ["gt_brought_medicine"]},
      nxt=flow("br5_don_vasco_answers", delay=1200))
QUESTS["br4_medicine_for_the_valley"][1]["goals"][1]["_far"] = True

quest("br5_don_vasco_answers", "Don Vasco Answers",
      "Don Vasco, head of Blackwater, waits in the Labyrinth. The Watch wants him alive to stand trial. Some in the valley "
      "would rather you didn't bother.",
      [reach("find_the_labyrinth", f"{II}:labyrinth", 0, radius=30000),
       kill("don_vasco", "minecraft:evoker", 1, 1)],
      threats=[threat("minecraft:evoker", "Don Vasco", stage=1, hp=260, spare=True, structure=f"{II}:labyrinth", search=30000,
                      effects=fx(("minecraft:resistance", 1), ("minecraft:strength", 1)),
                      talk=[["Don Vasco.", "The Watch's dog. Sit. Nobody fights in my house unless I say so."],
                            ["You're under arrest.", "By whom? Twelve farmers with spears?"],
                            ["By me.", "...Then I suppose I'll see you in court. Or I'll see you dead. Your choice."]],
                      attack_ambushes=[{"entity": "minecraft:vindicator", "name": "Vasco's Bodyguard", "count": 4, "max_health": 70,
                                        "equipment": {"mainhand": "minecraft:diamond_axe"}}])],
      choice={"spare": {"label": "Arrest him for the Watch", "line": "Vasco holds out his wrists. 'You'll regret being fair.'",
                        "traits": ["gt_vasco_arrested"]},
              "attack": {"label": "End him here", "traits": ["gt_vasco_killed"]}},
      rewards={"xp": 3000, "items": {"minecraft:emerald_block": 4}},
      nxt=flow("br6_the_valley_breathes"))

quest("br6_the_valley_breathes", "The Valley Breathes",
      "Blackwater is broken. Tell the Captain of the Watch.",
      [talk("the_captain", 0, "'The cult just lost a third of its gold. And the valley's children can walk to market again.'", name=WATCH)],
      rewards={"xp": 4000, "items": {"minecraft:diamond": 6, "minecraft:emerald_block": 6}},
      traits={"complete": ["gt_cartel_broken", "hag_act5_arc_done"]})
QUESTS["br6_the_valley_breathes"][1]["goals"][0]["_far"] = True

# The letter arrives when your Act V reputation chapter ends; which letter depends on where you stand.
for state, book_q, who in (("saint", "br1_the_watch_needs_eyes", "Reeve Halden"), ("good", "br1_the_watch_needs_eyes", "Reeve Halden"),
                           ("waver", "tm1_a_generous_offer", "A stranger in green"),
                           ("fallen", "gt1_a_note_in_the_stash", "Old Mara"), ("wicked", "gt1_a_note_in_the_stash", "Old Mara")):
    courier(f"act5_{state}", f"rc5_{state}", book_q, sender=who, lo=1200, hi=3600)
# Climbing back to Good during the trade: the mod sends Halden's "A Way Out" (custom trigger, never fires on its own)
EXTRA["courier_deliveries/gt_way_out.json"] = {"id": f"{NS}:gt_way_out", "trigger": {"type": "custom"},
                                               "delay": {"min_ticks": 600, "max_ticks": 1800},
                                               "delivery": {"type": "courier", "sender": "Reeve Halden", "quest_book": qid("go0_a_way_out")}}

# for the mod: every hand-to-hand sale counts (the court hears how many), and when the Fixer starts talking, the Watch comes
TRADE = {"sales": ["hag_gt3_first_customers_done", "hag_gt4_the_hash_market_done", "hag_gt7_white_powder_money_done",
                   "hag_gt11_crystal_done", "hag_gt11b_purity_done", "hag_gt13p_the_big_shipment_done", "hag_gt1b_the_recruiters_terms_done"],
         "watch_hears": ["gt_fixer_talked", "gt_fixer_spared"],
         "watch_line": "Word in the valley: the Fixer's been seen talking to the Watch. They'll come for you soon. Very soon."}

# Repeatable street deliveries on the quest board, with a high-emerald reward table
EXTRA["quest_board_contracts/deliveries/deliveries.json"] = {"deliveries": [
    {"item": f"{AD}:weed", "min": 16, "max": 48, "structures": ["minecraft:villages"], "search_radius": 3000,
     "ignore_current_structure": True, "min_structure_distance": 128, "spawn_min_radius": 0, "spawn_max_radius": 24,
     "recipient_name": "Discreet Buyer", "reward_table": f"{NS}:hag_street"},
    {"item": f"{AD}:joint", "min": 8, "max": 24, "structures": ["minecraft:villages"], "search_radius": 3000,
     "ignore_current_structure": True, "min_structure_distance": 128, "spawn_min_radius": 0, "spawn_max_radius": 24,
     "recipient_name": "Late-Night Customer", "reward_table": f"{NS}:hag_street"},
    {"item": f"{AD}:hash", "min": 8, "max": 24, "structures": ["minecraft:villages"], "search_radius": 3000,
     "ignore_current_structure": True, "min_structure_distance": 128, "spawn_min_radius": 0, "spawn_max_radius": 24,
     "recipient_name": "The Fixer's Runner", "reward_table": f"{NS}:hag_street"},
    {"item": f"{AD}:cocaine", "min": 4, "max": 16, "structures": ["minecraft:villages"], "search_radius": 4000,
     "ignore_current_structure": True, "min_structure_distance": 256, "spawn_min_radius": 0, "spawn_max_radius": 24,
     "recipient_name": "Blackwater Middleman", "reward_table": f"{NS}:hag_street"},
]}
EXTRA["quest_board_contracts/deliveries/reward_tables/hag_street.json"] = {
    "rolls": {"min": 2, "max": 3}, "rewards": [
        {"type": "item", "id": "minecraft:emerald", "weight": 10, "base_min": 16, "base_max": 32, "per_kill_min": 1.0, "per_kill_max": 2.0},
        {"type": "item", "id": "minecraft:emerald_block", "weight": 3, "base_min": 1, "base_max": 2, "per_kill_min": 0.05, "per_kill_max": 0.1},
        {"type": "xp", "weight": 4, "base_min": 60, "base_max": 120, "per_kill_min": 2, "per_kill_max": 4}]}
