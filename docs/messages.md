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

Use `{name}` placeholders shown in the default template. Replacements are literal and happen in one pass after template color translation: replacement text is not recursively interpreted as placeholders or `&` colors. Command syntax, IDs, flags, role identifiers, bounds, policy sources, and debug map data remain runtime data. Technical parser/configuration diagnostics may appear through `{reason}`.

Use `prefix: ''` to disable the prefix, or set a specific template to `''` to suppress that response. Multiline messages use a YAML string block and receive the prefix on every line. YAML `|-` avoids a trailing empty line.

Every message value must be a string, apart from the positive integer `config-version` at the bottom. Quote boolean/number-like message values. Missing keys are added from bundled defaults while supplied values are preserved. Obsolete/custom paths are removed from this fixed-schema file and retained in backups. Invalid YAML, duplicate keys, wrong structures, and unsupported placeholders are rejected.

## Reload and implementation

`/claima reload` requires `saltyclaims.command.claima.reload`. It uses the same versioning, migration, validation, backup and save pipeline as startup. It publishes the immutable bundle after saving and runtime application succeed. Failure keeps the previous runtime; completed file upgrades remain saved if a later save/application fails. Invalid startup files prevent dependent services starting and enter maintenance; fix them and restart. See [Configuration versions and recovery](configuration-versioning.md).

`message.Messages` is the only chat delivery service. `message.Notice` carries a stable message key and immutable placeholders through asynchronous saves. `message.MessageException` lets validation rules signal a configurable error without depending on chat formatting or Bukkit. Protection denials retain their existing rate limit.

Tests cover custom/default/empty prefixes, partial files, message suppression, multiline output, color handling, literal placeholders, invalid replacement snapshots, immutable asynchronous notices, and permission-denial formatting.
