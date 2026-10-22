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
| `selection/SelectionManager.java` | Transient corner/mode/resize state and bounded particle previews. |
| `command/ClaimCommands.java` | Executable paths, per-path permission registration, help/completion, management, and confirmations. |
| `protection/ProtectionListener.java` | Paper event adapters and cross-boundary checks. |
| `config/Settings.java` | Validated immutable configuration loaded before replacement. |
| `config/ConfigFiles.java` | Editable-file registry, independent migration histories, open schemas, and immutable runtime bundle. |
| `config/ConfigurationPipeline.java` | Shared prepare/validate/backup/save/apply/publish pipeline and overlapping-operation guard. |
| `config/SafeConfigFiles.java` | Exact private backups, file-change checks, flushed staging, and atomic replacement/fallback. |
| `config/YamlData.java`, `YamlFileSpec.java`, `MigrationStep.java` | Strict parsing, reconciliation, bundled comments/order, and conflict-safe migration helpers. |
| `src/main/resources/config.yml` | Administrator defaults. |
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

Trust and flag changes increment claim revisions. Deletion confirmations bind sender, original permission, target/tree snapshots, and a 30-second expiry. Disconnect clears player confirmations and bypass state. Confirmation rechecks its own permission, original permission, current ownership, and the target tree.

Protection checks perform no database I/O. Claims are bounded to 65,536 indexed chunks each; ordinary area/count limits are configurable. Index rebuilding and global validation occur on mutation/startup, not per protection event. Very large datasets still require server profiling.

## Message delivery

All player/console chat responses flow through `Messages`, including help/list output and asynchronous replies. `messages.yml` supplies templates and one global `prefix`; the service applies it once to each line. Validation uses `MessageException` with stable keys and placeholders. `Notice` carries immutable success data across the storage worker. Configuration reload validates both candidate files before publishing either. See [Messages](messages.md).

Startup/reload now share `ConfigurationPipeline`. Both editable YAML resources have independent version 1 metadata; unversioned files start at 0. The pipeline saves only changed files using sensitive exact backups, detects concurrent edits, and publishes one immutable `RuntimeConfiguration` only after file saving and runtime application. See [Configuration versions and recovery](configuration-versioning.md) for validation, history, and multi-file recovery limits.

## Plugin metadata

`plugin.yml` declares the plugin name as `SaltyClaims`, the entry point as `org.saltymc.SaltyClaims`, API version `26.3`, and load phase `POSTWORLD`. Gradle replaces `${version}` with the project version during resource processing.

Only `/claim` and `/claima` are registered. Individual command/flag permissions are registered programmatically by `ClaimCommands.registerPermissions()`, including default grants. Root commands have no blanket permission. Selection tools check the same mutation capability nodes as commands.

## Build configuration

- Java toolchain: 25.
- Gradle wrapper: 9.7.0, compatible with run-paper 3.1.0.
- Paper API dependency: `io.papermc.paper:paper-api:26.3.build.211-stable`, declared `compileOnly` because the server supplies the API at runtime.
- SQLite JDBC: 3.53.4.0, bundled with its native libraries in the plugin JAR. SLF4J is supplied by the server.
- Tests: JUnit 5.11.4, with SQLite integration tests using real temporary databases.
- Development server target: Minecraft `26.3`, using run-paper `3.1.0`, with 2 GB initial and maximum heap.
- Project coordinates: group `org.saltymc`, version `1.0-SNAPSHOT`.

The complete wrapper is included. `gradlew build` compiles, tests, and packages the plugin; `gradlew runServer` downloads/starts the configured development server subject to its initial setup requirements. GitHub Actions builds with Java 25 and publishes the JAR artifact.

The API adapters compile against the pinned target. A live Paper boot and two-client protection acceptance run remain unverified in this development session. Optional allowance accrual, economy, deeper nesting, and GP data import are not implemented; deeper nesting is explicitly outside scope.

## Maintenance

Document new components, their responsibilities, data flow, and persistence behavior as they are implemented. Keep command behavior and permission defaults in [Commands and permissions](commands-permissions.md).
