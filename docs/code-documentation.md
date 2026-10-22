# Code documentation

## Implementation status

The plugin implements SQLite-backed root/child claims, selection tools, area allowance, trust/delegation, native flags, player/admin commands, and Paper protection listeners. Domain, configuration, command permission, and SQLite integration tests are automated. Live multiplayer event behavior has not yet been verified; see [Protection coverage](protection-matrix.md).

## Source layout

| File | Purpose |
| --- | --- |
| `src/main/java/org/saltymc/SaltyClaims.java` | Paper plugin entry point. |
| `src/main/resources/plugin.yml` | Plugin identity, entry point, API version, and load phase. |
| `build.gradle.kts` | Java toolchain, repositories, dependencies, development server, and resource processing. |
| `gradle/libs.versions.toml` | Paper API, Minecraft, and run-paper plugin versions. |
| `gradle.properties` | Project coordinates, version, and Gradle options. |
| `settings.gradle.kts` | Gradle project name. |
| `gradle/wrapper/gradle-wrapper.properties` | Requested Gradle distribution. |
| `claim/` | Immutable bounds/claims/state, root/child validation, allowance rules, and world/chunk index. |
| `trust/` | Explicit player/public subjects, trust hierarchy, and scoped delegation rules. |
| `flag/` | Typed flag definitions and pure policy precedence/authorization. |
| `storage/SqliteStore.java` | Single SQLite connection, schema v1, transactional deltas, integrity checks, and backups. |
| `diagnostics/ClaimBenchmark.java` | Bounded synthetic lookup/edit/SQLite benchmarks and correctness checks, independent of Bukkit/live state. |
| `selection/SelectionManager.java` | Transient corner/mode/resize state and bounded particle previews. |
| `command/ClaimFlagCommands.java` | Owner-only flag listing, boolean toggles, and filtered completion through a shared authorization path. |
| `command/ClaimCommands.java` | Executable paths, per-path permission registration, help/completion, management, and confirmations. |
| `command/ClaimView.java` | Normalized corners/dimensions, ordered owner claim trees, and grouped trust presentation. |
| `command/DeleteConfirmations.java` | Sender-bound deletion snapshots, original permissions, and expiry without public tokens. |
| `claim/ClaimTargets.java` | Owner/context/location resolution with explicit ambiguity checks. |
| `message/HumanNames.java` | Player/world names and owner/bounds descriptions for outgoing identifiers. |
| `protection/ProtectionListener.java` | Paper event adapters and cross-boundary checks. |
| `config/Settings.java` | Validated immutable configuration loaded before replacement. |
| `config/ConfigFiles.java` | Editable-file registry, independent migration histories, open schemas, and immutable runtime bundle. |
| `config/ConfigurationPipeline.java` | Shared prepare/validate/backup/save/apply/publish pipeline and overlapping-operation guard. |
| `config/SafeConfigFiles.java` | Exact private backups, file-change checks, flushed staging, and atomic replacement/fallback. |
| `config/YamlData.java`, `YamlFileSpec.java`, `MigrationStep.java` | Strict parsing, reconciliation, bundled comments/order, and conflict-safe migration helpers. |
| `src/main/resources/config.yml` | Claim behavior defaults and player editability, limits and tools. |
| `config/WorldSettings.java`, `src/main/resources/worlds.yml` | Per-world claiming and independent boolean wilderness rules. |
| `src/main/resources/messages.yml` | Configurable response templates and the automatic global prefix. |
| `message/` | Immutable message snapshots/notices, configurable validation errors, and centralized prefixed delivery. |
| `src/test/java/` | Geometry, trust, flags, command permission, configuration, and database tests. |

## Plugin lifecycle

`org.saltymc.SaltyClaims` extends Bukkit's `JavaPlugin`.

- `onEnable()` runs the full configuration pipeline before starting a storage worker or opening SQLite, then validates persisted claim relationships and builds the index. Commands and maintenance protection remain available for startup failure reporting. One repeating task expires transient selection/confirmation state.
- Startup failures retain the plugin in maintenance: normal logins are rejected and protection decisions deny gameplay changes. Recovery-permitted staff can log in but cannot mutate claim data. Repair and restart are required to reload storage.
- `onDisable()` stops accepting work and waits up to 15 seconds for the storage worker before closing the database. Timeout/failure is logged explicitly. Whole-plugin hot reload/unload is unsupported.

## State and save path

Gameplay reads an immutable `ClaimState` and a world/chunk `ClaimIndex`, maintained on the server thread. Index lookups choose a bounded child before the root; siblings cannot overlap in 3D. Root Y bounds are absent, so world height changes do not require rewriting claims.

Each mutation validates against committed state. One global pending-operation slot reserves all mutations while a save/backup runs; simultaneous mutations receive a retry message. Immutable before/after snapshots go to a single worker. SQLite writes only changed records in one transaction. A successful commit is published on the server thread before player acknowledgement; failed saves keep existing protection and disable subsequent mutations until repair/restart.

Trust and flag changes increment claim revisions. Player deletion is immediate through `/unclaim` (child-first positional selection) or `/unclaim all` (all owned trees across worlds). `ClaimService.unclaim` checks ownership before deletion; `unclaimAll` filters owned parent IDs and their children, preserving other owners and allowance accounts. Both use the serialized SQLite mutation path and clear selection/preview only on success. Player `/claim delete` routes are removed. Administrative deletion confirmations still bind sender, original permission, target/tree snapshots, command family and a 30-second expiry. `/claima delete confirm` uses the original target even after movement; disconnect clears confirmations and bypass state.

`/claima reset all` uses the same pending-confirmation store with a distinct action and a snapshot of all claims. `/claima reset all confirm` checks its own and the original permission, sender, expiry, and unchanged claim snapshot. `ClaimService.resetAll` preserves allowance accounts while removing all claims; SQLite cascading deletes remove local trust/flags. `SaltyClaims.resetClaims` uses the ordinary serialized transaction path and clears selections/previews only after successful persistence and state/index publication. Reset and individual deletion confirmations cannot confirm each other.

`SelectionManager.Port` isolates the tool adapter from plugin lifecycle details. The second right-click invokes `ClaimCommands.commitSelection`, sharing create/resize permission checks, domain validation, and transactional persistence with commands. Validation failures clear both corners, selected parent/resize state, and selection previews; both tool and command attempts must start fresh. Creation is initiated only by the tools; the manual pos1/pos2/create, player resize, child create/restrict, and explain command routes are removed. The player resize permission remains registered for the tools; staff retain /claima resize. The existing regular creation permission remains registered for the tool.

Selection bounds always make roots full height; child Y bounds are absent for equal corner Y, otherwise inclusive min/max Y. Particle previews are separate from selection sessions, so successful submission retains the final border. Each player has at most one preview. The first corner outlines its block, the second replaces that with the inferred bounds. One shared 10-tick task repeats previews until their configured wall-clock expiry; rendering remains bounded and distance-limited. Cancel, quit, world changes, disable, and successful reload clear previews. `particle-duration-seconds` accepts integers 0–300, with 0 disabling particles and 10 as the default.

Tool material chooses regular or child selection directly; there are no mode commands. `toolSession` resets partial selection when the tool changes, and `selectParent` binds child selection to the root under the first click. Settings validate three distinct item materials, including `tools.child-selection`. Resize sessions preview committed old bounds while selecting; rejected attempts clear the session and preview. The resize transaction's post-commit callback shows replacement bounds only after state/index publication; rejected or failed saves never display a successfully resized claim.

Switching away from a selection tool also clears the session and preview immediately. Monitor listeners handle hotbar changes, offhand swaps, removing the last held tool, and inventory actions touching the held slot. Cancelled events do not clear state. A completed resize only starts its new preview when the sender still holds the matching tool. This clears transient selection/resize/parent state, not saved claims or pending delete confirmations.

`border-colors.parent` and `border-colors.child` accept quoted six-digit `#RRGGBB` strings. Settings parse these strictly into immutable Bukkit colors. Preview callers pass the claim type explicitly, so child color applies to full-height children and partial first-block selections too. Each preview stores one reusable dust option with its color, used for every repeated border frame. Inspection, occupied-land feedback, first/second positions and resize success all pass the appropriate claim type. Reload clears old previews and subsequent previews use the new colors.

Protection checks perform no database I/O. Claims are bounded to 65,536 indexed chunks each; ordinary area/count limits are configurable. Index rebuilding and global validation occur on mutation/startup, not per protection event. Very large datasets still require server profiling.

## Message delivery

All player/console chat responses flow through `Messages`, including help/list output and asynchronous replies. `messages.yml` supplies templates and one global `prefix`; the service applies it once to each line. Validation uses `MessageException` with stable keys and placeholders. `Notice` carries immutable success data across the storage worker. Configuration reload validates config.yml, worlds.yml and messages.yml before publishing one immutable bundle. See [Messages](messages.md).

Player names and claim locations form the public command/message interface. Stable UUIDs remain internal database keys. `HumanNames` renders identifiers in final outgoing lines, including custom legacy templates and diagnostic maps; missing names use configurable friendly labels. Deleted claims capture their display description before removal.

`ClaimView` renders inclusive dimensions and normalized minimum/maximum corners. Full-height claims show X/Z only, while bounded children include Y and height in `{size}`. Owned lists include each parent followed by its children. Info renders every assignable trust level with names/empty groups, for local grants only, only when the sender has Manage/owner or explicit administrative inspection authority. Contextual trust targeting remains child-first through `ClaimIndex` and `ClaimTargets`; no bulk trust operations exist.

Default messages use plain language and suppress redundant debug-style headings/maps. Legacy placeholders remain validated against the immutable version-7 contract in addition to current defaults. Message migration 8 uses frozen version-7/version-8 resource snapshots to replace only exact stock strings. Friendly On/Off, Yes/No and trust labels are configured under `labels`. Gameplay denials use `protection.action-denied`; detailed policy reasons remain available in explicit/custom diagnostic output.

Player help first renders a tool summary from runtime-configured materials, then the claiming guide, then authorized command routes. Bare `/claim` uses the same help path and permission. The removed `/claim tool` route is replaced by `commands.help-tools`; migration 10 performs a conflict-checked rename to preserve custom guidance before reconciliation. Startup maintenance can show help using bundled tool defaults when runtime settings are unavailable.

Tool clicks check occupied land before altering selection positions. A rejected click clears the selection, then shows that existing claim's border and owner name. Legitimate owned-corner resize operations and child selection in the selected parent remain allowed; domain overlap validation still checks the entire resulting area.

Startup/reload share `ConfigurationPipeline`. Editable resources have independent versions: `config.yml` is version 6 and `messages.yml` is version 12; unversioned files start at 0. The pipeline saves only changed files using sensitive exact backups, detects concurrent edits, and publishes one immutable `RuntimeConfiguration` only after file saving and runtime application. See [Configuration versions and recovery](configuration-versioning.md) for validation, history, and multi-file recovery limits.

## Plugin metadata

`plugin.yml` declares the plugin name as `SaltyClaims`, the entry point as `org.saltymc.SaltyClaims`, API version `26.3`, and load phase `POSTWORLD`. Gradle replaces `${version}` with the project version during resource processing.

`/claim`, `/claima`, and `/unclaim` are registered. Individual command/flag permissions are registered programmatically by `ClaimCommands.registerPermissions()`, including default grants. Unclaim base and all routes have independent default-true nodes, with no blanket permission. Selection tools check the same mutation capability nodes as commands.

## Build configuration

- Java toolchain: 25.
- Gradle wrapper: 9.7.0, compatible with run-paper 3.1.0.
- Paper API dependency: `io.papermc.paper:paper-api:26.3.build.211-stable`, declared `compileOnly` because the server supplies the API at runtime.
- SQLite JDBC: 3.53.4.0, bundled with its native libraries in the plugin JAR. SLF4J is supplied by the server.
- Tests: JUnit 5.11.4, with SQLite integration tests using real temporary databases.
- Development server target: Minecraft `26.3`, using run-paper `3.1.0`, with 2 GB initial and maximum heap.
- Project coordinates: group `org.saltymc`, version `1.0-SNAPSHOT`.

The complete wrapper is included. `gradlew build` compiles, tests, and packages the plugin; `gradlew runServer` downloads/starts the configured development server subject to its initial setup requirements. GitHub Actions builds with Java 25 and publishes the JAR artifact.

`/claima test` captures non-sensitive environment strings on the server thread and runs `ClaimBenchmark` on a dedicated single worker, with an atomic overlapping-run guard. Reports use unique CREATE_NEW log files; synthetic databases live in separate temporary subdirectories and are cleaned after closing. Test failure is isolated from live storage failure state. The benchmark has capped datasets/work counts and interruption/deadline checks between operations. Its statistics describe background synthetic costs; see [Testing](testing.md) for interpretation and limits.

The API adapters compile against the pinned target. A live Paper boot and two-client protection acceptance run remain unverified in this development session. Optional allowance accrual, economy, deeper nesting, and GP data import are not implemented; deeper nesting is explicitly outside scope.

## Maintenance

Document new components, their responsibilities, data flow, and persistence behavior as they are implemented. Keep command behavior and permission defaults in [Commands and permissions](commands-permissions.md).

## Flag configuration

Policy.resolve separates claims from wilderness. Wilderness reads the named WorldSettings only, independent of its claiming switch. Claims use Settings.ClaimFlag: locked defaults override saved grants; otherwise local override > configured default, with children independent of their parents. Enabled actor switches retain fixed trust thresholds; disabled switches deny owners too. SQLite stores boolean overrides as strings and accepts legacy typed values on load; enabled legacy actor values resolve using the fixed threshold. Staff gameplay bypass remains separate from editing authority.

Worlds are discovered at startup, world load and reload; absent world rules allow wilderness behavior and disable claiming. Config migration 7 moves legacy world policy into worlds.yml before removing it from config.yml. ConfigurationPipeline reads and snapshots every source before the bundle migration, validates the entire candidate, backs up changed originals, saves worlds first for safe migration retries, and publishes after application. YamlFileSpec supports an open root for literal world names and fills missing per-world flags without replacing supplied null/invalid values.

Children normalize the legacy inheritTrust field to false during construction/loading; ownership remains derived from the parent, and explicit local grants remain intact. ClaimState.trust never consults parent grants. New children keep an explicit local Manage grant only for a creating parent manager. Child-tool authorization uses the parent’s owner/Manage trust and the existing child.create tool permission, which is registered independently of executable command routes.

Trust editing uses standalone /trust and /untrust commands with independent command permissions; /claim trustlist remains the inspection route. Both player and administrative trust assignment default omitted levels to Access. TrustRules rejects owner/manager self-targeting before editing and preserves the actor’s grant during untrust-all. Player help includes a configurable help-trust message describing all levels, including Visitor and Owner.
