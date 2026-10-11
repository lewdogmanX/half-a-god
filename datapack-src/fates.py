"""What became of them: after the epilogue, a page for every person you judged, written from what you actually did.
Each person lists (trait, kind, text); the first trait the player has wins, and people you never met are left out.
kind: mercy / blade / other (the colour of the seal beside their name)."""

F = []


def fate(name, *options):
    F.append({"name": name, "options": [{"trait": t, "kind": k, "text": x} for t, k, x in options]})


fate("Reeve Halden",
     ("hag_halden_killed_by_the_hand", "blade", "The Gilded Hand came for Ashford's reeve in the end, and you let them. Ashford still lights a candle for him every winter. Nobody says your name when they do."),
     ("hag_halden_spared_by_the_hand", "mercy", "The Hand meant to make an example of him. You made them think better of it. Halden judged Ashford for twenty more years, fairly, and forgave slowly, as he always had."),
     ("hag_rep_saint", "mercy", "Halden lived to see Ashford whole again. He kept a chair by the hall fire that nobody else was allowed to sit in. It was yours."),
     ("hag_rep_good", "mercy", "Halden rebuilt Ashford one roof at a time. He never thanked you in words. He didn't need to."),
     ("hag_rep_waver", "other", "Halden never quite decided what you were. He kept your letters, though. All of them."),
     ("hag_rep_fallen", "other", "Halden barred his door the last time you came to Ashford. He watched you leave through the shutters, and did not sleep that night."),
     ("hag_rep_wicked", "blade", "Halden paid the Gilded Hand's tithe every month until he died, and every month he cursed the day he asked you for help."))
fate("Tomas, Halden's son",
     ("hag_tomas_home", "mercy", "You sent him home. He became Ashford's mason, and then its reeve. He still can't walk past a quarry without his hands shaking."),
     ("hag_tomas_guard", "other", "He held the portal while the others ran. He came home a soldier, which was not what his father wanted, but it was what he was."),
     ("hag_tomas_mocked", "blade", "You laughed at him. He went home anyway, and never spoke of you again, not even to his father."))
fate("Wick the Informant",
     ("hag_payoff_wick_kept_his_word", "mercy", "Wick sells bread in your village now. Not patrol times. Bread. Nell visits on feast days and calls him a fool, fondly."),
     ("hag_mercy_x1_the_informant", "mercy", "You let him go. Nobody knows quite where he went, but the raids on your village stopped, and that's an answer of sorts."),
     ("hag_cruelty_x1_the_informant", "blade", "Wick died on the village green. His sister Nell came to look you in the face, and then she went home and told everyone she met what kind of person you are."))
fate("Gorran the Warden",
     ("hag_payoff_gorran_at_the_gate", "mercy", "Gorran mends Ashford's locks and keeps its gate. His brothers live with him. He builds doors that keep people out now, instead of in."),
     ("hag_mercy_x2_the_bastille_warden", "mercy", "You spared the Bastille's warden. He walked south to find his brothers. Whether he did, nobody in Ashford ever heard."),
     ("hag_cruelty_x2_the_bastille_warden", "blade", "Gorran died with the Bastille's keys in his hand. His brothers came looking for the one who did it."))
fate("Ossian the Skull-Trader",
     ("hag_payoff_ossians_gift", "mercy", "Ossian left you gold for gold, and left the skull trade with it. Piglins in the Nether still argue about whether he's mad or wise."),
     ("hag_mercy_x3_the_third_skull", "mercy", "You let the skull-trader live. He was never seen in the bastions again."),
     ("hag_cruelty_x3_the_third_skull", "blade", "Ossian's skull ended up on a Bone Choir altar beside the ones he sold. They sang over it for a week."))
fate("The Pilgrim of the Sun",
     ("hag_payoff_pilgrim_blessing", "mercy", "The pilgrim said his goodbye to the Sun and came down from the clouds. He prays at dawn in your village, for you, whether you like it or not."),
     ("hag_mercy_x4_the_pilgrim_of_the_sun", "mercy", "You let him pray. He stayed in the Aether, tending a dimmer sun, and was content."),
     ("hag_cruelty_x4_the_pilgrim_of_the_sun", "blade", "His sunstone charm sold for a fortune. The phantoms that hunt the high places never forgot who carried it."))
fate("Brann of the Garrison",
     ("hag_payoff_widows_letter", "other", "Brann never came home. You gave his medal to Ilse instead. She hung it over the door, and never once asked how he died."),
     ("hag_payoff_brann_home", "mercy", "Ten years in the ice, and then home. Ilse cried for a week, then made him fix the roof. He keeps the snowy village's gate, warmer this time."),
     ("hag_mercy_x5_the_last_garrison_man", "mercy", "You let the last garrison man go. He walked down out of the snow, toward home."),
     ("hag_cruelty_x5_the_last_garrison_man", "blade", "The snowy village got what it asked for. They didn't feel any better afterwards."))
fate("The Smiling Peddler",
     ("heard_the_word", "other", "You let the peddler walk. He painted golden eyes on chapel doors for years after. Some of them are still there."),
     ("peddler_slain", "blade", "The peddler's golden smile ended in the dust of the market road. Nobody came to claim him."))
fate("Orsk the Tally-Keeper",
     ("orsk_spared", "mercy", "Orsk stood Halden's judgment and spent three years rebuilding what his raiders burned. He kept a tally of every plank."),
     ("orsk_executed", "blade", "You executed Orsk where he knelt. The raiders' tallies were never found."))
fate("Corwin the Refugee",
     ("corwin_spared", "mercy", "You paid for the grain he stole. His sister's children ate all winter. Corwin has never once forgotten it."),
     ("corwin_slain", "blade", "Corwin died for a sack of grain. His sister's children were taken in by the Ashford families. They grew up knowing your name."))
fate("Morrow the Assayer",
     ("took_the_gold", "other", "You took Morrow's gold and the Gilded Hand took you. He weighed coins for Silas until the day he died, smiling."),
     ("morrow_slain", "blade", "You cut the assayer down. The Gilded Hand sent a new one within the week."))
fate("Dunn the Deserter",
     ("hag_mercy_1", "mercy", "Dunn threw down his crossbow at Ashford and never picked one up again. He lived out his days doing what he swore he would: staying."),
     ("hag_cruelty_1", "blade", "You executed the deserter. Other raiders who might have dropped their crossbows heard what happened to the one who did, and kept theirs."))
fate("Tobin the Scavenger",
     ("hag_mercy_2", "mercy", "Tobin's girls ate all winter on your coin. He sells rabbits now. Rabbits don't have skulls worth anything."),
     ("hag_cruelty_2", "blade", "You took the skull from Tobin. His girls went hungry that winter. One of them didn't see the spring."))
fate("Sister Ysolde",
     ("hag_mercy_3", "mercy", "Ysolde scrubbed the first golden eye off the chapel door with her own hands. It took a week. She has been forgiving people ever since."),
     ("hag_cruelty_3", "blade", "Sister Ysolde was punished for a door she painted forty years ago. The eye on it was never scrubbed off."))
fate("Brother Hale",
     ("hag_mercy_4", "mercy", "Hale taught you the Old Tongue, and when it was over, he taught it to the Librarian. He is translating the cult's books, so nobody has to fear them again."),
     ("hag_cruelty_4", "blade", "You ended the scholar. Whatever else the Old Tongue said, nobody will ever know."))
fate("Saltjaw Mott",
     ("hag_mercy_5", "mercy", "Mott works the harbour nets, in chains for the first year and then without them. He lights the true lights on the rocks now. Every night."),
     ("hag_cruelty_5", "blade", "You drowned the wrecker. The sea took him the way it took his thirty ships."))
fate("Ansel the Blood-Drinker",
     ("hag_mercy_6", "mercy", "Ansel shook through one night and then many more. One morning he asked for water, just water, and kept asking. He has not touched the blood since."),
     ("hag_cruelty_6", "blade", "You put Ansel down. The blood wells dried up eventually. Nobody remembers him except the people he hurt."))
fate("Garret of Ashford",
     ("hag_mercy_7", "mercy", "Garret has rebuilt more houses than the fourteen he emptied. Halden checks every one. Nobody speaks to him, and he builds anyway."),
     ("hag_cruelty_7", "blade", "You judged Garret yourself. Halden never quite forgave you for taking it out of his hands."))
fate("Brother Vael",
     ("vael_slain", "blade", "You killed the cult's steward in the Nether. The gold kept flowing without him. It always does."),
     ("hag_payoff_vael_spy", "other", "Vael spied for you inside the cult. His last letter reached you. He didn't."),
     ("hag_vael_trial", "mercy", "Vael stood trial before Halden and was sent to rebuild the Bastille's cells into houses. He counts nails now, not skulls."),
     ("hag_vael_free", "mercy", "You let Vael go free. He was last seen walking east, counting his steps."),
     ("hag_vael_branded", "blade", "You branded Vael so the cult would know. They found him within the month."))
fate("The Midwife",
     ("midwife_slain", "blade", "The Midwife died beside her blood pits. The things in them never spoke, and neither did she."),
     ("hag_payoff_midwife_healed", "mercy", "The cleric healed what could be healed. Her daughter came to find you, to say so. The Midwife counts the village's children every morning. All of them breathe."),
     ("hag_midwife_cleric", "mercy", "You sent her to the cleric. She sweeps the chapel and counts the village children every morning. It's more than she thinks she deserves."),
     ("hag_midwife_exiled", "other", "You exiled her from the valley. She walked into the desert and never came back."),
     ("hag_midwife_watch", "other", "The Watch took her. She answered every question they asked, for a year, and then they let her go."),
     ("hag_midwife_kept_pit", "blade", "You let her keep one pit, for research. It is still there. It is not empty."))
fate("Pell",
     ("gt_getting_out", "mercy", "Pell grows wheat now. Just wheat. The Fixer came by once, saw your name on the gate, and never came back."),
     ("gt_silenced_the_witness", "blade", "Pell never testified. Nobody in the valley ever asked where he went."))
fate("Don Vasco",
     ("gt_vasco_killed", "blade", "Don Vasco died in his own Labyrinth. The valley took a year to stop flinching at footsteps."),
     ("hag_vasco_public", "mercy", "Don Vasco stood trial in front of the whole valley. Every grower he'd squeezed came to watch. None of them looked away."),
     ("hag_vasco_quiet", "other", "The Watch dealt with Don Vasco quietly. Some say he's in a cell. Some say he's in the river."),
     ("hag_vasco_testify", "mercy", "You testified against Don Vasco yourself. The Labyrinth is a farm now."),
     ("hag_vasco_ashford", "other", "Don Vasco paid for Ashford's rebuilding, every coin of his half. He never liked it. Ashford never knew."),
     ("hag_vasco_betrayal", "blade", "You shook on it, and then you betrayed him. He saw it coming. He just didn't see it coming from you."),
     ("hag_vasco_greedy", "other", "You took the bigger half. Vasco smiled and paid, and hated you every day until he died."),
     ("hag_vasco_even", "other", "You split the valley down the middle with Don Vasco. A third of it still goes to the Chariot."),
     ("gt_war_won", "blade", "You broke Don Vasco's cartel by force. The valley breathes again, under a new kind of fear."))
fate("The Librarian",
     ("hag_lib_study", "mercy", "The Librarian found what the cult missed in the Warden Journal, as she promised. She wrote it down for everyone, so nobody would ever have to fear the Old Tongue again."),
     ("hag_lib_burn", "other", "She watched the Warden Journal burn and said nothing for a long time. She never studied the Old Tongue again."),
     ("hag_lib_sold", "blade", "You sold the Warden Journal to the Gilded Hand. The Librarian locked her doors, and never opened them to you again."),
     ("hag_lib_kept", "other", "You kept the Warden Journal yourself. The Librarian still asks after it, every time you pass."))
fate("The Last Faithful",
     ("hag_faithful_sent_home", "mercy", "The last of the Faithful went home. Some of them had families who had stopped waiting. They started again."),
     ("hag_faithful_ended", "blade", "You ended the last of the Faithful. The cult is gone. So are they."))
fate("Maraal",
     ("maraal_killed_by_hand", "blade", "The Gilded Hand caught up with Maraal at last, and you let them. Nobody writes letters to strangers any more."),
     ("maraal_left", "other", "Maraal left. Some say she is choosing another champion, somewhere far away. Some say she finally rested."),
     ("hag_maraal_start_again", "mercy", "You forgave her and asked for nothing. Maraal lives in your village now, as Old Mara, and writes letters to nobody at all."),
     ("hag_maraal_no_trust", "mercy", "You forgave her, but you never trusted her. She understood. She still writes, once a year, and always signs her real name."),
     ("hag_maraal_everything", "mercy", "She told you everything. Every champion, every failure, all the way back. It took all winter. You wrote it down."),
     ("hag_maraal_never", "blade", "You told her never to write again. She never did."),
     ("hag_maraal_one_day", "other", "'Maybe one day,' you said. She is still waiting for that day. She is very good at waiting."),
     ("hag_maraal_silence", "other", "You walked away without a word. Maraal watched you go, the way she watched the first champion go."))


def build():
    return {"fates": F}
