# Changelog

## 0.9.0

- Add a **Splash weapon delay** setting so health-bar uncertainty can match the
  selected weapon's attack speed instead of assuming a fixed 4-tick weapon.
- Scale interval normalisation and stored-rate correction with the configured
  observation delay.
- Add test coverage for retaining a 100-tick rate with a slower splash weapon.

## 0.8.0

- Remove the OSRS Wiki setting, client, parser and tests completely.
- Use only RuneLite data, Monster Inspect/Examine results, configured fallbacks
  and locally observed timing.
- Remove the pause-while-dead setting and always shift the regeneration phase by
  the NPC's measured dead time.
- Enable the overhead regeneration countdown by default for new installations.

## 0.7.0

- Treat health-bar increases during four-tick splashing as having up to three
  ticks of client-visible delay.
- Keep the configured 100-tick interval when observations fall anywhere from
  97 to 103 ticks, while retaining the honest phase window.
- Correct previously stored 97-103-tick rates to 100 when the configured
  fallback is 100 ticks.
- Stop obtaining or parsing Hitpoints and Defence from the OSRS Wiki; the
  optional Wiki integration now supplies respawn timing only.
- Use RuneLite NPC data and Monster Inspect/Examine readings for HP and Defence
  reference information.
- Make the default panel match the compact Target, Regen rate and Next regen
  layout, with all extra rows and scene displays off by default.
- Keep the death-time respawn countdown visible by default while leaving the
  additional respawn-rate row optional.

## 0.6.0

- Keep the standard configured 100-tick regeneration rate when delayed
  observations also allow 100, instead of learning a false 99-tick rate.
- Continue sampling both the health bar and Monster Inspect/Examine after either
  source is used.
- Merge overlapping observations of the same heal and let the more precise
  source narrow the regeneration phase.
- Migrate previously learned rates within two ticks of the configured fallback
  back to that whole configured interval.
- Remove the NPC ID display setting and overlay row.
- Add optional selected-NPC highlighting, an overhead regeneration countdown,
  and a respawn-tile tick countdown.
- Preserve learned regeneration and respawn rates in RuneLite configuration by
  NPC type across client restarts.

## 0.5.0

- Add independent overlay toggles for base stats, current HP, time until full
  HP, and estimated Defence at full HP.
- Use RuneLite's NPC data to obtain maximum Hitpoints by NPC ID.
- Reverse rounded health bars into possible HP ranges instead of presenting an
  invented exact value.
- Project inspected and health-bar HP forward as predicted regeneration cycles
  pass.
- Add an opt-in OSRS Wiki lookup for maximum HP, base Defence and respawn ticks.
- Require the Wiki page's NPC ID to match the selected NPC before accepting its
  values.
- Prefer locally measured respawn timing over Wiki and default values.
- Add recovery-calculation and Wiki-parser tests.

## 0.4.0

- Read exact Hitpoints observations from manually cast Monster Inspect and
  Monster Examine spells.
- Confirm that the spell result belongs to the manually selected NPC before
  using it.
- Keep the latest inspected Hitpoints and Defence values available in optional
  overlay rows.
- Keep spell observations separate from health-bar observations to prevent one
  heal being counted twice.
- Add an optional observation-source row and spell-specific status message.
- Add parser and exact-hitpoint timer tests.

## 0.3.0

- Require Shift-right-click and **Select Regen Timer** to choose the NPC.
- Check the selected NPC's visible health ratio every game tick and on hitsplats.
- Immediately re-anchor the prediction whenever an unexpected heal is observed.
- Avoid false heal detections when the NPC's health bar temporarily becomes
  unavailable.
- Clarify the ordinary non-boss NPC scope and Plugin Hub compliance notes.
- Improve overlay labels and documentation wording.

## 0.2.0

- Keep the overlay and regeneration countdown visible while the NPC is dead.
- Ignore bankers and other clicked NPCs unless a combat hitsplat identifies them
  as the new target.
- Measure death-to-respawn time and remember it by NPC ID.
- Learn the health regeneration interval from repeated observed heals and
  remember it by NPC ID.
- Re-anchor the regeneration phase whenever a real heal is observed.
- Add optional NPC name, NPC ID, regeneration rate, respawn rate, game tick and
  seconds display rows.
- Add a setting for paused-during-death versus continuous regeneration phase.
