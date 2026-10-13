"""The rough characters talk like it: the cartel, raiders, hunters, the Gilded Hand's people, the Watch's grunts and the
prisoners swear, threaten and say what they mean. Everyone else (the Faithful, the court, Old Mara, the elders) keeps
their own voice: the swearing hits harder when not everyone does it.

Each change is an exact line, old -> new, applied to the finished quests (a line that's no longer there stops the
build, so nothing is silently missed). BARKS are what the rough ones shout when they first come for you: the mod says
them on your action bar (Mebahel's threats only talk if you can talk to them, and these ones just want you dead)."""
from lib import QUESTS

REPLACE = {
    # ---- the Fixer
    "The Fixer weighs the sample in one hand and you in the other. 'You'll do.'":
        "The Fixer weighs the sample in one hand and you in the other. 'Not bad. Not fucking bad at all. You'll do.'",
    "'Now we're talking. You've got a knack for this, which should worry you.'":
        "'Now we're fucking talking. You've got a knack for this, which should scare the shit out of you.'",
    "The Fixer counts emeralds into your hands until they spill. 'This is what the valley pays.'":
        "The Fixer counts emeralds into your hands until they spill. 'This is what the valley pays. Don't spend it all on whores and horses.'",
    "The Fixer goes quiet, then very rich. 'With this, we don't need Blackwater. We need Blackwater gone.'":
        "The Fixer goes quiet, then very rich. 'Christ. With this we don't need Blackwater. We need Blackwater in the fucking ground.'",
    "'Every week, the money comes. Every week, someone pays for it. Try not to count those.'":
        "'Every week the money comes. Every week some poor bastard pays for it. Do yourself a favour and don't count them.'",
    # ---- the buyers
    "He pays in emeralds and won't meet your eyes. 'Same time next week?'":
        "He pays in emeralds and won't meet your eyes. His hands shake. 'Same time next week? Please?'",
    # ---- Blackwater
    "The last one gasps: 'Don Vasco says... this is a courtesy.'":
        "The last one spits blood at your boots. 'Don Vasco says... this was the polite fucking version.'",
    "The Brand-Carrier falls. Reeve Halden crawls out of the burning hall. 'They didn't kill the men. They took them.'":
        "The Brand-Carrier falls. Reeve Halden crawls out of the burning hall. 'The bastards didn't kill the men. They took them.'",
    "Don Vasco's men load the wagons. 'Pleasure doing business.'":
        "Don Vasco's men load the wagons. One of them winks at you. 'Pleasure doing business, partner. Don't fuck it up.'",
    "The last enforcer runs. Blackwater knows now: you're out, and you're expensive.":
        "The last enforcer runs, screaming over his shoulder: 'You're fucking dead, grower! Dead!' Blackwater knows now: you're out, and you're expensive.",
    # Don Vasco (the trade)
    "The famous grower. Sit. Nobody fights in my house unless I say so.":
        "The famous grower. Sit down. Nobody bleeds in my house unless I fucking say so.",
    "And you burned my lieutenant. We are even. Now we talk business.":
        "And you burned my lieutenant alive. He owed me money, so honestly, we're even. Now we talk business.",
    "Half the valley. Together we stop paying the Chariot its third.":
        "Half the valley. And together we stop paying that gold-plated cunt in the Nether his third.",
    # Don Vasco (the raid)
    "The Watch's dog. Sit. Nobody fights in my house unless I say so.":
        "The Watch's little dog. Sit. Nobody bleeds in my house unless I fucking say so.",
    "By whom? Twelve farmers with spears?":
        "By whom? Twelve shit-shovelling farmers with spears?",
    "...Then I suppose I'll see you in court. Or I'll see you dead. Your choice.":
        "...Ballsy. Then I'll see you in court, or I'll see you in a ditch. Your fucking choice.",
    # the recruiter
    "Grow for us. Sell for us. Get rich. We already pay the cult; you'd just be paying yourself.":
        "Grow for us. Sell for us. Get stupid rich. We already pay those gold-eyed bastards their cut; you'd just be paying yourself.",
    "Because you're not a saint. Saints don't get letters from us. You did.":
        "Because you're not a fucking saint. Saints don't get letters from us. You did.",
    "Then we're enemies, and you'd better be very good at it.":
        "Then we're enemies, and you'd better be very fucking good at it.",
    # ---- the Gilded Hand
    "To pay you. Gold, steady. You only have to keep doing what you're good at.":
        "To pay you. Gold, steady. You only have to keep doing the shit you're already good at.",
    "Then you're a man who kills prisoners, with no friends. Ashford won't forgive you. We would.":
        "Then you're a prisoner-killing bastard with no friends. Ashford won't forgive you. We would. We'd buy you a drink.",
    "Silas slides off the throne. 'It was only ever about the gold,' he says. It's his last joke.":
        "Silas slides off the throne. 'It was only ever about the gold, you stupid fucker,' he says, smiling. It's his last joke.",
    "Blood. A trophy. A diary. Anything that says you're like the rest of us.":
        "Blood. A trophy. A diary. Any shit at all that says you're like the rest of us.",
    "Nothing. That's what scares him. That's what scares me.":
        "Fuck all. That's what scares him. That's what scares me.",
    # ---- hunters, deserters, avengers
    "The last hunter laughs. 'There'll be more. There's always more for someone like you.'":
        "The last hunter laughs through his broken teeth. 'There'll be more. There's always more for a piece of shit like you.'",
    "Gold. Same as everyone. Forty for a village. Two for a man.":
        "Gold. Same as every other bastard. Forty for a village. Two for a man.",
    "The Faithful. I only kept the count. I never held a torch, I swear it.":
        "The Faithful. I only kept the fucking count. I never held a torch, I swear it on my mother.",
    "Dying, he says: 'I hope it was worth it.' You aren't sure it was.":
        "Dying, he says: 'I hope it was worth it, you bastard.' You aren't sure it was.",
}

# what the rough ones shout when they first come for you (by the name the quest gives them)
BARKS = {
    "Road Captain": "Hand over the cart and nobody gets their fucking throat cut.",
    "Moon Axeman": "Big moon tonight. Good night for burning a shithole like this.",
    "Ashford Skirmisher": "Burn it! Burn the fucking lot!",
    "Wreck Looter": "Finders keepers, you nosy bastard.",
    "Bounty Hunter": "Your head's worth more than you are. Hold still.",
    "Gold Collector": "The Hand's owed. Pay up, or we take it out of your hide.",
    "Gorran's Brother": "You left our brother dead with the keys in his hand. Now it's your fucking turn.",
    "Aldo the Avenger": "Remember me? No? You killed my whole fucking family. I remember you.",
    "Blackwater Enforcer": "Don Vasco sends his regards. And this axe.",
    "Blackwater Crew": "Burn the fields! And somebody gut the fucking grower!",
    "Blackwater Collector": "Nobody retires from Blackwater, grower. Nobody.",
    "Blackwater Runner": "Shit, it's the Watch's pet. Kill it!",
    "Blackwater Remnant": "You killed the Don. Now we're going to fucking kill you.",
    "Silas the Pretender": "Ah. The famous one. Come in, sit, have a drink before I bleed you.",
}


def _walk(x, seen):
    if isinstance(x, dict):
        for k, v in x.items():
            if isinstance(v, str) and v in REPLACE:
                seen.add(v)
                x[k] = REPLACE[v]
            else:
                _walk(v, seen)
    elif isinstance(x, list):
        for i, v in enumerate(x):
            if isinstance(v, str) and v in REPLACE:
                seen.add(v)
                x[i] = REPLACE[v]
            else:
                _walk(v, seen)


def apply():
    seen = set()
    named = set()
    for _, q in QUESTS.values():
        _walk(q, seen)
        for t in q.get("threats", []):
            if t.get("name") in BARKS:
                named.add(t["name"])
    missing = set(REPLACE) - seen
    assert not missing, f"mature.py: lines no longer in the story: {sorted(missing)[:3]}"
    assert named == set(BARKS), f"mature.py: threats not found: {sorted(set(BARKS) - named)}"


def barks():
    return {"barks": BARKS}
