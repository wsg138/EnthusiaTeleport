# EnthusiaTeleport

[![Codacy Badge](https://app.codacy.com/project/badge/Grade/29bb9660bae745c1974c45f09cb23ed3)](https://app.codacy.com/gh/wsg138/EnthusiaTeleport/dashboard?utm_source=gh&utm_medium=referral&utm_content=&utm_campaign=Badge_grade)

EnthusiaTeleport provides Enthusia SMP's player teleport requests, homes, bed teleport, spawn behavior, first-join starter setup, and related staff utilities. Private messaging is handled by RoseChat rather than this plugin.

The player-facing values below were checked against the live Enthusia configuration on August 22, 2026.

## Standard teleport behavior

Most normal player teleports handled by this plugin use a **5-second warmup**.

During that warmup:

- moving more than **0.35 blocks** from the starting position cancels the teleport;
- taking real damage cancels the teleport;
- the player receives a warmup/cancellation message explaining what happened.

The production cooldown after a completed teleport is currently **0 seconds**, so there is no additional normal post-teleport cooldown.

Teleport commands are blocked while the player is in PvP combat. The current internal combat window is **30 seconds**, with CombatLogX/NewPlayerProtection integrations used where applicable.

Teleport destinations are also checked against blocked target worlds. The current configuration prevents this teleport system from sending ordinary players into the `surfevents` world.

When a command requests safe-location checking, the plugin searches around the destination instead of blindly placing the player into an unsafe block position. If no safe destination can be found, the teleport fails rather than forcing the player into an invalid location.

## Player teleport requests

### `/tpa <player>`

Requests to teleport **you to another player**.

Alias:

```text
/tpask <player>
```

### `/tpahere <player>`

Requests that **the other player teleport to you**.

### Accepting and denying

```text
/tpaccept [player]
/tpyes [player]
/tpadeny [player]
/tpno [player]
```

If no player name is supplied to `/tpaccept` or `/tpadeny`, the command acts on the most recent applicable incoming request.

Requests expire after **60 seconds**. A player can have requests involving different people at the same time, but cannot send a duplicate pending request to the same target.

When a request is accepted, the player who is actually moving goes through the normal teleport warmup and safe-destination check. The target location is resolved from the other player's **live location when the teleport completes**, rather than permanently locking to where they were when the request was first accepted.

### Canceling and ignoring requests

```text
/tpacancel
/tpignore <player>
/tpignore list
```

`/tpacancel` cancels outgoing request(s). `/tpignore` can ignore or unignore teleport requests from a particular player, and the ignore list persists across restarts.

## Homes

Every ordinary player starts with **1 home slot**. Permission/rank upgrades can raise the limit to **5, 10, or 20 homes**.

### Setting a home

```text
/sethome <name>
```

A home:

- must have a valid name;
- cannot duplicate one of your existing home names;
- cannot be created after you have reached your current home limit;
- must be set at a location the plugin considers safe.

### Using homes

```text
/home
/home <name>
/homes
/delhome <name>
```

`/homes` opens the home GUI, showing each home's name, world, and coordinates. Clicking a home starts the teleport.

`/home` behaves slightly differently:

- with **one** home, it teleports to that home directly;
- with multiple homes, it asks the player to specify a name;
- `/home <name>` teleports to that specific home.

### Unsafe homes

A location can become unsafe after a home was originally created—for example if blocks around it are changed later.

When `/home <name>` detects that situation, it does **not** automatically send the player into the unsafe location. Instead, it displays a clickable **Teleport anyway** option. Choosing that option runs the forced form of the home teleport:

```text
/home <name> force
```

This bypasses the home-location safety warning intentionally, but it does not turn the command into a general teleport/admin bypass.

### Losing home slots

If a player's allowed home limit decreases and they now have more homes than permitted, the plugin does not silently choose which homes to delete. It opens a selection GUI and requires the player to choose exactly which homes to keep; the unselected homes are removed after confirmation.

## Bed teleport

```text
/bed
```

Teleports to the player's current Minecraft bed-spawn location using the normal warmup and safe-destination handling.

If no valid bed spawn exists, the command reports that instead of teleporting.

A new player's bed-spawn location is initially set to the server spawn as part of the first-join setup, but sleeping in a bed can establish the player's normal Minecraft bed spawn afterward.

## Spawn

```text
/spawn
```

Teleports to the configured Enthusia spawn using the normal 5-second warmup and safe-location check.

The server currently also **forces normal death respawns to the configured server spawn**. In other words, the `/bed` command can still take a player to their bed spawn, but dying does not currently cause the player to respawn at that bed; the plugin overrides the death respawn location to server spawn.

## First join

On a player's first-ever join, EnthusiaTeleport currently:

1. teleports the player to server spawn;
2. sets their initial bed-spawn location to server spawn;
3. gives the starter kit without clearing any items already present.

Current starter kit:

- 8 Cooked Beef
- 1 Stone Sword
- 1 Stone Axe
- 1 Stone Shovel
- 1 Stone Pickaxe

The first-join numbering/welcome broadcast used elsewhere on Enthusia is handled by the playtime system; this plugin is responsible for the spawn/bed/starter-item portion.

## Current ordinary player commands

```text
/tpa <player>
/tpask <player>
/tpahere <player>
/tpaccept [player]
/tpyes [player]
/tpadeny [player]
/tpno [player]
/tpacancel
/tpignore <player|list>
/sethome <name>
/home [name]
/homes
/delhome <name>
/bed
/spawn
```

These commands are granted to ordinary players by default through the plugin's base permissions.

## Features present in code but not currently a normal SMP player feature

### Random teleport

The plugin contains a safe `/rtp` system with queued location searches and rank-based use limits, but **RTP is disabled in the current Enthusia production configuration** and its command permission is not granted to ordinary players by default. It should not be advertised as a current SMP feature unless it is deliberately enabled later.

### `/back`

The plugin keeps a bounded teleport-history stack (up to 10 locations in the current configuration) and implements `/back`, but the `/back` permission defaults to operator rather than ordinary players. It is therefore an administrative/staff capability in the current permission model, not a standard player command.

## Staff utilities

The same plugin contains staff-only tools including:

```text
/tppos <x> <y> <z> [world]
/tpo <player> [force]
/invsee <player>
/endersee <player>
/top
/back
/ahome <player>
/eteleport ...
```

Staff can inspect/manage other players' homes, inventories, ender chests, offline last-known positions, and teleport history according to their permissions. These utilities are part of the repository documentation but are not intended as player-facing wiki features.

## Integrations

Optional integrations include:

- **CombatLogX** for authoritative combat-state checks;
- **NewPlayerProtection** for compatibility with protection/combat state;
- other Enthusia plugins can use the exposed `TeleportApi` to request/cancel teleports and apply warmup/cooldown modifiers safely.

Private messages such as `/msg` are intentionally **not** implemented here; RoseChat owns messaging on the current SMP.

## Build

```powershell
mvn -q -DskipTests package
```
