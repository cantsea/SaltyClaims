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

Run `/claim help` for claiming instructions, configured tool items, and the commands you can use. `/claim tool` has been removed.

1. Right-click two opposite block corners with a stick. The second click immediately attempts creation, checking allowance, overlap, and claim limits.
2. If creation is rejected, the selection clears. Select both corners again.
3. Inspect with a feather or `/claim info`; view allowance with `/claim list`.
4. To resize, right-click an owned corner with the matching claim tool, then its new position. The old border stays visible while selecting; the new border appears after a successful save. Rejected attempts clear the selection and its preview.

Defaults: 10,000 starting claim blocks, 20 roots, 100 children per root, and a minimum X/Z width of three. Root area is charged once; children are free. Playtime accrual is not implemented. Configure limits and tool items in `plugins/SaltyClaims/config.yml`.

Particles first outline the selected block, then expand to the selected rectangle or cuboid. `particle-duration-seconds: 10` controls how long the current border repeats after a selection or inspection; set it to `0` to disable particles (range 0–300). New previews replace the previous border. Switching away from a selection tool, disconnecting, changing worlds, or a successful reload clears it. Inactive selections expire after `selection-timeout-seconds` (default 120); the border has its own particle duration. There is no cancel command.

Border colors are configured separately using quoted hex colors. Run `/claima reload` after editing:

```yaml
border-colors:
  parent: '#FFFF00' # yellow
  child: '#00FFFF'  # cyan
```

These colors apply to selection, inspection and resize borders. Child claims use the child color whether full height or 3D.

Right-clicking claimed land with the regular claim tool shows its border and `This land is already claimed by <owner>.` and clears any in-progress selection. Owned-corner resizing and child selection within the selected parent are allowed. Even when both corners are outside another claim, creation still checks the entire rectangle for overlaps.

## Trust, children, and flags

```text
/trust Alex access
/trust Alex container
/trust Alex build
/trust Alex manage
/trust public access
/claim trustlist
/untrust Alex

# Use the bone inside your parent claim; no mode command is needed.
# Right-click two corners: same Y creates a full-height child; different Y creates a 3D child.

/claimflags
/claimflag toggle pvp
```

Commands act on the child at your location, otherwise the root. Use `--root`, `--owner <player>`, or `--at <world> <x> <y> <z>` for explicit targeting. An owner name selects their sole root remotely; multiple claims require a location. Commands and messages use player names and claim locations. Children cannot contain children. Owners and parent managers can create children. Children never inherit parent trust or flags, including older saved children. The parent owner retains ownership, and a manager who creates the child receives a local Manage grant. Other parent members and public grants have no access unless granted locally. Child flags use their own overrides or the configured claim defaults. Player creation and resizing use only the regular/child tools; the resize, child create and restrict commands are removed. Staff retain /claima resize for coordinate-based edits. Managers can delegate lower trust, but resizing, ownership, flag edits, and manager assignment remain owner-only.

`/claim info` shows who owns the claim, its corners and size. Owners/managers also see every assignable trust level, including empty levels, with only the selected claim’s local grants shown. `/claim list` lists each owned claim followed by its child claims, with size and corners on two short lines; only main claim areas count toward the allowance. Full-height claims show X/Z corners; 3D child claims also show Y and height in their size. Trust commands edit only the selected child or main claim. Use /trust <player|public> [level] and /untrust <player|public|all>; omitted trust levels grant Access. Owners and managers cannot trust or untrust themselves, and /untrust all preserves the sender’s own grant. The new permissions are saltyclaims.command.trust and saltyclaims.command.untrust; former /claim trust and /claim untrust routes are removed. /claim help briefly explains every trust level.

Regular claims use `tools.selection: STICK`; child claims use `tools.child-selection: BONE`; inspection uses `tools.inspection: FEATHER`. All three items must be distinct. The child tool chooses its parent from the first clicked block. Switching away from a claim tool clears its selection and particle preview immediately, including resize state. Returning to the tool starts fresh. `/claim mode basic` and `/claim mode child` have been removed.

Every flag is listed in config.yml under claim-flags, with a boolean default and player-editable switch. False denies the behavior, even for the owner; true permits it subject to the normal Access/Container/Build trust requirement. Action flags default to true, environmental flags to false, and every player-editable setting defaults to false. A locked flag always uses the server default, ignoring saved claim overrides. For editable flags, each claim uses its own override or the configured default; children never read parent overrides.

Use /claimflags while standing in an owned claim to list flags you can edit, and /claimflag toggle [flag] to flip one. Listing and completion show only editable flags for which you have permission. Wilderness, another player's claim, and trusted claims do not grant flag-edit authority. The new commands have independent saltyclaims.command.claimflags and saltyclaims.command.claimflag.toggle permissions, plus saltyclaims.flag.<key> for each flag. Old /claim flag(s) and /claima flag(s) routes are removed.

### World wilderness settings

worlds.yml contains each loaded world by its literal name. Worlds are added at startup, world load and /claima reload with claiming: false and every wilderness flag true. False denies the behavior; true allows it. Claiming controls new claim creation, independently of wilderness protection; existing claims stay protected. World wilderness rules never apply inside claims. There is no enforced section.

```yaml
world:
  claiming: false
  wilderness-flags:
    block-break: true
    block-place: true
    pvp: true
    # The generated file includes every supported flag.
```

On upgrade, legacy explicit global/world rules are converted to boolean wilderness switches. Existing claiming availability is preserved for known worlds. Legacy enforced claim values become locked defaults; custom action thresholds become enabled/disabled switches with fixed trust requirements. Exact original files are backed up before migration. Review the resulting config.yml and worlds.yml before reopening the server.

Use `/unclaim` to remove the child claim you are standing in, or the main claim when there is no child. Removing a main claim also removes its children. `/unclaim all` removes every claim you own across all worlds. Both apply immediately without confirmation, preserve your earned/bonus claim blocks, and release the used area. Permissions are `saltyclaims.command.unclaim` and `saltyclaims.command.unclaim.all` (both default true). The old player `/claim delete` commands are removed.

Use `/claimblocks` anywhere to see your available, used, and total claim blocks. Total includes starting, earned and bonus blocks; only owned parent claim areas count as used. The command is player-only and requires `saltyclaims.command.claimblocks` (default true).

## Server-managed claims

Set server-manages-claims: true in config.yml for skyblock or another plugin-managed land system (default false). Players cannot create, resize or unclaim parent claims; /unclaim all and /claimblocks are disabled. Player help hides the unavailable commands, and /claim list omits claim-block balances. Existing parent protection, trust and editable flags remain active. Children can still be created, resized and unclaimed under their existing ownership/Manage and permission rules; configured world claiming switches, child limits and flags still apply.

The child tool and inspection tool remain available. Use claiming: true for worlds where players should create children; server-managed mode still prevents player parent creation. Staff administration remains available. Other plugins can create parent claims through the Bukkit Services Manager API, bypassing player budgets and claiming switches while retaining geometry/world validation. See [Plugin API](docs/plugin-api.md) for a complete integration example and persistence/error guarantees.

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

Each executable player/admin subcommand has its own permission; there is no shared root permission. Player nodes default to true and admin nodes to op. For example, `saltyclaims.command.claim.create` and `saltyclaims.command.claima.delete` are independent. Flag editing also checks `saltyclaims.flag.<key>`. Administrative trust commands remain under /claima, and also default omitted levels to Access. Administrative bypass is off on each connection. It bypasses player gameplay checks but does not grant command permissions or flag-edit authority.

`/claima reset all` requests deletion of every claim on this server. Confirm within 30 seconds using `/claima reset all confirm`. These require `saltyclaims.command.claima.reset.all` and `saltyclaims.command.claima.reset.all.confirm` respectively; confirmation also rechecks the request permission. Reset removes all parents, children, local trust, and local flags, clears selection previews after saving, and releases all used area. Earned/bonus balances, configuration, and backups remain. Any claim change invalidates the pending reset.

Changes become active after a successful SQLite transaction. Only one mutation/backup may be pending at once; other mutation requests receive a retry message. Runtime save failure retains existing protection and disables writes until repair/restart. Startup load failure enters maintenance mode. Whole-plugin hot reload/unload is unsupported.

## Messages

`plugins/SaltyClaims/messages.yml` is created on first startup. It controls player/console responses and maintenance kick text, with a global prefix applied automatically to every output line:

```yaml
prefix: '&8[&bSaltyClaims&8] &r'
errors:
  missing-permission: '&cYou need {permission}.'
success:
  created: '&a{type} created.'
```

The shipped prefix is `[SaltyClaims] `. Standard `&` color codes are supported. Keep each template's `{placeholders}` when customizing it. Placeholders are inserted literally after color processing, so `&` codes and placeholder-like text in data are not evaluated. Missing message keys fall back to bundled defaults; `prefix: ''` removes the prefix and an empty message suppresses that response. Prefixes need not be repeated in templates. Server diagnostic/audit logs continue through the standard plugin logger.

Defaults use short wording such as `Claim created.` and `Only the claim owner can do that.` They hide redundant location details, internal policy sources and raw flag maps. Access levels and switches use readable names and On/Off or Yes/No. Detailed placeholders remain available for customized messages. The version-8 migration changes only exact previous stock wording, preserving customized messages and prefixes.

Run `/claima reload` (permission `saltyclaims.command.claima.reload`) to migrate, validate and apply both files through the same pipeline used at startup. Each has its own `config-version` at the bottom. Unversioned files migrate from 0; invalid/future versions are rejected without overwriting them. Missing settings are added, configured values retained, and obsolete fixed-schema paths removed. See [message documentation](docs/messages.md) for template details.

Changed originals receive exact, sensitive backups in `plugins/SaltyClaims/config-backups/` before replacement. Validation failure writes nothing and reload failure keeps the previous runtime active. Multi-file saves are not one atomic transaction: completed upgrades remain saved if a later file/runtime application fails, and a retry resumes from each file's saved version. The plugin never automatically downgrades files. See [configuration upgrade and recovery documentation](docs/configuration-versioning.md).

Backups are written to `plugins/SaltyClaims/backups/`. To restore, stop the server, preserve the current database, replace `claims.db` with a consistent backup, and restart. Backup retention is manual.

## Validation and documentation

On your test server, run `/claima test` (permission `saltyclaims.command.claima.test`, default op). It benchmarks generated claim sets in the background and writes a log to `plugins/SaltyClaims/test-reports/`. Real claims and worlds are untouched. Repeat runs for a before/after baseline; `PASS` means correctness checks passed, not a guarantee of live-server performance. See [test instructions](docs/testing.md) for scenarios and how to interpret the timings.

`gradlew test` exercises geometry, trust/delegation, flag precedence, command permission isolation, SQLite persistence, rollback, and backup restore. Event adapters compile against the target Paper API; live multiplayer protection testing is still required before production use.

See [documentation](docs/README.md), [code architecture](docs/code-documentation.md), [commands/permissions](docs/commands-permissions.md), and [protection coverage](docs/protection-matrix.md). The [design plan](docs/plugin-plan.md) retains deferred ideas and original proposed defaults; implementation details are documented separately.
