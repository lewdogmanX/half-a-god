"""The Reputation Road: a five-step scale that every road choice moves you along.

    Saint  <->  Good  <->  Wavering  <->  Fallen  <->  Wicked

Mebahel has no counter and traits can't be removed, so your place on the scale is WHERE YOU ARE in the quest
graph: each act has one chapter per place on the scale, and the chapter's own choice (mercy or cruelty) sends you to
the next act's chapter one step up or one step down. You can fall, climb back, and fall again.

Opening (after Ashford Burns):  The Prisoner (start at Wavering) -> spare: Good  | execute: Fallen
                                The Granary Thief (from Good)    -> spare: Saint | strike: Wavering
                                The Assayer (from Fallen)        -> take gold: Wicked | cut him down: Wavering
Then 7 chapters (one per act). After the ending (kill or kneel, decided only by Azazel's dialogue) you get the
epilogue for wherever you stand on the scale.
"""
from lib import *

V = "minecraft:villager"
STATES = ["saint", "good", "waver", "fallen", "wicked"]
LABEL = {"saint": "Saint", "good": "Good", "waver": "Wavering", "fallen": "Fallen", "wicked": "Wicked"}

SEG_START = {2: "w1_the_skull_market", 3: "h2_hostile_paradise", 4: "e4_the_warden_journal",
             5: "s1_cold_hearths", 6: "v2_the_sinful_church", 7: "m2_the_cursed_quarries"}
SEG_JOIN = {1: "w0_two_roads_meet", 2: "w8_six_remain", 3: "h10_fireproof", 4: "e7_the_nether_is_singing",
            5: "f1_the_last_road", 6: "v8_sinbuster", 7: "m6_the_instruction"}
SILAS = "Silas of the Gilded Hand"


def say_talk(id, stage, name, lines, say=None):
    """A talk step whose NPC explains the job in their own words (lines = [[question, answer], ...])."""
    t = talk(id, stage, say, name=name)
    t["_talk"] = lines
    return t


def cid(k, s):
    return f"rc{k}_{s}"


# =====================================================================================================
# THE DILEMMAS: one per act. Mercy (spare) moves you up the scale, cruelty (attack) moves you down.
# Each person reacts to the reputation you arrive with.
# =====================================================================================================
DILEMMA = {
    1: dict(entity="minecraft:pillager", name="Dunn the Deserter", hp=40, say="Dunn threw down his crossbow at Ashford.",
            spare=("Shelter him", "Dunn drops to his knees. 'I won't forget this. Ever.'"), attack="Execute him",
            talk={"saint": [["You're the deserter?", "I threw my crossbow down at Ashford. They'll kill me if they find me."],
                            ["Why come to me?", "They say you light the roads for strangers. I hoped that meant me too."]],
                  "good": [["You're the deserter?", "Dunn. I dropped my crossbow at Ashford. I couldn't do it."],
                           ["What do you want?", "Somewhere to sleep where nobody wants me dead. Halden says it's your call."]],
                  "waver": [["You're the deserter?", "Yes. And you're the one who... I heard what you did."],
                            ["And you came to me anyway?", "You know what it is to want another chance. Don't you?"]],
                  "fallen": [["You're the deserter?", "Please. I know your name. Everyone does. I'm not worth your trouble."],
                             ["Why shouldn't I kill you?", "Because I ran from the killing. Maybe you could too."]],
                  "wicked": [["You're the deserter?", "Silas said you'd come. He wants me made an example."],
                             ["You know who I am.", "The Hand's knife. ...You don't have to be."]]}),
    2: dict(entity="minecraft:wandering_trader", name="Tobin the Scavenger", hp=40, say="Tobin is carrying a wither skull wrapped in a blanket.",
            spare=("Buy the skull and let him go", "Tobin presses the skull into your hands. 'That's a month of bread for my girls.'"),
            attack="Take it from him",
            handoff={"item": "minecraft:wither_skeleton_skull", "name": "Tobin's Skull", "count": 1},
            talk={"saint": [["You're selling that to the cult?", "They pay in gold. My girls eat gold now, apparently."],
                            ["There are other ways.", "Name one. ...You'd buy it? Just to keep it from them?"]],
                  "good": [["Where did you get that skull?", "Fortress. Nearly lost an arm. The cult pays twenty gold for it."],
                           ["They'll use it to raise the Wither.", "And I'll use the gold to feed my girls. Which matters more to you?"]],
                  "waver": [["Selling skulls to the cult?", "Same as you've done worse, from what I hear."],
                            ["That's not an answer.", "It's the only one I've got. I'm hungry."]],
                  "fallen": [["Hand it over.", "Please. It's all I've got. You've taken enough from people."],
                             ["Have I?", "Ask Ashford. They tell your story at night to scare children."]],
                  "wicked": [["The Hand wants that skull.", "I know. That's why I was selling it to them. Not you."],
                             ["Same buyer.", "Then you don't need to hurt me to get it. Just pay me."]]}),
    3: dict(entity=V, name="Sister Ysolde", hp=30, say="Sister Ysolde was the village cleric before this one.",
            spare=("Forgive her", "'Then I'll spend whatever I have left making it right.'"), attack="Punish her",
            talk={"saint": [["You painted the first eye?", "Forty years ago. They paid me to. I didn't know what it meant."],
                            ["Why tell me now?", "Because you've forgiven worse people than me. I need someone to."]],
                  "good": [["The village says you served the cult.", "I painted the first golden eye on a chapel door. For money. Young and stupid."],
                           ["What do you want from me?", "Judgement. From someone who's earned the right to give it."]],
                  "waver": [["You worked for them?", "Once. Like you've done things once. Twice."],
                            ["That's different.", "Is it? Then judge me, and we'll both find out."]],
                  "fallen": [["A cult painter.", "And you're a killer. We could stand here calling each other names."],
                             ["Or?", "Or you could decide what kind of person you are today."]],
                  "wicked": [["Silas says you know too much.", "I know who painted the eyes. I know who paid. Your master."],
                             ["And?", "And you can kill me for it, or you can stop working for him."]]}),
    4: dict(entity="minecraft:evoker", name="Brother Hale", hp=60, say="Brother Hale stole the librarian's copy of the journal, then surrendered.",
            spare=("Let him teach you", "Hale hands you a primer of the Old Tongue. 'Read it before the end. You'll need to understand what he says.'"),
            attack="End him",
            handoff={"item": "minecraft:written_book", "name": "Hale's Primer", "count": 1,
                     "quest_book": {"title": "Hale's Primer", "author": "Brother Hale",
                                    "pages": ["The Old Tongue has no word for 'god'. The nearest is 'the one who is whole'.",
                                              "So when they pray for him to be whole again, they are praying for a god.\n\nDo not let them."]}},
            talk={"saint": [["You stole the journal.", "I copied it. I wanted to know if what they told us was true."],
                            ["Was it?", "No. Let me show you what it really says. I owe someone the truth."]],
                  "good": [["You surrendered?", "I'm a scholar, not a soldier. And I've read enough to be afraid."],
                           ["Of what?", "Of what the cult is building. Spare me and I'll teach you to read it."]],
                  "waver": [["Why surrender to me?", "Because you might still listen. The others wouldn't."],
                            ["Might?", "You're not sure what you are yet. Neither am I."]],
                  "fallen": [["Give me one reason.", "The Old Tongue. You'll hear it at the end. You won't understand it without me."],
                             ["And if I don't care?", "Then you'll kneel without knowing what you're kneeling to."]],
                  "wicked": [["The Hand sent me.", "I know. Silas never forgives a scholar."],
                             ["Anything to say?", "Only that he's using you. Read the journal. See for yourself."]]}),
    5: dict(entity="minecraft:pillager", name="Saltjaw Mott", hp=50, say="Mott lit false lights on the rocks to wreck ships for cult gold.",
            spare=("Turn him over to the harbour", "The harbourmaster takes him in chains. 'He'll work the nets till the dead come home.'"),
            attack="Drown him",
            talk={"saint": [["You wrecked ships for gold?", "Every wreck paid. The cult wanted the cargo."],
                            ["People drowned.", "I know. I hear them. Let the harbour decide what I'm worth."]],
                  "good": [["You lit the false lights.", "I did. Thirty ships. The cult paid by the hull."],
                           ["Why stop now?", "Because the harbour caught me. I won't pretend I'd have stopped on my own."]],
                  "waver": [["Wrecker.", "Takes one to know one. I hear you've sunk a few lives yourself."],
                            ["What do you want?", "To live. Same as anyone. Same as you."]],
                  "fallen": [["You're a wrecker.", "And you're the one they warn ships about now."],
                             ["Careful.", "Why? Drown me and you prove them right."]],
                  "wicked": [["Silas wants your silence.", "Course he does. My cargo went to him."],
                             ["Then you know what happens now.", "Only if you're as bad as him. Are you?"]]}),
    6: dict(entity=V, name="Ansel the Blood-Drinker", hp=40, say="Ansel drank from the blood wells and can't stop.",
            spare=("Help him through it", "Ansel shakes through the night. By morning he asks for water. Just water."),
            attack="Put him down",
            talk={"saint": [["You drank the blood?", "Once. Then every night. It tastes like being strong."],
                            ["Do you want to stop?", "More than anything. I can't do it alone."]],
                  "good": [["Ansel?", "Don't come close. At night I... I've hurt people."],
                           ["I can help.", "Then tie me down and stay till morning. Please."]],
                  "waver": [["The village wants you gone.", "So do I. I can't stop drinking."],
                            ["What would you have me do?", "What you'd want done for you."]],
                  "fallen": [["You're dangerous.", "So are you. But they still let you through the gate."],
                             ["For now.", "Then you know how it feels. Help me. Or don't."]],
                  "wicked": [["Silas wants your blood.", "He wants all of it. That's why he sells it."],
                             ["And you buy it.", "Because I can't stop. Kill me or cure me. Just don't sell me."]]}),
    7: dict(entity=V, name="Garret of Ashford", hp=40, say="Garret sold his neighbours to the cult, and was found among the freed.",
            spare=("Let Halden judge him", "Halden looks at Garret for a long time. 'You'll rebuild every house you emptied.'"),
            attack="Judge him yourself",
            talk={"saint": [["You sold your neighbours.", "Fourteen names. For gold. Then they took me too."],
                            ["What should happen to you?", "Halden should decide. Not you. You've carried enough."]],
                  "good": [["You're the one who gave the cult the names.", "Yes. I've thought about nothing else in the quarries."],
                           ["Halden's waiting.", "Then take me to him. I won't run."]],
                  "waver": [["You betrayed Ashford.", "And you've betrayed yourself, they tell me. More than once."],
                            ["That's not your business.", "No. But it's why I think you'll understand."]],
                  "fallen": [["Fourteen names.", "Fourteen names. How many is it for you?"],
                             ["Enough.", "Then we're the same. Do what you think we deserve."]],
                  "wicked": [["Silas paid you.", "Silas paid both of us. Look where it got me."],
                             ["You're asking for mercy?", "I'm asking you to look where it's getting you."]]}),
}


def chapter(k, s, title, summary, goals, threats=None, rewards=None):
    d = DILEMMA[k]
    stage = max(g["stage"] for g in goals) + 1
    goals = goals + [kill(f"judge_{k}", d["entity"], 1, stage, d["say"] + " What you do next moves you on the scale.")]
    th = threat(d["entity"], d["name"], stage=stage, hp=d["hp"], spare=True, smin=6, smax=12,
                effects=fx(("minecraft:resistance", 0)), talk=d["talk"][s])
    if "handoff" in d:
        th["spare_handoff"] = d["handoff"]
    q = quest(cid(k, s), title, f"[Reputation: {LABEL[s]}] " + summary, goals, threats=(threats or []) + [th],
              choice={"spare": {"label": d["spare"][0], "line": d["spare"][1], "traits": [f"hag_mercy_{k}"]},
                      "attack": {"label": d["attack"], "traits": [f"hag_cruelty_{k}"]}},
              rewards=rewards or {"xp": 600},
              traits={"complete": [f"hag_route_seg{k}", f"hag_ch{k}_{s}"]})
    if k in SEG_START:
        q["requires"] = {"quests": [qid(SEG_START[k])]}
    return q


# =====================================================================================================
# THE OPENING (courier from Halden after Ashford Burns)
# =====================================================================================================
courier("halden_prisoner", "a1_ashford_burns", "r1_the_prisoner", sender="Reeve Halden", lo=2400, hi=4800)

quest("r1_the_prisoner", "The Prisoner",
      "Reeve Halden's men caught one of the Ashford raiders alive: Orsk, the man who kept the raiders' tally. Ashford wants "
      "him dead. Halden wants you to decide, because you saved them. Hear Orsk out, then hand him over for judgment or "
      "execute him. From now on, the choices you make move you along a scale: Saint, Good, Wavering, Fallen, Wicked.",
      [kill("judge_orsk", "minecraft:pillager", 1, 0, "Whatever you chose, Ashford saw you choose it.")],
      threats=[threat("minecraft:pillager", "Orsk the Tally-Keeper", hp=40, spare=True, smin=6, smax=12,
                      talk=[["Why did you do it?", "Gold. Same as everyone. Forty for a village. Two for a man."],
                            ["Who paid you?", "The Faithful. I only kept the count. I never held a torch, I swear it."],
                            ["Where did the men go?", "A Bastille first. Then the Nether. Ask the believers, they keep the books."],
                            ["What happens now?", "Halden says you've earned the right to choose. So choose."]],
                      spare_handoff={"item": "minecraft:written_book", "name": "Orsk's Tally", "count": 1,
                                     "quest_book": {"title": "Orsk's Tally", "author": "Orsk",
                                                    "pages": ["Ashford: 14 men taken. 2 gold each. Paid by the Faithful, through the Gilded Hand.",
                                                              "If you're reading this, I'm alive. Thank you. I didn't deserve it."]}})],
      choice={"spare": {"label": "Hand him to Halden for judgment", "line": "Orsk sags with relief. Halden nods at you, slowly.", "traits": ["orsk_spared"]},
              "attack": {"label": "Execute him here", "traits": ["orsk_executed"]}},
      book=book("We Caught One", "Reeve Halden", [
          "We caught one alive. Orsk. He kept the raiders' tally, how many men, how much gold.\n\nAshford wants to hang him from what's left of the hall.",
          "I want to know what you think. You saved us. That gives you the right to choose.\n\nCome and look him in the eye. Read this and he'll be brought to you.\n\n- Halden"], color="brown"),
      rewards={"xp": 400, "items": {"minecraft:emerald": 6}},
      on_spare=flow("r2g_the_granary_thief", delay=2400), on_kill=flow("r2e_the_assayer", delay=2400))

quest("r2g_the_granary_thief", "The Granary Thief",
      "[Reputation: Good] You showed Orsk mercy, and Ashford is watching to see if you meant it. Someone has been stealing "
      "from the granary you filled before winter: Corwin, an Ashford refugee. Let him go and cover the loss yourself "
      "(you become a Saint), or punish him (you slip to Wavering).",
      [kill("judge_corwin", V, 1, 0, "The village will tell this story for years.")],
      threats=[threat(V, "Corwin the Refugee", hp=30, spare=True, smin=6, smax=12,
                      talk=[["You stole the grain?", "For my sister's children. Ashford has nothing left."],
                            ["The village needs it for winter.", "I know. I'd do it again. I'm sorry."],
                            ["What would you have me do?", "Whatever you think is right. You're the only one here they listen to."]])],
      choice={"spare": {"label": "Let him go, and pay for the grain yourself", "line": "Corwin cries. The village elder looks at you differently now.", "traits": ["corwin_spared"]},
              "attack": {"label": "Punish him with steel", "traits": ["corwin_slain"]}},
      rewards={"xp": 300},
      on_spare=flow(cid(1, "saint")), on_kill=flow(cid(1, "waver")))

quest("r2e_the_assayer", "The Assayer",
      "[Reputation: Fallen] You executed a bound man, and word travels. Morrow, an assayer for the Gilded Hand (the merchants "
      "who fund the Faithful), wants to hire you. Take his gold (you become Wicked), or cut him down (you climb back to Wavering).",
      [kill("judge_morrow", "minecraft:wandering_trader", 1, 0, "The road ahead just narrowed.")],
      threats=[threat("minecraft:wandering_trader", "Morrow the Assayer", hp=60, spare=True, smin=6, smax=12,
                      talk=[["What do you want?", "To pay you. Gold, steady. You only have to keep doing what you're good at."],
                            ["And if I say no?", "Then you're a man who kills prisoners, with no friends. Ashford won't forgive you. We would."],
                            ["Who do you work for?", "The Gilded Hand. My master Silas buys what others won't sell."],
                            ["And the beasts I'm hunting?", "Kill them. Please. The Hand wants them dead even more than your Old Mara does."]],
                      spare_handoff={"item": "minecraft:gold_ingot", "name": "The Hand's Advance", "count": 16})],
      choice={"spare": {"label": "Take his gold", "line": "'Welcome to the Hand. Silas will be in touch.'", "traits": ["took_the_gold"]},
              "attack": {"label": "Cut him down", "traits": ["morrow_slain"]}},
      rewards={"xp": 300},
      on_spare=flow(cid(1, "wicked")), on_kill=flow(cid(1, "waver")))

# =====================================================================================================
# ACT I
# =====================================================================================================
chapter(1, "saint", "The Lantern Oath",
        "Halden heard what you did for Corwin. He wants you to take the old Watch-oath: keep the roads lit so no traveller walks "
        "in the dark. Hang twelve lanterns along the road, then walk a returning Ashford family home along it. Then a deserter "
        "from the raiders comes looking for you.",
        [say_talk("take_the_oath", 0, "Reeve Halden", [["You wanted me?", "Corwin told me. You paid for his grain."],
                                                         ["What's the Lantern Oath?", "The old Watch kept a light every hundred paces between villages. Raiders hate a lit road."],
                                                         ["What do I do?", "Hang twelve lanterns along the road. Then bring a family home along it."]]),
         place_anywhere("road_lanterns", "minecraft:lantern", 12, 1, "Travellers on the road start nodding to you by name."),
         escort("walk_them_home", 2, "The family's youngest runs ahead the last hundred paces, laughing.")],
        rewards={"xp": 700, "items": {"minecraft:lantern": 4, "minecraft:bread": 16, "minecraft:golden_apple": 1}})
chapter(1, "waver", "Ash on Your Hands",
        "Ashford knows what you did. The village elder says there is one way to start making it right: feed the families "
        "through the winter. Then a deserter from the raiders comes looking for you.",
        [collect("bread", "minecraft:bread", 32, 0), collect("iron", "minecraft:iron_ingot", 16, 1),
         say_talk("face_the_elder", 2, "Ashford Elder", [["I brought what you asked.", "It doesn't bring anyone back."],
                                                          ["I know.", "Good. Knowing is where it starts."]])],
        rewards={"xp": 400})
chapter(1, "wicked", "Protection Money",
        "Silas of the Gilded Hand has your first job: villages pay for protection now, and you are the protection. Trade or take "
        "until you have two dozen emeralds and bring them to Silas. Then he wants a deserter made an example of.",
        [collect("emeralds", "minecraft:emerald", 24, 0),
         say_talk("pay_silas", 1, SILAS, [["Your half.", "Prompt. I like prompt."],
                                          ["What's next?", "A raider who ran from Ashford. Deserters are bad for business. Deal with him."]])],
        rewards={"xp": 500, "items": {"minecraft:gold_ingot": 16}})

# =====================================================================================================
# ACT II (the Wither)
# =====================================================================================================
chapter(2, "saint", "No One Left Behind",
        "The Bone Choir is hunting the last Ashford men who escaped into the Nether. Find the hunters at the fortress before "
        "they find them. On the way out you meet a scavenger with a wither skull.",
        [kill("choir_hunters", "minecraft:wither_skeleton", 5, 0, "Behind the last hunter, two thin men in Ashford wool stand up from hiding.")],
        threats=[threat("minecraft:wither_skeleton", "Choir Hunter", count=5, hp=60, structure="minecraft:fortress", search=12000,
                        equipment=eq(mainhand=("minecraft:stone_sword", {"minecraft:sharpness": 2})))],
        rewards={"xp": 900, "items": {"minecraft:golden_apple": 2}})
chapter(2, "good", "Wardens of the Wall",
        "Things crawl out of Nether portals near the villages now. A guard captain asks you to help raise a wall facing the "
        "old portal. Then a scavenger with a wither skull asks for you.",
        [say_talk("meet_the_captain", 0, "Guard Captain", [["Things coming through the portal?", "Black bones. They walk out at night and stand there. Watching."],
                                                           ["How much wall?", "Thirty-two wall blocks. Cobblestone will do."]]),
         place("the_wall", "minecraft:cobblestone_wall", 32, 1, "The guards stand a little straighter behind it.")],
        rewards={"xp": 700, "items": {"minecraft:shield": 1, "minecraft:iron_ingot": 12}})
chapter(2, "fallen", "Wanted",
        "There is a notice nailed to the well with your face on it, and a price. Bounty hunters come at night. Survive them. "
        "Then a scavenger with a wither skull finds you.",
        [wait("wait_for_night", 0, "next_night", "Torches on the ridge. Four of them."),
         kill("bounty_hunters", "minecraft:vindicator", 4, 1, "The last hunter laughs. 'There'll be more. There's always more for someone like you.'")],
        threats=[threat("minecraft:vindicator", "Bounty Hunter", stage=1, count=4, hp=52, smin=12, smax=24,
                        equipment=eq(mainhand=("minecraft:iron_axe", {"minecraft:sharpness": 2}), head="minecraft:iron_helmet"))],
        rewards={"xp": 900, "items": {"minecraft:shield": 1}})
chapter(2, "wicked", "Skulls for the Hand",
        "The Hand buys wither skulls for the Chariot, the same skulls you're hunting in the fortress. Sell Silas two. Then a "
        "scavenger turns up trying to sell the Hand a skull of his own.",
        [collect("skulls", "minecraft:wither_skeleton_skull", 2, 0),
         say_talk("sell_to_silas", 1, SILAS, [["Two skulls.", "Beautiful. You see? Everybody wins."],
                                               ["The Wither will wake if they get three.", "Then don't sell them a third. Yet."]])],
        rewards={"xp": 900, "items": {"minecraft:gold_block": 2}})

# =====================================================================================================
# ACT III (the Aether)
# =====================================================================================================
chapter(3, "saint", "Seeds of Heaven",
        "The cleric has heard that white apples from the Aether cure any poison. Ashford's children are still sick from the "
        "smoke. Bring her six. Then the old cleric, Sister Ysolde, asks to confess something.",
        [collect("white_apples", "aether:white_apple", 6, 0),
         say_talk("give_to_the_cleric", 1, "The Cleric", [["White apples, from the sky.", "Then the stories were true. By morning, the coughing will stop."]])],
        rewards={"xp": 1000, "items": {"minecraft:golden_apple": 2, "minecraft:experience_bottle": 8}})
chapter(3, "good", "Petals for Medicine",
        "The cleric wants Aechor petals: the plants bite, but the petals make a paste for wounds that won't close. Kill four "
        "Aechor plants in the Aether and bring her eight petals. Then the old cleric asks to confess something.",
        [kill("aechor_plants", "aether:aechor_plant", 4, 0), collect("aechor_petals", "aether:aechor_petal", 8, 1),
         say_talk("give_to_the_cleric", 2, "The Cleric", [["Aechor petals.", "They smell like rain. Thank you. Sister Ysolde wants to see you."]])],
        rewards={"xp": 1000, "items": {"minecraft:experience_bottle": 12}})
chapter(3, "waver", "Penance in the Sky",
        "The cleric says you can't pray your mistakes away, but you can work them off. She wants ambrosium from the Aether for "
        "the chapel lamps. Then the old cleric asks to confess something to you, of all people.",
        [collect("ambrosium", "aether:ambrosium_shard", 16, 0),
         say_talk("give_to_the_cleric", 1, "The Cleric", [["The ambrosium.", "Then the lamps will burn tonight for the ones in the dark. Sit with me a while."]])],
        rewards={"xp": 900})
chapter(3, "fallen", "Fall from Grace",
        "The burned farmer's barn went too. Skyroot from the Aether won't catch fire. Bring him thirty-two logs. Then the old "
        "cleric asks to see you.",
        [collect("skyroot", "aether:skyroot_log", 32, 0),
         say_talk("give_to_the_farmer", 1, "The Farmer", [["Skyroot, for the barn.", "...That's more than I asked for. Huh."]])],
        rewards={"xp": 1000, "items": {"minecraft:diamond": 2}})
chapter(3, "wicked", "Gold from Heaven",
        "Silas has heard there is gold in the sky: golden amber from the Aether's golden oaks. Bring him sixteen. Then he wants "
        "the old cleric silenced: she knows who paid for the first golden eye.",
        [collect("golden_amber", "aether:golden_amber", 16, 0),
         say_talk("sell_to_silas", 1, SILAS, [["Golden amber.", "It's warm. Is it supposed to be warm? Never mind. Paid. Now, about Sister Ysolde..."]])],
        rewards={"xp": 1000, "items": {"minecraft:gold_block": 2}})

# =====================================================================================================
# ACT IV (the End)
# =====================================================================================================
chapter(4, "saint", "The Librarian's Lamp",
        "The librarian is copying the Warden Journal and the Faithful know it. Assassins will come for her tonight. Stand guard "
        "until morning. One of the cult's scholars is caught with her copy.",
        [wait("wait_for_night", 0, "next_night", "Footsteps on the roof."),
         kill("assassins", "minecraft:vindicator", 5, 1, "The librarian never stopped writing.")],
        threats=[threat("minecraft:vindicator", "Faithful Assassin", stage=1, count=5, hp=48, smin=10, smax=22,
                        equipment=eq(mainhand=("minecraft:iron_sword", {"minecraft:sharpness": 2})), effects=fx(("minecraft:speed", 0)))],
        rewards={"xp": 1300, "items": {"minecraft:diamond": 3}})
chapter(4, "good", "Messenger Pearls",
        "Halden wants a faster way to warn the villages: ender pearls thrown from tower to tower. Bring him twelve. Then a cult "
        "scholar who stole the librarian's copy surrenders to you.",
        [collect("pearls", "minecraft:ender_pearl", 12, 0),
         say_talk("give_to_halden", 1, "Reeve Halden", [["Pearls, as promised.", "A warning will cross the valley in a minute now."],
                                                         ["You've changed, Halden.", "So have you. For the better, both of us."]])],
        rewards={"xp": 1100, "items": {"minecraft:emerald": 16}})
chapter(4, "waver", "A Letter Unanswered",
        "Mara's letters to you have gone short and careful. The librarian has one she was asked to read aloud. Then a cult "
        "scholar surrenders to you.",
        [say_talk("hear_the_letter", 0, "The Librarian", [["A letter from Mara?", "'I chose you because you were good. Be good again.'"],
                                                          ["That's all?", "That's all. She underlined 'again'."]])],
        rewards={"xp": 1000})
chapter(4, "fallen", "Old Debts",
        "Everyone you've wronged has a family. One of them has stopped hiring killers and come himself, with everyone he could "
        "pay. Survive. Then a cult scholar surrenders to you.",
        [kill("kinsmen", "minecraft:vindicator", 4, 0),
         kill("the_avenger", "minecraft:evoker", 1, 0, "Dying, he says: 'I hope it was worth it.' You aren't sure it was.")],
        threats=[threat("minecraft:vindicator", "Avenger", count=4, hp=56, smin=10, smax=22, equipment=eq(mainhand=("minecraft:iron_axe", {"minecraft:sharpness": 2}))),
                 threat("minecraft:evoker", "Aldo the Avenger", hp=90, smin=12, smax=20, effects=fx(("minecraft:resistance", 0)))],
        rewards={"xp": 1400, "items": {"minecraft:golden_apple": 2}})
chapter(4, "wicked", "The Lantern Watch",
        "Halden has rebuilt Ashford's Watch, and the Watch is asking about you. Silas wants it broken before it learns "
        "anything. They patrol at night. Then Silas wants a runaway scholar dealt with.",
        [wait("wait_for_night", 0, "next_night"),
         kill("watchmen", "minecraft:vindicator", 3, 1, "The Watch's lanterns go out one by one.")],
        threats=[threat("minecraft:vindicator", "Lantern Watchman", stage=1, count=3, hp=50, smin=10, smax=22,
                        equipment=eq(mainhand="minecraft:iron_sword", head="minecraft:iron_helmet"))],
        rewards={"xp": 1300, "items": {"minecraft:gold_block": 3}})

# =====================================================================================================
# ACT V (Bosses' Rise)
# =====================================================================================================
chapter(5, "saint", "The Fisher's Debt",
        "The fishing village lost its boats to the Kraken. Without boats they starve. Bring them four boats. Then the harbour "
        "catches the wrecker who lit false lights on the rocks.",
        [collect("boats", "minecraft:oak_boat", 4, 0),
         say_talk("give_to_the_harbourmaster", 1, "Harbourmaster", [["Four boats.", "We'll name the first one after you."]])],
        rewards={"xp": 1200, "items": {"minecraft:trident": 1}})
chapter(5, "good", "Warm Hands",
        "The snowy village is still short of fires. Campfires don't go out in the wind. Bring six. Then the harbour asks you "
        "to decide what happens to a captured wrecker.",
        [collect("campfires", "minecraft:campfire", 6, 0),
         say_talk("hand_them_out", 1, "Village Elder", [["Campfires.", "No one will freeze this winter. That's never been true before."]])],
        rewards={"xp": 1100, "items": {"minecraft:diamond": 2}})
chapter(5, "waver", "Cold Welcome",
        "The snowy village heard about you before you arrived. They won't trade until you prove you're here to help: clear "
        "twelve strays stalking their woodcutters. Then a captured wrecker is brought to you.",
        [kill("strays", "minecraft:stray", 12, 0, "The woodcutters come home with full sleds and look at you twice.")],
        rewards={"xp": 1100, "items": {"minecraft:emerald": 12}})
chapter(5, "fallen", "Salt in the Wound",
        "Drowned followed your trail to a fishing village and are coming up the shore tonight because of you. Stop them. Then "
        "the wrecker who called them is dragged in front of you.",
        [wait("wait_for_night", 0, "next_night"),
         kill("drowned", "minecraft:drowned", 8, 1, "The tide goes out red. The fishermen don't say anything.")],
        threats=[threat("minecraft:drowned", "Trail-Drowned", stage=1, count=8, hp=36, smin=10, smax=22, equipment=eq(mainhand="minecraft:trident"))],
        rewards={"xp": 1300, "items": {"minecraft:diamond": 2}})
chapter(5, "wicked", "The Harbour Tax",
        "The fishing village refuses to pay the Hand. Silas wants their iron golem dead as a lesson. Then he wants the wrecker "
        "who knows where the Hand's cargo went silenced.",
        [kill("harbour_golem", "minecraft:iron_golem", 1, 0, "The fishermen pay the next morning. All of them.")],
        threats=[threat("minecraft:iron_golem", "Harbour Golem", hp=120, structure="minecraft:villages", search=8000, min_dist=300)],
        rewards={"xp": 1400, "items": {"minecraft:emerald": 24}})

# =====================================================================================================
# ACT VI (Sons of Sins)
# =====================================================================================================
chapter(6, "saint", "Hands That Heal",
        "The blood pits turned villagers into the shambling dead. The cleric can cure them with golden apples if someone brings "
        "four. Then a man who drank from the blood wells begs for help.",
        [collect("golden_apples", "minecraft:golden_apple", 4, 0),
         say_talk("give_to_the_cleric", 1, "The Cleric", [["Four golden apples.", "Four people back. I'll start tonight."]])],
        rewards={"xp": 1500, "items": {"minecraft:enchanted_golden_apple": 1}})
chapter(6, "good", "Sanctuary",
        "Families are fleeing the villages near the blood pits. Halden wants a safe house built in Ashford with four beds. Then "
        "a man who drank from the blood wells begs for help.",
        [say_talk("ask_halden", 0, "Reeve Halden", [["More refugees?", "From the south. They saw the pits."],
                                                     ["Where do they sleep?", "Nowhere yet. Four beds would change that."]]),
         place("beds", "minecraft:white_bed", 4, 1, "Four families sleep under one roof, and nobody locks the door.")],
        rewards={"xp": 1500, "items": {"minecraft:totem_of_undying": 1}})
chapter(6, "waver", "Amends",
        "Halden sends for you. He wants to see whether you'll pay a debt nobody can make you pay: gold for the families of "
        "everyone who died since you came. Then a man sick on cult blood is brought to you.",
        [collect("gold", "minecraft:gold_ingot", 32, 0),
         say_talk("face_halden", 1, "Reeve Halden", [["The gold.", "...You didn't have to."],
                                                      ["I know.", "Then maybe you're not who I thought you were. Not all the way."]])],
        rewards={"xp": 1500, "items": {"minecraft:totem_of_undying": 1}})
chapter(6, "fallen", "Blood Calls to Blood",
        "The Midwife's sisters can smell old blood on a person, and they smell it on you. Tonight they come. Then a man sick on "
        "cult blood finds you: he thinks you'll understand.",
        [wait("wait_for_night", 0, "next_night"),
         kill("blood_sworn", "minecraft:witch", 3, 1, "The last one whispers a name you'd rather forget.")],
        threats=[threat("minecraft:witch", "Blood-Sworn", stage=1, count=3, hp=50, smin=10, smax=20)],
        rewards={"xp": 1500, "items": {"minecraft:golden_apple": 2}})
chapter(6, "wicked", "Blood Money",
        "The Hand has buyers for the cult's blood. Bring Silas four bottles (hit creatures with the Sickle of Struggle while "
        "carrying empty bottles), and hear who the Hand really serves. Then he wants a blood addict silenced.",
        [collect("blood", "sons_of_sins:bottle_of_blood", 4, 0),
         say_talk("hear_the_truth", 1, SILAS, [["Who buys the skulls? The blood?", "The Chariot. Azazel's own hand. The Gilded Hand is his purse."],
                                               ["I've been killing his Wardens.", "He wanted them dead. The Nails held him down. You've been setting him free, and he paid you for it."]])],
        rewards={"xp": 1800, "items": {"minecraft:gold_block": 4}})

# =====================================================================================================
# ACT VII (the Mansion)
# =====================================================================================================
chapter(7, "saint", "The Lantern's End",
        "Halden has something for you before the end: the Lantern of Ashford, carried by the Watch for a hundred years. Then "
        "the freed men bring you the neighbour who sold them.",
        [say_talk("receive_the_lantern", 0, "Reeve Halden", [["You called for me.", "Take this. The Watch always carried it into the dark."],
                                                              ["Halden...", "Whatever you find down there, remember who you did this for."]])],
        rewards={"xp": 2000, "items": {"minecraft:lantern": 1, "minecraft:totem_of_undying": 1}})
chapter(7, "good", "The Freed Remember",
        "The men you freed from the quarries are walking home. One is too weak to make it alone. Walk him the last stretch. "
        "Then the freed men bring you the neighbour who sold them.",
        [escort("walk_him_home", 0, "He touches the village gate like he's not sure it's real.")],
        rewards={"xp": 1800, "items": {"minecraft:diamond": 4}})
chapter(7, "waver", "The Freed Don't Thank You",
        "The men freed from the quarries have heard stories about you. Halden's son wants to look you in the eye before he "
        "decides what you are. Then the freed men bring you the neighbour who sold them.",
        [say_talk("meet_haldens_son", 0, "Halden's Son", [["You're Halden's boy.", "I'm Halden's son. And you're the one they tell stories about."],
                                                          ["I've tried to make up for it.", "My father says so. I'll make up my own mind."]])],
        rewards={"xp": 1700})
chapter(7, "fallen", "Ash and Ember",
        "Before the end, the cleric wants a word. She has watched you make mistakes and pay for them. Then the freed men drag "
        "the neighbour who sold them in front of you.",
        [say_talk("hear_the_warning", 0, "The Cleric", [["You wanted to see me?", "He'll offer you mercy down there. A way out. People like you take it."],
                                                        ["People like me?", "People who think they're already damned. You're not. Remember that when he asks."]])],
        rewards={"xp": 2000, "items": {"minecraft:golden_apple": 3}})
chapter(7, "wicked", "Golden Chains",
        "The quarries are broken but their gold is still lying there. Carry four blocks out for Silas, and hear his last "
        "advice. Then he wants the neighbour who knows his name silenced.",
        [collect("gold_blocks", "minecraft:gold_block", 4, 0),
         say_talk("last_meeting", 1, SILAS, [["Quarry gold.", "Mined by your Ashford friends. Funny old world."],
                                             ["Anything else?", "When he offers you mercy, take it. He pays better than I do."]])],
        rewards={"xp": 2000, "items": {"minecraft:diamond": 5}})

# =====================================================================================================
# EPILOGUES: after the ending (kill or kneel), for wherever you stand on the scale
# =====================================================================================================
EPI = {
    "saint": ("The Lantern-Bearer", "Reeve Halden",
              [["It's over.", "I know. Every bell rang. Whatever you chose down there, you chose it as yourself."],
               ["What now?", "Now the Watch needs a captain. Ashford would follow you anywhere. So would I."]],
              {"xp": 5000, "items": {"minecraft:enchanted_golden_apple": 2, "minecraft:beacon": 1, "minecraft:totem_of_undying": 2}}),
    "good": ("Ashford's Friend", "Reeve Halden",
             [["It's over.", "Then come home. Ashford's built you a house. Don't argue."],
              ["I made mistakes.", "Everybody does. You kept coming back. That's the bit we remember."]],
             {"xp": 4000, "items": {"minecraft:enchanted_golden_apple": 1, "minecraft:totem_of_undying": 1, "minecraft:emerald_block": 4}}),
    "waver": ("The Scales", "The Cleric",
              [["It's over.", "Is it? You're still the same person who went down there. Half of everything."],
               ["What do I do with that?", "Choose. Every morning. That's all any of us can do."]],
             {"xp": 3500, "items": {"minecraft:golden_apple": 4, "minecraft:diamond": 6}}),
    "fallen": ("What Remains", "Halden's Son",
               [["It's over.", "My father says I should thank you. I'm still deciding."],
                ["And?", "You did terrible things, and you did this. I don't know which one you are. Maybe neither do you."]],
               {"xp": 3500, "items": {"minecraft:diamond": 8, "minecraft:netherite_scrap": 2}}),
    "wicked": ("The Gilded Throne", SILAS,
               [["It's over.", "For him. Not for us. The Hand always finds a new master, and you've proven you're worth paying."],
                ["And if I'm done?", "Nobody is ever done with gold. Come and collect your share."]],
               {"xp": 4000, "items": {"minecraft:gold_block": 16, "minecraft:emerald_block": 8, "minecraft:netherite_ingot": 1}}),
}
for s, (title, npc, lines, rew) in EPI.items():
    quest(f"rep_end_{s}", title,
          f"[Reputation: {LABEL[s]}] The god is dead or crowned, and you are the one who decided. This is who the valley thinks you are. Go and hear it.",
          [say_talk("hear_it", 0, npc, lines)],
          requires={"traits": ["hag_ending_reached"]}, rewards=rew, traits={"complete": [f"hag_rep_{s}"]})


# ---- link the scale: mercy moves one step toward Saint, cruelty one step toward Wicked
def _link():
    for k in range(1, 8):
        for i, s in enumerate(STATES):
            if cid(k, s) not in QUESTS:
                continue  # never reachable (the opening only lands on Saint, Wavering or Wicked)
            q = QUESTS[cid(k, s)][1]
            up, down = STATES[max(i - 1, 0)], STATES[min(i + 1, 4)]
            tgt_up = cid(k + 1, up) if k < 7 else f"rep_end_{up}"
            tgt_down = cid(k + 1, down) if k < 7 else f"rep_end_{down}"
            assert tgt_up in QUESTS and tgt_down in QUESTS, (k, s, tgt_up, tgt_down)
            q["flow"] = {"on_spare": {"delayed_next_quest": flow(tgt_up, retry=400)},
                         "on_kill": {"delayed_next_quest": flow(tgt_down, retry=400)}}


_link()


def join_main_story():
    for k, q in SEG_JOIN.items():
        QUESTS[q][1].setdefault("requires", {}).setdefault("traits", []).append(f"hag_route_seg{k}")
    for q in ("g1_maraals_last_letter", "b1_bow_at_my_feet"):
        QUESTS[q][1]["traits"]["complete"].append("hag_ending_reached")
