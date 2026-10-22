# Configuration versions, migrations, and recovery

## Independent versions

The editable file registry is `config.ConfigFiles`. It currently manages `config.yml` and `messages.yml`, each with its own positive integer `config-version` as the last setting. Both start at version 1. These versions are independent of each other, the plugin release (`1.0-SNAPSHOT`), and SQLite's schema version. `plugin.yml` is packaged plugin metadata, not a runtime-editable settings file.

Missing files are created from the current bundled resource after the whole bundle passes validation. Existing blank/comment-only files or mappings without `config-version` are legacy version 0. Explicit zero, negative, fractional, quoted, boolean, null, out-of-range, or future versions are rejected without overwriting supplied files. Do not manually change version numbers to bypass migrations or downgrade.

## Current migration history

| File | Step / destination | Preserved | Added | Renamed / intentionally converted |
| --- | --- | --- | --- | --- |
| `config.yml` | 0 -> 1 | Supplied valid settings, lists, world entries, flag maps | Independent metadata and missing current settings | None |
| `messages.yml` | 0 -> 1 | Supplied valid prefix and message templates | Independent metadata and missing current messages | None |

After migration, reconciliation removes obsolete paths in fixed-schema sections. It never resets a configured value merely because a bundled default changed. These initial migrations introduce no gameplay conversions or speculative legacy key renames.

Each `MigrationStep` number is its **destination** version. The history must contain every ordered step from 1 through the bundled version. An installation at version 1 upgrading to version 4 runs steps 2, 3, and 4. A missing file already uses current defaults and does not run historical conversions against them.

Released migration actions and descriptions are append-only history: never alter an old step to add a later change. VCS review must enforce historical immutability; constructor checks enforce ordering and completeness.

`MigrationStep.rename` moves scalars, lists, and complete nested sections before reconciliation removes obsolete paths. Equal existing destinations are handled idempotently; conflicting destinations or scalar destination parents are rejected with source/destination paths. It does not guess which administrator value should win.

## Schema boundaries and validation

All sections are fixed-schema unless explicitly opened by a file specification:

| File / open path | Preservation and separate validation |
| --- | --- |
| `config.yml`: `worlds` | Retain administrator world names, including literal dots, and their complete flag maps. Every world entry must be a mapping and each nested key/value must match the flag registry/type. Worlds may be unloaded or created later. |
| `config.yml`: `flags.defaults` | Retain configured flag entries. Validate every key against the flag registry and every value against that flag's allowed strings. |
| `config.yml`: `flags.enforced` | Same validation/preservation as defaults, with enforced runtime semantics. |
| `messages.yml`: none | All message paths are fixed. Obsolete/custom paths are removed during reconciliation and retained in exact backups. |

The plugin has no kits, rewards, requirements, or arbitrary gameplay sections. If a future feature introduces an administrator-defined tree, declare its open path and write a separate validator; do not prune its nested custom options using the bundled example tree as a fixed schema. Generic tests exercise this using isolated fixtures, not plugin gameplay migrations.

Strict YAML parsing rejects duplicate keys, non-mapping roots, unsafe tags, invalid UTF-8, non-string/control-character keys, excessive nesting/alias expansion, and unsupported scalar types. Known settings reject supplied nulls, wrong scalar/list/section types, invalid ranges, invalid tool references, and non-string world-list entries. They do not silently coerce supplied values or replace them with defaults.

Messages must be strings, apart from integer version metadata. A custom template may omit placeholders but cannot reference placeholders outside its bundled renderer contract. Empty templates/prefixes are valid. Flag references are checked against the actual registry. There currently are no YAML-to-YAML identifier references; the bundle factory is the validation boundary for any future cross-file references, and tests verify a cross-file reference failure prevents all saves. Live Paper item eligibility is checked before saving, using the available server registries.

## One startup/reload pipeline

`ConfigurationPipeline` runs for startup and `/claima reload`:

1. Reject an overlapping operation using an atomic operation guard.
2. Read original bytes and file identity/mtime; strictly parse, version, migrate, and reconcile each file in memory.
3. Validate each file, construct the immutable settings/messages bundle, and run cross-file/runtime preflight validation.
4. Check all originals for concurrent edits, including files requiring no rewrite.
5. Create exact backups of **all** changed existing files before replacing **any** file.
6. Stage complete output in private temporary files in the same directories and flush their contents.
7. Recheck originals, replace changed files individually, and recheck the resulting bundle before runtime application.
8. Apply runtime-dependent changes, then publish the entire immutable bundle through one volatile reference.

No writes occur while validation is incomplete. A failure leaves the previously working runtime bundle active on reload; startup does not start the storage worker/database until configuration application succeeds. The plugin's maintenance gate remains available for reporting startup failures.

Files with no semantic/schema/order changes remain byte-for-byte untouched, including custom comments and timestamps. Rewritten files use bundled comments and fixed-key order, keep administrator ordering within open sections, and put `config-version` last. Custom comments remain in backups rather than being merged into the rewritten template.

## Backups and sensitive data

Configuration backups are in `plugins/SaltyClaims/config-backups/`, named like `config.yml.v0-to-v1.<unique-id>.bak`. They contain the **exact original bytes**, including custom comments, credentials, and obsolete settings. They are separate from SQLite backups in `backups/`.

Backup directories/files and staged files receive owner-only POSIX permissions or owner-only Windows ACLs where supported. Filesystems without those facilities inherit directory permissions; the administrator must secure that storage. Backups are retained manually. Treat backups and leftover temporary files as sensitive and exclude them from public uploads/support bundles.

Logs contain filenames, version transitions, backup filenames, and added/removed/moved/changed paths. They omit configured values. YAML exception excerpts and raw exception causes are not forwarded because they can contain secrets. Validation errors identify the offending filename/path and expected type/range; IO errors identify the failed phase/file without exposing data.

## Recovery limits

- Multiple file replacements are **not one atomic transaction**. If the process stops after the first replacement, that file can be upgraded while a later file remains old.
- Each file uses same-directory atomic replacement when supported. When unsupported, replacement falls back to a regular same-filesystem move; filesystem/power-loss durability guarantees still apply.
- Successful replacements contain their destination version. On retry/restart, already completed steps are skipped and remaining files continue their missing migrations. Migrations must tolerate source keys already being renamed when values agree.
- If backup creation or staging fails, no replacements begin. If a later replacement fails, completed file upgrades remain saved and are explicitly reported.
- If runtime application fails after saving, the old runtime bundle stays active but the **file upgrades remain saved**. Fix the cause and retry. The plugin never automatically downgrades or rolls files back.
- A startup configuration failure prevents dependent services from starting. Repair the files and restart. Ordinary reload failures keep existing protection/settings active.
- Concurrent edits are detected through original bytes, identity and timestamps before replacement, and a final bundle check. Editors are not locked out by an OS-wide transaction: do not edit during an upgrade. There remains a small external edit race between the last check and a filesystem move; the plugin does not claim cross-process compare-and-swap semantics.

To restore a backup manually, stop the server, retain copies of the current files, and restore a compatible set. Retry on the same or a newer plugin so older restored files can migrate forward. Do not lower `config-version` on already-converted data or run an older plugin against newer files.

## Required workflow for every future change

1. Update only the affected bundled YAML resource(s), keeping each `config-version` last. Increment each affected file's integer independently; do not tie it to plugin/database versions.
2. Append the corresponding destination `MigrationStep` to that file's history in `ConfigFiles`. Do not modify previous steps. A default-only addition still needs an adoption/reconciliation step and a version increment.
3. Explicitly rename old paths before pruning. Use conflict-checking rename helpers for scalars, lists and complete sections. Any intentional replacement or type/unit conversion belongs in its new migration, with explicit validation and an explanation of what changes.
4. Update fixed/open schema declarations, separate validators, runtime construction, message placeholder contracts, and cross-file reference checks as needed.
5. Add tests starting from every relevant older shape, including skipped releases, conflicts, custom values, and repeat execution after partial saves.
6. Extend this history table/release documentation with what is preserved, added, renamed, removed or intentionally replaced. Record backup/recovery implications.

The pipeline tests cover fresh/unversioned installs, independent/skipped versions, custom values/default changes, scalar/list/section renames, conflicts, open sections, malformed YAML, invalid references/types/versions, unchanged files, concurrent edits/creation, backup/stage/replace failures, atomic-move fallback, interrupted upgrades, runtime failure, overlap rejection, and backup permissions.
