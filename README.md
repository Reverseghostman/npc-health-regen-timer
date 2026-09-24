# NPC Health Regen Timer

Tracks health regeneration and respawn timing for a selected NPC.

## Usage

1. Hold **Shift**, right-click an NPC and select **Select Regen Timer**.
2. Damage the NPC and keep its health bar visible.
3. The first visible heal starts the timer. Later heals refine the interval.

Hold **Shift** and right-click the currently tracked NPC again to select
**Clear Regen Timer** and stop tracking it without waiting for it to despawn.

The default regeneration interval is 100 game ticks (60 seconds). Learned
regeneration and respawn times are saved locally for each NPC type.

If a 1 HP heal does not change the health bar, set the **Mark regeneration now**
hotkey and press it when the heal occurs.

## Monster Inspect and Monster Examine

The plugin can read Hitpoints and Defence from the result panel after you
manually cast Monster Inspect or Monster Examine on the selected NPC. Repeated
readings can calibrate the timer without keeping the NPC in combat.

Close the panel and cast again to obtain a fresh reading; leaving it open does
not provide live HP updates. Pending results are read on client ticks as soon
as the visible panel becomes available. The possible sample time spans the cast
click through the visible response, rather than treating either as the heal tick.

Both overlays show an approximate window (for example **Regen: ~11-41t**) from
the first observed increase onward. Fresh readings, including unchanged HP when
known to be below maximum, refine the possible phases of the active regen cycle.
The tracker combines cumulative HP changes with successive readings, including
multiple heals between casts. Damage invalidates the HP comparison, and full HP
does not count as evidence that regeneration stopped. This model assumes one HP
per cycle and cannot identify other healing or damage that the client misses.

Casts roughly a minute apart can progressively narrow the window as their timing
varies. Repeatedly sampling at the identical point in each cycle may add no new
information; the display then retains its uncertainty. Broad observations do not
save their midpoint as a learned rate. The window remains an estimate based on
the active interval, not a guarantee of exact server timing.

To replace a suspect saved rate, hold **Shift**, right-click the selected NPC and
choose **Recalibrate Regen Timer** (or use **Reset timer**). This clears its saved
regen rate and phase, restores **Default regen interval**, and preserves its
respawn measurement. Continue inspecting periodically to refine the countdown;
a stale panel cannot establish an exact heal tick.

## Display options

- regeneration and respawn countdowns
- selected NPC and respawn-tile highlights
- current and maximum Hitpoints
- inspected Hitpoints and Defence from the latest result (shown by default)
- estimated time until full health
