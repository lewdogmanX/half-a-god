"""The Watch's prison: the people in it, what they say, and the way out.

A prison day (a whole Minecraft day, twenty real minutes) goes: the cell in the morning, a bell, hard labour in the
quarry, a bell, the cafeteria, a bell, lights out back in the cell. In the cafeteria you can talk to the other
prisoners: rumours, lore, how nobody knows where the prison is (it's a long way down and a long way from anywhere), and
now and then a hint towards one of the hidden questlines you haven't found.

Old Maddock knows a way out. It takes a tool from the quarry, the gaoler's rounds, and nights of digging, and getting
caught at any step costs two more days."""

PRISONERS = [
    {"id": "maddock", "role": "prisoner0", "name": "Old Maddock",
     "about": [
         "Forty years in and out of Watch cells. Poaching, mostly. Then a bit of smuggling. Then a bit of everything.",
         "I was here when the gaoler was a boy carrying the slop buckets. He's kinder now. Not much.",
         "Don't look at me like that. I'm not a monster. I'm just a man who never learned to want less."]},
    {"id": "wren", "role": "prisoner1", "name": "Wren",
     "about": [
         "I carried letters for the Gilded Hand. Didn't read them. That's what I told the court. It was even true, the first year.",
         "I hear things. That's my whole trade. Ears. Ask me what's going on out there, I'll tell you what came in with the last cart.",
         "Silas never came for me. He comes for the useful ones. I'm learning to be useful."]},
    {"id": "tam", "role": "prisoner2", "name": "Brother Tam",
     "about": [
         "I wore the gold eye for eleven years. I prayed in angles like they taught us. Then one night I asked the wrong question out loud.",
         "The Faithful handed me to the Watch themselves. Called me a thief. I'd never stolen anything but a doubt.",
         "I still pray. Just not to him."]},
    {"id": "kesh", "role": "prisoner3", "name": "Kesh",
     "about": [
         "Grave-robbing, they said. I call it archaeology without permission.",
         "I've walked more of this world than the Watch has maps for. Ruins, shrines, holes nobody should climb into.",
         "The things I've seen on the roads... you'd not believe half. Ask me sometime. I'll tell you the half you would."]},
]

# what comes in with the carts (anyone may say these)
RUMOURS = [
    "The Watch is short of men. They're taking anyone who can hold a spear and doesn't ask why.",
    "Word is the Gilded Hand has a ledger with every debt in the valley in it. Every single one. Even the Watch's.",
    "Somebody saw gold-eyed pilgrims walking north in a line, singing. Nobody walks north in winter for good reasons.",
    "Ashford's rebuilding. Folk say the walls go up faster when the one who saved them is watching.",
    "There's a piglin down in the Nether who'll sell you anything. Anything. Skulls. Names. Your own future, if you've the gold.",
    "Blackwater's lost three boats this month. Don Vasco's men say it was weather. The weather had crossbows.",
    "A shepherd swears his sheep knelt when the bell didn't ring. Knelt. All of them. Facing the same way.",
    "The Faithful pay double for gold. Always have. Ever wondered what they need that much of it for?",
    "They say Reeve Halden doesn't sleep. Walks the walls all night with a lantern. Counting.",
    "There's talk of a woman in the Aether who doesn't age. Prays to the sun, and the sun listens.",
    "The cult's Wardens are falling, one by one. Somebody out there is doing it. Some say it's a hero. Some say it's worse.",
    "Merchants won't take the east road any more. Not since the raiders. Not since what happened to the raiders.",
]

# where are we? Nobody knows.
WHERE = {
    "maddock": "Hood on, a cart, three days of bumping. Forty years and I've never once seen the road in. Could be under a mountain. Could be under your bed.",
    "wren": "I counted the turns. Left, left, right, a long straight, then down. Down for an hour. Who builds a road that goes down for an hour?",
    "tam": "Look out of your window. Fire below and no sky I know. I've prayed every night to be told where we are. Nobody answers down here. Not even him.",
    "kesh": "I asked the gaoler once. He said they bring him in blindfolded too. Then he laughed, and I don't think it was a joke.",
}
WHERE_MORE = [
    "The guards never go home. Ever noticed? Same faces every day. Where would they go?",
    "Nobody's ever written to anyone in here. Not because they won't let us. Because nobody knows where to send it.",
    "The Watch built this place before there was a Watch. That's what the old ones say. Found it, more like.",
]

# hints towards a hidden questline you haven't found yet (one of the prisoners, now and then)
HINTS = {
    "tongue": ("kesh", "Those shrines on the roads, the ones with the gold eye? Pray at one, nothing. Pray at two, nothing. My cousin swore the third one talked back."),
    "pilgrim": ("tam", "There are pilgrims on the roads, starving, since that steward of theirs in the Nether was dealt with. Feed them. Three of them. The third one... feed them, and see."),
    "godsaw": ("tam", "Dream long enough and you'll want to sleep somewhere higher. Up above the clouds. That's where the god's own dreams are kept, they taught us."),
    "skyfire": ("kesh", "The sky country and the burning one never meet. Take something from up there down into the fire. Make them meet. I'd love to know what happens."),
    "clean": ("maddock", "There's a kind of person the gods notice: one who gets to the fourth act of all this without a drop of anyone's blood on them. No villager, nobody that mattered. Not many of those."),
}

# Old Maddock's way out
ESCAPE = {
    "ask": "Is there a way out?",
    "plan": [
        "Keep your voice down. ...There's always a way out. There's never a cheap one.",
        "Three things. A tool that bites stone: the quarry cart has chisels, if the guard ever looks the other way.",
        "A wall nobody checks: the one under your window. Dig at night, after lights out, with your hands free. Keep at it.",
        "And the gaoler's rounds. He walks the corridor when he likes. I know when he likes. But I don't know you yet.",
        "Get caught and it's two more days, and they'll take whatever you've got. Still want out?"],
    "trust": "Bring me your bread at supper, and maybe I'll know you.",
    "give": "Give him your bread",
    "given": [
        "He eats both pieces, slowly, watching you.",
        "Alright. After lights out I'll tap the pipe that runs through the cells. Twice. That's him coming. Hear the tap, stop digging. Stop breathing, if you can.",
        "And don't dig when the bell's about to go. They count us in the morning."],
    "no_bread": "No bread, no trust. Earn your supper at the quarry first.",
    "progress": {
        "need_tool": "You've still no tool. The cart in the quarry. Watch the guard's head, not his hands.",
        "need_dig": "You've the tool. Now the wall, under your window, after lights out. Mind my taps.",
        "digging": "You're getting there. I can hear it at night. So can others, if you're careless.",
    },
    "caught": "I warned you. Two more days, and back to nothing. Want to try again? I'd wait a while.",
}

# the gaoler
GAOLER = {
    "morning": "Morning. Bell goes soon. Quarry, then food, then sleep. Every day the same. You'll learn to like it.",
    "labour": "Stone doesn't break itself. Your quota, or no supper.",
    "meal": "Eat. Talk if you like. Talk quietly.",
    "night": "Lights out. No beds in here. You'll feel every hour of it.",
    "out": "Off you go. Don't come back. They always come back.",
}

LABOUR = {
    "quota": 48,
    "start": "The quarry. Break your quota of stone (48) before the bell, or no supper.",
    "done": "Quota met. The guard makes a mark next to your name.",
    "short": "You didn't meet your quota. No supper tonight.",
    "strike": "That's three days without your quota. The guards add a day to your sentence for idleness.",
    "cart": "The quarry cart: chisels, wedges, a broken hammer. The guard is watching it.",
    "cart_free": "The quarry cart: chisels, wedges, a broken hammer.",
    "stolen": "You slip a chisel up your sleeve while the guard's back is turned.",
    "seen": "The guard turns as your hand closes on the chisel.",
}

MEAL = {
    "fed": "A bowl of grey porridge and two heels of bread. It's the best thing you've eaten in your life.",
    "unfed": "The cook looks at the list, looks at you, and gives your bread to the next in line.",
}

ESCAPE_STEPS = {
    "tap": "Tap. Tap. (Maddock's signal: the gaoler's coming.)",
    "dig": "The mortar under the window gives a little.",
    "through": "The wall gives. Night air, and a ledge of basalt along the wall outside. Go. Now.",
    "caught": "The gaoler's lantern is on you. Two more days, and they take your tool and fill your hole.",
    "found_tool": "The guards turn your cell over at dawn, and find the chisel. Two more days.",
    "found_hole": "The guards find the scratched mortar under your window at morning count. Two more days.",
    "dawn": "The bell rings while the hole is open. The guards are already in the corridor. Two more days.",
    "need_tool": "You scrape at the wall with your fingernails. You'll need a tool.",
    "need_night": "Not now. Not with the whole prison awake.",
}

ESCAPE_DIGS = 500

VISITORS = [
    {"id": "hand", "name": "A Man in Gilded Gloves", "lines": [
        "Silas sends his regards. He says a debt's a debt, and you're good for yours.",
        "He also says: if anyone in here asks about the Hand, you've never heard of it. We'll know if you have. We always know.",
        "Sit tight. Eat your bread. Don't dig anything you can't finish."]},
    {"id": "watch", "name": "The Watch Captain", "lines": [
        "I put your name forward for the Watch. Do you know what they said to me when it came up in court?",
        "Nothing. They just looked at me. That was worse.",
        "Serve your time. Then come and find me, and we'll see if there's anything left to fix."]},
    {"id": "widow", "name": "A Widow", "lines": [
        "I asked them to bring me. I wanted to see you. I thought I'd feel something.",
        "I don't. That's what I came to tell you. You took that too."]},
    {"id": "stranger", "name": "A Hooded Visitor", "lines": [
        "They blindfolded me the whole way. Three days in a cart. I'll never find this place again, so I'll say it now.",
        "Ashford keeps your name on a wall. Some of them want it scratched off. Some of them don't.",
        "Whichever you are, you've time to decide in here."]},
]

# the prison's own sounds are chosen in code (drips, chains, distant doors, the bell)


def build():
    keys = {"tongue", "pilgrim", "godsaw", "skyfire", "clean"}
    assert set(HINTS) == keys, set(HINTS) ^ keys
    ids = {p["id"] for p in PRISONERS}
    for k, (who, _) in HINTS.items():
        assert who in ids, k
    assert set(WHERE) == ids
    return {"prisoners": PRISONERS, "rumours": RUMOURS, "where": WHERE, "where_more": WHERE_MORE,
            "hints": {k: {"who": w, "text": t} for k, (w, t) in HINTS.items()},
            "escape": ESCAPE, "gaoler": GAOLER, "labour": LABOUR, "meal": MEAL, "escape_steps": ESCAPE_STEPS,
            "escape_digs": ESCAPE_DIGS, "visitors": VISITORS}
