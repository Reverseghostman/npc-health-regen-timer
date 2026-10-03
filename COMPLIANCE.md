# Rules and Plugin Hub Review

Reviewed on 22 September 2026 against:

- [Jagex Third Party Client Guidelines](https://secure.runescape.com/m=news/third-party-client-guidelines?oldschool=1)
- [RuneLite Plugin Hub Review](https://github.com/runelite/runelite/wiki/Plugin-Hub-Review)
- [RuneLite Rejected or Rolled Back Features](https://github.com/runelite/runelite/wiki/Rejected-or-Rolled-Back-Features)
- [RuneLite Plugin Hub submission guide](https://github.com/runelite/plugin-hub)

## Intended scope

NPC Health Regen Timer is a passive timing overlay for manually selected,
ordinary non-boss NPCs. It observes client-visible NPC health ratios, game ticks,
deaths and spawns. It stores learned regeneration and respawn timings locally in
RuneLite's configuration, keyed by the selected NPC's ID.

When the player manually casts Monster Inspect or Monster Examine on the
selected NPC, the plugin may also read the Hitpoints and Defence values already
displayed by the game. It does not cast the spell, select its target or automate
any resulting action. Increases in inspected Hitpoints or Defence supply
restoration observations. Fresh unchanged HP readings below known maximum HP
also constrain the possible regeneration phase. Cast-to-result timing remains
an uncertainty range. Defence-at-full is labelled as an estimate and
does not trigger or recommend a combat action.

The optional scene display highlights only the NPC manually selected with the
client-side menu entry. After that NPC dies, it can outline the recorded spawn
tile and display the passive respawn countdown already shown in the panel. It
does not recommend a tile for the player to stand on, identify a safe tile,
indicate a boss mechanic or send a movement action.

It is not intended to indicate boss mechanics. Jagex's guidelines prohibit
features that indicate when a boss mechanic may start or end, and RuneLite does
not currently consider new high-end PvM boss plugins.

## Technical review

The source code:

- is Java 11 and follows RuneLite's example-plugin project structure;
- uses RuneLite APIs and dependency injection at runtime, with JUnit and Mockito
  for automated tests;
- does not use reflection in production code, JNI, native code, subprocesses,
  executable downloads or dynamically loaded external code;
- makes no network requests of its own and contains no third-party data client.
  When the player has joined a RuneLite party, it sends the selected NPC's
  world, NPC ID, NPC index, regen window (as tick offsets, with whether it was
  carried through time the NPC was out of view) and venom-ring timing through
  RuneLite's own party service, and uses the same data from party members
  tracking the same NPC. Both directions can be turned off in the Party
  settings. Party membership and member names are handled by RuneLite;
- does not transmit account names, player names, combat results or saved
  learned timings;
- does not click, cast, attack, move the mouse, inject keyboard input or automate
  any game action. The venom ring only displays a countdown after the player's
  own manual hit and dynamite use; it reads the worn weapon and helm to tell
  whether that hit envenoms and to show the player's venom chance, which is
  fixed data from the OSRS Wiki and needs no network request;
- in Automatic overlay placement, reads the display names and enabled state of
  the other installed plugins through RuneLite's `PluginManager`, only to keep
  its overhead text clear of Poison Dynamite, Poisoned NPCs and Venom Timer. It
  reads no other data from them, changes nothing in them and does not consume
  events they receive. The other placements do not look at other plugins;
- adds `MenuAction.RUNELITE` entries for manual selection, clearing and
  recalibration, which run only in the client and do not send actions to the
  game server;
- does not remove, reorder or alter the game's Attack, Cast or PvP menu entries;
  and
- draws only informational panel, NPC, overhead-text and respawn-location
  overlays from client-visible observations and local RuneLite data.

## Review conclusion

The implementation avoids the specifically prohibited security and menu-action
behaviours and is designed to comply when used for its stated ordinary-NPC
purpose. This is not a guarantee of acceptance or of compliance in every future
use: RuneLite states that Plugin Hub review is best-effort, rules can be
subjective or change, and every initial submission and update is reviewed.

## Re-review of the later features (29 September 2026)

Party sharing, the venom ring, the venom chance row and overlay placement were
added after the review above. They were re-checked on 29 September 2026 against
the full text of the [Jagex guidelines](https://secure.runescape.com/m=news/third-party-client-guidelines?oldschool=1)
(the page is dated 1 June 2022), RuneLite's Plugin Hub Review and Rejected or
Rolled Back Features wiki pages, and the plugin-hub README.

- **In-code rules.** Nothing in `src/main` uses reflection, JNI, subprocesses or
  runtime code loading. The Plugin Hub jar contains only `src/main`; the tests do
  use reflection to reach private state, but are not packaged. The hub's own
  packager, run locally under Java 11 as its CI does, builds the plugin.
- **Party sharing.** It sends the NPC's world, ID and index, and the regen window
  as tick offsets with two flags (rate learned, timer unconfirmed), through
  RuneLite's party service. No player names, locations or gear, so it is not
  crowdsourcing player data. Received windows are checked before use: the rate
  must be within the settings' range and the window ordered and narrower than one
  cycle, which is all this client ever sends.
- **Venom ring, venom chance and dynamite labels.** The Jagex prohibitions are
  about boss fights (next-attack prediction, projectile or stand-here indicators,
  prayer switching, attack counters, boss-mechanic timing) plus freeze and
  flinch timing. None names poison, venom, dynamite, health regeneration or
  respawn timing, and RuneLite's rejected list does not either. The hub already
  lists plugins in the same area (poison-dynamite, poisoned-npcs, venom-timer and
  venomed-npc-tracker), and version 0.9.7, which already had the venom ring, party
  sharing, the venom chance row and overlay placement, was merged into the hub on
  29 September 2026.
- **Overlay placement.** The Automatic setting reads other plugins' names and
  whether they are enabled through `PluginManager`. That is not reflection and
  changes nothing in them. It is still disclosed above.
- **Menu entries.** Select, Clear and Recalibrate use `MenuAction.RUNELITE`, which
  is handled in the client only. The guidelines prohibit new entries that send
  actions to the server.

What this does not settle: RuneLite says its review is best-effort and the rules
can be subjective, and being merged is not a guarantee about Jagex's view. The
venom ring's Wait, Send and Too late labels are a timing prompt for the player's
own dynamite, not only a passive display, so a reviewer or Jagex could still
object. If they do, reduce the labels to a passive countdown to the proc window.
The plugin works on whichever NPC the player selects and does not block bosses;
the intended use is ordinary NPCs, and no boss-specific behaviour should be added.

Version 0.9.8 added one true/false field to the shared window (whether it was
carried through time out of view) and version 0.9.9 validates received windows.
Neither adds a new kind of data, request or on-screen prompt.

Before submitting, keep the description and feature set generic and non-boss.
Do not extend the recorded spawn-location display into attack prediction,
prayer guidance, safe/unsafe standing tiles or boss-mechanic timing. If
RuneLite's reviewers consider health regeneration a boss mechanic for a
particular encounter, that use should be excluded or the plugin adjusted as
they request.

## Version 0.9.10 data and behavior changes

Venom messages now include their send time in milliseconds, solely to compensate
for delivery delay and reject out-of-order updates. Active locally observed venom
is refreshed at most once per five game ticks, plus on a party join. Recipients do
not relay it. The existing party member ID identifies which member may refresh or
cancel a received ring. No external network endpoint, player location, gear data,
automated game action or new combat prompt is added.

Damage also invalidates inspected current-HP and Defence-at-full estimates until a
fresh inspection reading arrives. This changes the validity of displayed estimates,
not the inspection spell or any game action.
