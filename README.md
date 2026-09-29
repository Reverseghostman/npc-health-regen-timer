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

### Across kills

The regen countdown carries over from one life to the next: it pauses while the
NPC is dead and resumes when it respawns. It does not pause for the whole time
from the killing hit to the respawn, though: the NPC keeps counting on a tick or
two around its death and respawn. The plugin learns that small adjustment per
NPC type from the first heal you observe after a respawn. Until then, the regen
window widens by up to two ticks per kill to cover it, and it narrows again once
learned.

The plugin only sees a respawn once the NPC is within 15 tiles of you. If it respawns
while you are farther off (or logged out) and you walk back later, the sighting is late
by an unknown amount. It then uses the NPC's respawn time if it has one, and otherwise
widens the countdown to say so, rather than counting the whole time away as dead time.
It does not learn a respawn time from a late sighting.

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

## Venom ring and poison dynamite

For envenoming an NPC on one account and landing poison dynamite on another
before the venom first damages it:

1. Wear a charged serpentine helm with a toxic blowpipe, trident of the swamp
   or toxic staff of the dead on the venom account. A landed hit on the
   selected NPC, such as a Snare just before its regen, starts a ring over the
   NPC. A hit that lands always shows at least 1; a miss or splash shows 0 and
   cannot envenom, so it starts nothing.
2. The ring counts down to the venom's first damage. Below it:
   - **Wait Nt**: regen has not yet restored the hit's damage
   - **Send Nt**: send the dynamite now; N ticks are left to send it
   - **Too late** / **No window**: it would land after the venom
   - **Venom!** or **No venom** once the result is seen
3. Put both accounts in the same RuneLite party with the NPC selected on each,
   and the dynamite account shows the same ring.

### Venom chance

The info panel's **Venom chance** row shows how likely each landed hit is to
envenom the selected NPC, from the weapon and helm you are wearing. It uses the
[OSRS Wiki](https://oldschool.runescape.wiki/w/Venom) figures for an NPC target:

| Wearing | Chance per landed hit |
| --- | --- |
| Toxic blowpipe, trident of the swamp or toxic staff of the dead, with a charged serpentine helm | 100% (the ring starts) |
| The same weapons without the helm | 25% |
| Noxious halberd | 33%, or 50% with the helm |

Only the 100% setups start a ring, because a ring for a venom that may not
happen would send the dynamite for nothing. The row is green at 100% and orange
below it, which means no ring will start. The helm alone (1/6 with a non-poisoned melee weapon, 1/2 with poisoned
weapons) is not modelled. RuneLite does not know which NPCs are immune to venom,
so the row assumes the NPC is not.

The send window allows for the dynamite's travel time, measured each time you
use Dynamite(p) on the selected NPC (or set **Dynamite delay**; selecting another
NPC or changing that setting discards the measurement). The first
venom damage normally lands 30 ticks after the venom hit. The NPC's poison
timer does not restart on each envenoming, so after a kill that was not a
poison or venom hit, the ring counts only the part of the timer that remains.
The first-hit delay is re-learned from each proc. Logging the venom account out
cancels its ring on the party.

## Working alongside other plugins

Several plugins draw above an NPC's head, at the same spot as this one's regen
countdown: **Poison Dynamite** (its ring) and **Poisoned NPCs** (its timer) at
the same height, and **Venom Timer** (up to four lines, running down through it).
Drawn there together they overlap.

By default the placement is **Automatic**: the countdown and venom ring stay
centred above the NPC's head, and move beside the NPC, clear of all three, while
any of those plugins is enabled. The venom label and ring are stacked above the
countdown in pixels, so they cannot overlap it at any zoom. The info panel no
longer asks to be first in its corner, so other panels keep their order.

Nothing else here interferes with those plugins, or with **Venomed NPC Tracker**,
which only recolours NPC menu entries. This plugin never consumes or alters a
game event, and its **Select / Clear / Recalibrate Regen Timer** entries only
appear next to Examine while Shift is held.

To choose yourself, open **Overlay placement** in the settings: **Above head**,
**Beside NPC (right)** or **Beside NPC (left)**, with **Side distance** for how
far from the NPC's centre it sits. Only Automatic looks at other plugins, and it
reads just their names and whether they are enabled.

## Logging out and losing sight of the NPC

The selected NPC and its timer are kept when you log out and back in on the
same world, and when the NPC walks out of view. The timer carries on by the
time that passed, widened by a tick (plus one per five minutes away), and the
NPC is picked up again when it reappears. Logging into another world, or
moving more than 50 tiles from it while it is out of view, drops it. Deaths and
respawns that happen out of sight cannot be seen, and one of them moves the
regen timing by the time the NPC was dead. So a timer carried on through time
out of sight is treated as unconfirmed until you see a heal yourself, or a party
member who was watching sends a window (see below).

## Sharing timers with a party

Join the same RuneLite party (the core **Party** plugin) as your friends. When
you each select the same NPC on the same world, your regen windows are shared
automatically:

- a member with no timer yet picks up yours straight away, including a learned
  regen rate
- a member with a rougher window is narrowed by yours when the two agree
- your own observations always take priority: a shared window that contradicts
  them is ignored, and your first own heal takes over from a shared one
- a timer you only carried on while the NPC was out of sight, or you were
  logged out, is not an observation. A member who has been watching it replaces
  it, even if they disagree, since a kill you did not see moves the timing. This
  also happens while the NPC is out of your view, so you come back to the right
  timer. A member whose own timer is in that state does not replace yours

Party members count ticks separately, so a shared window is widened by one
tick on each side for delivery timing. The overlay shows **Timer from** with
the member's name while you are using their timer. Updates are only sent when
the window changes, about once per regen cycle when nothing new is seen.

## Settings

The standard options sit at the top of the plugin's settings:

- **Highlight selected NPC**: **Tile** (default, follows the NPC as it walks),
  **True tile** (its server-side tile), **Hull** or **Off**, with a colour
- overhead regen countdown
- respawn tile highlight and countdown while the NPC is dead
- seconds alongside ticks

Everything else is in collapsible sections underneath, closed by default:

- **Overlay details**: which rows the panel shows (NPC name, regen and respawn
  rates, inspected Hitpoints and Defence, current and maximum HP, time until
  full HP, Defence at full HP, observation source, tick counter) and when the
  countdown turns orange
- **Overlay placement**: above the head or beside the NPC (Automatic by
  default), and how far beside it
- **Venom & dynamite**: the venom ring, the venom chance row and the dynamite
  delay
- **Hotkeys**: **Mark regeneration now** and **Reset timer**
- **Party**: share your timer, use party members' timers, and show who shared
  the timer you are using
- **Timing (advanced)**: learning, fallback regen and respawn times, and the
  splash weapon delay
