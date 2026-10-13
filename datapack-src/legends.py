"""Legends: villagers saying the names that mean something (see Legends.java). {hero} is the most famous Good or Saint
player in the world, {name} is the player they're talking to or about."""

TABLE = {
    # a dark-named player hurts a villager, and there's a hero in the world
    "threat": [
        "You'll pay for what you've done. {hero} will put you in the dirt, you motherfucker.",
        "Go on, {name}. Do it. {hero} will make you eat every one of us.",
        "{hero} hunts things like you. They're coming. They always come.",
        "Touch me again and {hero} will hear about it. And then God help you.",
        "You think you're something? {hero} is something. You're just a cunt with a sword.",
        "Run while you can, {name}. When {hero} finds you, they won't bother with the Watch.",
        "Mark me, {name}. {hero} will bury you under our bell, and we'll ring it on your grave.",
    ],
    # ...and there isn't one
    "threat_nobody": [
        "The Watch will hang you for this, you bastard.",
        "Somebody will stop you, {name}. Somebody always does.",
        "Get away from me! Help! Somebody fucking help us!",
        "You'll swing for this, {name}. I'll hold the rope myself.",
    ],
    # what the hero hears, far away
    "called": [
        "Somewhere, a village is screaming your name. {name} is hurting them.",
        "Your name, carried on the wind, from a long way off. {name} is in a village, and they're calling for you.",
    ],
    # a Wicked player walks into a village
    "whisper": [
        "Don't look. That's {name}. Don't fucking look at them.",
        "That's {name}. Get the kids inside. Now.",
        "Lock the door. {name}'s here.",
        "Is that... oh gods. It's {name}.",
        "Nobody say a word. {name} doesn't like noise.",
    ],
    # a Saint walks in
    "cheer": [
        "Is that {name}? It is! It's {name}!",
        "{name}! Here! Wait till I tell my sister.",
        "Someone get the good cups out. {name}'s here.",
        "That's {name}. My dad says they saved a whole village once. Twice, maybe.",
    ],
}


def build():
    return TABLE
