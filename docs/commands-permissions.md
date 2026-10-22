# Commands and permissions

## Current availability

`plugin.yml` registers `/claim` and `/claima`. `ClaimCommands.registerPermissions()` registers each executable path and its default programmatically. No standalone GP command aliases or shared root permission are registered.

Claims, child claims, trust, flags, and session-only administrative bypass are implemented. Commands and permission isolation have automated tests; live server interaction acceptance remains unverified.

## Commands

The tables below describe the implemented command paths. All geometry changes require a loaded world, valid bounds, and relevant authority. Player management commands accept `--id <uuid>` or `--root`; admin claim commands take the UUID positionally, immediately after their subcommand. Unknown offline names must already be in the server cache, or be supplied as UUIDs; name resolution never blocks on a remote lookup.

Command structure: `/claim <subcommand>` for players and `/claima <subcommand>` for administration. Every executable subcommand, including nested subcommands, has its own permission node.

Player command nodes use `saltyclaims.command.claim.<path>`; administrative command nodes use `saltyclaims.command.claima.<path>`. A root command or grouping node must not impose an additional blanket permission that prevents a player from using an individually granted subcommand. Bare roots show only authorized help, subject to their respective help permission. Help and completion filter unavailable commands, while execution always checks permissions independently.

### Player commands

Player commands require a player sender. Context resolves to the child containing the player, otherwise its root. Management commands support an explicit claim UUID or root scope; targeting a claim never bypasses authorization. Read-only list/inspection output must avoid exposing private trust details to unrelated players.

| Command | Purpose | Permission | Claim authority |
| --- | --- | --- | --- |
| `/claim help` | Show commands available to the sender. | `saltyclaims.command.claim.help` | None |
| `/claim tool` | Explain tool items and selection modes; does not generate unlimited items. | `saltyclaims.command.claim.tool` | None |
| `/claim pos1` | Select the first block corner at the player's position. | `saltyclaims.command.claim.pos1` | Checked when committing |
| `/claim pos2` | Select the second block corner at the player's position. | `saltyclaims.command.claim.pos2` | Checked when committing |
| `/claim create` | Create a root from the current selection. | `saltyclaims.command.claim.create` | Available allowance and valid bounds |
| `/claim resize` | Apply the selection to the current claim. | `saltyclaims.command.claim.resize` | Owner |
| `/claim cancel` | Clear a selection or pending confirmation. | `saltyclaims.command.claim.cancel` | Sender's own session |
| `/claim info [id]` | Inspect identity, bounds, and effective policy. | `saltyclaims.command.claim.info` | Public summary; private details only for authorized users |
| `/claim list` | List owned root claims and allowance. | `saltyclaims.command.claim.list` | Own claims |
| `/claim delete` | Delete the current claim, confirming destructive tree removal. | `saltyclaims.command.claim.delete` | Owner |
| `/claim confirm <token>` | Confirm the specific pending destructive action. | `saltyclaims.command.claim.confirm` plus original action's permission | Revalidate original authority and target revision |
| `/claim trust <player\|public> [level]` | Set a local grant; omitted level means Build. | `saltyclaims.command.claim.trust` | Owner or scoped Manage; delegation limits apply |
| `/claim untrust <player\|public\|all>` | Remove local grants; explain remaining inherited/public access. | `saltyclaims.command.claim.untrust` | Owner or scoped Manage; cannot remove peer Manage grants |
| `/claim trustlist` | Show local and inherited grants and sources. | `saltyclaims.command.claim.trustlist` | Owner or scoped Manage |
| `/claim flags` | List effective flag values and their sources. | `saltyclaims.command.claim.flags` | Owner or scoped Manage |
| `/claim flag <key> <value\|reset>` | Set or remove a local flag override. | `saltyclaims.command.claim.flag` plus `saltyclaims.flag.<key>` | Owner; server locks still apply |
| `/claim explain <action> [player]` | Explain a policy decision at this location. | `saltyclaims.command.claim.explain` | Owner or scoped Manage; staff may inspect others |
| `/claim mode basic` | Switch selection to root claims. | `saltyclaims.command.claim.mode.basic` | Checked when committing |
| `/claim mode child <full\|3d>` | Switch to child selection. | `saltyclaims.command.claim.mode.child` | Owner of selected root; use `--root` when inside an existing child |
| `/claim child create` | Create a child inside the selected root. | `saltyclaims.command.claim.child.create` | Owner |
| `/claim child restrict <on\|off>` | Disable/enable inherited root trust. | `saltyclaims.command.claim.child.restrict` | Owner |

Trust levels are `access`, `container`, `build`, and `manage`. Public cannot receive Manage. Managers may grant/revoke only Access, Container, and Build within their scope. If `/claim untrust all` is used by a manager, it removes only grants the manager is authorized to remove and reports retained Manage grants. Inherited grants are never deleted through child scope.

Children use the normal info, resize, delete, trust, and flag commands once selected, each with its own command permission and the same claim authority checks. Creating a child requires that the selected parent is a root, never another child.

GP-style actions are exposed through `/claim` subcommands only. For example, `/claim trust Alex access` grants Access and `/claim trust Alex manage` grants Manage, with the latter remaining owner-only. Selection tools must enforce the corresponding create/resize/child-create permissions too, so a tool cannot bypass a denied command capability.

### Staff and console commands

| Command | Purpose | Permission | Senders |
| --- | --- | --- | --- |
| `/claima help` | Show available administrative commands. | `saltyclaims.command.claima.help` | Player or console |
| `/claima inspect <id>` | Inspect full claim/trust/flag state. | `saltyclaims.command.claima.inspect` | Player or console |
| `/claima bypass <on\|off>` | Toggle a session-only protection bypass. | `saltyclaims.command.claima.bypass` | Player |
| `/claima delete <id>` | Confirm deletion of any claim/tree. | `saltyclaims.command.claima.delete` | Player or console |
| `/claima resize <id> ...` | Resize using player selection or explicit bounds from console. | `saltyclaims.command.claima.resize` | Player or console |
| `/claima trust <id> ...` | Manage trust in an explicit claim. | `saltyclaims.command.claima.trust` | Player or console |
| `/claima untrust <id> <player\|public\|all>` | Remove local trust grants. | `saltyclaims.command.claima.untrust` | Player or console |
| `/claima trustlist <id>` | Inspect local/inherited trust grants. | `saltyclaims.command.claima.trustlist` | Player or console |
| `/claima flag <id> ...` | Edit a claim override; does not defeat server locks. | `saltyclaims.command.claima.flag` | Player or console |
| `/claima flags <id>` | List effective flag values and sources. | `saltyclaims.command.claima.flags` | Player or console |
| `/claima balance show <player>` | Inspect starting, earned, bonus, and used allowance. | `saltyclaims.command.claima.balance.show` | Player or console |
| `/claima balance set <player> <amount>` | Set bonus allowance. | `saltyclaims.command.claima.balance.set` | Player or console |
| `/claima balance add <player> <amount>` | Add bonus allowance. | `saltyclaims.command.claima.balance.add` | Player or console |
| `/claima balance remove <player> <amount>` | Remove bonus allowance, subject to balance validation. | `saltyclaims.command.claima.balance.remove` | Player or console |
| `/claima reload` | Validate and apply configuration and messages together. | `saltyclaims.command.claima.reload` | Player or console |
| `/claima backup` | Request a consistent database backup. | `saltyclaims.command.claima.backup` | Player or console |
| `/claima confirm <token>` | Confirm a bound administrative destructive action. | `saltyclaims.command.claima.confirm` plus original action's permission | Player or console |

Each administrative action requires its exact command permission; there is no shared edit permission. Gameplay bypass does not authorize deletion, trust changes, policy configuration, or allowance changes. Configuration locks cannot be overridden with a claim flag command. Staff use the explicit `/claima` action for administrative authority rather than gaining unrelated player command capabilities.

Admin resize syntax is `/claima resize <id> <minX> <minZ> <maxX> <maxZ> [minY maxY]`. If coordinates are omitted, a player sender's selection is used. Root claims remain full height. Trust syntax is `/claima trust <id> <player|public> [level]`; flag syntax is `/claima flag <id> <key> <value|reset>`. Console requires explicit coordinates for resizing. Admin resizes can exceed the owner's allowance, but still obey size, containment, overlap, and world rules.

### Permission defaults

| Node | Default | Meaning |
| --- | --- | --- |
| Each concrete `saltyclaims.command.claim.<path>` node listed above | `true` individually | Its exact player subcommand, subject to claim authority and limits. |
| Each concrete `saltyclaims.command.claima.<path>` node listed above | `op` individually | Its exact administrative subcommand. |
| `saltyclaims.flag.<key>` | `true` individually | Owner flag-edit eligibility; replace `<key>` with a concrete registered flag below. |
| `saltyclaims.admin.recovery` | `op` | Login access during storage maintenance; does not bypass maintenance mutation restrictions. |

No wildcard or aggregate permission nodes are registered. Player permissions do not grant access to somebody else's claim. Explicit staff actions use their corresponding admin nodes; gameplay bypass never substitutes for them.

### Registered flags

Each key below has its own `saltyclaims.flag.<key>` permission. Actor flags accept `public`, `access`, `container`, `build`, `manage`, `owner`, or `deny`. Environment/boundary flags accept `allow` or `deny`. `reset` removes the local override.

| Keys | Built-in claim default |
| --- | --- |
| `block-break`, `block-place`, `bucket-use`, `animal-damage`, `decoration-edit`, `vehicle-edit`, `ignite` | `build` |
| `container-use`, `utility-use`, `crop-harvest`, `crop-replant`, `animal-interact` | `container` |
| `door-use`, `switch-use`, `bed-use`, `vehicle-use` | `access` |
| `pvp`, `explosion-block-damage`, `explosion-entity-damage`, `fire-spread`, `mob-block-grief`, `fluid-crossing`, `piston-crossing`, `hopper-crossing` | `deny` |

Missing configuration uses these defaults inside claims and permissive behavior in wilderness. Explicit global/world configuration also applies in wilderness. Enforced values win everywhere. Restricted children still inherit parent flag values. Actor `deny` blocks owners until they change the setting; environmental policy has no owner exemption.

## Reference requirements

When commands are implemented, document each command's syntax, aliases, purpose, arguments, required permission, player/console availability, and examples here.

When permission nodes are implemented, document each node's exact name, allowed actions, default grant (`true`, `false`, `op`, or `not op`, where applicable), and any child permissions or administrative bypass effects here.

Keep this reference synchronized with `plugin.yml` and any programmatic registrations. Document claim ownership, trust, and flag requirements separately from server permission checks where both apply.
