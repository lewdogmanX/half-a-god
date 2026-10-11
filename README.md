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

Mebahel's quest-map key opens the Half a God story map (or use `/halfagod map`). It's a parchment map of your journey,
one page per act. The main story is a winding road; side stories branch off above and below it. Every quest you've
reached is a wax seal: red when done (with a green or black dot for mercy or the blade), gold where you are now, plain
when it's ready to start. Anything you haven't reached isn't drawn at all; the road just fades out ahead of you.
Click a seal for its story and goals. Drag to move, Ctrl+scroll to zoom, the arrow keys turn the page.
"Quest list" opens Mebahel's own map.

**The Codex** (button at the bottom) collects what you've learned about the people, places and the cult. Entries
appear as the story reaches them.

## Antique Atlas

With Antique Atlas (and Surveyor) installed, every story quest with a destination puts a marker on your atlas. It moves
when the target moves and disappears when the quest is done.

## Characters stay put

Every story character lives somewhere: Reeve Halden and Ashford's people in Ashford, the elder and the cleric in your
home village, the snowy village's people in a snowy village, and so on. When a quest needs someone, they're in their
village, standing where you last saw them, and there's only ever one of them. Characters you meet for the first time
settle in the village they're placed in. Guard Villagers and iron golems won't touch anyone you need to talk to or judge.

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
- **Duties and contracts.** Walk into a village and a resident may glow: that's work. Watch duties (night watch,
  clearing the road, spiders in the hedges, raiders on the hill, creepers, bread for families, mending fences) raise
  your reputation a little. Gilded Hand contracts (the tithe, stealing a bell, rival pillagers, the farmers' share,
  gunpowder, bones for the Choir) pay more and drag it down. One at a time, at most every half a day.

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
| `/halfagod story` or `/halfagod unlockall` | operators | opens the whole story: every act and quest, what it needs and where it leads, with a button to start any quest |
| `/halfagod jump <quest> [player]` | operators | start any story quest |
| `/halfagod setrep <value> [player]` | operators | set reputation |

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
