# Half a God

A NeoForge 1.21.1 mod that turns **Mebahel's RPG: Villager Quests & Companions** into one long story about the Cult of Azazel, and adds what a datapack can't: a reputation meter, story safety nets, bigger choices, cinematic moments, Mara's letters and a Chronicle of your playthrough.

The whole Half a God story datapack is **built into the jar**. Don't also install `Half-a-God.zip`.

## Requirements

- NeoForge 21.1.252 or newer (Minecraft 1.21.1)
- Mebahel's RPG 3.4.4, running through **Sinytra Connector** + **Forgified Fabric API**
- The story's mods: Cult of Azazel, Bosses' Rise, Sons of Sins, The Aether, It Takes a Pillage Continuation, Iron's Spells 'n Spellbooks, End Remastered, Illager Invasion, Create: Addictives, The Bumblezone, Legionary

Install: put `half_a_god-<version>.jar` in `mods/` on the client **and** the server.

## The reputation bar

A slim bar at the top centre of the screen, away from the hotbar where Tough As Nails, stamina and rolling mods draw. It steps down below boss bars, and shows your tier's name for a moment whenever your reputation changes.

> **Wicked · Fallen · Wavering · Good · Saint** (-100 to +100)

What moves it:

- **Story choices:** every mercy and every cruelty, including Mebahel's own spare-or-kill quests.
- **Everyday play:**
  - killing a villager (-15), a trader (-10) or a village's iron golem (-10)
  - looting a village's chests (-3)
  - curing a zombie villager (+8)
  - Hero of the Village (+12)
  - killing raiders during a raid, and monsters that are hunting villagers
  - trading

What it changes:

- **Prices:** villagers' trades get cheaper or dearer.
- **Villagers:** they greet you differently and gossip about what you did.
- **Mara's letters:** she writes to you differently.
- **Companions:** they talk to you differently.
- **Unlocks:** reaching Saint brings *The Lantern Road*; sinking to Wicked brings *Silas Makes an Offer*.

### Good and evil

Both sides have their rewards, but evil makes the world much harder on you.

| | Good / Saint | Fallen / Wicked |
| --- | --- | --- |
| In villages | Hero of the Village while you're there (stronger as a Saint) | a village you walk into may get a raid because of you (15% / 35% a day) |
| Fights | golems, guards and the Watch join in when something attacks you; a Saint gets a Watchman at their side at night near a village | bounty hunters come for you at night (35% / 70%); illagers near you are stronger (and tougher at Wicked) |
| Trading | cheaper trades | clerics won't serve the Fallen; nobody trades with the Wicked; golems attack the Wicked; wandering traders won't deal with them |
| Money | | Silas pays you a cut of every story quest (4 / 8 emeralds); sneak + right-click a villager to scare something out of them (once a day each) |
| Work | Watch duties | Gilded Hand contracts (they pay better) |
| Ashford | rebuilt as the story goes on | stays burned, and the Gilded Hand moves in |
| Notice boards | thanks with your name on it | a wanted poster with your name on it |

Wavering gets none of the perks and none of the penalties, and no work: nobody's sure what you are yet.

Settings are in `config/half_a_god-client.toml`:

- `position`: TOP_CENTER, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT or BOTTOM_RIGHT
- `width`, `offset_x` / `offset_y`: size and pixel nudges
- `show_number`
- `screen_shake`
- `replace_quest_map`: open the story map instead of Mebahel's quest map

## The story map

Mebahel's quest-map key opens the Half a God story map (or use `/halfagod map`). Each act is a page of an old map:

- **The road.** The main story is a broad worn road through the middle, and the side stories run alongside it above
  and below, each with a signpost. Every quest you've reached is a wax seal: red when done (with a green or black mark
  for mercy or the blade), gold with a flag where you are now, a paper token when it's ready to start. Letters that
  sent you somewhere show as little envelopes on the road.
- **Nothing ahead is drawn.** Quests you haven't reached aren't on the map at all, and past the last place you've been
  the paper clouds over.
- **The journal page.** Click a seal and its page opens on the right: the story so far, your objectives (ticked off as
  you do them, with the current one marked), what led there and where it goes next. Click any of those to jump to it.
- **Now.** A note in the corner always shows the quest you're on and what to do next; click it to go there. If it's
  off screen, an arrow at the edge points to it.
- **Acts.** The arrows and the row of diamonds at the top turn the page; acts you haven't reached stay dark.
- **Margin notes.** Beside every main-story quest you've finished, your character has scribbled a note in the margin.
  Its tone is set by your reputation at the moment you finished that quest (warm if you were Good or a Saint, flat if
  Wavering, cold if Fallen or Wicked), so a player who turned halfway sees their notes turn too. The journal page shows
  the note as well.
- **Friends.** On a server, the footer shows the other players: which act they're in and who they've become (your
  rivals, on the other side from you, in red when you hover).
- **Controls.** Drag to move, scroll to zoom (or + and -), the arrow keys or A and D turn the page, Space jumps to
  where you are, C opens the Codex, Esc closes the journal page.
  Mebahel's old quest map is gone; Mebahel's Quest Journal (for abandoning quests and choosing what's tracked) is untouched.

**The Codex** is a book with bookmarks:

- **Lore:** the people, places and the cult, and the seven **Wardens** (with the day each one fell), as the story reaches them.
- **Letters:** every letter Mara sent you, to read again.
- **Your Path:** a chart of your reputation quest by quest across the acts, who you are now, and your rank in the
  Watch and the Gilded Hand with what the next rank needs.
- **Dreams:** every dream you've had, to read again (appears after your first dream).
- **Secrets:** the five hidden questlines. "Secrets found: 2 / 5", with the ones you haven't found shown as ???.
- **Trials:** every time the Watch took you to court: what each witness said, your plea, the verdict, and how you got out.
- **Your Story:** the end card: your ending and the half of the god you became, days, deaths, who you spared and
  killed, Wardens, letters, dreams, secrets, trials, your rank and your three biggest choices. **Save image** puts it
  in your screenshots folder to share. It opens by itself when the story ends.
- **What Became of Them:** once the story is over, a page for everyone you judged, saying how their life turned out
  because of what you chose. `/halfagod fates` brings it back.

Unread entries and letters have a red dot.

## Antique Atlas

With Antique Atlas (and Surveyor) installed, every story quest with a destination puts a marker on your atlas. It moves
when the target moves and disappears when the quest is done.

## Characters stay put

Every story character lives somewhere: Reeve Halden and Ashford's people in Ashford, the elder and the cleric in your
home village, the snowy village's people in a snowy village, and so on. When a quest needs someone, they're in their
village, standing where you last saw them, and there's only ever one of them. Characters you meet for the first time
settle in the village they're placed in. Guard Villagers and iron golems won't touch anyone you need to talk to or judge.
Story characters glow only when it's their turn, never hand out Mebahel's random side quests, and if you talk to one
too early they tell you what to do first.

## The world answers back

- **Ashford rebuilds.** On an empty lot near Ashford's middle you'll see what's left after the raid, then the families'
  tents and supplies, then a house going up, then finished houses, the Watch's hall and lanterns down the walk. If you're
  Fallen or Wicked when the rebuilding starts, the ruins stay ruins and the Gilded Hand puts up a counting house with a
  debtors' cage instead (it changes back if you climb back up). Nothing is ever built over something you placed.
- **The people you saved move in.** Wick, Gorran, Tomas, the Pilgrim, Brann, the Midwife, Tobin, Sister Ysolde,
  Garret, Pell and the freed Ashford men settle in your village, Ashford or the snowy village once their story is
  done well. Right-click them and they'll talk. In time a Good Ashford gets a Captain of the Watch, and a Fallen or Wicked one a Tallyman of
  the Gilded Hand.
- **Notice boards.** Each village you know puts up a sign by its bell: thanks, a call for Watch recruits, or a wanted
  poster, depending on who you are. When your reputation changes, the notice changes (even if you tore the old one down).
- **Encounters on the road.** Now and then (at most every day and a half of game time, usually less often), something
  happens on the road between villages: a wounded deserter, a merchant being robbed by highwaymen, a man running from
  bounty hunters, a golden cult shrine, a hungry pilgrim. Feed them, save them, tear it down, or take what they have,
  kill for the bounty, kneel and pray. Each one moves your reputation.
- **Duties and contracts.** Walk into a village and a resident may glow: that's work. Watch duties (night watch,
  clearing the road, spiders in the hedges, raiders on the hill, creepers, bread for families, mending fences) raise
  your reputation a little. Gilded Hand contracts (the tithe, stealing a bell, rival pillagers, the farmers' share,
  gunpowder, bones for the Choir) pay more and drag it down. One at a time, at most every half a day.
- **Ranks.** Finished duties and contracts earn ranks, and they're hard to get. Every job for the other side wears five
  off your tally, and if it falls below a rank's mark you lose the rank.

  | Watch | needs | Gilded Hand | needs |
  | --- | --- | --- | --- |
  | Watchman | 6 duties | Runner | 6 contracts |
  | Sergeant of the Watch | 18, and Good or better | Collector | 18, and Fallen or worse |
  | Captain of the Watch | 40, and a Saint | The Hand's Favourite | 40, and Wicked |

  Each rank pays a bonus on every job. A Sergeant gets a Watchman at their side when attacked near a village at night
  and guards come from further away; a Captain gets one day or night. A Collector shakes down twice as much and bounty
  hunters come a quarter less often; the Hand's Favourite gets double Silas's cut and half the bounty hunters.

## Dreams

When you sleep through a whole night, about one night in four, you dream (never two nights running, never the same
dream twice). The screen goes dark, a picture breathes in the middle, and a few lines appear one at a time. Click to
see the rest, click again to wake. There are three kinds: the god's own past, told in order a dream at a time; omens
about the act you're in; and echoes of choices you made coming back to you.

## Hidden questlines

Five questlines that aren't on the map until you find them. Each opens when you do something odd, with a title card
("A hidden path opens"), and pays out in story: Codex pages and a fate of its own at the end. No items with powers.

| Secret | How it opens |
| --- | --- |
| The Old Tongue | pray at three of the cult's golden shrines on the road (the first two whisper) |
| The Last Pilgrim | feed three hungry pilgrims on the road, after you've met Brother Vael |
| What the God Saw | have five dreams, then sleep a full night in the Aether |
| Sky and Fire | carry anything from the Aether into the Nether |
| Clean Hands | reach Act IV without killing a single villager or story character |

## The Trial

From Act II on, if you're Fallen or Wicked, the Watch can come for you in your sleep: once per act, on a random night
(one in three). You wake in chains in the dock of the Watch's own court, a small dimension of its own: a vaulted
hall of deepslate and dark oak, with amber windows, chandeliers, balconies and a rose window, the judge on a raised
bench before a crimson screen under the Watch's great eye and its rays of light, a clerk, the Watch Captain, a witness
stand, six jurors in a two-tier box, the evidence table, two iron golem guards and a tiered gallery of onlookers. The court speaks in a panel along the bottom of the screen, so you can watch it all
happen:

- **The court acts it out.** Whoever is speaking turns to you and glows. Each witness takes the stand in turn (the
  stand stays empty for one who "cannot be found"), and the gallery mutters at the worst of it.
- **Answer each charge.** After every witness: *deny it* (struck out if the court has only one witness, worse if it
  has proof), *explain* (admit it and say why: it weighs a little less) or *say nothing*.
- **Your standing counts.** The Gilded Hand sends an advocate for its members (the case weighs less). A sworn
  officer of the Watch who has only lost their way gets leniency; a Wicked one hears "you of all people".
- **A confession counts.** Anything you've confessed to a priest is still read out, but weighs less.
- **Rivals speak.** Another player who saw what you did may testify against you, or for you.

A witness you killed can't testify. Then you plead (guilty: a day off; not guilty: walk free or a day longer; say
nothing; or pay a juror with Hand rank and 16 emeralds), the six jurors vote one at a time, the gavel falls, and the
verdict. The Codex keeps the whole transcript, your answers and the vote.

## The Watch's prison

Found guilty, you serve 1 to 5 days (up to 7 for repeat convictions) in the Watch's prison: a fortress on a basalt
shelf in a vast cavern deep underground, with lava falling down the walls into a lava lake, glow lichen and stalactites
in the dark. Obsidian cells off a vaulted corridor, a quarry, a cafeteria and a gate, and beyond the gate a bridge over
the lava and a tunnel into the rock. Somewhere nobody can find. The other prisoners don't know where it is either.
Your things are kept as evidence. There's no bed: a prison day is a whole Minecraft day (20 real minutes), counted only
while you're there (logged out, the clock stops). The action bar tells you how long is left once a minute. Dying puts
you back in your cell.

A day, on bells (each time the doors open for a minute, then the guards drag anyone who dawdles):

| Time | Where |
| --- | --- |
| 0:00 | your cell: morning (halfway through your sentence, someone visits) |
| 5:00 | the quarry: hard labour. Break your quota of stone (48) with the pickaxe they hand you, or no supper. Three days short, and a day more |
| 11:00 | the cafeteria: supper, and the other prisoners to talk to |
| 16:00 | your cell: lights out, and the gaoler walks his rounds |

**The prisoners.** Old Maddock, Wren, Brother Tam and Kesh will tell you rumours from outside, about themselves, how
nobody knows where the prison is, and now and then a hint towards a hidden questline you haven't found.

**The visitor.** Someone who matters to your story: a man in gilded gloves from Silas, the Watch Captain, a widow, or
a hooded stranger who was brought blindfolded.

**Walking out.** Sentence served, your door and the gate open and you walk out past the cafeteria.

**Escaping (hard).** Ask Old Maddock. He'll want your bread before he trusts you; then he'll tap the pipes to warn you
when the gaoler's coming. Steal a chisel from the quarry cart while the guard is looking the other way. Then dig at
the wall under your window after lights out, a little each night, stopping whenever the gaoler passes. Through the
wall, follow the ledge along the outside. Caught at any step (the guard sees you at the cart, the gaoler sees you
digging, the morning search finds the chisel or the hole, or the bell rings with the hole open): two more days, and the
tool and the hole are gone. Out, you wake at home with your things; the Hand respects it, the Watch remembers it
(more bounty hunters).

**Broken out.** At Collector of the Hand or above, Silas's men come on the second day.

## Your reputation travels

- **Ahead of you.** Villagers you've never met have heard of you: a Saint gets better prices, the Wicked pay more
  (through vanilla's own villager gossip). The first time you walk into a village, you hear what it's heard.
- **Confession.** Sneak and use a cleric to confess something the Watch could try you for (once a day).
- **Interrogations.** When you spare one of the story's enemies, before they go you can threaten them (works if
  you're feared; costs reputation), pay them (5 emeralds) or reason with them (works if you're trusted; earns a
  little). What they tell you is kept in the Codex, under Letters.

## Mebahel's screens, in paper and ink

The mod has a built-in, always-on resource pack ("Half a God: paper and ink") that repaints Mebahel's quest board and
contract notes (dark walnut, brass, aged parchment, brass tacks and red wax seals, at 16 times the resolution) and
recolours his talk screen to match. Only how they look changes. Turn it off in Resource Packs if you prefer his.

## Multiplayer

Everyone plays their own story; nothing forces a fight.

- **One Ashford.** Ashford follows whoever is furthest along; the others share it.
- **See each other.** The map's footer and the Codex show the other players' acts and reputations.
- **Gossip.** Mara's letters sometimes end with news of another player.
- **Rivals.** A Watch duty done while a Fallen or Wicked player is online (or a Hand contract while a Good one is) is
  noted with a line on the action bar and 2 extra emeralds.
- **A price on their head.** With a Gilded Hand rank and 20 emeralds, "Put a price on their head" (Codex, Your Path,
  under the other players) sends the Hand's hunters after a player the next night they're out under the sky. Not on
  someone higher in the Hand than you, and not the same player twice in three days.
- **Witnesses.** If you were near another player when they killed a villager or emptied a village's stores, and the
  Watch takes them, a runner asks whether you'll testify against them, speak for them, or stay out of it.

## What the mod adds to the story

- **Choices with three or four answers:**
  - the Librarian's Bargain
  - the One Who Speaks
  - Halden's Son
  - Maraal's Offer
  - what becomes of Vael, the Midwife and Don Vasco
  - what you say to Maraal

  Close the screen to decide later; `/halfagod choose` brings it back.
- **Consequences that come back:** several answers send a letter later that starts its own quest.
- **Mebahel's questlines woven in:** when you walk into a village, a resident offers the next questline that the current act is waiting on. They glow for a moment so you can find them.
- **The Blackwater way out:** if you climb back to Good while you're deep in the Green Trade, Reeve Halden writes with a way out.
- **Mara's letters:** a letter at the end of every act, warm, careful or frightened depending on your reputation. She signs as Maraal once you forgive her, and stops writing if you don't.
- **The First Champion:** find all eight lost Warden Journal pages to unlock it.
- **Companion moods:** Dunn, Corwin and Ansel change how they talk to you as your reputation moves, and comment on big story moments.
- **Cinematic moments:** a title card, a sound and a screen shake when a Warden falls, Maraal is revealed, the siege breaks and the ending comes.
- **The Chronicle:** when your epilogue ends, Old Mara writes the book of your playthrough, act by act, with who you were in each. `/halfagod chronicle` gives you the story so far.

## Safety nets

- Monsters and raiders can't hurt (or zombify) story villagers or the villagers offering story questlines.
- A story quest you abandon starts again by itself.
- `/halfagod lostmyway` finds where you are and restarts anything missing:
  - quests
  - letters
  - pending choices
  - the questlines the act is waiting on

## Commands

| Command | Who | What |
| --- | --- | --- |
| `/halfagod rep` | everyone | your reputation and tier |
| `/halfagod lostmyway` | everyone | put your story back on track |
| `/halfagod choose` | everyone | reopen a choice you closed |
| `/halfagod chronicle` | everyone | the Chronicle so far |
| `/halfagod map` | everyone | open the story map |
| `/halfagod fates` | everyone | what became of them (after the ending) |
| `/halfagod story` or `/halfagod unlockall` | operators | opens the whole story: every act and quest, what it needs and where it leads, with a button to start any quest |
| `/halfagod jump <quest> [player]` | operators | start any story quest |
| `/halfagod setrep <value> [player]` | operators | set reputation |

**Testing (operators):**

| Command | What |
| --- | --- |
| `/halfagod test story <point> [good\|evil\|waver\|random]` | jump to a believable point in the story without playing it: `prologue`, `act1` … `act7`, `end`, `after` or `random`. Everything before it is done (map, margin notes, Your Path, Wardens), decisions are made the way that kind of player would, and reputation follows. Laid over your real story; Mebahel's own quests don't move. Ashford and settlers do follow it, so use a test world. |
| `/halfagod test story clear` | put your real story back exactly as it was |
| `/halfagod test story status` | whether you're in a simulated story |
| `/halfagod test dream` | dream now (a dream that fits where you are) |
| `/halfagod test dream <id>` | a particular dream (tab completes; `kidnap`, `godsaw`, `release`, `escape`, `breakout` are the special ones) |
| `/halfagod test dreams reset` | forget your dreams |
| `/halfagod test secret <tongue\|pilgrim\|godsaw\|skyfire\|clean>` | find a hidden questline now |
| `/halfagod test trial` | the Watch takes you to court now (any reputation, any act) |
| `/halfagod test trial status` | where you are with the Watch and whether a trial is due |
| `/halfagod test trial witnesses` | a trial with a made-up record: four witnesses speak, three "cannot be found" (your real story isn't changed) |
| `/halfagod test trial day` | serve one day of your sentence now, instead of waiting 20 minutes |
| `/halfagod test trial rival` | a made-up traveller testifies against you (on trial, before you plead) |
| `/halfagod test prison time <morning\|labour\|supper\|night\|lightsout>` | jump to that part of the prison day (the bell goes a second later) |
| `/halfagod test prison quota` | today's stone quota is met |
| `/halfagod test prison visitor` | today's visitor comes to your bars (in the morning) |
| `/halfagod test prison escape <plan\|trust\|chisel\|dig>` | skip to a step of the escape (`dig`: one more dig at the wall gets you through, after lights out) |
| `/halfagod test confess` | confession now, without a cleric |
| `/halfagod test interrogate <who>` | an interrogation now (tab completes: peddler, wick, vael, orsk, corwin, gorran, ossian, pilgrim, vasco, brann, midwife) |
| `/halfagod test ahead` | villages forget you've visited; walk into one to hear what it's heard of you |
| `/halfagod test bounty <player>` | a price on a player's head, free (hunters come the next night they're out under the sky) |
| `/halfagod test trial release` | let yourself out (things and bed back) |
| `/halfagod test trial reset` | the Watch forgets you (this act's trial can happen again) |
| `/halfagod test endcard` | open "Your Story" (a preview before the ending) |
| `/halfagod test codex <page>` | open the Codex on a page |

## Building

```
gradle build
```

GitHub Actions builds every push and publishes the jar to the **latest** release.

The datapack is generated by the Python scripts in `datapack-src/`. Mebahel's own quest files are included there; they're CC0.

```
cd datapack-src && python3 build.py
```

That rebuilds:

- `src/main/resources/data/`
- the mod tables in `data/half_a_god/hag_mod/`
- the operator story index
- the language file
