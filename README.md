# CombatLock

A lightweight Paper plugin that locks players out of configured commands for
a short time after they take part in PvP combat.

## Why?

Without something like this, PvP combat is trivially cheesed with "hit and
run" or "hit and fly" tactics: a player takes a hit or two, then teleports
away with `/tp`, `/home`, `/warp`, or a town/faction `spawn` command, or
simply pops `/fly` on and escapes upwards. CombatLock closes that loophole by
tagging both participants in a PvP hit as being "in combat" and blocking a
configurable list of commands (teleports, warps, homes, spawn commands, etc.)
until the tag naturally expires. It can also run arbitrary console commands
when a player enters or leaves combat, which is how it turns off `/fly` the
moment a fight starts (via an Essentials-style `fly off` command) - so
players can't just fly away either.

## How it works

- Whenever a player takes damage from another player (or their projectile),
  both the attacker and the victim are put "in combat" - the attacker as the
  **aggressor**, the victim as the **defender**.
- Each role has its own configurable duration. Every fresh hit on a player
  resets their timer back to the full duration for their role, so a fight
  that keeps going never lets either side slip away early.
- While in combat, any command matching the configured `blocked-commands`
  list is silently cancelled (before any other plugin sees it) and the
  player is shown a message telling them how much longer they're locked for.
- When a player *first* enters combat, the commands configured under
  `on-combat-start` for their role are run from the console (used by default
  to force `/fly off` on both the aggressor and defender).
- When a player's lock expires naturally (i.e. they weren't hit again before
  the timer ran out), the commands under `on-combat-end` for their role are
  run, and they're shown the `combat-ended` message. If a player disconnects
  while locked, their lock is simply cancelled - no end-of-combat commands
  or messages are fired at an offline player.
- Players with the bypass permission are never locked and never blocked,
  whether they're the aggressor or the defender.

## Commands

| Command | Description | Permission |
| --- | --- | --- |
| `/combatlock reload` | Reloads `config.yml` from disk without restarting the server. | `combatlock.reload` |
| `/combatlock debug` | Toggles debug logging on/off. | `combatlock.reload` |
| `/combatlock debug on` | Turns debug logging on. | `combatlock.reload` |
| `/combatlock debug off` | Turns debug logging off. | `combatlock.reload` |

Debug logging prints a console line whenever a player first enters combat,
whenever a lock ends, and whenever a player is blocked from running a
command while locked - it does **not** log every single hit, so it stays
readable during a prolonged fight. The current on/off state is written back
to `config.yml`'s `debug` key, so it survives restarts.

## Permissions

| Permission | Description | Default |
| --- | --- | --- |
| `combatlock.bypass` | Never gets combat-locked or command-blocked, whether attacking or defending. | `op` |
| `combatlock.reload` | Allows use of `/combatlock reload` and `/combatlock debug`. | `op` |

## Configuration (`config.yml`)

```yaml
debug: false
```
Enables verbose console logging (see [Commands](#commands) above). Usually
left off and toggled temporarily with `/combatlock debug` when diagnosing an
issue.

```yaml
combat:
  aggressor-seconds: 15
  defender-seconds: 10
```
How long (in seconds) a lock lasts for each role, reset on every fresh hit.
Aggressors and defenders can be given different durations - by default the
aggressor is locked a little longer than the defender.

```yaml
bypass-permission: combatlock.bypass
```
The permission node checked to exempt a player from combat-locking entirely.
The actual permission is still registered/described in `plugin.yml`; this
just lets you point CombatLock at a different node if you want to reuse an
existing one.

```yaml
blocked-commands:
  - "pw"
  - "pwarp"
  - "t spawn"
  - "town spawn"
  - "warp"
  - "spawn"
  - "tpa"
  - "tp"
  - "home"
```
Commands blocked while a player is combat-locked. Entries are matched
against exactly what the player typed (minus the leading `/`, lower-cased),
so list every alias you actually use - this is **not** resolved through
Bukkit's command/alias system. Matching is by prefix, so an entry like
`"t spawn"` blocks `/t spawn` and `/t spawn now`, but not `/t spawnprotect`.

```yaml
on-combat-start:
  aggressor:
    - "fly off %player%"
  defender:
    - "fly off %player%"
```
Console commands run the moment a player *freshly* enters combat as the
given role (not re-triggered on every subsequent hit while already locked).
`%player%` is replaced with the player's name. Defaults to forcing `/fly`
off on both participants so they can't just fly out of a fight.

```yaml
on-combat-end:
  aggressor: []
  defender: []
```
Console commands run when a player's lock expires naturally. Empty by
default - add your own commands here if you want something to happen when
combat ends (e.g. re-enabling something you disabled on entry).

```yaml
messages:
  command-blocked: "&cYou can't do that while in combat! (&e%time%s&c left)"
  entered-combat-aggressor: "&cYou are in combat as the &4aggressor&c for %time%s."
  entered-combat-defender: "&cYou are in combat as the &4defender&c for %time%s."
  combat-ended: "&aYou are no longer in combat."
  reload-success: "&aCombatLock configuration reloaded."
  no-permission: "&cYou don't have permission to do that."
```
Player-facing messages, with `&`-style colour codes. `%time%` is replaced
with the relevant number of seconds (remaining lock time for
`command-blocked`, full duration for the `entered-combat-*` messages).

## Building

```bash
mvn clean package
```
Produces `target/combatlock-<version>.jar` - drop it into your Paper
server's `plugins/` folder.
