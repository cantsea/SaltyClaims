# Messages and global prefix

The plugin copies bundled `messages.yml` to `plugins/SaltyClaims/messages.yml` when the file is absent. The resource in `src/main/resources/messages.yml` is the complete default reference.

## Prefix and templates

`prefix` applies automatically to all player and console command responses, help/list lines, selection feedback, protection denials, asynchronous save/backup replies, and maintenance kick text. Server diagnostics and audit entries use the normal server logger instead.

```yaml
prefix: '&8[&bSaltyClaims&8] &r'
commands:
  allowance: '&eUsed {used} / {total} blocks.'
errors:
  missing-permission: '&cMissing permission: {permission}'
```

The default prefix is `[SaltyClaims] `. Standard legacy `&` colors/formats are translated in configured templates and the prefix. MiniMessage and `&#rrggbb` syntax are not supported. Add `&r` at the end of a colored prefix to reset its style.

Use `{name}` placeholders shown in the default template. Replacements are literal and happen in one pass after template color translation: replacement text is not recursively interpreted as placeholders or `&` colors. Command syntax, flags, role identifiers, bounds, and policy sources remain runtime data. Technical parser/configuration diagnostics may appear through `{reason}`. Player identifiers render as player names, worlds as world names, and claim identifiers as owner/location descriptions, including identifiers inside legacy custom templates. `{id}` retains its placeholder spelling for compatibility but displays the claim description. Unknown players and unloaded worlds use the configurable `labels` entries.

Use `prefix: ''` to disable the prefix, or set a specific template to `''` to suppress that response. Multiline messages use a YAML string block and receive the prefix on every line. YAML `|-` avoids a trailing empty line.

Every message value must be a string, apart from the positive integer `config-version` at the bottom. Quote boolean/number-like message values. Missing keys are added from bundled defaults while supplied values are preserved. Obsolete/custom paths are removed from this fixed-schema file and retained in backups. Invalid YAML, duplicate keys, wrong structures, and unsupported placeholders are rejected.

Version 2 updates unchanged stock tool, targeting, overlap, and deletion instructions to automatic corner submission, player names, and `/claim delete confirm` (or `/claima delete confirm`). Customized wording and prefixes remain, except exact obsolete command spellings are converted to their supported replacements. Legacy `{token}` in deletion prompts and `{command}` in selection previews remain accepted and expand to empty strings. New name/location labels and targeting errors are added; the obsolete `errors.uuid` path is removed. See the migration history for exact conversions.

Version 3 simplifies the stock deletion prompt to `Type /{command} delete confirm to delete this {type}.` The type uses `labels.claim` or `labels.child-claim`. Stock child selection/help errors explain inferred height instead of requiring a full/3D argument. Custom prompts remain preserved, including their legacy `{id}`, `{seconds}`, and `{token}` placeholders. The confirmation timeout remains 30 seconds.

Version 4 adds `selection.already-claimed: 'This land is already claimed by {owner}.'` for occupied tool clicks. Existing templates and prefixes are preserved; no paths are renamed, removed, or intentionally replaced. The owner placeholder is a player name, with the normal unknown-player fallback.

Version 5 adds `commands.confirm-reset-all` (`{count}`, `{seconds}`), `success.reset-all` (`{count}`), and `errors.reset-changed` for server-wide claim reset. Counts include both parents and children. Existing templates/prefixes are preserved with no renamed, removed, or intentionally replaced paths. `config.yml` and the database schema do not change.

Version 6 updates only exact stock `commands.tool`, `errors.selection-mode`, and `errors.select-root` instructions to separate tools. The tool template adds `{child-selection}`; customized tool templates may still omit it. Obsolete `commands.mode-basic`, `commands.mode-child`, and `errors.child-mode` paths are removed during reconciliation and retained in backups. Custom values on remaining paths and the prefix are preserved; no paths are renamed.

Version 7 expands exact stock `commands.list-entry`, `commands.bounds`, and `commands.trust-list` templates. It adds corner lines, grouped trust/owner/source lines, an empty-list response, and local/inherited labels. List/bounds rendering supplies `{width}`, `{length}`, `{height}`, `{corner1}` and `{corner2}`. Custom templates and prefix remain preserved; legacy list `{id}`, bounds `{bounds}`, and trust-list `{grants}` remain valid. No paths are renamed or removed. Local and inherited trust groups are distinct, with all four assignable levels shown even when empty.

Version 8 simplifies the stock wording across commands, selections, successes, denials and errors. Player-facing text uses Claim/Child claim, readable trust names, On/Off and Yes/No. Simple responses omit repeated locations and internal policy sources. Redundant preview, trust-owner/heading and raw flag-map responses are suppressed by empty defaults. The user's edited inspection wording, `This land is owned by {owner}`, is retained. Gameplay denials have a dedicated `protection.action-denied` message; selection errors still give specific reasons such as insufficient blocks or overlaps.

Info/list supply a compact `{size}` (width x length, plus height for bounded children). Corners label X/Z for full-height claims and X/Y/Z for 3D children. List entries use two lines. Custom messages can still use all version-7 placeholders, including hidden details; switching text supplied through `{enabled}` is now On/Off and displayed trust names are title case. Restricted-child success describes whether main-claim trust is On or Off.

The migration compares exact previous stock values against immutable packaged `config-migrations/messages-v7.yml` and `messages-v8.yml` snapshots. Only matching stock strings are replaced. Customized strings, empty responses, prefixes and comments in backups remain preserved; no paths are renamed or removed. These snapshots are migration history resources, not editable server configuration, and must never be updated for later releases. Version-7 placeholder contracts remain accepted even where the new default omits those fields.

## Reload and implementation

`/claima reload` requires `saltyclaims.command.claima.reload`. It uses the same versioning, migration, validation, backup and save pipeline as startup. It publishes the immutable bundle after saving and runtime application succeed. Failure keeps the previous runtime; completed file upgrades remain saved if a later save/application fails. Invalid startup files prevent dependent services starting and enter maintenance; fix them and restart. See [Configuration versions and recovery](configuration-versioning.md).

`message.Messages` is the only chat delivery service. `message.Notice` carries a stable message key and immutable placeholders through asynchronous saves. `message.MessageException` lets validation rules signal a configurable error without depending on chat formatting or Bukkit. Protection denials retain their existing rate limit.

Tests cover custom/default/empty prefixes, partial files, message suppression, multiline output, color handling, literal placeholders, invalid replacement snapshots, immutable asynchronous notices, and permission-denial formatting.

Version 9 removes the obsolete `commands.explain` path during reconciliation after removal of that command. The old value remains in backups. Remaining templates and prefixes are preserved; no new messages, renamed paths, or intentional replacements are introduced.

Version 10 moves `commands.tool` to `commands.help-tools` before pruning, preserving custom instructions and their three tool placeholders. Conflicting source/destination values reject migration; equal values are idempotent. Exact former stock tool text becomes a short tool summary, and exact former stock `commands.help-player` text becomes a multiline claiming guide. Other custom text remains. `commands.help-commands` separates the guide from the existing filtered command list. Help renders configured material names in readable lowercase words. `/claim tool` and its permission are removed; `/claim help` displays these instructions instead. Configuration and database versions are unchanged.

Version 11 adds `success.unclaimed` (`{type}`), `success.unclaimed-all` (optional `{count}`), and `errors.no-owned-claims` for immediate player claim removal. Existing templates and prefixes are preserved; no renamed, removed, or intentionally replaced paths. The delete confirmation template remains for administrative deletion only. Configuration and database versions are unchanged.

Version 12 adds the benchmark start/success/error responses (`commands.test-started`, `success.test`, `errors.test-busy`, `errors.test-failed`, `errors.test-report`). Completion/failure may include `{file}`. Existing templates and prefixes are preserved; no renamed, removed, or intentionally replaced paths. Configuration and database versions are unchanged.
