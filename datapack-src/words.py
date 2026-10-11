"""Smaller things: your reputation travelling ahead of you, confessions, a price on a rival's head, and testifying at
a rival's trial."""

# when you walk into a village for the first time (on the action bar), by reputation tier
AHEAD = {
    "saint": "Word of you got here first. People nod as you pass, and somebody's already putting the kettle on.",
    "good": "They've heard of you here. Mostly good things.",
    "waver": "They've heard your name here. Nobody seems sure what to make of it.",
    "fallen": "They've heard of you here. Conversations stop when you walk past.",
    "wicked": "Doors close as you walk in. They've heard of you, even here.",
}
# how much the gossip that reaches a village moves its prices (vanilla villager gossip: minor positive/negative)
AHEAD_GOSSIP = {"saint": 40, "good": 20, "waver": 0, "fallen": -25, "wicked": -50}

CONFESSION = {
    "title": "Confession",
    "open": "The cleric draws a curtain across the window and sits down with you. 'Say what you came to say. It stays between you, me and the gods.'",
    "none": "'You've nothing on you the Watch would hang you for. Go home. Sleep.'",
    "today": "'Once a day. Even the gods need a rest from some of us.'",
    "after": ["The cleric listens without looking at you, and is quiet for a long time after.",
              "'It doesn't wash off. But it weighs less, carried in the open. If the Watch ever asks, I'll say you came to me first.'"],
    "villagers": "I killed people. Villagers. ({n} of them.)",
    "stores": "I broke into people's stores and took what they'd saved.",
    "hand": "I collected for the Gilded Hand.",
    "shrines": "I prayed at the cult's shrines, and something answered.",
    "leave": "Leave",
}

BOUNTY = {
    "cost": 20,
    "need_rank": "The Gilded Hand doesn't take contracts from people it doesn't know. (You need a Hand rank.)",
    "need_money": "The Hand's price is 20 emeralds, in your hands. You don't have them.",
    "protected": "The Hand's agent shakes his head. '{name} is one of ours, higher than you. Try that again and the price will be on you.'",
    "cooldown": "There's already a price on {name}'s head. The Hand doesn't take the same job twice in three days.",
    "placed": "Twenty emeralds change hands. 'Consider it done,' the Hand's agent says. 'Night falls, they come.'",
    "warned": "Someone has paid the Gilded Hand for your head. They'll come after dark.",
    "title": "A Price on {name}'s Head",
    "text": "The Gilded Hand will send its hunters after {name} the next night they're out under the sky. 20 emeralds, from your inventory. You'll need a Hand rank, and they'll refuse if {name} is higher in the Hand than you.",
    "yes": "Pay the Hand (20 emeralds)",
    "no": "Not today",
}

TESTIFY = {
    "title": "The Watch Has Taken {name}",
    "text": "A Watch runner finds you. '{name} is on trial tonight. You were there on day {day}, when they {what}. The court would hear you, if you'll speak.'",
    "what": {"villager": "killed a villager", "stores": "broke into a village's stores"},
    "against": "Testify against them",
    "for": "Speak for them",
    "out": "Stay out of it",
    "late": "'Too late. The court's already given its verdict.'",
    "thanks_against": "'The court thanks you.' The runner writes your name down.",
    "thanks_for": "'The court will hear it.' The runner doesn't look convinced.",
}


def build():
    return {"ahead": AHEAD, "ahead_gossip": AHEAD_GOSSIP, "confession": CONFESSION, "bounty": BOUNTY, "testify": TESTIFY}
