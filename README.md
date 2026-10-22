# SaltyClaims

A Paper plugin for full-height rectangular land claims, one-level subdivisions (including 3D children), GP-style trust, and native claim flags. Uses a local SQLite database and in-memory protection lookups.

## Build and install

Requires Java 25. The build targets Paper/Minecraft 26.3 with Paper API build 211-stable.

```powershell
.\gradlew.bat build
```

On Linux/macOS, run `bash gradlew build`. Copy `build/libs/SaltyClaims-1.0-SNAPSHOT.jar` to the server's `plugins/` directory and restart. SQLite JDBC and native libraries are included; no external database is needed. Java may report its native-access advisory; `--enable-native-access=ALL-UNNAMED` explicitly enables SQLite's required native access.

`gradlew runServer` downloads/starts a development server using run-paper. Complete the server's own initial setup and EULA requirements before using it.

## Claim land

1. Right-click two opposite block corners with a golden shovel. The second click immediately attempts creation, checking allowance, overlap, and claim limits.
2. If creation is rejected, read the denial and choose another second corner. For manual selection, use `/claim pos1`, `/claim pos2`, then `/claim create`.
3. Inspect with a stick or `/claim info`; view allowance with `/claim list`.
4. To resize, right-click an owned corner with the shovel, then its new position. The second click attempts the resize immediately. Manual selections can still be applied with `/claim resize`.

Defaults: 10,000 starting claim blocks, 20 roots, 100 children per root, and a minimum X/Z width of three. Root area is charged once; children are free. Playtime accrual is not implemented. Configure limits and tool items in `plugins/SaltyClaims/config.yml`.

Particles first outline the selected block, then expand to the selected rectangle or cuboid. `particle-duration-seconds: 10` controls how long the current border repeats after a selection or inspection; set it to `0` to disable particles (range 0–300). New previews replace the previous border, and `/claim cancel`, disconnecting, changing worlds, or a successful reload clears it.

Right-clicking claimed land with the shovel shows its border and `This land is already claimed by <owner>.` without changing either selected position. Owned-corner resizing and child selection within the selected parent are allowed. Even when both corners are outside another claim, creation still checks the entire rectangle for overlaps.

## Trust, children, and flags

```text
/claim trust Alex access
/claim trust Alex container
/claim trust Alex build
/claim trust Alex manage
/claim trust public access
/claim trustlist
/claim untrust Alex

/claim mode child --root
# Right-click two corners: same Y creates a full-height child; different Y creates a 3D child.
/claim child restrict on

/claim flags
/claim flag pvp allow
/claim flag container-use public
/claim flag container-use reset
/claim explain container-use Alex
```

Commands act on the child at your location, otherwise the root. Use `--root`, `--owner <player>`, or `--at <world> <x> <y> <z>` for explicit targeting. An owner name selects their sole root remotely; multiple claims require a location. Commands and messages use player names and claim locations. Children cannot contain children. Restricting a child disables parent trust inheritance, while flags continue to inherit. Managers can delegate lower trust, but ownership, geometry, flag edits, and manager assignment remain owner-only.

To delete, run `/claim delete`. The response is `Type /claim delete confirm to delete this claim.` (or `child claim`). Run that command within 30 seconds. Confirmation remains bound to the original claim/tree even if you move; deleting a parent also removes its children.

## Administration

```text
/claima help
/claima inspect Alex --at world 100 64 200
/claima bypass on
/claima trust Alex Sam build --at world 100 64 200
/claima delete Alex --at world 100 64 200
/claima delete confirm
/claima reset all
/claima reset all confirm
/claima balance add Alex 1000
/claima backup
/claima reload
```

Each executable player/admin subcommand has its own permission; there is no shared root permission. Player nodes default to true and admin nodes to op. For example, `saltyclaims.command.claim.create` and `saltyclaims.command.claima.delete` are independent. Flag editing also checks `saltyclaims.flag.<key>`. Administrative bypass is off on each connection and does not override enforced server flags or grant administrative command permissions.

`/claima reset all` requests deletion of every claim on this server. Confirm within 30 seconds using `/claima reset all confirm`. These require `saltyclaims.command.claima.reset.all` and `saltyclaims.command.claima.reset.all.confirm` respectively; confirmation also rechecks the request permission. Reset removes all parents, children, local trust, and local flags, clears selection previews after saving, and releases all used area. Earned/bonus balances, configuration, and backups remain. Any claim change invalidates the pending reset.

Changes become active after a successful SQLite transaction. Only one mutation/backup may be pending at once; other mutation requests receive a retry message. Runtime save failure retains existing protection and disables writes until repair/restart. Startup load failure enters maintenance mode. Whole-plugin hot reload/unload is unsupported.

## Messages

`plugins/SaltyClaims/messages.yml` is created on first startup. It controls player/console responses and maintenance kick text, with a global prefix applied automatically to every output line:

```yaml
prefix: '&8[&bSaltyClaims&8] &r'
errors:
  missing-permission: '&cYou need {permission}.'
success:
  created: '&aCreated {type} {id}.'
```

The shipped prefix is `[SaltyClaims] `. Standard `&` color codes are supported. Keep each template's `{placeholders}` when customizing it. Placeholders are inserted literally after color processing, so `&` codes and placeholder-like text in data are not evaluated. Missing message keys fall back to bundled defaults; `prefix: ''` removes the prefix and an empty message suppresses that response. Prefixes need not be repeated in templates. Server diagnostic/audit logs continue through the standard plugin logger.

Run `/claima reload` (permission `saltyclaims.command.claima.reload`) to migrate, validate and apply both files through the same pipeline used at startup. Each has its own `config-version` at the bottom. Unversioned files migrate from 0; invalid/future versions are rejected without overwriting them. Missing settings are added, configured values retained, and obsolete fixed-schema paths removed. See [message documentation](docs/messages.md) for template details.

Changed originals receive exact, sensitive backups in `plugins/SaltyClaims/config-backups/` before replacement. Validation failure writes nothing and reload failure keeps the previous runtime active. Multi-file saves are not one atomic transaction: completed upgrades remain saved if a later file/runtime application fails, and a retry resumes from each file's saved version. The plugin never automatically downgrades files. See [configuration upgrade and recovery documentation](docs/configuration-versioning.md).

Backups are written to `plugins/SaltyClaims/backups/`. To restore, stop the server, preserve the current database, replace `claims.db` with a consistent backup, and restart. Backup retention is manual.

## Validation and documentation

`gradlew test` exercises geometry, trust/delegation, flag precedence, command permission isolation, SQLite persistence, rollback, and backup restore. Event adapters compile against the target Paper API; live multiplayer protection testing is still required before production use.

See [documentation](docs/README.md), [code architecture](docs/code-documentation.md), [commands/permissions](docs/commands-permissions.md), and [protection coverage](docs/protection-matrix.md). The [design plan](docs/plugin-plan.md) retains deferred ideas and original proposed defaults; implementation details are documented separately.
