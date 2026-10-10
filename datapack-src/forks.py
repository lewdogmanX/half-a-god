"""Parallel story branches. A courier on quest X starts a second path while X's own flow continues the first.
Both paths meet again at a join quest that requires the other path's last quest (it waits until it is done)."""
from lib import *

IS = "irons_spellbooks"

# ======================= IRON'S SPELLS, PART 1 (before the Wither) =======================
# fork: the believer house (A6) also sends a priest's warning
courier("letter_magic", "a6_believers_house", "i1_the_priests_warning", sender="The Priest", lo=1200, hi=2400)
quest("i1_the_priests_warning", "The Priest's Warning",
      "A village priest writes: the cult's Faithful are casting real spells. To fight magic, you must learn it.",
      [place_anywhere("inscription_table", f"{IS}:inscription_table", 1, 0, "Runes crawl across the wood as if they have been waiting."),
       collect("arcane_essence", f"{IS}:arcane_essence", 6, 1)],
      book=book("A Priest's Warning", "The Priest", [
          "The Faithful who burned Ashford were not only axes and torches. Some of them cast fire with their hands.",
          "That is evoker magic, stolen and sold. Build an inscription table. Learn to write spells. Then go and take back what they stole."], color="purple"),
      rewards={"xp": 600, "items": {f"{IS}:arcane_essence": 4}},
      nxt=flow("i2_fire_in_the_tower"))

quest("i2_fire_in_the_tower", "Fire in the Tower",
      "A Pyromancer has been selling fire spells to the cult. His tower stands in the hills or forest.",
      [reach("find_the_tower", f"{IS}:pyromancer_tower", 0, radius=30000),
       kill("pyromancer", f"{IS}:pyromancer", 1, 1, "His ledger is half burned. The half that survived lists the Faithful by name.")],
      rewards={"xp": 1200, "items": {"minecraft:diamond": 2}},
      nxt=flow("i3_the_evoker_fort"))

quest("i3_the_evoker_fort", "The Evoker Fort",
      "The stolen magic came from an Evoker Fort. Its Archevoker taught the cult to cast. End the lessons.",
      [reach("find_the_fort", f"{IS}:evoker_fort", 0, radius=30000),
       kill("archevoker", f"{IS}:archevoker", 1, 1, "Caged villagers cheer as the Archevoker falls. One of them is from Ashford.")],
      rewards={"xp": 2000, "items": {"minecraft:totem_of_undying": 1}},
      nxt=flow("i4_the_scroll_forge"))

quest("i4_the_scroll_forge", "The Scroll Forge",
      "Build a Scroll Forge so you face the Wither with magic of your own.",
      [place_anywhere("scroll_forge", f"{IS}:scroll_forge", 1, 0, "The priest nods. 'Now you can answer fire with fire.'")],
      rewards={"xp": 800, "items": {f"{IS}:arcane_essence": 8}})

# join: Act I's last quest waits here until the magic branch is done too
quest("w0_two_roads_meet", "Two Roads Meet",
      "Steel and spell. Both roads led to the same name: the Chariot. Tell the elder you're ready.",
      [talk("tell_the_elder", 0, "'Then go. A courier is already looking for you.'")],
      requires={"quests": [qid("i4_the_scroll_forge")]},
      rewards={"xp": 500, "items": {"minecraft:emerald": 10}})

# ======================= IRON'S SPELLS, PART 2 (after the Wither) =======================
courier("letter_dead_king", "w6_the_withered_crown", "i5_the_dead_king", sender="The Priest", lo=2400, hi=4800)
quest("i5_the_dead_king", "The Dead King",
      "With the Wither dead, the dead are restless. Something in the Catacombs is waking.",
      [reach("find_the_catacombs", f"{IS}:catacombs", 0, radius=30000),
       kill("the_dead_king", f"{IS}:dead_king", 1, 1,
            "On his crown, in the Old Tongue: 'Champion of M. Second of seven. Failed.' You are not the first she sent.")],
      book=book("The Dead Are Restless", "The Priest", [
          "The Wither's death rang through every grave. In the Catacombs, a king sat up.",
          "He was a hero once, they say. Someone sent him to kill a god. Go and ask him how that went."], color="purple"),
      rewards={"xp": 3000, "items": {"minecraft:diamond": 4, f"{IS}:arcane_essence": 8}},
      nxt=flow("i6_the_mountain_tower"))

quest("i6_the_mountain_tower", "The Mountain Tower",
      "The Dead King's journal points to a mountain tower where Cryomancers guard a second failed champion's notes.",
      [reach("find_the_tower", f"{IS}:mountain_tower", 0, radius=30000),
       kill("cryomancers", f"{IS}:cryomancer", 1, 1, "The notes are frozen into the ice: 'She sends a new one every hundred years.'")],
      rewards={"xp": 2000, "items": {f"{IS}:frozen_bone": 4, "minecraft:echo_shard": 1}},
      nxt=flow("i7_echo_of_tyros", dims=NETHER))

quest("i7_echo_of_tyros", "Echo of Tyros",
      "In a Nether Citadel burns the Echo of Tyros, First Flamebearer, the god's oldest fire. Put it out.",
      [reach("find_the_citadel", f"{IS}:citadel", 0, radius=20000),
       kill("ancient_knights", f"{IS}:citadel_keeper", 2, 1, "Ancient Knights drop Cinder Essence. You need four, plus the Echo Shard and two Netherite Scrap, to craft a Cinderous Soulcaller."),
       kill("tyros", f"{IS}:fire_boss", 1, 2, "As it gutters out, Tyros whispers: 'He gave me this fire. Tell him it went out.'")],
      rewards={"xp": 4000, "items": {f"{IS}:cinder_essence": 4, "minecraft:netherite_scrap": 2}},
      nxt=flow("i8_the_arcane_anvil", dims=OVER))

quest("i8_the_arcane_anvil", "The Arcane Anvil",
      "Build an Arcane Anvil to bind spells into your weapons before the End.",
      [place_anywhere("arcane_anvil", f"{IS}:arcane_anvil", 1, 0, "You are a sword and a spell now. The priest says that is what a champion is.")],
      rewards={"xp": 1500, "items": {f"{IS}:arcane_essence": 12}})

# join: the Aether's last quest waits here until the second magic branch is done
quest("e0_the_road_to_the_end", "The Road to the End",
      "Two champions before you failed. You have seen their bones. Tell the elder you are going on anyway.",
      [talk("tell_the_elder", 0, "'Then the End is next. Mara's courier is already on the road.'")],
      requires={"quests": [qid("i8_the_arcane_anvil")]},
      rewards={"xp": 800, "items": {"minecraft:emerald": 12}})

# ======================= END REMASTERED: THE EYE HUNT (two parallel paths) =======================
ER = "endrem"
def eye_quest(id, title, summary, eye, where, nxt_id, extra_goals=None, dims=None, book_=None, reward=None):
    goals = list(extra_goals or [])
    goals.append(collect("the_eye", f"{ER}:{eye}", 1, len(goals), f"The {eye.replace('_', ' ').title()} stares back at you. (It goes back in your pack.)",
                         name=f"{eye.replace('_', ' ').title()} (given back)"))
    r = {"xp": 1200, "items": {f"{ER}:{eye}": 1}}
    if reward:
        r["items"].update(reward)
    return quest(id, title, f"{summary} Where: {where}.", goals, book=book_, rewards=r,
                 nxt=flow(nxt_id, dims=dims) if nxt_id else None)

# Path A: Overworld eyes
eye_quest("ea1_the_old_eye", "The Old Eye", "The first eye, said to have belonged to one of the great Sandworms.", "old_eye", "desert pyramids and dungeons", "ea2_the_rogue_eye")
eye_quest("ea2_the_rogue_eye", "The Rogue Eye", "The eye that taught our ancestors redstone.", "rogue_eye", "jungle temples", "ea3_the_cold_eye")
eye_quest("ea3_the_cold_eye", "The Cold Eye", "Left behind by the Iceologers before they vanished.", "cold_eye", "igloo basements", "ea4_the_black_eye")
eye_quest("ea4_the_black_eye", "The Black Eye", "Pirates say it gave sight to a legendary ship.", "black_eye", "buried treasure and shipwreck treasure", "ea5_the_lost_eye")
eye_quest("ea5_the_lost_eye", "The Lost Eye", "Forged by the first blacksmiths of time.", "lost_eye", "abandoned mineshafts and dungeons", "ea6_the_corrupted_eye")
eye_quest("ea6_the_corrupted_eye", "The Corrupted Eye", "The eye of a greedy king. The cult would love it.", "corrupted_eye", "pillager outposts", "ea7_the_guardian_eye")
eye_quest("ea7_the_guardian_eye", "The Guardian Eye", "It has watched warriors drown in the deep.", "guardian_eye", "the Elder Guardian of an ocean monument",
          "e3_the_road_underground", extra_goals=[kill("elder_guardian", "minecraft:elder_guardian", 1, 0)])

# Path B: started by a second letter, Nether and crafted eyes
courier("letter_eyes", "e1_the_librarians_request", "eb1_the_magical_eye", sender="The Librarian", lo=1200, hi=2400)
eye_quest("eb1_the_magical_eye", "The Magical Eye", "Without it, the Evoker has no power.", "magical_eye", "evokers and woodland mansions", "eb2_the_nether_eye",
          book_=book("Twelve Eyes", "The Librarian", [
              "Ordinary eyes of ender will not open this gate any more. The frames want older eyes, sixteen kinds, scattered across the world.",
              "You take the overworld. I've marked the rest on this page: the Nether, the dead, and the crafted ones. Twelve should open it."], color="blue"))
eye_quest("eb2_the_nether_eye", "The Nether Eye", "Lost by a demon in the war between the Aether and the Nether.", "nether_eye", "Nether fortress chests", "eb3_the_cursed_eye",
          extra_goals=[enter_nether("go_below", 0)])
eye_quest("eb3_the_cursed_eye", "The Cursed Eye", "The piglins swore to protect it with their lives.", "cursed_eye", "bastion treasure rooms", "eb4_the_wither_eye")
eye_quest("eb4_the_wither_eye", "The Wither Eye", "It watched the Wither die. Did you keep it?", "wither_eye", "dropped by the Wither (summon another if you lost it)", "eb5_the_witch_eye", dims=OVER)
eye_quest("eb5_the_witch_eye", "The Witch Eye", "Craft it from a Witch Pupil.", "witch_eye", "witches drop pupils, then craft the eye", "eb6_the_undead_eye")
eye_quest("eb6_the_undead_eye", "The Undead Eye", "Craft it from an Undead Soul.", "undead_eye", "skeleton horses drop souls, then craft the eye", None)

# Act IV rejoins once both eye paths are done
QUESTS["e3_the_road_underground"][1].setdefault("requires", {}).setdefault("quests", []).append(qid("eb6_the_undead_eye"))
