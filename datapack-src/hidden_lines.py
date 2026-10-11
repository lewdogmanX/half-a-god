"""Five hidden questlines. None of them is on the map or in the journal until you find it; the mod starts the first
quest of each when you do the odd thing that opens it:

  The Old Tongue      pray at three of the cult's wayside shrines
  The Last Pilgrim    feed three hungry pilgrims on the road (after you've met Brother Vael)
  What the God Saw    have five dreams, then sleep a full night in the Aether
  Sky and Fire        carry something from the Aether into the Nether
  Clean Hands         reach Act IV without killing a villager or any story character

Each pays out in story: Codex pages (category "Secrets"), and a fate of its own at the end. No items with powers."""
from lib import *
import npc_lines

OVERWORLD = ["minecraft:overworld"]

# key, name, how it's found (shown once found), the trait the mod sets when found, the trait set when it's finished
LINES = [
    ("tongue", "The Old Tongue", "You prayed at three of the cult's shrines, and the third one answered.", "sec_tongue1"),
    ("pilgrim", "The Last Pilgrim", "You fed three pilgrims on the road. The third one knew your name.", "sec_pilgrim1"),
    ("godsaw", "What the God Saw", "You dreamt five dreams, then slept a night in the Aether.", "sec_godsaw1"),
    ("skyfire", "Sky and Fire", "You carried something from the Aether into the Nether, and the Nether went quiet.", "sec_skyfire1"),
    ("clean", "Clean Hands", "You reached Act IV without killing a single villager or story character.", "sec_clean1"),
]

# traits only the mod sets (counted as "produced" for the fates check)
MOD_TRAITS = {f"hag_secret_{k}_found" for k, *_ in LINES} | {"hag_secret_pilgrim_vael_alive", "hag_secret_pilgrim_vael_dead"}


# ======================= THE OLD TONGUE =======================
quest("sec_tongue1", "A Voice Under the Gold",
      "The third shrine answered you. Not in any language you know: in the Old Tongue, the cult's own. One word, over and over. "
      "Write it down before it goes.",
      [collect("write_it_down", "minecraft:writable_book", 1, 0, "You write it as it sounded: a name with too many angles in it."),
       wait("sleep_on_it", 1, "next_day", "In the morning the word is still on the page. The ink has moved a little."),
       talk("read_it_aloud", 2, "The elder goes white. 'Don't say that here. Don't say it anywhere. That isn't his name. That's what he was called before.'",
            name="Village Elder")],
      rewards={"xp": 400, "items": {"minecraft:writable_book": 1, "minecraft:candle": 4}},
      nxt=flow("sec_tongue2", delay=1200, dims=OVERWORLD))
npc_lines.GREET[("sec_tongue1", "Village Elder")] = "You've got a look on you. Like you've heard something you shouldn't."
QUESTS["sec_tongue1"][1]["goals"][2]["_far"] = False
QUESTS["sec_tongue1"][1]["goals"][2]["_talk"] = [
    ["What does the word mean?", "I don't know and I don't want to. My grandmother knew. She never said it either."],
    ["Who was he, before?", "Before the gold? Before the eyes on the doors? Somebody the sky was fond of. That's all the old songs say."]]

quest("sec_tongue2", "The Name Before",
      "The word is a name: the Chariot's name, from before the cult gave him another. A Believer's House would have it written "
      "somewhere, under the prayers. Find it, and find out why nobody says it.",
      [reach("under_the_prayers", "netherman:believer_house", 0,
             "Scratched into the floor under the Preacher's mat: the same angles. And a second line beneath them."),
       wait("translate_it", 1, "next_day", "It takes you all night. 'He who speaks the name gives it back.'"),
       talk("tell_the_elder", 2, "'So that's why they pray in angles,' the elder says. 'Not to keep from offending him. To keep from feeding him.'",
            name="Village Elder")],
      rewards={"xp": 900, "items": {"minecraft:emerald": 12, "minecraft:experience_bottle": 8}},
      traits={"complete": ["hag_secret_tongue_done"]})
npc_lines.GREET[("sec_tongue2", "Village Elder")] = "You found it, didn't you. Whatever it was. Sit down. Don't say it."
QUESTS["sec_tongue2"][1]["goals"][2]["_far"] = False
QUESTS["sec_tongue2"][1]["goals"][2]["_talk"] = [
    ["Every time it's said, he gets a piece back.", "Then we'll not say it. Not here, not ever. Burn the book if you have to."],
    ["The cult never says it either.", "Of course they don't. You don't feed a dog you're trying to keep hungry."]]

# ======================= THE LAST PILGRIM =======================
quest("sec_pilgrim1", "The Last Pilgrim",
      "The pilgrim you fed knew your name. He is Oren, and long ago he taught a boy called Vael to count, before the cult taught "
      "Vael what to count. He asks one thing: light a candle where Vael kept his ledgers.",
      [collect("a_candle", "minecraft:candle", 1, 0, "Oren closes your fingers around it. 'Not for him. For the boy he was.'"),
       enter_nether("go_below", 1),
       reach("where_he_counted", "minecraft:fortress", 2,
             "You light Oren's candle on a ledge above the lava, where a steward might have stood counting skulls."),
       leave_nether("come_home", 3)],
      rewards={"xp": 800, "items": {"minecraft:bread": 16, "minecraft:emerald": 8}},
      nxt=flow("sec_pilgrim2", delay=1200, dims=OVERWORLD))

quest("sec_pilgrim2", "What Oren Left",
      "Oren was gone by morning. He left his bowl at your door and a note: 'Thank you. Whatever he became, he was a good boy once. "
      "Fill this for whoever walks the road next.'",
      [collect("fill_the_bowl", "minecraft:bread", 16, 0),
       talk("share_it", 0, "The elder takes the bread for the road-walkers. 'There's always someone,' she says. 'Thank you for remembering.'",
            name="Village Elder")],
      rewards={"xp": 900, "items": {"minecraft:bowl": 1, "minecraft:golden_carrot": 12}},
      traits={"complete": ["hag_secret_pilgrim_done"]})
npc_lines.GREET[("sec_pilgrim2", "Village Elder")] = "Is that a pilgrim's bowl? We get them on the road now and again. Hungry, always."
QUESTS["sec_pilgrim2"][1]["goals"][1]["_far"] = False
QUESTS["sec_pilgrim2"][1]["goals"][1]["_talk"] = [
    ["An old man named Oren left it.", "Oren? He's walked this road since I was a girl. He's looking for someone. I never asked who."],
    ["He taught a cult steward to count.", "Then I hope the steward remembers something else he was taught. Teachers are owed that much."]]

# ======================= WHAT THE GOD SAW =======================
quest("sec_godsaw1", "What the God Saw",
      "You dreamt the fall in full, and it doesn't match what anyone teaches. The hymn says the Sun Spirit threw him out of the sky. "
      "In your dream, he jumped. The Sun Spirit's hall might still show it.",
      [reach("the_spirits_hall", "aether:gold_dungeon", 0,
             "Under the dungeon's floor tiles, a scorch mark older than the dungeon: two shapes, one falling, one diving after it."),
       wait("remember_it", 1, "next_day", "You don't dream that night. It feels like being allowed to rest.")],
      rewards={"xp": 900, "items": {"minecraft:golden_apple": 2}},
      nxt=flow("sec_godsaw2", delay=1200, dims=OVERWORLD))

quest("sec_godsaw2", "Why a God Would Jump",
      "Why would anyone leap out of the sky? Tell someone what you saw, and see if it sounds as true out loud.",
      [talk("say_it_out_loud", 0, "'Why would he jump?' 'To catch someone who was falling.' The elder is quiet for a long time.",
            name="Village Elder")],
      rewards={"xp": 1200, "items": {"minecraft:emerald": 16}},
      traits={"complete": ["hag_secret_godsaw_done"]})
npc_lines.GREET[("sec_godsaw2", "Village Elder")] = "You've been dreaming again. It's all over your face."
QUESTS["sec_godsaw2"][1]["goals"][0]["_far"] = False
QUESTS["sec_godsaw2"][1]["goals"][0]["_talk"] = [
    ["He wasn't thrown. She was.", "Maraal? Then he jumped after her. Gods aren't meant to do that. Maybe that's what broke him."],
    ["The fall split him in two.", "The half that caught her, and the half that hated the sky for letting her go. Aye. That sounds like a god."]]

# ======================= SKY AND FIRE =======================
quest("sec_skyfire1", "Sky and Fire",
      "You carried something from the Aether into the Nether, and the Nether went quiet around you. Something down here "
      "recognises it. The silence leads toward a bastion.",
      [reach("follow_the_silence", "minecraft:bastion_remnant", 0,
             "Deep in the bastion the piglins have painted a sun on the wall. Half of it is gold. Half of it is scratched out."),
       collect("the_suns_blood", "minecraft:gilded_blackstone", 4, 1,
               "Gilded blackstone: gold grown into stone. The piglins say it is where the sun bled when it fell."),
       leave_nether("carry_it_up", 2)],
      rewards={"xp": 900, "items": {"minecraft:gold_ingot": 8}},
      nxt=flow("sec_skyfire2", delay=1200, dims=OVERWORLD))

quest("sec_skyfire2", "The War of Halves",
      "The painting told a war: the sky against the fire, a god against himself. Show the gilded stone to someone old enough to know the stories.",
      [talk("show_the_stone", 0, "'Two halves of the same god fought once,' the elder says, turning the stone over. "
                                 "'The sky half won the sky. The fire half got everything else.'", name="Village Elder")],
      rewards={"xp": 1200, "items": {"minecraft:emerald": 16, "minecraft:gilded_blackstone": 4}},
      traits={"complete": ["hag_secret_skyfire_done"]})
npc_lines.GREET[("sec_skyfire2", "Village Elder")] = "Is that gilded blackstone? From a bastion? My grandfather had a piece. He called it the sun's scar."
QUESTS["sec_skyfire2"][1]["goals"][0]["_far"] = False
QUESTS["sec_skyfire2"][1]["goals"][0]["_talk"] = [
    ["Which half won?", "Neither. That's the trouble with a war against yourself. Both sides lose, and both sides have to live with it."],
    ["Is that why he's only half a god?", "Half in the sky, half in the fire, and the cult trying to glue him back together with gold. Yes. I think so."]]

# ======================= CLEAN HANDS =======================
quest("sec_clean1", "A Letter from Silas",
      "Silas of the Gilded Hand has written to you. He finds you suspicious.",
      [wait("sleep_on_it", 0, "next_day", "You sleep badly. Something in the house has been moved. You're almost sure of it.")],
      book=book("Clean Hands", "Silas", [
          "Friend,\n\nThree acts, and not one drop of blood on you. Not a villager, not a villain, not a peddler with a smile. "
          "Do you know how rare that is? I have met saints. Saints have tempers.",
          "I don't trust clean hands. Clean hands are hiding something.\n\nSo I've sent someone to find out what. Don't hurt him. "
          "Or do. Either way, I learn something.\n\n- S."], color="red"),
      rewards={"xp": 200},
      nxt=flow("sec_clean2", delay=600, dims=OVERWORLD))

quest("sec_clean2", "Clean Hands",
      "Silas's man has been going through your things. He's somewhere near, in the Gilded Hand's colours. Catch him. "
      "What you do with him is up to you, and Silas is watching.",
      [kill("the_sneak_thief", "minecraft:wandering_trader", 1, 0, "Whatever you chose, Silas will hear of it by morning.")],
      threats=[threat("minecraft:wandering_trader", "The Hand's Sneak-Thief", hp=40, spare=True, smin=10, smax=20,
                      talk=[["What were you looking for?", "Blood. A trophy. A diary. Anything that says you're like the rest of us."],
                            ["Did you find anything?", "Nothing. That's what scares him. That's what scares me."],
                            ["Go home.", "Spare me and I'll tell him you're exactly what you look like. He won't believe it, but I'll tell him."]],
                      target="Silas's sneak-thief is somewhere near your home.")],
      choice={"attack": {"label": "End it here", "traits": ["hag_secret_clean_bloodied"]},
              "spare": {"label": "Let him go back to Silas", "line": "He backs away, bowing. 'Clean. Even now. He'll hate that.'",
                        "traits": ["hag_secret_clean_kept"]}},
      rewards={"xp": 1200, "items": {"minecraft:emerald": 16}},
      traits={"complete": ["hag_secret_clean_done"]})


# ======================= CODEX AND FATES =======================
CODEX = {
    "tongue": "The cult calls him Azazel. The Aether's hymn calls him Azriel. Neither is his name. His name was a word in the Old Tongue, "
              "something like 'the one who carries the sun across', and every time a mouth says it, a little of him comes back.\n"
              "That is why the Faithful pray in angles and never in names. They are not afraid of offending him. They are afraid of feeding him.\n"
              "You know the word now. You won't write it here.",
    "pilgrim": "Oren has walked the roads for fifty years. He taught village children their letters and their numbers, and one of them was a "
               "quick, quiet boy called Vael, who loved to count.\n"
               "The cult found Vael at fifteen and taught him what to count: gold, skulls, people. Oren has been looking for him ever since, "
               "not to bring him home, only to tell him the count is done.",
    "godsaw": "He wasn't thrown out of the sky. She was. The Sun Spirit cast Maraal down for loving the one who carried the sun, and "
              "he let go of the reins and jumped after her.\n"
              "He caught her. The fall split him in two when they landed: the half that caught her, and the half that hated the sky for "
              "letting her go. The cult worships the second half. Maraal has spent every year since trying to find the first.",
    "skyfire": "The piglins remember a war nobody else does: the sky against the fire, fought by two halves of the same god. The gold half "
               "kept the sky. The iron half kept everything else.\n"
               "Gilded blackstone is where the sun bled when the halves tore at each other. The cult mines it and calls it holy. "
               "The piglins mine it and call it a scar.",
    "clean": "Silas doesn't believe in clean hands. In his trade, everybody's are dirty: the trick is only knowing whose.\n"
             "So he sent a thief to find your secret, and the thief found nothing, and that frightened Silas more than any blade. "
             "A person with nothing to hide is a person who cannot be bought.",
}

FATES = [
    ("The Voice in the Old Tongue",
     ("hag_secret_tongue_done", "other", "You never said the name again. Some nights you wake with it in your mouth, and you swallow it, "
                                         "and the dark is a little hungrier for a while. It never gets what it wants.")),
    ("Oren the Pilgrim",
     ("hag_secret_pilgrim_vael_alive", "mercy", "Oren found Vael in the end, on a road in the east. They didn't talk about the cult. They "
                                                 "counted stars together until the old man fell asleep."),
     ("hag_secret_pilgrim_vael_dead", "other", "Oren never found Vael. He still lights a candle every winter, for the boy who loved to count, "
                                                "and he still walks the road, feeding whoever he meets.")),
    ("What the God Saw",
     ("hag_secret_godsaw_done", "other", "You never told anyone else what you dreamt. You didn't have to. Maraal looked at you once, the way "
                                         "you look at someone who's read your letters, and smiled.")),
    ("The Painted Sun",
     ("hag_secret_skyfire_done", "other", "The elder told the story of the war of halves to every child in the village. Nobody believed it except the piglins, who "
                                          "added a third figure to the painting: you, standing between them.")),
    ("Silas's Sneak-Thief",
     ("hag_secret_clean_kept", "mercy", "The thief went back to Silas and told him the truth. Silas fired him for it. He keeps bees now, and "
                                        "says the best thing that ever happened to him was failing to find your secret."),
     ("hag_secret_clean_bloodied", "blade", "Your hands were clean for three acts. Silas framed the letter you wrote him in blood, and hung it "
                                            "over his desk, and laughed every time he saw it.")),
]


def codex_entries(entry):
    for key, name, how, first in LINES:
        entry("Secrets", name, first, CODEX[key])


def mod_table():
    return {"lines": [{"key": k, "name": n, "how": h, "first": qid(f), "found": f"hag_secret_{k}_found", "done": f"hag_secret_{k}_done"}
                      for k, n, h, f in LINES]}
