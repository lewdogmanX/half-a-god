"""The Cult of Azazel mod (netherman), made part of the story.

- Azazel can't be called before the story sends you to him: the summoning pattern, the altar, the Altar Compass-Key and
  the Cult Scroll do nothing until The Instruction (after Do Not Provoke Gehinnom), and he can't be brought into the
  world any other way (spawn eggs only work for players in creative).
- The Cult Scroll (his mod's "cult way": three-day quotas of villagers killed or converted, sacrifices, altars, gold) is
  only for the dark side: after you've knelt to the True Azazel, or with a Wicked name. Every bit of the cult's work
  costs reputation, and the Watch hears about it at your trial. So does every villager you bring to "the faith".
- The Totem of Rebirth turns the killing blow aside where you stand (no carrying you off to your bed).
- His English text is rewritten in the story's voice (a built-in resource pack, so his mod is untouched): the
  advancements, the True Azazel's speech, the tooltips, and the guidebook, which becomes the Book of the Chariot, a
  pilgrim's primer that hints instead of spelling out how to summon him.
- The men you free from the Cursed Quarries remember it: one of them writes, and one speaks for you if you're ever tried.
- Sons of Sins' Sinful Church is three times rarer (it's late in the story)."""
from lib import *
import npc_lines

NM = "netherman"
UNLOCK = ["hag_m5_do_not_provoke_gehinnom_done", "hag_good_ending", "hag_hand_of_azazel"]
KNELT = ["hag_b1_bow_at_my_feet_done", "hag_hand_of_azazel"]

TABLE = {
    "unlock": UNLOCK,
    "knelt": KNELT,
    "locked": {
        "summon": "The gold won't take. Something in the stone is waiting for someone else, or for later.",
        "altar": "The altar is cold under your hand. It isn't time.",
        "compass": "The needle spins and spins. Whatever it points at doesn't want to be found. Not yet.",
        "scroll": "The scroll's ink won't stay still long enough to read. It isn't for you. Not yet.",
        "azazel": "Something tried to come through, and couldn't. Not yet. He isn't ready for you, and you aren't ready for him.",
    },
    "scroll_good": "The ink crawls away from your fingers. The Chariot doesn't write to people like you.",
    "work": "The cult's work. It's in your hands now, and it doesn't wash off.",
    "conversion": "Its eyes go gold, and it doesn't know your name any more. Somebody will notice.",
    "totem": "The totem cracks. The blow that should have killed you goes somewhere else.",
    "rep_work": -2,
    "rep_conversion": -5,
}

# ---------------------------------------------------------------- his English, in the story's voice
LANG = {
    # advancements
    "advancement.netherman.enter_mansion.title": "The Sacred City",
    "advancement.netherman.enter_mansion.description": "Walk into the house that gold built",
    "advancement.netherman.enter_maze.title": "Mind the Walls",
    "advancement.netherman.enter_maze.description": "Something in here is counting your steps",
    "advancement.netherman.tame_ghastly.title": "The Crimson Bee",
    "advancement.netherman.tame_ghastly.description": "It hums when you're near. It knows the way",
    "advancement.netherman.azazel.title": "The Chariot",
    "advancement.netherman.azazel.description": "Stand before the one who carried the sun",
    "advancement.netherman.kill_azazel.title": "The Throne Is Empty",
    "advancement.netherman.kill_azazel.description": "You killed him. Half of you killed the other half",
    # the Chariot
    "entity.netherman.azazel.mistake": "THAT WAS A MISTAKE",
    "entity.netherman.azazel.surrender": "Lay down your weapon, half-thing, and I may let you live.",
    "entity.netherman.azazel.mercy": "Good. You always did choose right, in the end.",
    "entity.netherman.azazel.death": "Forgive me, my lord... I could not hold your sky...",
    # the True Azazel (his last line, in the Old Tongue, is left as it is: "Maraal, it's my fault...")
    "boss.netherman.azazel_human.line1": "§6You. The half that caught her.",
    "boss.netherman.azazel_human.line2": "§6The half that fell soft, and forgot.",
    "boss.netherman.azazel_human.line3": "§6You came up through my quarries, through my gold.",
    "boss.netherman.azazel_human.line4": "§6I should take your eyes and keep your tongue.",
    "boss.netherman.azazel_human.line5": "§6But no. You came all this way.",
    "boss.netherman.azazel_human.line6": "§6So I will give you what I was never given:",
    "boss.netherman.azazel_human.line7": "§6a choice. Kneel, and be whole with me,",
    "boss.netherman.azazel_human.line8": "§6and we will take back the sky together.",
    "boss.netherman.azazel_human.line9": "§6That is my mercy. There is no other.",
    "boss.netherman.azazel_human.line10": "§6Approach, half of me, and bow at my feet.",
    "message.netherman.door_locked": "§4The door wants an answer.",
    # names and tooltips
    "item.netherman.azazel_guide_book": "§4The Book of the Chariot",
    "item.netherman.note": "The Instruction",
    "item.netherman.quota": "Cult Scroll",
    "item.netherman.altar_compass_key": "Altar Compass-Key",
    "tooltip.netherman.compass": "Points the faithful home",
    "tooltip.netherman.guide": "Given to every pilgrim at the gate",
    "tooltip.netherman.chance_totem.line1": "Turns a killing blow aside, where you stand",
    "tooltip.netherman.essence": "[RMB] on a creature to bring it to the faith",
    "tooltip.netherman.essence.line1": "Nine pieces of faith make one",
    # the cult's quotas
    "gui.netherman.quota.altar": "Raise an altar to Azazel",
    "gui.netherman.quota.destroy": "Burn a village, or kill %s/%s villagers",
    "gui.netherman.quota.sacrifice": "Offer up lives %s/%s",
    "gui.netherman.quota.mine": "Dig gold for him %s/%s",
    "gui.netherman.quota.pillagers": "Bring pillagers to the faith %s/%s",
    "gui.netherman.quota.villagers": "Bring villagers to the faith %s/%s",
}

# The Book of the Chariot (his guidebook's pages, line for line, same number of lines per page)
BOOK = {
    1: ["§4§lThe Book of the Chariot",
        "Given to every pilgrim at the gate.",
        "Read it on your knees.",
        "Copied by a Preacher of the Sacred City.",
        "§c§lWHAT HE ASKS:",
        "Gold. Obedience. Patience.",
        "He has waited. So can you."],
    2: ["§c§lHIS HOUSE: §4§lthe Sacred City",
        "Three courts, one above the other:",
        "§6§lTHE QUARRIES",
        "§c§lTHE SACRED CITY",
        "§4§lTHE LAIR",
        "§6§lTHE QUARRIES:§f§l where the faithless",
        "dig gold until they believe. The §4Manipulators",
        "keep them at it. Kill a Manipulator and its",
        "workers go free. You will be remembered for it."],
    3: ["§c§lTHE SACRED CITY:§f§l where the faithful live.",
        "Walk softly. Two here will serve you:",
        "",
        "§cThe Doctor§f§l - mends the faithful, for a price",
        "§cThe Blacksmith§f§l - mends what you carry,",
        "and leaves its blessings where they are.",
        "§4§lTHE LAIR:§f§l the end of the road.",
        "What waits there is not for pilgrims.",
        "Not until you are sent."],
    4: ["§c§lHIS SHRINES BEYOND THE CITY",
        "§2§lIn the world above:",
        "Pipes in the hills. The ground cracks beneath them.",
        "A compass, a pillar, a head. Read what's inside.",
        "A maze. Be careful. Look up.",
        "And past all of it: God.",
        "Two roads to him. The faithful road takes three days."],
    5: ["§c§lHIS CREATURES: §4the Ghastly",
        "builds its hive in crimson stems,",
        "makes crimson honey,",
        "can be gentled,",
        "and always knows the way home.",
        "§c§lHis servants:",
        "§6Welcomer§f§l - bows at the gate.",
        "§6Trader§f§l - sells to the faithful.",
        "§6Gilded Golem§f§l - drinks gold, and blood.",
        "§4Gehinnom§f§l - do not provoke.",
        "§4Manipulator§f§l - drives the workers."],
    6: ["§c§lHIS GIFTS",
        "§cTotem of Rebirth:",
        "Turns a killing blow aside, once, where you stand.",
        "§4Staff of Manipulation:",
        "Bends the will, pulls the body,",
        "and calls the dead to serve.",
        "§e§lAZAZEL IS MY LIGHT.",
        "§4Be praised."],
}
for page, lines in BOOK.items():
    for i, line in enumerate(lines):
        LANG[f"book.netherman.page{page}.line{i + 1}"] = line

# ---------------------------------------------------------------- Sons of Sins: three times rarer
# (spacing x sqrt(3): a third as many churches in the same area; same salt, so it's the same kind of spread)
EXTRA["sons_of_sins/worldgen/structure_set/sinful_church.json"] = {
    "structures": [{"structure": "sons_of_sins:sinful_church", "weight": 1}],
    "placement": {"type": "minecraft:random_spread", "spacing": 64, "separation": 24, "salt": 611326438}}

# ---------------------------------------------------------------- the men from the quarries remember
courier("letter_from_the_quarries", "m3_hold_the_quarry_gate", "y3_free_men_remember", sender="A freed man of Ashford",
        lo=6000, hi=12000)
quest("y3_free_men_remember", "Free Men Remember",
      "A letter from one of the men you freed from the Cursed Quarries. He's home in Ashford, and he'd like to see you.",
      [talk("see_him", 0, "He shakes your hand too long, the way men do when they've been in chains. 'If anyone ever "
                          "says a word against you,' he says, 'they'll have to say it over me.'", name="Freed Ashford Man")],
      book=book("From the Quarries", "Corin Vale, of Ashford", [
          "You won't remember me. I was the one with the broken hand, fourth in the line, the morning you came down "
          "into the quarries and killed the things that drove us.",
          "Three years I dug gold for a god I'd never seen. I'd stopped being able to picture my wife's face. Now "
          "I see it every morning.",
          "I'm home. Ashford's half-built and the roof leaks and I've never been happier. Come and see. I owe you "
          "a drink and a great deal more than a drink.\n\n- Corin Vale"], color="yellow"),
      rewards={"xp": 800, "items": {"minecraft:cooked_beef": 8, "minecraft:emerald": 8}},
      traits={"complete": ["hag_quarry_men_remember"]})
npc_lines.GREET[("y3_free_men_remember", "Freed Ashford Man")] = "You came! You actually came. Sit. Sit down. I'll find the good cups."


def mod_table():
    return TABLE


def lang():
    return LANG
