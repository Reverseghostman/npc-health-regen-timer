# NPC Health Regen Timer

Tracks health regeneration and respawn timing for a selected NPC.

## Usage

1. Hold **Shift**, right-click an NPC and select **Select Regen Timer**.
2. Damage the NPC and keep its health bar visible.
3. The first visible heal starts the timer. Later heals refine the interval.

The default regeneration interval is 100 game ticks (60 seconds). Learned
regeneration and respawn times are saved locally for each NPC type.

If a 1 HP heal does not change the health bar, set the **Mark regeneration now**
hotkey and press it when the heal occurs.

## Monster Inspect and Monster Examine

The plugin can read Hitpoints and Defence from the result panel after you
manually cast Monster Inspect or Monster Examine on the selected NPC. Repeated
readings can calibrate the timer without keeping the NPC in combat.

## Display options

- regeneration and respawn countdowns
- selected NPC and respawn-tile highlights
- current and maximum Hitpoints
- inspected Defence
- estimated time until full health

RuneLite does not always provide an NPC's maximum Hitpoints. Health recovery
estimates remain hidden when that information is unavailable.

This plugin only reads client-visible information and draws overlays. It does
not click, attack, cast spells or send actions to the game server. It is intended
for ordinary NPCs, not boss-mechanic guidance.

## Development

Run `./gradlew test jar` to test and build the plugin. Run `./gradlew run` to
open the RuneLite development client.

See [COMPLIANCE.md](COMPLIANCE.md) for the rules review.
