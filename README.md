# NPC Health Regen Timer

A RuneLite overlay that learns an NPC's natural health regeneration phase,
regeneration interval and respawn time, then remembers the learned rates for
each NPC type.

## How it works

1. Hold Shift, right-click the NPC and choose **Select Regen Timer**. Normal
   clicks and attacks do not change the selected NPC.
2. Set **Default regen interval** to the expected cycle. The normal value is 100
   game ticks (60 seconds). This is only a fallback until the plugin learns the
   NPC's actual interval.
3. Attack the NPC once, then keep its health bar visible by splashing it. The
   plugin checks the selected NPC's visible health ratio every game tick and
   also when a hitsplat is applied.
4. When the visible health ratio rises, the overlay immediately re-anchors the
   prediction to the observed tick. This corrects drift after a respawn, server
   delay or an inaccurate previous prediction.
5. The first observed heal establishes the phase. A second observed heal gives
   an initial interval window. If the configured or previously learned whole
   interval fits that window, it is retained; for example, delayed observations
   cannot turn the standard 100-tick rate into 97-103 ticks.
6. Further health-bar and inspection observations are combined. An overlapping,
   more precise observation narrows the same heal window instead of being counted
   as another heal.
7. Learned regeneration and respawn rates are saved locally by NPC type and are
   restored after RuneLite restarts.

If a 1 HP heal is too small to change RuneLite's rounded health ratio, bind
**Mark regeneration now** and press it when Monster Examine or another manual
observation confirms the heal.

## Monster Inspect and Monster Examine

You can calibrate the timer without remaining in combat:

1. Hold Shift, right-click the NPC and choose **Select Regen Timer**.
2. Cast **Monster Inspect** or **Monster Examine** on that same NPC.
3. Cast the spell again periodically. The plugin reads the Hitpoints value from
   the resulting stats panel after each manually cast spell.
4. When a later reading has more Hitpoints than the previous reading, the timer
   records a regeneration window and immediately re-anchors its prediction.

The overlay can also retain the latest inspected Hitpoints and Defence values.
These rows and the observation-source row can be turned off in the settings.
Defence is shown as a reference for manually checking the result of a Dragon
Warhammer, Elder Maul, Arclight or similar special attack. If enabled, the
overlay estimates the Defence remaining when HP reaches full by assuming both
stats restore by one on each natural regeneration cycle. This is clearly marked
as an estimate because individual NPC mechanics may differ.

Inspection readings and the health bar remain active together. This means you
can inspect an NPC, damage it, leave combat, inspect it again, return to
splashing and still have every later visible heal re-anchor the same timer. If
both sources report the same heal, their observation windows are intersected so
the more precise source tightens the phase without learning a false interval.

## Respawn tracking

The overlay remains visible after the NPC dies. If its respawn time has not been
learned, it counts up from death. When the matching NPC index respawns, the
measured number of ticks is saved for that NPC type. Future kills show a
countdown using that learned value.

The measured dead time is always added to the previously observed regeneration
phase. The next observed heal automatically re-anchors the phase and corrects
timing drift or server delay.

Clicking a banker or another non-combat NPC does not replace the active target.
The target changes only when you deliberately use **Select Regen Timer**.

The selection menu is client-side only and works independently of NPC highlight
plugins, including Better NPC Highlight. Optional scene overlays can highlight
the manually selected NPC, show the regeneration countdown above its head, and
highlight its recorded spawn tile with a tick countdown while it is dead.

## NPC data

RuneLite's NPC data supplies maximum Hitpoints for the selected NPC type. This
is used to reverse the rounded health bar into an honest possible-HP range and
calculate the time until full HP. Monster Inspect/Examine supplies the current
Hitpoints and Defence shown by the game. The plugin retains the highest Defence
it has inspected for that selected NPC type as a local reference.

Respawn timing comes only from the configured fallback or a death-to-respawn
measurement observed locally by the plugin. The plugin does not contact
third-party sites or services.

## Splash weapon delay

Set **Splash weapon delay** to the maximum number of ticks between an NPC heal
and that change becoming visible while splashing. This is normally the splash
weapon's attack speed minus one: use the default value of 3 for a 4-tick weapon,
4 for a 5-tick weapon, or 5 for a 6-tick weapon.

This setting affects only health-bar observations. Monster Inspect/Examine uses
the interval between repeated readings, while **Mark regeneration now** records
the tick on which you manually activate the hotkey. Setting the delay too low
can make the timer overconfident; setting it higher preserves a wider, safer
prediction window.

## Display settings

The default panel is intentionally compact and shows only the target name,
regeneration rate and next-regeneration countdown once calibrated. Seconds are
shown alongside ticks. The regeneration countdown above the selected NPC is
also enabled by default and can be turned off. The following additional
information and scene displays can be enabled independently:

- learned/default respawn time
- running game tick counter
- health-bar or Monster Inspect/Examine observation source
- latest inspected Hitpoints and Defence values
- maximum HP and highest inspected Defence
- current HP (exact from inspection, otherwise a health-bar range)
- estimated time until full HP in minutes and seconds
- estimated Defence when HP reaches full
- selected-NPC scene highlight
- respawn-tile highlight and countdown

## Important limitations

- RuneLite receives a scaled health-bar ratio, not an NPC's exact current HP.
- The Defence-at-full estimate assumes Defence and HP each restore by one on the
  same cycle. Repeated Monster Inspect/Examine casts should be used to verify it
  for the NPC being tested.
- Monster Inspect/Examine support depends on the text shown in the game's stats
  panel. A future game-interface change may require the reader to be updated.
- Other healing mechanics can look like natural regeneration and may recalibrate
  the timer.
- During four-tick splashing, a health change may become visible up to three
  ticks after the server-side heal. The plugin preserves that uncertainty for
  the phase countdown while normalising 97-103-tick observations to the standard
  configured 100-tick interval.
- The plugin reacts when RuneLite exposes a health-ratio or inspection change.
  This is the earliest client-visible observation, but spell travel, interface
  updates and unavailable health bars can leave a window rather than one proven
  server-side tick.
- Respawn measurements can vary with world conditions. The plugin updates its
  stored measurement when it observes that NPC again.
- One heal establishes phase only. At least two visible heals are required to
  calculate a regeneration interval automatically.
- This is a passive display only. It does not click, cast, attack or send input.
- Do not use it as a boss-mechanic helper; RuneLite's Plugin Hub restricts new
  boss timing and prediction features.

## Rules and Plugin Hub scope

This plugin is intended for manually selected, ordinary non-boss NPCs. It reads
RuneLite events and visible NPC health data, stores only local timing settings,
and draws a passive overlay. Monster Inspect and Monster Examine are cast
manually by the player; the plugin only reads the result. The plugin performs no
network requests, does not automate gameplay, does not send menu actions to the
game server, and does not alter attack options or execute external code.

See [COMPLIANCE.md](COMPLIANCE.md) for the code and rules review. Final Plugin
Hub acceptance remains a decision for RuneLite's reviewers because game-rule
interpretation can change.

## Development test

Run `./gradlew test` for the timer logic tests. Run `./gradlew run` to open the
RuneLite development client for an in-game test.
