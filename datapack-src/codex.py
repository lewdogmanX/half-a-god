"""The lore codex: people, places and the cult, unlocked as the story reaches them (assets/half_a_god/story/codex.json).
Each entry unlocks when the named quest has started (or been done)."""
from lib import qid

E = []


def entry(category, title, unlock, text):
    E.append({"category": category, "title": title, "unlock": qid(unlock), "text": text})


# ---- People
entry("People", "Old Mara", "the_first_letter",
      "An old woman who writes letters to strangers and is always right about where trouble will be. Nobody knows where she lives. "
      "Her handwriting is very old-fashioned, and so, some say, is she.")
entry("People", "Maraal", "v10_maraal",
      "Old Mara's real name. Once she rode beside the Charioteer; together they were thrown out of heaven by the Sun Spirit. "
      "She has spent a very long time choosing champions to undo what she helped begin. You are not the first.")
entry("People", "Reeve Halden", "a1_ashford_burns",
      "The reeve of Ashford, what's left of it. He lost his village's men to the cult and refuses to lose anything else. "
      "He judges fairly and forgives slowly.")
entry("People", "Tomas, Halden's son", "m2_the_cursed_quarries",
      "Taken from Ashford with the other men and set to mine gold in the Sacred City's quarries. Brave, angry, and very much his father's son.")
entry("People", "The Smiling Peddler", "p7_gold_at_double_weight",
      "A merchant who paid double for gold and asked what a person weighs. He painted golden eyes on chapel doors so 'He' could see.")
entry("People", "Brother Vael", "a12_brother_vael",
      "The cult's steward in the Nether: he counts gold, skulls and people. Good at counting. Less good at being sure he's on the right side.")
entry("People", "The Librarian", "e1_the_librarians_request",
      "A scholar who has studied the cult's Old Tongue for thirty years. She can read enough of it to be properly frightened.")
entry("People", "Silas of the Gilded Hand", "r2e_the_assayer",
      "A merchant who buys what others won't sell: skulls, blood, silence. The Gilded Hand is the cult's purse. Silas is its smile.")
entry("People", "The Midwife", "v3_the_midwife",
      "She ran the blood pits under the Sinful Church, where the cult tried to grow its god a new body. 'None of them ever spoke.'")
entry("People", "The Fixer", "gt1_a_note_in_the_stash",
      "The Blackwater Cartel's man in the villages: he finds growers, buyers, and people who can keep their mouths shut.")
entry("People", "Don Vasco", "gt12_don_vasco",
      "Head of the Blackwater Cartel. Holds court in the Labyrinth. Pays a third of everything to the Chariot, and resents every coin.")
entry("People", "Wren the Apiarist", "bz1_a_letter_smelling_of_honey",
      "A beekeeper whose bees began flying into their nests and never coming out after the light rose from the Wither's pit.")
entry("People", "Old Aurel", "lg1_the_golden_legion",
      "A retired soldier of the Ashford Watch who fought piglins in his youth, and still dreams of the warrior who could not die.")
entry("People", "Dunn the Deserter", "rc1_saint",
      "A raider who threw his crossbow down at Ashford and couldn't pick it up again.")
entry("People", "Corwin the Refugee", "r2g_the_granary_thief",
      "An Ashford refugee who stole grain for his sister's children.")
entry("People", "Wick the Informant", "x1_the_informant",
      "A pillager scout who sold patrol times for bread. The gold, he says, goes up, never down.")
entry("People", "Gorran the Warden", "x2_the_bastille_warden",
      "He locked the Bastille's doors; the cult decided who went behind them. He gave the sick his bread.")
entry("People", "Brann of the Garrison", "x5_the_last_garrison_man",
      "The last living soldier of the frozen garrison. His wife has thought him dead for ten years.")

# ---- Places
entry("Places", "Ashford", "a1_ashford_burns",
      "The next village over from yours. The cult's raiders burned it and took its men, counted, not killed. What it becomes is up to you.")
entry("Places", "The Bastille", "a7_the_bastille",
      "An illager fortress paid for in cult gold, built with cells for a hundred. Ashford's men were held there.")
entry("Places", "The Believer's House", "a6_believers_house",
      "A small house where a Preacher prays over the cult's prisoners on their way north.")
entry("Places", "The Aether", "h2_hostile_paradise",
      "A realm above the clouds that should be full of light and is strangely empty. Something left. Or was thrown out.")
entry("Places", "The End", "e5_free_the_end",
      "Where the second Warden waited: a dragon guarding something it had forgotten.")
entry("Places", "The Blackwater Valley", "gt1_a_note_in_the_stash",
      "Where the cartel grows and sells. A third of every emerald made here goes to the Chariot.")
entry("Places", "The Sinful Church", "v2_the_sinful_church",
      "A church with blood pits beneath it. The cult tried to grow its god a body here.")
entry("Places", "The Sacred City", "m1_follow_the_crimson_bee",
      "The Nether Mansion: the Cult of Azazel's city of gold, its quarries worked by stolen men, its Lair at the very end.")

# ---- The Cult
entry("The Cult", "The Cult of Azazel", "p4_eye_on_chapel",
      "They paint golden eyes on chapel doors. They believe gold is faith made solid, and that enough of it buys a god's favour.")
entry("The Cult", "The Faithful", "a4_refugee_road",
      "The cult's warriors. They watch the roads and take people by night.")
entry("The Cult", "The Divine Chariot", "w0_two_roads_meet",
      "The name every trail leads to. Once a charioteer of heaven; now half a god, waiting in the Sacred City.")
entry("The Cult", "The Seven Wardens", "w8_six_remain",
      "Seven great beasts that held the Chariot's pieces apart: the Wither, the Ender Dragon, and five more in the snow, the sand, "
      "the sea, the underworld and the dragon tower. Kill one, and the others grow hungry.")
entry("The Cult", "The Gilded Hand", "r2e_the_assayer",
      "The cult's purse: merchants who buy skulls, blood and gold, and sell anything to anyone.")
entry("The Cult", "The Old Tongue", "e1_the_librarians_request",
      "The cult's written language. Mostly angles and anger. The Warden Journal is written in it.")
entry("The Cult", "The Champions of M.", "i6_the_mountain_tower",
      "The Dead King's crown named him 'Champion of M. Second of seven. Failed.' Maraal has done this before.")
entry("The Cult", "The True Azazel", "m9_half_a_soul",
      "A half-soul cannot die. To end him, his head must be joined to his body, and then he must be killed while he is whole.")


def build():
    return {"entries": E}
