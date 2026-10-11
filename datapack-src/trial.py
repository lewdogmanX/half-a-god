"""The Trial. From Act II on, a Fallen or Wicked player can be taken in their sleep (once per act, on a random night) and
tried by the Watch in a court of its own. Each charge is something the player really did (a trait), told by a witness.
A witness the player killed can't testify: the court only hears "the witness cannot be found".

charge: (id, crime trait, weight, witness, testimony, [traits meaning the witness is dead])
The mod adds a few charges of its own from everyday play (villagers killed, village stores taken, work for the Hand,
prayers at the cult's shrines)."""

C = []


def charge(cid, trait, weight, witness, testimony, silenced=()):
    C.append({"id": cid, "trait": trait, "weight": weight, "witness": witness, "testimony": testimony, "silenced": list(silenced)})


HALDEN_DEAD = ["hag_halden_killed_by_the_hand"]

charge("peddler", "peddler_slain", 1, "The Village Elder",
       "The Smiling Peddler was a liar and worse. But they cut him down in the market road, in front of the children.")
charge("corwin", "corwin_slain", 2, "Reeve Halden",
       "Corwin stole a sack of grain for his sister's children. This one killed him for it. I buried him myself.", HALDEN_DEAD)
charge("orsk", "orsk_executed", 1, "Dunn the Deserter",
       "Orsk was on his knees. He'd dropped his blade. They executed him anyway. I was there. I saw it.", ["hag_cruelty_1"])
charge("dunn", "hag_cruelty_1", 2, "Orsk the Tally-Keeper",
       "Dunn threw down his crossbow at Ashford. He was done fighting. This one killed him for it.", ["orsk_executed"])
charge("tobin", "hag_cruelty_2", 2, "Tobin the Scavenger",
       "I had one skull to sell and two girls to feed. They took the skull. One of my girls didn't see the spring.")
charge("ysolde", "hag_cruelty_3", 1, "Sister Ysolde",
       "I painted that eye forty years ago, when I was young and stupid. I was punished for it in front of my whole chapel.")
charge("hale", "hag_cruelty_4", 2, "The Librarian",
       "Brother Hale was a scholar. He was going to teach the Old Tongue to the world. They ended him.")
charge("mott", "hag_cruelty_5", 1, "A Harbour Lad",
       "Saltjaw Mott was a wrecker, aye. But we watched them drown him off the rocks like a sack of cats.")
charge("ansel", "hag_cruelty_6", 2, "The Midwife",
       "Ansel was sick. He was trying. I saw this one put him down like a dog.", ["midwife_slain"])
charge("garret", "hag_cruelty_7", 1, "Reeve Halden",
       "Garret was Ashford's to judge. They took it out of my hands and judged him themselves.", HALDEN_DEAD)
charge("wick", "hag_cruelty_x1_the_informant", 2, "Nell, Wick's Sister",
       "My brother sold patrol times for bread. They killed him on the village green. I came to see their face. I'll never forget it.")
charge("gorran", "hag_cruelty_x2_the_bastille_warden", 1, "Brother Vael",
       "Gorran kept the Bastille's keys and gave the sick his bread. I know. I paid him. They left him dead with the keys in his hand.",
       ["vael_slain", "hag_vael_branded"])
charge("ossian", "hag_cruelty_x3_the_third_skull", 1, "Brother Vael",
       "Ossian sold skulls, like I counted them. Now his own is on a Bone Choir altar.", ["vael_slain", "hag_vael_branded"])
charge("pilgrim_sun", "hag_cruelty_x4_the_pilgrim_of_the_sun", 1, "Wren the Apiarist",
       "The Pilgrim of the Sun was harmless. They took his charm and sold it. The bees wouldn't go near them after.")
charge("garrison", "hag_cruelty_x5_the_last_garrison_man", 2, "Ilse of the Snowy Village",
       "My husband was in the ice ten years. He came back, and this one sent him back to the snow.")
charge("vael", "vael_slain", 2, "Ossian the Skull-Trader",
       "Brother Vael was a counter, not a killer. They killed him in his own fortress.", ["hag_cruelty_x3_the_third_skull"])
charge("vael_branded", "hag_vael_branded", 2, "Brother Vael",
       "(The Captain calls Brother Vael. Nobody answers. Branded men don't live long once the cult finds them.)",
       ["hag_vael_branded"])
charge("midwife", "midwife_slain", 2, "Ansel the Blood-Drinker",
       "She was trying to make it right. She was teaching me to stop. They killed her beside her own pits.", ["hag_cruelty_6"])
charge("pit", "hag_midwife_kept_pit", 2, "The Cleric",
       "There is still a blood pit under that church. They let her keep it. 'For research.' It is not empty.")
charge("pell", "gt_silenced_the_witness", 3, "Pell",
       "(The Captain calls Pell, the grower who was going to testify against the cartel. Nobody in the valley knows where he went.)",
       ["gt_silenced_the_witness"])
charge("vasco", "hag_vasco_betrayal", 1, "Don Vasco",
       "We shook hands. Then they sold me out. I'm a criminal, your honour, but I keep my word.", ["gt_vasco_killed"])
charge("journal", "hag_lib_sold", 2, "The Librarian",
       "Thirty years I waited for that journal. They sold it to the Gilded Hand before I'd read the first page.")
charge("faithful", "hag_faithful_ended", 2, "Brother Hale",
       "They were beaten. They were kneeling. They were ended anyway.", ["hag_cruelty_4"])
charge("morrow", "took_the_gold", 1, "Morrow the Assayer",
       "Oh, they took the Hand's gold, your honour. I weighed it myself. Every coin.", ["morrow_slain"])

# What the court says. {name} is the accused; the mod fills in the counts.
SCRIPT = {
    "open": [
        ["The Judge", "This court of the Watch is in session. The accused stands before us in chains."],
        ["The Watch Captain", "We found them asleep, your honour. They didn't wake until we'd tied them. We have witnesses."],
        ["The Judge", "Then let us hear them."]],
    "silent_witness": "The Captain calls {witness}. Silence. 'The witness cannot be found, your honour.'",
    "villagers": ["The Families", "{n} of our people were killed by the accused. We have their names. We will read them if you want."],
    "stores": ["A Shopkeeper", "They broke into our stores. {n} chests, emptied. Winter stores, some of them."],
    "hand": ["A Watchman", "I watched them collect for the Gilded Hand. {n} times that I saw. Probably more."],
    "shrines": ["A Shepherd", "I saw them kneel at a golden eye on the road. They prayed to it, your honour. It answered."],
    "plea": ["The Judge", "You have heard the witnesses. How do you plead?"],
    "no_case": ["The Judge", "There is no case here. Whoever brought this one in wasted the court's night. Take them home."],
    "acquitted": ["The Judge", "The court is not convinced. The accused is free to go. Do not make us regret it."],
    "guilty": ["The Judge", "Guilty. The accused will serve {days} in the obsidian cells."],
    "bribe_worked": ["The Judge", "The jury cannot agree. One of them can't seem to stop looking at the floor. The accused is free to go."],
    "bribe_failed": ["The Judge", "The jury is agreed. Even the one who was paid. Guilty: {days} in the obsidian cells."],
    # answering each charge (the court weighs the answer)
    "deny_proven": ["The Watch Captain", "The witness swore to it, your honour, and we have more who would. The accused is lying."],
    "deny_weak": ["The Judge", "One witness, and no more. The court has doubts. Struck from the record."],
    "explain": ["The Judge", "The court hears your reasons. It does not forgive them, but it hears them."],
    "silent_answer": ["The Judge", "Nothing to say. The court notes it."],
    "confessed": ["The Clerk", "The accused confessed this to a priest before the court ever heard it. The priest has said so."],
    # your ranks
    "advocate": ["An Advocate of the Hand", "Your honour, the Gilded Hand speaks for the accused. A valued friend of ours. I'm sure the court understands."],
    "advocate_judge": ["The Judge", "The court understands perfectly. It doesn't have to like it."],
    "watch_lenient": ["The Watch Captain", "The accused has served the Watch, your honour. They lost their way. They can find it again."],
    "watch_harsh": ["The Judge", "A sworn officer of the Watch. You of all people knew better. The court will remember that."],
    # the jury and the gavel
    "jury": ["The Judge", "Members of the jury. Your verdict."],
    "rival_against": ["{name}", "I saw it with my own eyes, your honour. Day {day}. I'll swear to it."],
    "rival_for": ["{name}", "Your honour, I know the accused. Whatever they've done, they're not what the court's been told."],
}

# what the jury says when it votes
VOTES = {"guilty": "Guilty.", "not": "Not guilty."}

PLEAS = [
    {"id": "guilty", "label": "Guilty", "hint": "A shorter sentence, if the court has a case."},
    {"id": "not_guilty", "label": "Not guilty", "hint": "A gamble: walk free, or serve longer."},
    {"id": "silent", "label": "Say nothing", "hint": "Let the court decide on the evidence alone."},
    {"id": "bribe", "label": "Pay a juror (16 emeralds from your things)", "hint": "The Gilded Hand knows which juror. Needs Hand rank and 16 emeralds."},
]


def build():
    import settlement
    known = settlement.produced_traits()
    ids = set()
    for c in C:
        assert c["id"] not in ids, c["id"]
        ids.add(c["id"])
        assert c["trait"] in known, f"trial charge {c['id']}: trait {c['trait']} is never set"
        for t in c["silenced"]:
            assert t in known, f"trial charge {c['id']}: silencing trait {t} is never set"
    return {"charges": C, "script": SCRIPT, "pleas": PLEAS, "votes": VOTES,
            "answers": [{"id": "deny", "label": "Deny it", "hint": "If the court can prove it, lying makes it worse. If it can't, it may be struck out."},
                        {"id": "explain", "label": "Explain", "hint": "Admit it, and say why. It weighs a little less."},
                        {"id": "silent", "label": "Say nothing", "hint": "Let it stand as it is."}]}
