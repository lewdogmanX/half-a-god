"""Choices that come back later, companions, the siege, the lost Warden Journal pages and what happens after the ending.

Datapack choices (work without the mod): five people you meet on the main road, each a spare-or-kill decision.
  The main story carries on either way; what you did comes back later as its own quest (a courier letter).
Mod choices (three or four answers, hag_mod/choices.json) are written in mod_tables.py; the quests they lead to are here.
"""
import copy
from lib import *

LATER = (36000, 60000)  # "comes back later": 30-50 minutes of play after the choice


def _insert_choice(prev, cid, title, summary, entity, name, talk_lines, spare, attack, *, hp=40, structure=None, search=20000,
                   aftermath, rewards=None, extra_threats=None, extra_goals=None):
    """Put a spare-or-kill choice between prev and whatever prev led to. Killing passes through a short aftermath quest."""
    pq = QUESTS[prev][1]
    d = pq["flow"]["delayed_next_quest"]
    old = d["quest"]
    d["quest"] = qid(cid)
    goals = list(extra_goals or [])
    stage = max([gl["stage"] for gl in goals], default=-1) + 1
    goals.append(kill(f"judge_{cid}", entity, 1, stage, None))
    th = threat(entity, name, stage=stage, hp=hp, spare=True, smin=5, smax=10, effects=fx(("minecraft:resistance", 0)),
                talk=talk_lines, **({"structure": structure, "search_radius": search} if structure else {}))
    cont = copy.deepcopy(d)
    cont["quest"] = old
    ak, (atitle, asummary, agoals, athreats) = f"{cid}_after", aftermath
    quest(cid, title, summary, goals, threats=(extra_threats or []) + [th],
          choice={"spare": {"label": spare[0], "line": spare[1], "traits": [f"hag_mercy_{cid}"]},
                  "attack": {"label": attack, "traits": [f"hag_cruelty_{cid}"]}},
          rewards=rewards or {"xp": 400},
          on_spare=cont, on_kill=flow(ak, delay=200, dims=d.get("conditions", {}).get("dimensions")))
    quest(ak, atitle, asummary, agoals, threats=athreats, rewards={"xp": 150}, nxt=copy.deepcopy(cont))


def _payoff(courier_id, after, trigger, book_q, sender):
    courier(courier_id, after, book_q, sender=sender, lo=LATER[0], hi=LATER[1], trigger=trigger)


def _home_talk(gid, stage, name, say, lines):
    t = talk(gid, stage, say, name=name)
    t["_talk"] = lines
    t["_far"] = False
    return t


def _far_talk(gid, stage, name, say, lines):
    t = talk(gid, stage, say, name=name)
    t["_talk"] = lines
    t["_far"] = True
    return t


# =====================================================================================================
# 1. THE INFORMANT (Prologue: after The Pillager Moon)
# =====================================================================================================
_insert_choice(
    "p8_pillager_moon", "x1_the_informant", "The Informant",
    "The watch caught the man who sold the village's patrol times to the raiders: Wick, a pillager scout who lost his nerve. "
    "He says he knows who paid for the raid and will tell you, if he lives. Hear him out, then decide.",
    "minecraft:pillager", "Wick the Informant",
    [["You sold our patrols.", "For bread. They don't feed us, you know. The gold goes up, never down."],
     ["Who paid for the raid?", "Men with gold eyes on their coats. They came from the east, toward the old camp."],
     ["Why should I let you live?", "Because I'll owe you. And a scout who owes you is worth ten who don't."]],
    ("Let him go, and keep him owing you", "Wick bolts, then stops and looks back. 'I won't forget. Honest.'"),
    "Hang him as a lesson",
    aftermath=("The Lesson", "You hanged Wick at the crossroads. The village elder wants to talk about what the children saw.",
               [_home_talk("the_elder", 0, "Village Elder", None,
                           [["You wanted to see me?", "The children watched. Some of them cheered. That frightens me more than the raiders."],
                            ["He sold us out.", "He did. I only wonder what we're teaching them about what comes next."]])], None))

quest("x1s_wicks_back_door", "Wick's Back Door",
      "Wick kept his word. He writes that a Gold-Sworn wagon train is resting at a pillager outpost with stolen grain and two "
      "captives. He's left a gap in the palisade for you.",
      [reach("find_the_outpost", "minecraft:pillager_outpost", 0, radius=10000),
       kill("teamsters", "minecraft:pillager", 5, 1, "The captives run for the trees. One of them shouts Wick's name as a thank-you.")],
      threats=[threat("minecraft:pillager", "Gold-Sworn Teamster", stage=1, count=5, hp=36, structure="minecraft:pillager_outpost",
                      search=10000, smin=4, smax=14)],
      book=book("Wick's Back Door", "Wick", [
          "You let me go. Most wouldn't have.\n\nThere's a wagon train at the outpost east of you. Grain they took, and two of yours in the cart.",
          "I loosened a stake on the north side. Go at night.\n\nWe're square now. Mostly.\n\n- W."], color="brown"),
      rewards={"xp": 900, "items": {"minecraft:bread": 32, "minecraft:emerald": 16, "minecraft:crossbow": 1}},
      traits={"complete": ["hag_payoff_wick_kept_his_word"]})
_payoff("x1s_payoff", "x1_the_informant", "quest_spared", "x1s_wicks_back_door", "Wick")

quest("x1k_wicks_sister", "Wick's Sister",
      "A girl from the pillager camps has walked three days to find you. Wick was her brother. She doesn't want revenge. "
      "She wants to know why.",
      [_far_talk("nell", 0, "Nell", None,
                 [["You're Wick's sister.", "Nell. He was the only one who ever came back for me."],
                  ["He sold us to the raiders.", "He sold you patrol times. For bread. I ate that bread."],
                  ["What do you want?", "Nothing you can give. I just wanted to see your face. Now I have."]])],
      book=book("A Letter in Bad Handwriting", "Nell", [
          "You hanged my brother.\n\nI'm not coming to hurt you. I couldn't if I wanted to.",
          "I just want to look at the person who decided he wasn't worth a second chance. I'll be in a village to the west.\n\n- Nell"],
           color="gray"),
      rewards={"xp": 300},
      traits={"complete": ["hag_payoff_met_nell"]})
_payoff("x1k_payoff", "x1_the_informant_after", "quest_success", "x1k_wicks_sister", "Nell")

# =====================================================================================================
# 2. THE BASTILLE WARDEN (Act I: after The Bastille)
# =====================================================================================================
_insert_choice(
    "a7_the_bastille", "x2_the_bastille_warden", "The Bastille Warden",
    "The Bastille's warden, Gorran, threw down his axe the moment the gate fell. He has the keys to the deep cells, and he "
    "says he only took the job because the cult holds his brothers. Decide what the keys are worth.",
    "minecraft:vindicator", "Gorran the Warden",
    [["You ran this prison.", "I locked the doors. The cult decided who went behind them."],
     ["Your prisoners were from Ashford.", "Fourteen men. Twelve left. I kept the two weakest out of the quarry carts. Ask them."],
     ["What do you want?", "To walk out of here and never hold a key again."]],
    ("Take the keys and let him walk", "Gorran presses the ring of keys into your hand. 'Second door on the left. They're alive.'"),
    "Take the keys from his body",
    hp=90, structure="takesapillage:bastille", search=20000,
    aftermath=("Keys Off a Dead Man", "You took the keys from Gorran's body. Ashford's survivors want to know who was guarding them.",
               [_home_talk("a_survivor", 0, "Ashford Survivor", None,
                           [["The warden's dead.", "Gorran? He gave me his bread for a week when I was sick."],
                            ["He ran the prison.", "He did. And he was the only kind one in it. Both things are true."]])], None))

quest("x2s_the_wardens_debt", "The Warden's Debt",
      "Gorran writes from a village far from any prison. He guards their gate now, with a spear and no keys, and he has "
      "something to give you.",
      [_far_talk("gorran", 0, "Gorran the Gatekeeper", None,
                 [["Gorran.", "No keys any more. Just a gate and a spear. I sleep at night now."],
                  ["You said you had something for me.", "The cult paid me in this. I never spent it. It's yours."]])],
      book=book("A Gate Without a Lock", "Gorran", [
          "You let me walk out of the Bastille.\n\nI found a village that needed a gate guard and didn't ask questions.",
          "Come and see me. I've kept something for you all this time.\n\n- Gorran"], color="brown"),
      rewards={"xp": 900, "items": {"minecraft:totem_of_undying": 1, "minecraft:gold_block": 4}},
      traits={"complete": ["hag_payoff_gorran_at_the_gate"]})
_payoff("x2s_payoff", "x2_the_bastille_warden", "quest_spared", "x2s_the_wardens_debt", "Gorran")

quest("x2k_gorrans_brothers", "Gorran's Brothers",
      "The cult released Gorran's brothers to hunt you. They know what you did in the Bastille and they've come at night.",
      [wait("wait_for_dark", 0, "next_night"),
       kill("brothers", "minecraft:vindicator", 4, 1, "The last brother dies with Gorran's name on his lips.")],
      threats=[threat("minecraft:vindicator", "Gorran's Brother", stage=1, count=4, hp=70, smin=12, smax=24,
                      equipment=eq(mainhand=("minecraft:iron_axe", {"minecraft:sharpness": 2})))],
      book=book("Four Names", "Unsigned", [
          "Gorran Ost.\n\nYou took his keys off his body.\n\nWe were the reason he held them.",
          "We're coming for you. Tonight, or the night after.\n\nFour of us. One for each year he kept that job to keep us alive."],
           color="red"),
      rewards={"xp": 1200, "items": {"minecraft:iron_axe": 1}},
      traits={"complete": ["hag_payoff_gorrans_brothers"]})
_payoff("x2k_payoff", "x2_the_bastille_warden_after", "quest_success", "x2k_gorrans_brothers", "Unsigned")

# =====================================================================================================
# 3. THE THIRD SKULL (Act II: after The Skull Harvest)
# =====================================================================================================
_insert_choice(
    "w3_the_skull_harvest", "x3_the_third_skull", "The Third Skull",
    "A piglin skull-trader, Ossian, has the third wither skull the cult needs. He'll sell it to whoever pays first. He is "
    "terrified of the cult, and more terrified of you.",
    "minecraft:piglin", "Ossian the Skull-Trader",
    [["You have a wither skull.", "One. Only one. The cult's buyer comes tomorrow."],
     ["Why sell to them?", "They pay in gold. You pay in threats. I'd rather gold."],
     ["And if I pay?", "Then it's yours and I go somewhere quiet. There are no quiet places here."]],
    ("Pay him fairly and let him go", "Ossian bites the gold, then grins. 'Fair! Nobody is fair down here. Remember Ossian.'"),
    "Take the skull",
    hp=40,
    aftermath=("The Skull Remembers", "You took the skull from a dead trader. Wither skeletons in the fortress smell it on you now.",
               [kill("wither_skeletons", "minecraft:wither_skeleton", 3, 0, "The bones settle. The skull in your pack stays cold.")], None))

quest("x3s_ossians_gift", "Ossian's Gift",
      "Ossian writes (badly, in gold ink) that he found his quiet place: a bastion the piglins abandoned. He left something "
      "there for the only fair customer he ever had.",
      [reach("the_bastion", "minecraft:bastion_remnant", 0, radius=12000, say="A pile of gold with a note: 'For the fair one.'")],
      book=book("For the Fair One", "Ossian", [
          "You paid me!\n\nNobody pays Ossian. They threaten Ossian.",
          "I found a quiet place. Old bastion. I left you something on the top floor. Gold for gold.\n\n- O."], color="yellow"),
      rewards={"xp": 1200, "items": {"minecraft:netherite_scrap": 2, "minecraft:gold_block": 3}},
      traits={"complete": ["hag_payoff_ossians_gift"]})
_payoff("x3s_payoff", "x3_the_third_skull", "quest_spared", "x3s_ossians_gift", "Ossian")

quest("x3k_the_choir_wants_it_back", "The Choir Wants It Back",
      "The Bone Choir knows you killed Ossian for the third skull. They've crossed into the Overworld to take it back.",
      [wait("wait_for_dark", 0, "next_night"),
       kill("choristers", "minecraft:wither_skeleton", 4, 1, "The last chorister crumbles. The skull is still yours.")],
      threats=[threat("minecraft:wither_skeleton", "Bone Chorister", stage=1, count=4, hp=60, smin=12, smax=22,
                      equipment=eq(mainhand="minecraft:stone_sword"))],
      book=book("A Hymn Sheet", "The Bone Choir", ["Thief of the third.\n\nWe sing for what is ours.\n\nWe are coming to collect it."],
           color="gray"),
      rewards={"xp": 1500, "items": {"minecraft:bone_block": 8}},
      traits={"complete": ["hag_payoff_choir_came"]})
_payoff("x3k_payoff", "x3_the_third_skull_after", "quest_success", "x3k_the_choir_wants_it_back", "The Bone Choir")

# =====================================================================================================
# 4. THE PILGRIM OF THE SUN (Act III: after the Valkyrie cairn, before the Sun Spirit)
# =====================================================================================================
_insert_choice(
    "h8_dethroned", "x4_the_pilgrim_of_the_sun", "The Pilgrim of the Sun",
    "A pilgrim has climbed to the Aether to pray to the Sun Spirit you're about to kill. He begs you to spare it. He also "
    "carries a sunstone charm the cult would pay a fortune for.",
    "minecraft:wandering_trader", "Pilgrim of the Sun",
    [["You came all this way to pray?", "Forty years I walked. The Sun answered once. Once is enough."],
     ["The Sun Spirit threw two people out of heaven.", "Then it is a hard god. I did not say it was a kind one."],
     ["What's that charm?", "Sunstone. Mine. It keeps the cold off old bones. Please don't."]],
    ("Let him pray in peace", "The pilgrim kneels and doesn't look up again. You hear him praying for you too."),
    "Take the sunstone",
    hp=30,
    aftermath=("Embers", "The sunstone burns in your pack. Little flames follow you across the clouds.",
               [kill("embers", "minecraft:blaze", 3, 0, "The embers go out. The sunstone is just a stone now.")],
               [threat("minecraft:blaze", "Ember of the Sun", count=3, hp=30, smin=8, smax=16)]))

quest("x4s_sunlight_in_a_bottle", "Sunlight in a Bottle",
      "The pilgrim came down from the clouds. He's resting in a village and asked for you by description: 'the one who let me finish.'",
      [_far_talk("pilgrim", 0, "The Pilgrim", None,
                 [["You came down.", "The Sun is gone. You did it, didn't you? I heard it stop."],
                  ["I'm sorry.", "Don't be. You let me say goodbye. Most people don't get that with their gods."]])],
      book=book("To the One Who Let Me Finish", "The Pilgrim", [
          "I saw the light go out.\n\nI am not angry. I had my prayer.",
          "Come to the village by the river, if you pass. I have something that remembers the Sun."], color="yellow"),
      rewards={"xp": 900, "items": {"minecraft:golden_apple": 3, "minecraft:glowstone": 16}},
      traits={"complete": ["hag_payoff_pilgrim_blessing"]})
_payoff("x4s_payoff", "x4_the_pilgrim_of_the_sun", "quest_spared", "x4s_sunlight_in_a_bottle", "The Pilgrim")

quest("x4k_the_sun_remembers", "The Sun Remembers",
      "Since you took the sunstone, things with wings follow you at night. Phantoms circle your roof and won't leave.",
      [wait("wait_for_dark", 0, "next_night"),
       kill("phantoms", "minecraft:phantom", 4, 1, "The sky is quiet. You throw the sunstone into the sea.")],
      threats=[threat("minecraft:phantom", "Sun-Hungry Phantom", stage=1, count=4, hp=30, smin=10, smax=20)],
      book=book("Scorched Feathers", "Unsigned", ["A bundle of scorched feathers, tied with a pilgrim's cord. Nothing else."], color="gray"),
      rewards={"xp": 1200, "items": {"minecraft:phantom_membrane": 4}},
      traits={"complete": ["hag_payoff_sun_remembered"]})
_payoff("x4k_payoff", "x4_the_pilgrim_of_the_sun_after", "quest_success", "x4k_the_sun_remembers", "Unsigned")

# =====================================================================================================
# 5. THE LAST GARRISON MAN (Act V: after The Frozen Garrison)
# =====================================================================================================
_insert_choice(
    "s2_the_frozen_garrison", "x5_the_last_garrison_man", "The Last Garrison Man",
    "One soldier of the frozen garrison is still alive: Brann, half-mad from cold, who led the Yeti's hunters to the "
    "village for food. He wants to go home. The snowy village wants him dead.",
    "minecraft:stray", "Brann of the Garrison",
    [["You led the Yeti to the village.", "I led it away from the hideout. I didn't think. I was so cold."],
     ["The village wants you dead.", "They should. My wife lives there. She thinks I died ten years ago."],
     ["What do you want?", "To walk home. Even if they hang me at the door."]],
    ("Walk him home to face them", "Brann takes your arm. His hand is ice. 'Thank you. Whatever happens.'"),
    "End him in the snow",
    hp=40,
    aftermath=("Snow on the Grave", "You ended Brann in the snow. Someone in the village should know.",
               [_home_talk("snowy_elder", 0, "Village Elder", None,
                           [["The last garrison man is dead.", "Brann? He was alive? All this time?"],
                            ["He led the Yeti to you.", "Then it's done. I'll tell his wife. ...Unless you want to."]])], None))

quest("x5s_brann_comes_home", "Brann Comes Home",
      "Brann's wife writes. The village didn't hang him. It put him to work rebuilding the walls he helped break.",
      [_far_talk("brann_wife", 0, "Ilse", None,
                 [["How is he?", "Warm, finally. Working. Ashamed. Alive. In that order."],
                  ["And the village?", "They forgave him slower than I did. But they did."]])],
      book=book("From the Snowy Village", "Ilse", [
          "My husband came home after ten years.\n\nYou walked him to our door when you had every reason not to.",
          "Come and eat with us when you pass.\n\n- Ilse, wife of Brann"], color="blue"),
      rewards={"xp": 900, "items": {"minecraft:cooked_beef": 16, "minecraft:diamond": 2}},
      traits={"complete": ["hag_payoff_brann_home"]})
_payoff("x5s_payoff", "x5_the_last_garrison_man", "quest_spared", "x5s_brann_comes_home", "Ilse")

quest("x5k_a_widows_letter", "A Widow's Letter",
      "Brann's wife learned what happened in the snow. She wants his medal back. You have it.",
      [_far_talk("brann_widow", 0, "Ilse", None,
                 [["I'm sorry.", "He survived ten years in the ice. He didn't survive you."],
                  ["He led the Yeti to you.", "I know. I would have forgiven him. I'll never know if he'd have forgiven himself."]])],
      book=book("Give Me His Medal", "Ilse", [
          "You found my husband alive after ten years, and you killed him.\n\nI'm told you had reasons.",
          "Bring me his medal. That's all I want from you."], color="gray"),
      rewards={"xp": 300},
      traits={"complete": ["hag_payoff_widows_letter"]})
_payoff("x5k_payoff", "x5_the_last_garrison_man_after", "quest_success", "x5k_a_widows_letter", "Ilse")

# =====================================================================================================
# Payoffs for the mod's three-and-four-answer choices (sent by the mod's custom couriers)
# =====================================================================================================
def _custom(cid, book_q, sender, lo=LATER[0], hi=LATER[1]):
    EXTRA[f"courier_deliveries/{cid}.json"] = {"id": f"{NS}:{cid}", "trigger": {"type": "custom"},
                                               "delay": {"min_ticks": lo, "max_ticks": hi},
                                               "delivery": {"type": "courier", "sender": sender, "quest_book": qid(book_q)}}


quest("y1_what_the_last_page_said", "What the Last Page Said",
      "The librarian has finished studying the Warden Journal you trusted her with. She found something on the last page "
      "the cult missed: where the Chariot's armour was forged.",
      [_far_talk("librarian", 0, "The Librarian", None,
                 [["What did you find?", "A recipe. Not for a weapon. For a ward. The god's fire can't cross it."],
                  ["Can you make it?", "I already did. Take these. And thank you for trusting me with it."]])],
      book=book("The Last Page", "The Librarian", [
          "You let me keep the journal. I've barely slept.\n\nThe last page isn't a prayer. It's a smith's note.",
          "Come and see me. I've made you something from it."], color="purple"),
      rewards={"xp": 1500, "items": {"minecraft:enchanted_book": 2, "minecraft:fire_charge": 8, "minecraft:golden_apple": 2}},
      traits={"complete": ["hag_payoff_last_page"]})
_custom("y1_lib_study", "y1_what_the_last_page_said", "The Librarian")

quest("y2_the_hand_reads_too", "The Hand Reads Too",
      "Silas of the Gilded Hand has read the Warden Journal you sold him. He's pleased, and paying a bonus, and he wants you "
      "to know the cult will be very interested in where he got it.",
      [_far_talk("silas", 0, "Silas of the Gilded Hand", None,
                 [["You wanted to see me.", "To pay you. The journal was worth every emerald. More, actually."],
                  ["What will you do with it?", "Sell it again. To the people you're fighting. Business is business."]])],
      book=book("A Bonus", "Silas of the Gilded Hand", ["The journal sold beautifully.\n\nCome and collect your bonus. Bring a bag.\n\n- S."],
           color="yellow"),
      rewards={"xp": 600, "items": {"minecraft:emerald_block": 4}},
      traits={"complete": ["hag_payoff_hand_reads"]})
_custom("y2_lib_sold", "y2_the_hand_reads_too", "Silas of the Gilded Hand")

quest("y3_tomas_at_the_gate", "Tomas at the Gate",
      "You sent Halden's son home instead of taking him into the Mansion. Halden has written to you about it.",
      [_far_talk("halden", 0, "Reeve Halden", None,
                 [["Is Tomas all right?", "Angry. Alive. Angrier than alive, if you ask him."],
                  ["I didn't want him killed.", "I know. That's why I'm giving you this. It was my father's."]])],
      book=book("You Sent Him Home", "Reeve Halden", [
          "My son came home furious with you.\n\nThank you.",
          "Come and see me. I have something that should go with you into the dark.\n\n- Halden"], color="blue"),
      rewards={"xp": 1200, "items": {"minecraft:shield": 1, "minecraft:totem_of_undying": 1}},
      traits={"complete": ["hag_payoff_tomas_safe"]})
_custom("y3_tomas_home", "y3_tomas_at_the_gate", "Reeve Halden", lo=6000, hi=12000)

quest("y4_vaels_last_letter", "Vael's Last Letter",
      "Brother Vael has been your spy inside the cult since you spared him. This is his last letter. The handwriting shakes.",
      [reach("the_dead_drop", "minecraft:ruined_portal", 0, radius=8000, say="A loose stone. Behind it: Vael's maps of the Mansion.")],
      book=book("They Know", "Brother Vael", [
          "They know about me. I have hours, not days.\n\nI left everything I learned at a ruined portal near you.",
          "The maps of the Sacred City. The guard rotations. The name of the door that is never locked.",
          "You could have killed me in the fortress. You made me useful instead. I hope I was.\n\n- V."], color="red"),
      rewards={"xp": 1500, "items": {"minecraft:map": 1, "minecraft:ender_pearl": 8, "minecraft:golden_apple": 2}},
      traits={"complete": ["hag_payoff_vael_spy"]})
_custom("y4_vael_spy", "y4_vaels_last_letter", "A courier who won't give a name")

quest("y5_the_midwifes_daughter", "The Midwife's Daughter",
      "The Midwife has been healing with the cleric since you sent her there. Her daughter, born in the blood pits, wants to "
      "meet the person who gave her mother a second life.",
      [_far_talk("daughter", 0, "Wren of the Pits", None,
                 [["You're her daughter?", "Born in the pits. I've never seen a sky that wasn't red until this month."],
                  ["How is she?", "Better. She cries when she sees blood now. The cleric says that's good."]])],
      book=book("My Mother Talks About You", "Wren", [
          "My mother says you could have killed her and didn't.\n\nShe says you sent her somewhere she could get better.",
          "I'd like to meet you. I'm at the cleric's village."], color="green"),
      rewards={"xp": 1000, "items": {"minecraft:golden_apple": 2, "minecraft:honey_bottle": 8}},
      traits={"complete": ["hag_payoff_midwife_healed"]})
_custom("y5_midwife_cleric", "y5_the_midwifes_daughter", "Wren")

# =====================================================================================================
# COMPANIONS: Dunn (chapter 1), Corwin (the granary), Ansel (chapter 6) join you if you spare them
# =====================================================================================================
for k in list(QUESTS):
    if k.startswith("rc1_") or k.startswith("rc6_") or k == "r2g_the_granary_thief":
        QUESTS[k][1]["recruit_companion"] = True

# =====================================================================================================
# THE SIEGE (chapter 7): before the end, the Hand's army comes. How it goes depends on who you've been.
# =====================================================================================================
SIEGE = {
    "saint": ("The whole Watch stands on the wall with you. Hold until the Hand breaks.",
              [("minecraft:vindicator", "Hand Besieger", 4), ("minecraft:pillager", "Hand Crossbow", 2)]),
    "good": ("Half the Watch came. The other half is guarding the children. Hold.",
             [("minecraft:vindicator", "Hand Besieger", 6), ("minecraft:pillager", "Hand Crossbow", 3)]),
    "waver": ("Nobody's sure whose side you're on. Only Halden stands with you. Hold anyway.",
              [("minecraft:vindicator", "Hand Besieger", 8), ("minecraft:pillager", "Hand Crossbow", 3), ("minecraft:evoker", "Hand Conjurer", 1)]),
    "fallen": ("The Watch locked the gate on you. You fight outside the walls, alone.",
               [("minecraft:vindicator", "Hand Besieger", 10), ("minecraft:pillager", "Hand Crossbow", 4), ("minecraft:ravager", "Hand Ravager", 1)]),
    "wicked": ("The siege isn't the Hand's. It's Ashford's. They've set their golems on you.",
               [("minecraft:iron_golem", "Ashford Golem", 2), ("minecraft:vindicator", "Hand Deserter", 4)]),
}


def _shift(q, n):
    for gl in q["goals"]:
        gl["stage"] += n
    for t in q.get("threats", []):
        t["stage"] = t.get("stage", 0) + n


for s, (say, foes) in SIEGE.items():
    q = QUESTS[f"rc7_{s}"][1]
    _shift(q, 2)
    goals = [wait("siege_night", 0, "next_night", "Night falls. Horns on the road. The siege has come.")]
    threats = []
    for ent, name, n in foes:
        gid = "siege_" + ent.split(":")[1]
        goals.append(kill(gid, ent, n, 1))
        threats.append(threat(ent, name, stage=1, count=n, hp=60 if ent != "minecraft:ravager" else 140, smin=12, smax=26))
    goals[-1]["say"] = say
    q["goals"] = goals + q["goals"]
    q["threats"] = threats + q.get("threats", [])
    q["summary"] = "The Siege: " + say + " Then: " + q["summary"]
    q["traits"]["complete"].append(f"hag_siege_{s}")

# =====================================================================================================
# LOST PAGES OF THE WARDEN JOURNAL (found in chests anywhere) and THE FIRST CHAMPION
# =====================================================================================================
PAGES = [
    ("ancient_city", "minecraft:chests/ancient_city", "The Calling", "minecraft:ancient_city",
     ["I am the first. She found me in a field and told me I was going to kill a god.",
      "I laughed. She didn't. That was how I knew."]),
    ("end_city", "minecraft:chests/end_city_treasure", "The Wardens", "minecraft:end_city",
     ["Seven Wardens hold his pieces apart. Kill one and the others grow hungry.",
      "She says I must kill all seven. She says it the way you'd say 'fetch water'."]),
    ("jungle_temple", "minecraft:chests/jungle_temple", "Her Name", "minecraft:jungle_pyramid",
     ["She has two names. I only know the second one.", "At night she writes letters to someone who will never answer."]),
    ("mansion", "minecraft:chests/woodland_mansion", "The Price", "minecraft:mansion",
     ["The cult offered me a crown. I nearly took it.", "She said nothing. She just looked tired. That was worse."]),
    ("treasure", "minecraft:chests/buried_treasure", "The Sea", "minecraft:buried_treasure",
     ["Three Wardens dead. My hands shake now even when I sleep.", "I buried my sword's twin here. In case the next one needs it."]),
    ("igloo", "minecraft:chests/igloo_chest", "The Cold", "minecraft:igloo",
     ["I sheltered here with the second champion. He was younger than I was.", "He asked me if it was worth it. I lied."]),
    ("outpost", "minecraft:chests/pillager_outpost", "The Fall", "minecraft:pillager_outpost",
     ["Five Wardens dead. The sixth killed me. Or it will. I'm writing this the night before.",
      "If you are reading this, she found someone else. Be kinder to her than I was."]),
    ("ruin", "minecraft:chests/underwater_ruin_big", "The Grave", "minecraft:ocean_ruin_cold",
     ["Bury me where the old stones are, where the road used to be.",
      "Don't put my name on it. She'll come and read it, and she's cried enough."]),
]
for i, (key, table, title, struct, text) in enumerate(PAGES, 1):
    quest(f"lp{i}_{key}", f"Lost Page {i}: {title}",
          f"A loose page from the Warden Journal, written by Mara's first champion. It mentions a place. Go and see it.",
          [reach("the_place", struct, 0, radius=15000, say="You stand where the first champion stood. It feels colder than it should.")],
          book=book(f"Warden Journal, Page {i}", "The First Champion", text, color="gray"),
          rewards={"xp": 400 + 50 * i, "items": {"minecraft:experience_bottle": 4}},
          traits={"complete": [f"hag_lost_page_{i}"]})
    EXTRA[f"quest_book_loot_pools/{key}.json"] = {"table": table, "drop_chance": 0.3,
                                                   "books": [{"quest": qid(f"lp{i}_{key}"), "weight": 1}]}

quest("lp9_the_first_champion", "The First Champion",
      "All eight pages found. The first champion asked to be buried 'where the old stones are, where the road used to be'. "
      "Find the trail ruins and pay your respects to the one who walked this road before you.",
      [reach("the_grave", "minecraft:trail_ruins", 0, radius=20000,
             say="A grave with no name. Someone has left fresh flowers. Recently. Only one person would know it's here.")],
      book=book("The Last Page", "Old Mara", [
          "You found all of them. I didn't think anyone ever would.\n\nShe was the first. I loved her like a daughter and sent her to die.",
          "Go to her grave if you want. Don't tell me what it looks like."], color="gray"),
      rewards={"xp": 3000, "items": {"minecraft:enchanted_golden_apple": 1, "minecraft:diamond": 6}},
      traits={"complete": ["hag_first_champion_found"]})
_custom("first_champion", "lp9_the_first_champion", "Old Mara", lo=1200, hi=2400)

# =====================================================================================================
# AFTER THE ENDING
# =====================================================================================================
# --- The Empty Throne (you killed him): the Gilded Hand tries to take his place
quest("et1_whispers_of_a_new_god", "Whispers of a New God",
      "The god is dead, but his Sacred City is not empty. Halden has heard that someone is sitting on Azazel's throne and "
      "calling himself the Gilded One.",
      [_far_talk("halden", 0, "Reeve Halden", None,
                 [["Someone took the throne?", "Silas of the Gilded Hand. He's paying the cult's leftovers to kneel to him."],
                  ["He's not a god.", "He doesn't need to be. He just needs enough people to believe it."]])],
      rewards={"xp": 1000}, nxt=flow("et2_the_pretender", delay=2400, dims=NETHER))
quest("et2_the_pretender", "The Pretender",
      "Return to the Nether Mansion. Silas sits on the throne surrounded by gold-wardens. End his reign.",
      [reach("the_mansion", "netherman:mansion_nether", 0, radius=30000),
       kill("wardens", "minecraft:piglin_brute", 4, 1),
       kill("silas", "minecraft:evoker", 1, 1, "Silas slides off the throne. 'It was only ever about the gold,' he says. It's his last joke.")],
      threats=[threat("minecraft:piglin_brute", "Gilded Warden", stage=1, count=4, hp=90, structure="netherman:mansion_nether", search=30000,
                      smin=4, smax=14, equipment=eq(mainhand="minecraft:golden_axe")),
               threat("minecraft:evoker", "Silas the Pretender", stage=1, hp=220, structure="netherman:mansion_nether", search=30000,
                      smin=4, smax=12, effects=fx(("minecraft:resistance", 1)))],
      rewards={"xp": 3000, "items": {"minecraft:gold_block": 16}}, nxt=flow("et3_the_last_faithful"))
quest("et3_the_last_faithful", "The Last Faithful",
      "The last of the Faithful kneel in front of the empty throne. Their leader asks you what they're supposed to do now.",
      [kill("the_last_faithful", "minecraft:vindicator", 1, 0)],
      threats=[threat("minecraft:vindicator", "The Last Faithful", hp=60, spare=True, smin=4, smax=8,
                      talk=[["Your god is dead.", "We know. We felt it. Like a bell that stopped."],
                            ["What will you do?", "Whatever you tell us. We've never not been told."]])],
      choice={"spare": {"label": "Tell them to go home and learn to farm", "line": "'Home.' He says it like a word in a foreign language.",
                        "traits": ["hag_faithful_sent_home"]},
              "attack": {"label": "Finish the cult for good", "traits": ["hag_faithful_ended"]}},
      rewards={"xp": 2000}, nxt=flow("et4_morning", delay=2400, dims=OVER))
quest("et4_morning", "Morning",
      "The throne is empty for good. Go home. Ring the bell.",
      [_home_talk("elder", 0, "Village Elder", None,
                  [["It's over.", "Is it? Truly?"],
                   ["Truly.", "Then ring the bell. It's been a long time since it rang for something good."]])],
      rewards={"xp": 2000, "items": {"minecraft:bell": 1, "minecraft:cake": 1}},
      traits={"complete": ["hag_empty_throne_done"]})

# --- The Hand of Azazel (you knelt): your god's first commands
quest("ha1_the_first_command", "The First Command",
      "Azazel is whole and you are his Hand. His first command: the beacon you lit over the village must go dark. It offends him.",
      [breakb("the_beacon", "minecraft:beacon", 1, 0, "The light goes out. Somewhere a child asks why.", rng=30000000)],
      rewards={"xp": 1500}, nxt=flow("ha2_ashford_must_kneel", delay=2400, dims=OVER))
quest("ha2_ashford_must_kneel", "Ashford Must Kneel",
      "Ashford refuses to tithe. Azazel wants their golems broken so they understand there is nothing left to protect them.",
      [kill("golems", "minecraft:iron_golem", 2, 0, "Ashford pays the next morning, every coin, in silence.")],
      threats=[threat("minecraft:iron_golem", "Ashford's Golem", count=2, hp=100, smin=10, smax=20)],
      rewards={"xp": 2000, "items": {"minecraft:gold_block": 8}}, nxt=flow("ha3_haldens_last_stand", delay=2400))
quest("ha3_haldens_last_stand", "Halden's Last Stand",
      "Reeve Halden has come to stop you, alone, with his father's spear. He doesn't expect to win.",
      [kill("halden", "minecraft:vindicator", 1, 0)],
      threats=[threat("minecraft:vindicator", "Reeve Halden", hp=80, spare=True, smin=4, smax=8,
                      talk=[["Go home, Halden.", "I am home. You're the one who left."],
                            ["You can't win.", "I know. I came anyway. That's the difference between us now."]])],
      choice={"spare": {"label": "Let him live with it", "line": "Halden lowers the spear. 'That's crueller. You know that.'",
                        "traits": ["hag_halden_spared_by_the_hand"]},
              "attack": {"label": "Obey your god", "traits": ["hag_halden_killed_by_the_hand"]}},
      rewards={"xp": 2500}, nxt=flow("ha4_the_hand_of_azazel", delay=2400))
quest("ha4_the_hand_of_azazel", "The Hand of Azazel",
      "The roads are yours. Bring your god his tithe, and take your place at his right hand.",
      [collect("tithe", "minecraft:gold_block", 16, 0, "Azazel smiles. It is not a human smile.")],
      rewards={"xp": 4000, "items": {"minecraft:enchanted_golden_apple": 1}},
      traits={"complete": ["hag_hand_of_azazel_done"]})


def link_after_endings():
    """Called by build.py once the main story exists."""
    QUESTS["g2_ashford_rebuilt"][1].setdefault("flow", {})["delayed_next_quest"] = flow("et1_whispers_of_a_new_god", delay=12000, dims=OVER)
    fl = QUESTS["b3_the_last_preacher"][1].setdefault("flow", {})
    fl["delayed_next_quest"] = flow("ha1_the_first_command", delay=6000, dims=OVER)

# =====================================================================================================
# REPUTATION UNLOCKS (the mod sends these letters when your reputation crosses a line)
# =====================================================================================================
quest("ru1_the_lantern_road", "The Lantern Road",
      "Word of what you are has spread. The villages along the old road want to light it end to end, the way it was before "
      "the cult, and they want you to carry the first flame. Place twenty lanterns along the road out of your village.",
      [place_anywhere("lanterns", "minecraft:lantern", 20, 0, "The road glows all the way to the hills. Travellers walk it at night again."),
       _home_talk("elder", 1, "Village Elder", None, [["The road's lit.", "Then nobody walks it alone any more. That's your doing."]])],
      book=book("Light the Road", "The Villages of the Old Road", [
          "We've talked it over, all of us, from the river to the hills.\n\nWe want the old road lit again.",
          "We want you to light the first lantern. You know why."], color="yellow"),
      rewards={"xp": 2000, "items": {"minecraft:beacon": 1, "minecraft:golden_apple": 4}},
      traits={"complete": ["hag_lantern_road_lit"]})
_custom("unlock_saint", "ru1_the_lantern_road", "The Villages of the Old Road", lo=1200, hi=3600)

quest("ru2_silas_makes_an_offer", "Silas Makes an Offer",
      "Your name frightens people now, and Silas of the Gilded Hand has noticed. He wants to make you a partner. Meet him, "
      "hear him out, and take his first job: break a village's iron golem so it learns who protects it now.",
      [_far_talk("silas", 0, "Silas of the Gilded Hand", None,
                 [["You wanted me.", "Everybody wants you. Most of them want you dead. I want you rich."],
                  ["What's the job?", "A village that won't pay. Their golem is the only reason. Remove the reason."]]),
       kill("golem", "minecraft:iron_golem", 1, 1, "The village pays Silas the next morning. Silas pays you.")],
      threats=[threat("minecraft:iron_golem", "Stubborn Golem", stage=1, hp=100, smin=8, smax=16)],
      book=book("A Business Proposal", "Silas of the Gilded Hand", [
          "I've followed your career with interest.\n\nYou and I want the same things. Gold. Fear. Quiet.",
          "Come and talk. Bring nothing but ambition.\n\n- S."], color="yellow"),
      rewards={"xp": 1500, "items": {"minecraft:emerald_block": 6, "minecraft:gold_block": 4}},
      traits={"complete": ["hag_silas_partner"]})
_custom("unlock_wicked", "ru2_silas_makes_an_offer", "Silas of the Gilded Hand", lo=1200, hi=3600)
