# EnthusiaTeleport Testing Checklist

## Status Summary
- Current state: ready for server testing.
- Build status: `mvn -q -DskipTests compile` passes.
- Release posture: feature-complete enough for live staging with focused manual coverage before production rollout.

## Known Acceptable Limitations
- `/tpo` only has reliable offline locations for players whose logout locations were recorded by this plugin version. If no tracked location exists, the command falls back to the configured spawn only when forced.
- `/msglog` reads log files on the main thread. This is acceptable for staff-only audit usage, but very large windows should still be tested and used carefully.
- `/rtp` remains synchronous. It is bounded and acceptable for testing/release, but repeated use by many players can still create chunk/heightmap load.
- `/back` is intentionally session-only history and is not persisted across restart/reload.

## Recommended Testing / Release Approach
1. Test first on a staging server running the real target stack: Leaf/Paper fork, Minecraft `1.21.11`, Java `21`.
2. Start with a clean plugin data folder to avoid confusion from old beta data.
3. Test with at least:
   - 2 normal players
   - 1 admin/staff account
   - optional vanished/spectator staff account
4. Test once with default config, then again with several toggles changed.
5. Before production, do one restart-cycle test and one reload-cycle test with players online.

## Manual Testing Checklist

### 1. Startup / Files / Basic Boot
- Start the server with the plugin installed.
- Confirm no startup exceptions or command registration warnings appear in console.
- Confirm these files exist and are generated correctly:
  - `config.yml`
  - `messages.yml`
  - `homes.yml`
  - `ignore.yml`
  - `rtp_uses.yml`
  - `last-locations.yml`
  - `logs/` after using messaging/admin features
- Confirm `/eteleport reload` is registered and executable by staff.

### 2. First-Join / Spawn / Starter Kit
- Join with a brand-new player.
- Confirm first join teleports to configured spawn when `spawn.first-join.teleport-to-spawn: true`.
- Confirm first join does not teleport when that toggle is `false`.
- Confirm bed spawn is set only when `spawn.first-join.set-bed-spawn: true`.
- Confirm starter kit is granted only when `spawn.first-join.starter-kit.enabled: true`.
- Confirm starter kit item list matches config exactly.
- Confirm invalid starter-kit materials are skipped without crashing the plugin.
- If `clear-inventory: true`, confirm inventory is cleared before kit is applied.
- If `clear-inventory: false`, confirm kit stacks/adds normally.

### 3. Respawn Handling
- Die with `spawn.respawn.override-to-configured-spawn: true` and confirm respawn goes to plugin spawn.
- Set `spawn.respawn.override-to-configured-spawn: false`, reload, die again, and confirm vanilla/world respawn behavior is respected.
- Test respawn with `spawn.use-configured-spawn: false` and confirm it uses the world spawn instead of configured coordinates.

### 4. Core Warmup / Cooldown Behavior
- Use `/spawn`, `/home`, `/tpa`, and `/rtp` as a normal player and confirm warmup starts.
- Move during warmup and confirm teleport cancels with the correct message.
- Take damage during warmup and confirm teleport cancels with the correct message.
- Confirm cooldown is applied only after successful teleport completion.
- Confirm cooldown does not apply when teleport fails.
- Confirm `enthusia.teleport.bypass-teleport` skips warmups and cooldowns.
- Confirm cooldown resets after `/eteleport reload`.

### 5. `/tpa`, `/tpahere`, Accept / Deny / Cancel / Ignore
- Send `/tpa` and `/tpahere` between two players.
- Confirm receiver sees correct request text and sender sees sent confirmation.
- Accept requests with and without specifying the sender name.
- Deny requests with and without specifying the sender name.
- Cancel outgoing requests with `/tpacancel`.
- Confirm requests expire after configured timeout.
- Confirm ignored requests are blocked by `/tpignore`.
- Confirm `/tpignore list` shows ignored players.
- Disconnect requester while a request is pending and confirm counterpart gets disconnect cancellation message.
- Disconnect receiver while a request is pending and confirm counterpart gets disconnect cancellation message.
- Accept a player-to-player teleport, then have the target logout during warmup and confirm the teleporter is cancelled cleanly.

### 6. Reload / Disable / Restart Safety
- Start one or more pending warmups.
- Start one or more pending teleport requests.
- Run `/eteleport reload`.
- Confirm:
  - active warmups are cancelled
  - pending requests are cancelled
  - players receive reload cancellation messages
  - cooldown timers are cleared
  - plugin remains responsive after reload
- With players online, stop the server cleanly.
- Restart and confirm no startup corruption or YAML save issues.
- Confirm homes, ignore lists, RTP uses, and tracked logout locations survive restart.
- Confirm pending warmups, pending requests, `/back` history, and msglog cache do not survive restart.

### 7. `/back`
- Teleport using `/spawn`, `/home`, `/tpa`, `/rtp`, `/top`, and `/tpo`, then use `/back`.
- Confirm `/back` returns to the prior location and consumes only one history entry at a time.
- Confirm repeated `/back` walks backward through history up to configured max.
- Confirm `/back` does not record itself into history and does not loop.
- Confirm invalid world/removed world back entries are rejected cleanly.
- Confirm `/back` history clears on logout.

### 8. Homes / GUI / Admin Homes
- Create a home named `Home`.
- Try creating `home` and confirm it is rejected as a duplicate.
- Confirm the display name keeps the original casing in GUI/messages.
- Confirm `/home` opens GUI when no name is provided.
- Confirm `/home <name>` teleports correctly.
- Confirm `/home <name> force` works for unsafe homes.
- Confirm unsafe-home warning appears when appropriate.
- Confirm `/delhome <name>` works with any casing.
- Test home limit permissions:
  - no rank limit
  - `enthusia.homes.5`
  - `enthusia.homes.10`
- Exceed the limit and confirm the “choose homes to keep” GUI works and only selected homes remain.
- As staff, test `/ahome <player>` GUI.
- Confirm staff home teleport and delete actions work.
- Confirm admin home actions are logged to `logs/admin-YYYY-MM-DD.log`.

### 9. `/tpo`
- Use `/tpo` on a player who has never joined and confirm it refuses with the correct message.
- Have a player join, move somewhere distinctive, logout, then use `/tpo <player>` as staff and confirm it lands at tracked logout location.
- Use `/tpo` on a known player without tracked location data and confirm warning path appears.
- Use `/tpo <player> force` in that situation and confirm it falls back to spawn.
- Confirm `/tpo` refuses to use offline logic for players currently online.

### 10. `/invsee` / `/endersee`
- As staff with view-only permission, open `/invsee` and confirm viewing works but editing is blocked.
- As staff with edit permission, confirm editing works.
- Repeat for `/endersee`.
- Confirm self-view does not duplicate or corrupt items.
- Confirm clicking filler slots cannot move pane items into player inventory.
- Confirm shift-click from player inventory cannot inject items into protected filler slots.
- Confirm number-key hotbar swap into GUI top inventory is blocked where appropriate.
- Confirm offhand swap into GUI top inventory is blocked.
- Confirm drag across filler/armor slots does not place invalid items.
- Confirm armor slot validation rejects invalid armor types.
- Close the GUI after edits and confirm target inventory/ender chest retains expected changes.
- Confirm disconnecting the target while GUI is open does not throw errors.

### 11. Messaging / Reply / Logging
- Use `/msg` to one player and to multiple players.
- Confirm `/r` replies to the most recent partner set.
- Confirm message formatting is correct for sender and receiver.
- Confirm private messages are written to `logs/msg-YYYY-MM-DD.log`.
- Use `/msglog` with:
  - short window
  - page argument
  - `--from`
  - `--to`
  - `--contains`
- Use `/msglog view <index>` and confirm context lookup works.
- Run `/eteleport reload` and confirm old msglog cached selections expire.

### 12. Combat / Restrictions / World Blocking
- With combat enabled, enter combat and confirm teleport commands are blocked.
- Confirm `enthusia.teleport.bypass-combat` bypasses that restriction.
- Add a blocked target world and confirm `/tpa`, `/home`, `/rtp`, `/spawn`, and similar routes cannot place players there unless bypassed.
- Confirm `enthusia.teleport.bypass-world-block` bypasses target-world restrictions.
- If CombatLogX is installed, confirm hook works and no duplicate/broken combat behavior appears.

### 13. RTP
- Confirm `/rtp` respects enabled/disabled toggle.
- Confirm per-rank limits work:
  - default limit
  - `enthusia.rtp.10`
  - `enthusia.rtp.20`
  - `enthusia.rtp.unlimited`
- Confirm use count only increments after successful teleport.
- Confirm safe-location lookup eventually fails gracefully when bounds are hostile/impossible.
- Test with tighter bounds and wider bounds.
- Watch TPS/timings while repeatedly issuing `/rtp` with multiple players.

### 14. Performance / Hot-Path Checks
- With several players active, spam normal teleports enough to confirm no obvious lag spikes from warmup bookkeeping.
- Have multiple staff open `/invsee` on active players and confirm no desync or inventory corruption.
- Run a large `/msglog` query and confirm the temporary hitch is acceptable for staff usage.
- Stress `/rtp` with multiple users and confirm load is acceptable for your server hardware and world settings.

## Edge Cases To Verify
- Teleport while carrying passengers.
- Teleport while vanished/spectator and confirm anchor visibility messaging is correct.
- Home in a world that later unloads or is removed.
- Reload while an admin home GUI is open.
- Reload while a player is in the home-limit selection GUI.
- Player logs out during warmup, during pending request, and immediately after teleport completion.
- Invalid world name in spawn or RTP config.

## Integration Checks
- CombatLogX installed and not installed.
- Any vanish plugin that sets `vanish` or `vanished` metadata.
- Leaf/Paper fork behavior for teleports, respawn, and async chat event compatibility.

## Final Release Decision
- Decision after checklist passes: ready for controlled production rollout.
- Block production rollout if any of these fail:
  - reload leaves stale pending teleports/requests alive
  - `/invsee` causes item duplication/loss or unwanted movement
  - `/tpo` sends staff to wrong locations
  - home uniqueness or home deletion/teleport behavior is inconsistent
  - `/rtp` creates unacceptable lag under expected usage
