"""Dreams: sometimes, when you wake from a full night's sleep (about one night in four, never two nights running, never the
same dream twice), you dream. Three kinds:

  past   the god before he was split, told in order, one dream at a time
  omen   about the act you're in now
  echo   a choice you made, coming back

Each dream: id, kind, title, motif (the picture behind the words: sun, eye, chains, crown, flame, bell, drop, wave),
lines (shown one at a time), and for omens the act (chronicle act trait) / for echoes the trait that must be set.
The mod also has two dreams it shows itself: the kidnapping before a trial, and the full fall (a hidden questline)."""

D = []


def past(i, title, motif, *lines):
    D.append({"id": f"past_{i}", "kind": "past", "order": i, "title": title, "motif": motif, "lines": list(lines)})


def omen(act_trait, title, motif, *lines):
    D.append({"id": "omen_" + act_trait.replace("hag_", "").replace("_done", ""), "kind": "omen", "act": act_trait,
              "title": title, "motif": motif, "lines": list(lines)})


def echo(trait, title, motif, *lines):
    D.append({"id": "echo_" + trait, "kind": "echo", "trait": trait, "title": title, "motif": motif, "lines": list(lines)})


# ---- the god's past, in order
past(1, "The Charioteer", "sun",
     "You are driving a chariot of fire across the sky.",
     "The horses are made of noon.",
     "Someone laughs beside you. You can't see her face, only her hands on the reins next to yours.")
past(2, "Embers", "flame",
     "She is young. Her hair is the colour of embers.",
     "'Faster,' she says. 'Let them see us.'",
     "Far below, the whole world looks up.")
past(3, "The Spirit of the Sun", "sun",
     "A voice like a forge: 'You carry the sun. You do not get to love anything.'",
     "The chariot shudders. The reins burn.",
     "You wake with your palms hot.")
past(4, "Two of You", "eye",
     "Two of you stand in a field of ash.",
     "One is gold, and weeps. One is iron, and laughs.",
     "Both of them have your face.")
past(5, "The Head and the Body", "crown",
     "A head sits on an altar in a city of gold, eyes open.",
     "Somewhere far below, a body walks the dark, looking for it.",
     "Neither of them can sleep. Neither can you.")
past(6, "Seven Keepers", "chains",
     "Seven great beasts circle you, holding the pieces of you apart.",
     "One by one, they lie down.",
     "Something, somewhere, is very glad.")

# ---- omens: one for each act
omen("hag_the_first_letter_done", "A Bell With No Tongue", "bell",
     "A bell with no tongue swings in an empty tower.",
     "In the square, a man in a fine coat weighs the villagers one at a time.",
     "He smiles at you. He already knows what you weigh.")
omen("hag_a1_ashford_burns_done", "Counted", "chains",
     "Fourteen men walk north in chains.",
     "Someone counts them as they pass. Fourteen. Fourteen. Fourteen.",
     "The counting doesn't stop when the men are gone.")
omen("hag_w1_the_skull_market_done", "Three Skulls", "crown",
     "Three skulls on a heap of soul sand, waiting.",
     "A black crown hangs in the air above them.",
     "It is exactly the right size for your head.")
omen("hag_h1_a_light_above_the_pit_done", "An Empty Throne in the Sky", "sun",
     "A throne of cloud. Nobody on it.",
     "The light on the throne is going out, very slowly.",
     "Something up there is ashamed.")
omen("hag_e1_the_librarians_request_done", "What the Dragon Forgot", "eye",
     "A dragon circles a pillar of black stone.",
     "It has forgotten what it is guarding.",
     "You haven't.")
omen("hag_s1_cold_hearths_done", "Seven Beasts", "chains",
     "Snow. Sand. Sea. Fire. A knight. A dragon's bones.",
     "Each of them is holding something shut.",
     "Each of them is so tired.")
omen("hag_v1_blood_in_the_wells_done", "A Body in the Blood", "drop",
     "Something is growing in a pit of blood.",
     "It has your hands.",
     "It is waiting for a head.")
omen("hag_m1_follow_the_crimson_bee_done", "The City of Gold", "eye",
     "Every door in the city has an eye painted on it.",
     "Every eye turns to follow you.",
     "At the end of the street stands a chariot with no horses.")
omen("hag_z1_caution_done", "Whole", "crown",
     "A head on a body. A god opens his eyes.",
     "He looks at you and says your name, kindly.",
     "That is the worst part.")

# ---- echoes of what you did
echo("peddler_slain", "Double Weight", "eye",
     "The Smiling Peddler sits at the end of your bed, counting gold coins onto the blanket.",
     "'You were worth twice that,' he says. 'I'd have paid it.'")
echo("heard_the_word", "The Book", "bell",
     "You are reading the Peddler's book. The words move on the page.",
     "'Gold is faith made solid,' it says, in a voice you used to trust.")
echo("vael_slain", "Counting", "chains",
     "Brother Vael counts the steps from his fortress to your door.",
     "He never reaches the end. He keeps counting.")
echo("hag_vael_free", "East", "sun",
     "A man in a cult robe walks east into the sunrise, counting his steps.",
     "He stops, turns, and waves to you.")
echo("corwin_slain", "A Sack of Grain", "drop",
     "A sack of grain sits in the middle of the road.",
     "Children walk past it, one by one, and none of them look at you.")
echo("corwin_spared", "Winter Bread", "flame",
     "You can smell bread. Somewhere, a child is warm.",
     "That's all. That's the whole dream.")
echo("midwife_slain", "None of Them Spoke", "drop",
     "The blood pits are very quiet.",
     "Then, all at once, all of them speak.")
echo("hag_midwife_cleric", "Counting Children", "bell",
     "The Midwife counts children in a chapel.",
     "She reaches the last one, and starts again, smiling.")
echo("maraal_forgiven", "Grey and Red", "flame",
     "Maraal sits by your fire. Her hair is grey, then red, then grey again.",
     "'Thank you,' she says, to someone who isn't you.")
echo("maraal_rejected", "Unsent", "bell",
     "Letters pile up at a crossroads, unsent.",
     "Every one of them is addressed to you.")
echo("hag_cruelty_x1_the_informant", "The Green", "drop",
     "Wick is selling bread on the village green.",
     "When you go to buy some, there's nobody there.")
echo("hag_mercy_x1_the_informant", "Flour", "flame",
     "Wick waves at you from a bakery doorway.",
     "He has flour on his face. He looks embarrassed to be happy.")
echo("orsk_executed", "Tallies", "chains",
     "Someone is cutting marks into a post. Hundreds of marks.",
     "Slowly you realise they're counting you.")
echo("orsk_spared", "Planks", "flame",
     "A man counts planks as he builds a house.",
     "He reaches the end of the count, and the house is finished.")
echo("hag_lib_burn", "Ash Pages", "flame",
     "Pages of the Warden Journal fall like snow.",
     "You catch one. It has your name on it, and then it burns.")
echo("hag_cruelty_2", "Hungry", "drop",
     "Two little girls sit at an empty table.",
     "Their father is out selling rabbits. Rabbits don't have skulls worth anything.")
echo("hag_mercy_5", "True Lights", "wave",
     "Lights on the rocks, all along the coast.",
     "A man in chains is lighting them, one by one, and the ships come home.")

# ---- shown by the mod, not by chance
SPECIAL = {
    "kidnap": {"id": "kidnap", "kind": "special", "title": "Torchlight", "motif": "chains", "lines": [
        "Torchlight through your eyelids. Boots on the boards.",
        "'That's them. Take them.'",
        "A hood. Rope. The tar smell of the Watch's torches."]},
    "godsaw": {"id": "godsaw", "kind": "special", "title": "What the God Saw", "motif": "sun", "lines": [
        "You are the god, and you are carrying the sun across the sky.",
        "Maraal is beside you. The Spirit of the Sun is shouting.",
        "The Spirit throws her out of the sky.",
        "You let go of the reins. You don't fall. You jump.",
        "All the way down, you hold her.",
        "The ground splits you in two when you land: the half that caught her, and the half that hated the sky for letting her go."]},
    "release": {"id": "release", "kind": "special", "title": "Dawn", "motif": "sun", "lines": [
        "They drag you out at dawn, blindfolded, and leave you where they found you.",
        "Your things are beside you, tied in a sack with a Watch seal on it."]},
    "escape": {"id": "escape", "kind": "special", "title": "Out", "motif": "flame", "lines": [
        "The block gives. Behind it, a crack just wide enough for a desperate person.",
        "You squeeze through into the dark, and drop, and run, and keep running.",
        "By morning you're home. Your things are there too: someone in the Watch has a soft heart, or a price."]},
    "breakout": {"id": "breakout", "kind": "special", "title": "The Hand Pays Its Debts", "motif": "eye",
                 "lines": [
                     "Shouting. A door that shouldn't open, opening.",
                     "A man in gilded gloves: 'Silas sends his regards. And your things.'",
                     "You're home before the Watch has finished counting the guards."]},
}

MOTIFS = {"sun", "eye", "chains", "crown", "flame", "bell", "drop", "wave"}


def build():
    import settlement
    known = settlement.produced_traits()
    import mod_tables
    acts = {a for a, _ in mod_tables.ACTS}
    ids = set()
    for d in D + list(SPECIAL.values()):
        assert d["id"] not in ids, d["id"]
        ids.add(d["id"])
        assert d["motif"] in MOTIFS, d
        if d["kind"] == "echo":
            assert d["trait"] in known, f"dream {d['id']}: trait {d['trait']} is never set"
        if d["kind"] == "omen":
            assert d["act"] in acts, f"dream {d['id']}: {d['act']} doesn't start an act"
    return {"dreams": D, "special": SPECIAL}
