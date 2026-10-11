"""Tables the Half a God mod reads (data/half_a_god/hag_mod/*.json), the operator story index
(assets/half_a_god/story/index.json) and the mod's English language file."""
import json, os, re
from lib import QUESTS, EXTRA, qid
import base_quests, dialogue

ACTS = [  # trait that opens the act in the Chronicle, heading
    ("hag_the_first_letter_done", "Prologue: The Bell That Won't Ring"),
    ("hag_a1_ashford_burns_done", "Act I: Ashford Burns"),
    ("hag_w1_the_skull_market_done", "Act II: The Withered Crown"),
    ("hag_h1_a_light_above_the_pit_done", "Act III: Hostile Paradise"),
    ("hag_e1_the_librarians_request_done", "Act IV: The Edge of the World"),
    ("hag_s1_cold_hearths_done", "Act V: The Seven Wardens"),
    ("hag_v1_blood_in_the_wells_done", "Act VI: The Sons of Sins"),
    ("hag_m1_follow_the_crimson_bee_done", "Act VII: The Sacred City"),
    ("hag_z1_caution_done", "The Reunion"),
    ("hag_et1_whispers_of_a_new_god_done", "After: The Empty Throne"),
    ("hag_ha1_the_first_command_done", "After: The Hand of Azazel"),
]

# ------------------------------------------------------------------ reputation
REP = {
    "prefixes": {"hag_mercy_": 5, "hag_cruelty_": -6},
    "traits": {
        "heard_the_word": 2, "peddler_slain": -1, "corwin_spared": 6, "vael_spared": 4, "vael_slain": -2,
        "midwife_spared": 3, "midwife_slain": -1, "maraal_forgiven": 5, "maraal_rejected": -2,
        "maraal_killed_by_hand": -12, "maraal_left": -4, "hag_sworn": -25, "hag_good_ending": 12,
        "gt_took_the_offer": -8, "gt_refused_the_offer": 4, "gt_getting_out": 10, "gt_silenced_the_witness": -15,
        "gt_saved_a_buyer": 3, "gt_kingpin": -10, "gt_partners": -4, "gt_war_won": -2, "gt_fields_burned": 4,
        "gt_got_out": 8, "gt_brought_medicine": 6, "gt_vasco_arrested": 5, "gt_vasco_killed": -2, "gt_cartel_broken": 6,
        "hag_faithful_sent_home": 6, "hag_faithful_ended": -6, "hag_halden_spared_by_the_hand": 3,
        "hag_halden_killed_by_the_hand": -20, "hag_lantern_road_lit": 6, "hag_silas_partner": -8,
        "hag_payoff_wick_kept_his_word": 2, "hag_payoff_gorran_at_the_gate": 2, "hag_payoff_brann_home": 3,
        "hag_payoff_pilgrim_blessing": 2, "hag_payoff_midwife_healed": 3, "hag_payoff_tomas_safe": 2,
        "hag_first_champion_found": 3, "hag_a9_open_the_cells_done": 4, "hag_a3_roof_and_hearth_done": 3,
        "hag_a2_what_was_left_done": 3, "hag_w7_a_light_they_cannot_dim_done": 3, "hag_g2_ashford_rebuilt_done": 5,
    },
    "unlocks": [
        {"at": 60, "trait": "hag_unlock_saint", "courier": "half_a_god:unlock_saint"},
        {"at": -60, "below": True, "trait": "hag_unlock_wicked", "courier": "half_a_god:unlock_wicked"},
    ],
}

# ------------------------------------------------------------------ choices with three or four answers
def opt(label, trait, rep, line=None, chronicle=None, courier=None, hint=None):
    o = {"label": label, "trait": trait, "rep": rep}
    for k, v in (("line", line), ("chronicle", chronicle), ("courier", courier), ("hint", hint)):
        if v:
            o[k] = v
    return o


CHOICES = [
    {"id": "librarians_bargain", "trigger": "hag_e4_the_warden_journal_done", "speaker": "The Librarian", "title": "The Librarian's Bargain",
     "text": "The librarian's hands are shaking. 'The Warden Journal. You have it. Thirty years I've studied the Old Tongue for a book "
             "like this. Let me keep it. Or burn it, if you think nobody should read it. Just don't let the cult have it.'",
     "options": [
         opt("Give it to her to study", "hag_lib_study", 2, "'Thank you. I'll find what they missed. I promise.'",
             "You trusted the librarian with the Warden Journal.", "half_a_god:y1_lib_study", "She'll write when she finds something."),
         opt("Burn it. Nobody should read it.", "hag_lib_burn", 0, "She watches it burn and says nothing for a long time.",
             "You burned the Warden Journal."),
         opt("Sell it to the Gilded Hand", "hag_lib_sold", -8, "'You... sold it?' She doesn't look at you again.",
             "You sold the Warden Journal to the Gilded Hand.", "half_a_god:y2_lib_sold", "Silas pays well. Silas also talks."),
         opt("Keep it yourself", "hag_lib_kept", -1, "'Then may it do you more good than it did them.'",
             "You kept the Warden Journal for yourself."),
     ]},
    {"id": "one_who_speaks", "trigger": "hag_v2_the_sinful_church_done", "speaker": "A voice in the blood", "title": "The One Who Speaks",
     "text": "As the last Devourer dies, the blood pit bubbles and speaks with the voice of an Ashford man you never saved. "
             "'It's warm down here. They said you'd come. Did you come for me?'",
     "options": [
         opt("'I came for all of you. Rest now.'", "hag_speaks_kind", 3, "The bubbling stops. It sounds, almost, like a sigh.",
             "You spoke kindly to the voice in the blood."),
         opt("Say nothing", "hag_speaks_silent", 0, None, "You said nothing to the voice in the blood."),
         opt("Ask it for the names of the dead", "hag_speaks_names", 1, "It gives you eleven names. You write them all down.",
             "You wrote down the names of eleven Ashford men the pits took."),
         opt("Promise it more blood", "hag_speaks_cruel", -8, "The pit laughs with a dead man's voice.",
             "You promised the blood pit more blood."),
     ]},
    {"id": "haldens_son", "trigger": "hag_m3_hold_the_quarry_gate_done", "speaker": "Tomas, Halden's son", "title": "Halden's Son",
     "text": "Tomas, Halden's son, followed the freed men to the quarry gate. 'I'm coming into the Mansion with you. My father lost "
             "Ashford. I won't lose this.'",
     "options": [
         opt("Send him home to his father", "hag_tomas_home", 3, "'You sound like him.' He goes. Slowly. Furious.",
             "You sent Tomas home to his father.", "half_a_god:y3_tomas_home", "Halden will hear about it."),
         opt("Let him guard the portal", "hag_tomas_guard", 1, "'The portal. Fine. Nothing gets through.'",
             "You let Tomas guard the portal behind you."),
         opt("Laugh him off", "hag_tomas_mocked", -3, "He walks away without a word. You've made an enemy, maybe.",
             "You laughed at Halden's son."),
     ]},
    {"id": "maraals_offer", "trigger": "hag_m6_the_instruction_done", "requires": ["maraal_forgiven"], "speaker": "Maraal",
     "title": "Maraal's Offer",
     "text": "At the door of the Lair, Maraal stops you. 'I can go in with you. I can give you what years I have left, so you won't "
             "fall. Or I can wait here, and pray, which I have not done in a very long time.'",
     "options": [
         opt("'Walk in beside me.'", "hag_maraal_beside", 3, "'Together, then. Like I should have done the first time.'",
             "Maraal walked into the Lair beside you."),
         opt("'Keep your years. You've paid enough.'", "hag_maraal_kept_years", 4, "She cries, very quietly.",
             "You refused Maraal's years. She'd paid enough."),
         opt("'Give me your years.'", "hag_maraal_gave_years", -3, "She does. She looks a hundred years older.",
             "You took Maraal's remaining years to make yourself stronger."),
         opt("'Wait here and pray.'", "hag_maraal_prayed", 1, "'For you. Not for him. Never for him.'",
             "Maraal waited outside the Lair and prayed for you."),
     ]},
    # ---- four answers on top of the datapack's two-way choices
    {"id": "vael_fate", "trigger": "vael_spared", "speaker": "Brother Vael", "title": "What Becomes of Vael",
     "text": "You spared Brother Vael. He's on his knees in the ash, waiting to hear what that means.",
     "options": [
         opt("Send him to Halden for trial", "hag_vael_trial", 3, "'A trial. Yes. I deserve that much.'", "You sent Brother Vael to stand trial."),
         opt("Let him go free", "hag_vael_free", 0, "'I will not forget.'", "You let Brother Vael go free."),
         opt("Make him your spy in the cult", "hag_vael_spy", -1, "'They'll kill me if they find out.' 'Then don't let them.'",
             "You sent Brother Vael back into the cult as your spy.", "half_a_god:y4_vael_spy", "He'll write if he learns something."),
         opt("Brand him so the cult knows what he did", "hag_vael_branded", -5, "He screams, and then he's very quiet.",
             "You branded Brother Vael and sent him back to his cult."),
     ]},
    {"id": "midwife_fate", "trigger": "midwife_spared", "speaker": "The Midwife", "title": "What Becomes of the Midwife",
     "text": "You let the Midwife live. She wipes the blood off her hands, slowly, as if she's never done it before.",
     "options": [
         opt("Send her to the cleric to heal", "hag_midwife_cleric", 3, "'Heal? Me?' She almost laughs.",
             "You sent the Midwife to the cleric to heal.", "half_a_god:y5_midwife_cleric", "She has a daughter."),
         opt("Exile her from the valley", "hag_midwife_exiled", 0, "She goes without looking back.", "You exiled the Midwife."),
         opt("Hand her to the Watch", "hag_midwife_watch", 1, "'Chains, then. Fine. I've worn worse.'", "You handed the Midwife to the Watch."),
         opt("Let her keep one pit, for research", "hag_midwife_kept_pit", -6, "She smiles for the first time.",
             "You let the Midwife keep one blood pit."),
     ]},
    {"id": "maraal_forgiven_words", "trigger": "maraal_forgiven", "speaker": "Maraal", "title": "Forgiveness",
     "text": "'You forgave me. I don't know what to do with that. What do I owe you now?'",
     "options": [
         opt("'Nothing. We start again.'", "hag_maraal_start_again", 3, "'Again. I've never had an again.'", "You told Maraal you'd start again."),
         opt("'I forgive you. I don't trust you.'", "hag_maraal_no_trust", 0, "'Fair. More than fair.'", "You forgave Maraal, but you didn't trust her."),
         opt("'Tell me everything. Every champion.'", "hag_maraal_everything", 1, "She talks until the fire goes out.",
             "Maraal told you about every champion before you."),
     ]},
    {"id": "maraal_rejected_words", "trigger": "maraal_rejected", "speaker": "Maraal", "title": "Rejection",
     "text": "'Then I'll go. Is there anything you want to say to me, before I do?'",
     "options": [
         opt("'Never write to me again.'", "hag_maraal_never", -2, "She nods. She doesn't argue.", "You told Maraal never to write again."),
         opt("'Maybe one day.'", "hag_maraal_one_day", 1, "'One day. I can live on that.'", "You told Maraal: maybe one day."),
         opt("Walk away without a word", "hag_maraal_silence", -1, None, "You walked away from Maraal without a word."),
     ]},
    {"id": "vasco_partners", "trigger": "gt_partners", "speaker": "Don Vasco", "title": "Splitting the Valley",
     "text": "'Partners, then. Now: how do we split the valley?'",
     "options": [
         opt("'Down the middle.'", "hag_vasco_even", 0, "'Fair. I hate fair, but fine.'", "You split the valley evenly with Don Vasco."),
         opt("'I take the bigger half.'", "hag_vasco_greedy", -3, "'Greedy. I like that.'", "You took the bigger half of Blackwater's valley."),
         opt("'Your half pays for Ashford's rebuilding.'", "hag_vasco_ashford", 2, "'Charity? From a grower?' He laughs. He agrees.",
             "You made Blackwater pay for rebuilding Ashford."),
         opt("Shake on it, and plan to betray him", "hag_vasco_betrayal", -2, "You shake his hand. You count his guards.",
             "You shook Don Vasco's hand and planned his betrayal."),
     ]},
    {"id": "vasco_trial", "trigger": "gt_vasco_arrested", "speaker": "Captain of the Watch", "title": "Don Vasco's Trial",
     "text": "'We have him. Now what? A public trial will be messy. Blackwater still has friends.'",
     "options": [
         opt("A public trial. Everyone should see.", "hag_vasco_public", 3, "'Messy, then. Good.'", "You made Don Vasco's trial public."),
         opt("Let the Watch decide quietly", "hag_vasco_quiet", 0, "'Quietly. All right.'", "You let the Watch try Don Vasco quietly."),
         opt("Testify against him yourself", "hag_vasco_testify", 4, "'You'd stand up there? With your name on it?'",
             "You testified against Don Vasco yourself."),
     ]},
]

# ------------------------------------------------------------------ cinematic moments
CINE = [
    ("hag_the_first_letter_done", "Half a God", "Old Mara has found you", "#F2C14E", "minecraft:ui.toast.challenge_complete", 0, 0),
    ("hag_a1_ashford_burns_done", "Ashford Burns", "The men were counted, not killed", "#D2483A", "minecraft:block.bell.use", 20, 0.6),
    ("hag_a9_open_the_cells_done", "Open the Cells", "Some of them are coming home", "#6DCB5B", "minecraft:ui.toast.challenge_complete", 0, 0),
    ("hag_w6_the_withered_crown_done", "The Withered Crown", "One Warden falls. Six remain.", "#8E1B1B", "minecraft:entity.wither.death", 40, 1.2),
    ("hag_h9_extinguished_done", "Extinguished", "The sun is a little dimmer", "#F2C14E", "minecraft:block.beacon.deactivate", 30, 0.8),
    ("hag_e5_free_the_end_done", "Free the End", "The second Warden is dead", "#B070E0", "minecraft:entity.ender_dragon.death", 40, 1.2),
    ("hag_f3_unbound_done", "Unbound", "Seven Wardens. Seven dead.", "#D2483A", "minecraft:entity.lightning_bolt.thunder", 60, 1.5),
    ("hag_v10_maraal_done", "Maraal", "Mara was never Mara", "#B8B2A6", "minecraft:ambient.cave", 0, 0),
    ("hag_m7_hand_over_your_weapon_done", "The Divine Chariot", "Half a god. For now.", "#F2C14E", "minecraft:entity.wither.spawn", 60, 1.4),
    ("hag_good_ending", "A God Is Dead", "The gold turns back to iron", "#6DCB5B", "minecraft:ui.toast.challenge_complete", 30, 0.8),
    ("hag_sworn", "The Hand of Azazel", "You knelt", "#8E1B1B", "minecraft:entity.elder_guardian.curse", 40, 1.0),
    ("gt_kingpin", "King of the Valley", "Every week, someone pays for it", "#3E8E3E", "minecraft:entity.villager.celebrate", 0, 0),
    ("gt_got_out", "Getting Out", "Your name is on page nine. You're free.", "#6DCB5B", "minecraft:ui.toast.challenge_complete", 0, 0),
    ("gt_cartel_broken", "The Valley Breathes", "Blackwater is finished", "#6DCB5B", "minecraft:ui.toast.challenge_complete", 0, 0),
    ("hag_first_champion_found", "The First Champion", "Someone left fresh flowers", "#B8B2A6", "minecraft:music_disc.11", 0, 0),
    ("hag_empty_throne_done", "Morning", "The bell rings for something good", "#F2C14E", "minecraft:block.bell.use", 0, 0),
    ("hag_hand_of_azazel_done", "At His Right Hand", "The roads are yours", "#8E1B1B", "minecraft:entity.wither.ambient", 30, 0.8),
]
for s in ("saint", "good", "waver", "fallen", "wicked"):
    CINE.append((f"hag_siege_{s}", "The Siege", {"saint": "The Watch held the wall with you", "good": "The wall held",
                                                 "waver": "Halden stood beside you", "fallen": "You held alone",
                                                 "wicked": "Ashford's golems lie broken"}[s],
                 "#D2483A", "minecraft:event.raid.horn", 30, 0.9))

# ------------------------------------------------------------------ companions comment on the story
REACT = {
    "hag_w6_the_withered_crown_done": {"Dunn the Deserter": "The Wither. The actual Wither. The camps used to scare recruits with stories of it.",
                                       "Corwin the Refugee": "My children won't believe this. I barely do.",
                                       "Ansel the Blood-Drinker": "Its blood was black. I'm glad I didn't want it."},
    "hag_e5_free_the_end_done": {"Dunn the Deserter": "A dragon. If my old captain could see me now.",
                                 "Corwin the Refugee": "I farmed turnips a year ago.",
                                 "Ansel the Blood-Drinker": "Everyone's dreaming of a headless king. Me too."},
    "hag_f3_unbound_done": {"Dunn the Deserter": "Seven. Something's wrong. It's too quiet.",
                            "Corwin the Refugee": "Why don't I feel safer?",
                            "Ansel the Blood-Drinker": "I can feel something waking up. Like hunger."},
    "hag_v10_maraal_done": {"Dunn the Deserter": "The old woman lied to you for years. I know what that's like, from the other side.",
                            "Corwin the Refugee": "She sent you letters every week. Liars don't usually bother.",
                            "Ansel the Blood-Drinker": "She's older than she looks. A lot older."},
    "hag_m7_hand_over_your_weapon_done": {"Dunn the Deserter": "Whatever you decide in there, I'm still behind you.",
                                          "Corwin the Refugee": "Come back. That's all. Just come back.",
                                          "Ansel the Blood-Drinker": "He offered you something, didn't he? I know that look."},
    "hag_good_ending": {"Dunn the Deserter": "It's over. I don't know what to do with my hands.",
                        "Corwin the Refugee": "I'm going to plant something. Anything. Just to watch it grow.",
                        "Ansel the Blood-Drinker": "Thank you. For all of it. For sitting with me."},
    "hag_sworn": {"Dunn the Deserter": "You knelt. I ran from men like you once. I'm too tired to run again.",
                  "Corwin the Refugee": "...I'll stay. Someone has to remember who you were.",
                  "Ansel the Blood-Drinker": "Now you're the one with the hunger."},
    "gt_silenced_the_witness": {"Dunn the Deserter": "He was just a boy with a sick friend.",
                                "Corwin the Refugee": "I stole grain to feed my children. What was that for?",
                                "Ansel the Blood-Drinker": "I know that look. I wore it for two years."},
    "gt_got_out": {"Dunn the Deserter": "You walked away from the camp too. Welcome to the club.",
                   "Corwin the Refugee": "Good. Good.",
                   "Ansel the Blood-Drinker": "Getting clean is the hardest thing. You did it."},
}

# ------------------------------------------------------------------ chronicle
CHRON = {
    "hag_the_first_letter_done": "A letter from someone called Old Mara found you, and you read it.",
    "hag_p1_the_bell_done": "You brought back the bell a smiling stranger had bought.",
    "heard_the_word": "You let the Peddler walk away.",
    "peddler_slain": "You drove the Peddler out with steel.",
    "hag_a1_ashford_burns_done": "Ashford burned. You came too late to stop it.",
    "hag_a9_open_the_cells_done": "You opened the Bastille's cells.",
    "vael_spared": "You spared Brother Vael.", "vael_slain": "You killed Brother Vael.",
    "hag_w6_the_withered_crown_done": "You killed the Wither, the first of seven Wardens.",
    "hag_w7_a_light_they_cannot_dim_done": "You lit a beacon over the village.",
    "hag_h9_extinguished_done": "You killed the Sun Spirit and learned the name Maraal.",
    "hag_e5_free_the_end_done": "You killed the Ender Dragon, the second Warden.",
    "hag_f3_unbound_done": "All seven Wardens were dead.",
    "midwife_spared": "You spared the Midwife of the blood pits.", "midwife_slain": "You ended the Midwife and her pits.",
    "maraal_forgiven": "You learned Mara was Maraal, and you forgave her.", "maraal_rejected": "You learned Mara was Maraal, and you sent her away.",
    "hag_m7_hand_over_your_weapon_done": "You faced the Divine Chariot Azazel in his Lair.",
    "hag_good_ending": "You killed the True Azazel. The god is dead.",
    "hag_sworn": "You knelt to the True Azazel and became his Hand.",
    "maraal_killed_by_hand": "On your god's orders, you killed Maraal.", "maraal_left": "You left Maraal to live forever, half-alive.",
    "gt_kingpin": "You became King of the Valley.", "gt_got_out": "You got out of the Blackwater trade and testified.",
    "gt_cartel_broken": "You helped the Watch break the Blackwater Cartel.", "gt_silenced_the_witness": "You silenced Pell to stay in the trade.",
    "gt_took_the_offer": "You took Blackwater's offer.", "gt_refused_the_offer": "You refused Blackwater's offer. Hard.",
    "hag_lantern_road_lit": "You lit the old road end to end.", "hag_silas_partner": "You became Silas's partner.",
    "hag_first_champion_found": "You found the first champion's grave.",
    "hag_empty_throne_done": "You ended the Pretender's reign and left the throne empty.",
    "hag_hand_of_azazel_done": "You brought your god his tithe and took your place at his right hand.",
    "hag_faithful_sent_home": "You sent the last of the Faithful home.", "hag_faithful_ended": "You ended the last of the Faithful.",
    "hag_halden_spared_by_the_hand": "You let Halden live with what you'd become.",
    "hag_halden_killed_by_the_hand": "You killed Reeve Halden on your god's orders.",
}
for s, line in {"saint": "The whole Watch held the wall with you in the siege.", "good": "You held the wall in the siege.",
                "waver": "Only Halden stood with you in the siege.", "fallen": "You fought the siege alone outside the walls.",
                "wicked": "Ashford set its golems on you, and you broke them."}.items():
    CHRON[f"hag_siege_{s}"] = line
for t, line in {"hag_payoff_wick_kept_his_word": "Wick kept his word.", "hag_payoff_met_nell": "Wick's sister came to look you in the face.",
                "hag_payoff_gorran_at_the_gate": "Gorran gave you what the cult had paid him.", "hag_payoff_gorrans_brothers": "Gorran's brothers came for you.",
                "hag_payoff_ossians_gift": "Ossian left you gold for gold.", "hag_payoff_choir_came": "The Bone Choir came for the third skull.",
                "hag_payoff_pilgrim_blessing": "The pilgrim thanked you for letting him say goodbye.",
                "hag_payoff_sun_remembered": "Phantoms hunted you for the sunstone.", "hag_payoff_brann_home": "Brann came home after ten years.",
                "hag_payoff_widows_letter": "You gave Brann's medal to his widow.", "hag_payoff_last_page": "The librarian made you a ward from the last page.",
                "hag_payoff_hand_reads": "Silas paid you a bonus for the Warden Journal.", "hag_payoff_tomas_safe": "Halden thanked you for sending his son home.",
                "hag_payoff_vael_spy": "Vael's last letter reached you. He didn't.", "hag_payoff_midwife_healed": "The Midwife's daughter came to thank you."}.items():
    CHRON[t] = line


def _choice_chronicle():
    for k, (_, q) in QUESTS.items():
        c = q.get("choice")
        if not c:
            continue
        for branch in ("spare", "attack"):
            for t in c.get(branch, {}).get("traits", []):
                CHRON.setdefault(t, f"{q['title']}: {c[branch]['label'][0].lower() + c[branch]['label'][1:]}.")


# ------------------------------------------------------------------ Mara's letters at the end of each act
L = lambda title, *pages: {"title": title, "pages": list(pages)}
LETTERS = [
    ("hag_w0_two_roads_meet_done", {
        "bright": L("You Did Well", "Ashford's men are home, the ones that could be brought home. You did that.",
                    "I've watched a great many people try to be good. Most of them stop when it gets expensive. You haven't.\n\n- M."),
        "grey": L("A Word of Caution", "Ashford's men are home. Some of them talk about you with gratitude. Some don't talk about you at all.",
                  "You're not finished deciding who you are. That's all right. Just decide on purpose.\n\n- M."),
        "dark": L("I Heard", "I heard what you did. I won't write it down.",
                  "You're still the best chance this world has. That frightens me more than the cult does.\n\n- M.")}),
    ("hag_w8_six_remain_done", {
        "bright": L("Six Remain", "The beacon is beautiful. I can see it from my window.", "Six more Wardens. I'm sorry. I'll explain when I can.\n\n- M."),
        "grey": L("Six Remain", "The Wither is dead. Six remain.", "Be careful whom you make enemies of. You'll need friends for the rest.\n\n- M."),
        "dark": L("Six Remain", "The Wither is dead. The villages are afraid of you.", "Fear is a tool. It is a bad one. It breaks in your hand.\n\n- M.")}),
    ("hag_h10_fireproof_done", {
        "bright": L("About Maraal", "You came back from the clouds saying a name.", "I'll tell you about her. Not yet. Soon. I promise.\n\n- M."),
        "grey": L("About Maraal", "You heard a name above the clouds.", "Some names are better left up there. This one won't stay.\n\n- M."),
        "dark": L("About Maraal", "So you know the name now.", "Don't use it against me. I've used it against myself enough.\n\n- M.")}),
    ("hag_e7_the_nether_is_singing_done", {
        "bright": L("The Singing", "You hear it too, don't you? The Nether singing.", "It's singing for him. Not for long, if you have anything to do with it.\n\n- M."),
        "grey": L("The Singing", "The Nether is singing. Two champions died before you.", "I won't let it happen a third time. Help me not let it.\n\n- M."),
        "dark": L("The Singing", "The Nether is singing.", "Sometimes I wonder whether it's singing for him, or for you.\n\n- M.")}),
    ("hag_f3_unbound_done", {
        "bright": L("Seven", "All seven. I've waited a very long time to write that word.", "Rest a night. Then come and find me. I owe you a truth.\n\n- M."),
        "grey": L("Seven", "All seven Wardens are dead.", "Now he is unguarded. And so are we. Rest while you can.\n\n- M."),
        "dark": L("Seven", "All seven Wardens are dead.", "I keep telling myself I chose the right champion. I keep telling myself.\n\n- M.")}),
    ("hag_v8_sinbuster_done", {
        "bright": L("Armour for a God", "You'll stand in front of him soon.", "Whatever he offers you, remember the bell. Remember the grain. Remember Ashford.\n\n- M."),
        "grey": L("Armour for a God", "He will offer you mercy.", "It is not mercy. It is a leash with a velvet handle.\n\n- M."),
        "dark": L("Armour for a God", "He will offer you mercy, and you will want it.", "I am asking you, as someone who once said yes to him: don't.\n\n- M.")}),
    ("hag_m6_the_instruction_done", {
        "bright": L("Before the Door", "Whatever happens past this door, you were worth it.", "Every champion was. You most of all.\n\n- Maraal"),
        "grey": L("Before the Door", "This is the last letter before the door.", "I'm not going to tell you what to do. I've done enough of that.\n\n- Maraal"),
        "dark": L("Before the Door", "Last letter.", "If you kneel, I'll understand. I won't forgive you. But I'll understand.\n\n- Maraal")}),
]


def letters():
    out = []
    for trigger, tones in LETTERS:
        e = {"trigger": trigger, **tones}
        # the last letter only comes if you still have Maraal; earlier ones stop if you rejected her
        e["forbids"] = ["maraal_rejected"]
        out.append(e)
    return out


# ------------------------------------------------------------------ story graph for /halfagod lostmyway
def story_graph():
    quests = {}
    for k, (folder, q) in QUESTS.items():
        fl = q.get("flow", {})
        g = lambda d: [d["quest"]] if d else []
        quests[qid(k)] = {"next": g(fl.get("delayed_next_quest")), "spare": g(fl.get("on_spare", {}).get("delayed_next_quest")),
                          "kill": g(fl.get("on_kill", {}).get("delayed_next_quest")), "book": "quest_book" in q}
    couriers = []
    for rel, c in EXTRA.items():
        if not rel.startswith("courier_deliveries/"):
            continue
        t = c["trigger"]
        if c["delay"]["min_ticks"] >= 30000:
            continue  # a consequence that comes back later on its own; "Lost my way" shouldn't rush it
        if t["type"] == "quest_success":
            couriers.append({"after": t["quest"], "book": c["delivery"]["quest_book"]})
        elif t["type"] == "quest_spared":
            couriers.append({"after": t["quest"], "book": c["delivery"]["quest_book"], "spared": True})
        elif t["type"] == "advancement":
            couriers.append({"book": c["delivery"]["quest_book"]})
    return {"quests": quests, "couriers": couriers}


# ------------------------------------------------------------------ operator story index
GROUPS = [
    ("Prologue", ("the_first_letter", "p"), "main"), ("Act I: Ashford", ("a",), "main"), ("The Arcane Road", ("i",), "arc"),
    ("Act II: The Withered Crown", ("w",), "main"), ("Act III: Hostile Paradise", ("h",), "main"),
    ("Act IV: The Edge of the World", ("e", "ea", "eb"), "main"), ("Act V: The Seven Wardens", ("s", "d", "c", "u", "f"), "main"),
    ("Act V: The Underworld Arc", ("gt", "go", "br", "tm"), "arc"), ("Act VI: The Sons of Sins", ("v",), "main"),
    ("Act VII: The Sacred City", ("m", "z"), "main"), ("The Endings", ("g1", "g2", "b1", "b2", "b3"), "main"),
    ("After the Ending", ("et", "ha"), "main"), ("The Reputation Road", ("r1", "r2", "rc", "rep_end"), "road"),
    ("Choices That Come Back", ("x", "y", "ru"), "road"), ("Lost Pages", ("lp",), "letter"),
    ("The Bumblezone", ("bz",), "arc"), ("Legionary", ("lg",), "arc"), ("Side Quests", ("sq",), "side"),
    ("Secrets", ("sec_",), "arc"),
]


def _group(k):
    best = None
    for name, prefixes, kind in GROUPS:
        for p in prefixes:
            if k == p or (k.startswith(p) and len(k) > len(p) and (len(p) > 1 or k[len(p)].isdigit())):
                if best is None or len(p) > best[0]:
                    best = (len(p), name, kind)
    return (best[1], best[2]) if best else ("Other", "side")


def _pretty(i):
    return i.split(":")[-1].replace("_", " ")


def _goal_text(gl):
    n = gl.get("amount", 1)
    if "kill" in gl: return f"Defeat {n} x {_pretty(gl['kill'])}"
    if "talk" in gl: return f"Talk to {gl.get('name', 'a villager')}"
    if "collect" in gl: return f"Collect {n} x {_pretty(gl['collect'])}"
    if "place" in gl: return f"Place {n} x {_pretty(gl['place'])}"
    if "break" in gl: return f"Break {n} x {_pretty(gl['break'])}"
    if "reach" in gl: return f"Reach a {_pretty(gl['reach'])}"
    if "escort" in gl: return "Escort someone home"
    if "bring" in gl: return f"Bring {n} x {_pretty(gl['bring'])}"
    t = gl.get("type", "")
    return {"read_book": f"Find and read: {gl.get('name', 'a book')}", "wait_time": "Wait for " + gl.get("wait_until", "").replace("_", " "),
            "enter_nether": "Enter the Nether", "leave_nether": "Leave the Nether", "defend_raid": "Defend a raid"}.get(t, t or "Do it")


def story_index():
    title = {qid(k): q["title"] for k, (_, q) in QUESTS.items()}
    leads = {}
    for k, (_, q) in QUESTS.items():
        fl = q.get("flow", {})
        for lab, d in (("", fl.get("delayed_next_quest")), ("if spared: ", fl.get("on_spare", {}).get("delayed_next_quest")),
                       ("if killed: ", fl.get("on_kill", {}).get("delayed_next_quest"))):
            if d:
                leads.setdefault(k, []).append(lab + title.get(d["quest"], d["quest"]))
    for rel, c in EXTRA.items():
        if rel.startswith("courier_deliveries/") and c["trigger"].get("quest"):
            src = c["trigger"]["quest"].split(":hag_")[-1]
            pre = "letter if spared: " if c["trigger"]["type"] == "quest_spared" else "letter: "
            leads.setdefault(src, []).append(pre + title.get(c["delivery"]["quest_book"], _pretty(c["delivery"]["quest_book"])))
    groups = {}
    for k, (_, q) in QUESTS.items():
        g, kind = _group(k)
        req = q.get("requires", {})
        needs = [title.get(r, _pretty(r)) for r in req.get("quests", [])] + [t for t in req.get("traits", []) if not t.startswith("hag_base_")]
        if any(t.startswith("hag_base_") for t in req.get("traits", [])):
            needs.append("this act's questlines from Mebahel's RPG")
        groups.setdefault(g, []).append({"id": qid(k), "title": q["title"], "kind": "letter" if "quest_book" in q and kind == "main" else kind,
                                         "summary": q["summary"], "goals": [_goal_text(gl) for gl in q["goals"]],
                                         "needs": needs, "leads": leads.get(k, [])})
    acts = [{"name": n, "quests": groups[n]} for n, _, _ in GROUPS if n in groups]
    if "Other" in groups:
        acts.append({"name": "Other", "quests": groups["Other"]})
    # Mebahel's own questlines, by act
    for line in base_quests.mod_table():
        qs = []
        for m in line["members"]:
            name = m.split(":")[1]
            q = base_quests.BASE[name][1]
            qs.append({"id": m, "title": base_quests.title_of(name), "kind": "side",
                       "summary": (q.get("summary") if isinstance(q.get("summary"), str) else (q.get("summary") or {}).get("fallback", "")) or "",
                       "goals": [], "needs": [f"unlocks with: {title.get(line['gate'], line['gate'])}"],
                       "leads": [f"the story waits for this line before: {title.get(line['join'], line['join'])}"]})
        acts.append({"name": f"Mebahel's RPG ({line['act']}): {line['title']}", "quests": qs})
    return {"acts": acts}


def write(out_pack, mod_root):
    _choice_chronicle()
    d = os.path.join(out_pack, "data/half_a_god/hag_mod")
    os.makedirs(d, exist_ok=True)
    def dump(name, obj, root=d):
        json.dump(obj, open(os.path.join(root, name), "w", encoding="utf-8"), indent=1, ensure_ascii=False)
    dump("reputation.json", REP)
    dump("choices.json", {"choices": CHOICES})
    dump("cinematics.json", {"moments": [{"trait": t, "title": ti, "subtitle": su, "color": c, "sound": so, "shake": sh, "strength": st}
                                         for t, ti, su, c, so, sh, st in CINE]})
    dump("companions.json", {"names": list(dialogue.COMPANION_NAMES.values()),
                             "profiles": {v: k for k, v in dialogue.COMPANION_NAMES.items()},
                             "reactions": [{"trait": t, "lines": l} for t, l in REACT.items()]})
    dump("chronicle.json", {"traits": CHRON, "acts": dict(ACTS)})
    dump("story_graph.json", story_graph())
    dump("letters.json", {"letters": letters()})
    if mod_root:
        a = os.path.join(mod_root, "src/main/resources/assets/half_a_god")
        os.makedirs(os.path.join(a, "story"), exist_ok=True)
        os.makedirs(os.path.join(a, "lang"), exist_ok=True)
        dump("index.json", story_index(), os.path.join(a, "story"))
        import story_map, codex
        dump("map.json", story_map.build(_goal_text), os.path.join(a, "story"))
        cx = codex.build()
        for e in cx["entries"]:
            assert e["unlock"].split(":hag_")[1] in QUESTS, e["unlock"]
        dump("codex.json", cx, os.path.join(a, "story"))
        lang = dict(dialogue.LANG)
        lang.update({"key.categories.half_a_god": "Half a God"})
        dump("en_us.json", lang, os.path.join(a, "lang"))
    # what became of everyone you judged (the mod picks each person's line from your traits)
    import fates, settlement
    known = settlement.produced_traits()
    for f in fates.F:
        for o in f["options"]:
            assert o["trait"] in known, f"fate of {f['name']}: trait {o['trait']} is never set"
            assert o["kind"] in ("mercy", "blade", "other")
    dump("fates.json", fates.build())
    # Wardens: the day each one falls is remembered for the Codex
    import codex
    dump("moments.json", {"traits": [e["unlock"].replace("mebahelrpgrquest:", "") + "_done" for e in codex.build()["entries"] if e["category"] == "Wardens"]})
    # dreams, the Trial, and the hidden questlines
    import dreams, trial, hidden_lines
    dump("dreams.json", dreams.build())
    dump("trial.json", trial.build())
    dump("secrets.json", hidden_lines.mod_table())
    dump("acts.json", {"acts": [{"trait": t, "name": n} for t, n in ACTS]})
