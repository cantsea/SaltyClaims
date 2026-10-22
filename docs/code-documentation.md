# Code documentation

## Implementation status

The plugin currently consists of an entry point and plugin metadata. Claim management, protection, flags, commands, permissions, configuration, and persistence are not implemented. There are no automated tests yet.

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

## Plugin lifecycle

`org.saltymc.SaltyClaims` extends Bukkit's `JavaPlugin`.

- `onEnable()` is called when the server enables the plugin. Its body currently contains only a placeholder comment.
- `onDisable()` is called when the server disables the plugin. Its body currently contains only a placeholder comment.

Neither method currently registers services, commands, or event listeners, or reads or writes data.

## Plugin metadata

`plugin.yml` declares the plugin name as `SaltyClaims`, the entry point as `org.saltymc.SaltyClaims`, API version `26.3`, and load phase `POSTWORLD`. Gradle replaces `${version}` with the project version during resource processing.

## Build configuration

- Java toolchain: 25.
- Gradle distribution configured by wrapper properties: 9.4.0.
- Paper API dependency: `io.papermc.paper:paper-api:26.3.build.+`, declared `compileOnly` because the server supplies the API at runtime.
- Development server target: Minecraft `26.3`, using run-paper `3.1.0`, with 2 GB initial and maximum heap.
- Project coordinates: group `org.saltymc`, version `1.0-SNAPSHOT`.

These values describe the checked-in configuration; runtime compatibility has not been verified. The Paper dependency uses a dynamic version, so resolved builds may change.

The wrapper scripts (`gradlew`, `gradlew.bat`) and `gradle-wrapper.jar` are absent. Until the complete wrapper is restored, builds require an independently installed Gradle distribution. With Gradle available, `gradle build` builds the plugin and `gradle runServer` starts the configured development server. The latter downloads server components as needed and may require initial server setup.

## Maintenance

Document new components, their responsibilities, data flow, and persistence behavior as they are implemented. Keep command behavior and permission defaults in [Commands and permissions](commands-permissions.md).
