# Half a God

One connected story campaign for **Mebahel's RPG: Villager Quests & Companions** (3.4.4, Minecraft 1.21.1): 281 quests of its own, plus all 89 of Mebahel's built-in quests woven into the acts. The Bumblezone, Legionary and Create: Addictives arcs are part of the main line, and a reputation scale runs through every act.

> **Using the Half a God mod?** Then you don't need this zip: the mod has the same datapack built in, plus the reputation bar, the bigger choices, Mara's letters and the Chronicle. Don't install both.

## Requirements

- Mebahel's RPG 3.4.4. It is a Fabric mod, so on NeoForge it also needs **Sinytra Connector** and **Forgified Fabric API**.
- Cult of Azazel, Bosses' Rise, Sons of Sins, The Aether, It Takes a Pillage Continuation, Iron's Spells 'n Spellbooks, End Remastered, Illager Invasion, Create: Addictives, The Bumblezone and Legionary.

## Install

- **One world:** put `Half-a-God.zip` in `saves/<world>/datapacks/`, then run `/reload` or rejoin. A gold "[Half a God] Story datapack loaded" message confirms it is on.
- **Whole modpack:** use a global datapack loader (for example Paxi or Global Packs).

## How the story starts

When a player first has a **full set of iron armour** (helmet, chestplate, leggings and boots), Mebahel's courier brings *A Letter from Old Mara*. Read it to begin. The trigger is a hidden achievement: it never shows a toast.

After that:

- quests start automatically and letters arrive by courier
- everything shows in the quest journal, tracker and **quest map**, and every id starts with `hag_`

## Characters live in villages

When a quest says *talk to* someone, that person is placed in a village by Mebahel's own village search, not next to you:

- **Home-village characters** (Prologue) are in the village you're in.
- **Everyone else** (Reeve Halden, the Librarian, the Fixer and others) lives in a different village at least 300 blocks away. Follow the quest marker, find them and introduce yourself.

Ordinary villagers don't count. Only the named, glowing one does.

**If a story villager dies** (zombies, raids, lava), Mebahel puts them back automatically and the quest carries on. They also have extra health, Resistance II and fire resistance. If one ever goes missing without dying (for example a zombie turned them), an operator can run `/mebahelquestrespawn <quest id>` while the quest is active.

**Don't abandon story quests** from the journal. An abandoned quest is failed and the story stops there. If it happens, an operator can restart it with `/mebahelquest start <quest id>`.

## One story, start to finish

Everything is one questline now. The main story waits at the end of each act until that act's arcs are done:

| Act | Main story | Arcs woven in |
| --- | --- | --- |
| Prologue | The village, the Peddler, the Pillager Camp | |
| I | Ashford, the Bastille, the Invoker, the Nether | Iron's Spells part 1, reputation chapter 1 |
| II | The Wither | **The Golden Legion** (Legionary): Old Aurel writes after The Skull Market. Kill the Unbreakable (wake him with a **netherite pickaxe**), bring his Thermal Stone home and find his head in the next bastion treasure chest. Reputation chapter 2 |
| III | The Aether | **The Honey Crown** (Bumblezone): Wren writes after the Wither falls. Throw an ender pearl at a bee nest, meet the Bee Queen. Iron's Spells part 2, reputation chapter 3 |
| IV | The End | End Remastered eye hunt, reputation chapter 4 |
| V | Bosses' Rise | **The Blackwater Cartel** (Create: Addictives), decided by your reputation: Saint/Good get *The Blackwater Raid* (help the Watch break the cartel), Wavering get *A Generous Offer* (take it or refuse it), Fallen/Wicked get *The Green Trade* (join it from the inside). Halfway up the trade, a witness gives you one chance to get out. Reputation chapter 5 |
| VI | Sons of Sins | Reputation chapter 6 |
| VII–VIII | The Nether Mansion, Azazel | Reputation chapter 7 (with **the Siege**: how many come, and who stands with you, depends on your reputation), then your epilogue |
| After | *The Empty Throne* (you killed him) or *The Hand of Azazel* (you knelt) | |

**Mebahel's own questlines are part of the story too.** Each one opens with an act and the act's last story quest waits until they're finished: the Prologue/Act I lines (settling in, the cow, the village watch, the man with no banner, the witch, the kill quests, the delivery), Act II (the Ash Court, the knight at the portal, the blazing mine, the smith), Act III (Blackthorn House, the listening mine, the treasure map), Act IV (the Fourth Member, the house that owes money), Act V (the false hero, the captain's share, the ship, the undertaker), Act VI (the mad scientist, the funeral, the two dead men) and Act VII (the zombie in disguise, the ash merchant). Their first quest is offered by a villager; their letters arrive by courier when the act begins. Without the mod you'll need to find a villager offering them (or use `/mebahelquest start <id>`); the mod has a village resident offer the next one whenever you walk into a village.

## Reputation: every choice moves you

> **Saint · Good · Wavering · Fallen · Wicked**

It starts after *Ashford Burns*, when Halden's men catch a raider (Orsk). You start in the middle:

- **Orsk:** hand him to Halden → Good, then Corwin the grain thief (let him go → Saint, punish him → Wavering). Execute him → Fallen, then Morrow of the Gilded Hand (take his gold → Wicked, cut him down → Wavering).
- **Each act after that** has one chapter for where you stand: Saints rebuild and heal, the Wavering make amends, the Fallen are hunted by bounty hunters and avengers, and the Wicked work for Silas of the Gilded Hand. Each chapter ends with a person you have to judge: a deserter, a skull seller, a cleric with a secret, a cult scholar, a wrecker, a blood addict, a traitor. They react to your reputation.
- **Mercy moves you one step towards Saint; cruelty one step towards Wicked.** So you can fall, climb back over a few acts of doing good, and fall again. Getting from Wicked to Saint takes four acts in a row of mercy.
- **Your reputation changes your rewards** (protection and healing for the merciful, gold and power for the cruel), **what villagers gossip about**, and **your epilogue**. After the ending (still decided only by Azazel's dialogue: kill him or kneel) you get *The Lantern-Bearer*, *Ashford's Friend*, *The Scales*, *What Remains* or *The Gilded Throne*.
- The four story choices (the Peddler, Brother Vael, the Midwife, Maraal) still send the story down different paths of their own, and Don Vasco's handshake or execution decides how the Green Trade ends.

## The story branches

The main line forks, runs paths side by side, and has outcomes that depend on your choices:

- **Iron's Spells, part 1 (before the Wither):** a priest's warning starts a magic path that runs alongside Act I. Build an Inscription Table, then kill the Pyromancer and the Archevoker of the Evoker Fort. Both paths meet at *Two Roads Meet* before the Wither.
- **Iron's Spells, part 2 (after the Wither):** a second path runs alongside the Aether. It covers the **Dead King** in the Catacombs, the Cryomancers' Mountain Tower, and the **Echo of Tyros** in a Nether Citadel. You learn you are not Maraal's first champion. It rejoins at *The Road to the End*.
- **End Remastered eye hunt:** two paths of 13 eyes. Your path covers old, rogue, cold, black, lost, corrupted and guardian. The Librarian's covers magical, nether, cursed, wither, witch and undead. Each eye quest takes the eye and gives it straight back. Both must be finished before the stronghold.
- **Illager Invasion:** find the Woodland Mansion where the **Invoker** lives and kill him. Illager Forts appear in board contracts, and their mobs in the Green Trade's cartel war.
- **Choices that lead somewhere different:**

  | Choice | One path | The other path |
  | --- | --- | --- |
  | The Peddler | Spare him: *The Peddler's Gift* | Kill him: *The Peddler's Debt* |
  | Brother Vael | Spare him: *Vael's Map* | Kill him: *The Steward's Guard* |
  | The Midwife | Spare her: *Cleansing the Pits* | Kill her: *The Coven* |
  | Maraal | Forgive her: *Walking with Maraal* | Reject her: *Alone* |
  | Azazel | Kill him: good ending | Kneel: bad ending |

## Choices that come back later

Five people on the main road ask you to decide their fate. The story carries on either way, and 30 to 50 minutes later what you did comes back as its own quest:

| Who | Spare | Kill |
| --- | --- | --- |
| Wick the Informant (Prologue) | *Wick's Back Door* | *Wick's Sister* |
| Gorran, the Bastille Warden (Act I) | *The Warden's Debt* | *Gorran's Brothers* |
| Ossian the Skull-Trader (Act II) | *Ossian's Gift* | *The Choir Wants It Back* |
| The Pilgrim of the Sun (Act III) | *Sunlight in a Bottle* | *The Sun Remembers* |
| Brann of the Garrison (Act V) | *Brann Comes Home* | *A Widow's Letter* |

**Companions:** spare Dunn the Deserter, Corwin the Refugee or Ansel the Blood-Drinker and they can join you.

**Lost pages:** eight pages of the Warden Journal hide in chests (ancient cities, end cities, jungle temples, woodland mansions, buried treasure, igloos, pillager outposts, big ocean ruins). Each leads somewhere. With the mod, finding all eight brings *The First Champion*.

## Side quests (31)

Villagers offer these at random once the linked story quest is done. They include escorts, village defence, animals and deliveries. The quest board also has repeatable street-delivery contracts that pay in emeralds.

## Endings

The Cult of Azazel's own dialogue decides the ending:

- **Kill the True Azazel:** his barrel holds Maraal's last letter, which starts the good ending.
- **Accept his offer:** he leaves the Cult Scroll, and *Bow at My Feet* completes and starts the bad ending. The scroll is given straight back.

## Testing commands (operator)

- `/mebahelquest givebook mebahelrpgrquest:hag_the_first_letter` gives the opening letter.
- `/mebahelquest givebook mebahelrpgrquest:hag_gt1_a_note_in_the_stash` gives the Green Trade note.
- `/mebahelquest delayed skip` skips waiting delays.
- `/mebahelquest courier skip` makes a waiting courier arrive now.
- `/mebahelquest start <quest id>` restarts a story quest you abandoned (it spawns a Quest Giver next to you; talk to it).
- `/mebahelquestrespawn <quest id>` brings back a quest's missing villager or target.
