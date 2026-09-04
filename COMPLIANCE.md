# Rules and Plugin Hub Review

Reviewed on 4 September 2026 against:

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
any resulting action. Only an increase in inspected Hitpoints is used as a
health-regeneration observation. Defence-at-full is labelled as an estimate and
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
- uses only RuneLite APIs, dependency injection and JUnit test dependencies from
  the standard template;
- does not use reflection, JNI, native code, subprocesses, executable downloads
  or dynamically loaded external code;
- performs no network requests and contains no third-party data client;
- does not transmit account names, player names, combat results or learned
  timings;
- does not click, cast, attack, move the mouse, inject keyboard input or automate
  any game action;
- adds one `MenuAction.RUNELITE` entry for manual selection, which runs only in
  the client and does not send an action to the game server;
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

Before submitting, keep the description and feature set generic and non-boss.
Do not extend the recorded spawn-location display into attack prediction,
prayer guidance, safe/unsafe standing tiles or boss-mechanic timing. If
RuneLite's reviewers consider health regeneration a boss mechanic for a
particular encounter, that use should be excluded or the plugin adjusted as
they request.
