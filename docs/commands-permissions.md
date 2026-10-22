# Commands and permissions

## Current availability

`plugin.yml` registers `/claim` and `/claima`. `ClaimCommands.registerPermissions()` registers each executable path and its default programmatically. No standalone GP command aliases or shared root permission are registered.

Claims, child claims, trust, flags, and session-only administrative bypass are implemented. Commands and permission isolation have automated tests; live server interaction acceptance remains unverified.

## Commands

The tables below describe the implemented command paths. All geometry changes require a loaded world, valid bounds, and relevant authority. Player management commands accept `--owner <player>`, `--root`, and `--at <world> <x> <y> <z>`. Admin claim commands take the owner's player name immediately after their subcommand. Player arguments accept online or cached offline names; unresolved names are rejected without a remote lookup. UUID arguments are not accepted.

An owner name selects their sole root remotely. If the owner has multiple roots, stand in the intended claim or supply `--at`; the command rejects ambiguous targets. Location targeting chooses the containing child unless `--root` is supplied. Staff can use `here` instead of an owner to target their current location (or `--at` from console), including claims whose owner name is unavailable. Use `--owner <player>` to disambiguate a player literally named `here` or `confirm`.

Command structure: `/claim <subcommand>` for players and `/claima <subcommand>` for administration. Every executable subcommand, including nested subcommands, has its own permission node.

Player command nodes use `saltyclaims.command.claim.<path>`; administrative command nodes use `saltyclaims.command.claima.<path>`. A root command or grouping node must not impose an additional blanket permission that prevents a player from using an individually granted subcommand. Bare roots show only authorized help, subject to their respective help permission. Help and completion filter unavailable commands, while execution always checks permissions independently.

### Player commands

Player commands require a player sender. Context resolves to the child containing the player, otherwise its root. Targeting by owner, location, or root scope never bypasses authorization. Read-only list/inspection output avoids exposing private trust details to unrelated players.

| Command | Purpose | Permission | Claim authority |
| --- | --- | --- | --- |
| `/claim help` | Show commands available to the sender. | `saltyclaims.command.claim.help` | None |
| `/claim tool` | Explain tool items and selection modes; does not generate unlimited items. | `saltyclaims.command.claim.tool` | None |
| `/claim pos1` | Select the first block corner at the player's position. | `saltyclaims.command.claim.pos1` | Checked when committing |
| `/claim pos2` | Select the second block corner at the player's position. | `saltyclaims.command.claim.pos2` | Checked when committing |
| `/claim create` | Create a root from the current selection. | `saltyclaims.command.claim.create` | Available allowance and valid bounds |
| `/claim resize` | Apply the selection to the current claim. | `saltyclaims.command.claim.resize` | Owner |
| `/claim cancel` | Clear a selection or pending confirmation. | `saltyclaims.command.claim.cancel` | Sender's own session |
| `/claim info [owner]` | Inspect owner, bounds, and effective policy. | `saltyclaims.command.claim.info` | Public summary; private details only for authorized users |
| `/claim list` | List owned root claims and allowance. | `saltyclaims.command.claim.list` | Own claims |
| `/claim delete` | Delete the current claim, confirming destructive tree removal. | `saltyclaims.command.claim.delete` | Owner |
| `/claim delete confirm` | Confirm the pending deletion within 30 seconds. | `saltyclaims.command.claim.delete.confirm` plus `saltyclaims.command.claim.delete` | Revalidate original authority and target tree |
| `/claim trust <player\|public> [level]` | Set a local grant; omitted level means Build. | `saltyclaims.command.claim.trust` | Owner or scoped Manage; delegation limits apply |
| `/claim untrust <player\|public\|all>` | Remove local grants; explain remaining inherited/public access. | `saltyclaims.command.claim.untrust` | Owner or scoped Manage; cannot remove peer Manage grants |
| `/claim trustlist` | Show local and inherited grants and sources. | `saltyclaims.command.claim.trustlist` | Owner or scoped Manage |
| `/claim flags` | List effective flag values and their sources. | `saltyclaims.command.claim.flags` | Owner or scoped Manage |
| `/claim flag <key> <value\|reset>` | Set or remove a local flag override. | `saltyclaims.command.claim.flag` plus `saltyclaims.flag.<key>` | Owner; server locks still apply |
| `/claim explain <action> [player]` | Explain a policy decision at this location. | `saltyclaims.command.claim.explain` | Owner or scoped Manage; staff may inspect others |
| `/claim mode basic` | Switch selection to root claims. | `saltyclaims.command.claim.mode.basic` | Checked when committing |
| `/claim mode child` | Switch to automatic child selection: equal corner Y means full height; different Y means 3D. | `saltyclaims.command.claim.mode.child` | Owner of selected root; use `--root` when inside an existing child |
| `/claim child create` | Create a child inside the selected root. | `saltyclaims.command.claim.child.create` | Owner |
| `/claim child restrict <on\|off>` | Disable/enable inherited root trust. | `saltyclaims.command.claim.child.restrict` | Owner |

Trust levels are `access`, `container`, `build`, and `manage`. Public cannot receive Manage. Managers may grant/revoke only Access, Container, and Build within their scope. If `/claim untrust all` is used by a manager, it removes only grants the manager is authorized to remove and reports retained Manage grants. Inherited grants are never deleted through child scope.

Children use the normal info, resize, delete, trust, and flag commands once selected, each with its own command permission and the same claim authority checks. Creating a child requires that the selected parent is a root, never another child.

Parent selections always produce full-height rectangles regardless of corner Y. Child selections and tool resizes infer height from the two corners. Legacy `/claim mode child full` and `/claim mode child 3d` remain accepted, but both now use this automatic height rule. Manual position selection uses the same rule when committed; an admin resize with explicit coordinates uses its supplied/existing Y bounds.

Right-clicking claimed land with the selection tool shows its borders and owner name without setting or replacing either position. Existing owned corners can still start a resize, and child selection can use unoccupied parts of its selected parent. An existing child interior cannot start another child selection. Final mutation validation continues checking overlaps across the whole area, including claims between two otherwise valid corners.

GP-style actions are exposed through `/claim` subcommands only. For example, `/claim trust Alex access` grants Access and `/claim trust Alex manage` grants Manage, with the latter remaining owner-only. Right-clicking the second tool corner immediately attempts creation or resizing through the same validation and save path as commands. Insufficient allowance, overlap, invalid geometry, and missing permissions reject the operation; the first corner remains selected for a retry. Selection tools enforce the corresponding create/resize/child-create permission. Manual `/claim pos1` and `/claim pos2` selections still require the matching create/resize command.

### Staff and console commands

| Command | Purpose | Permission | Senders |
| --- | --- | --- | --- |
| `/claima help` | Show available administrative commands. | `saltyclaims.command.claima.help` | Player or console |
| `/claima inspect <owner>` | Inspect full claim/trust/flag state. | `saltyclaims.command.claima.inspect` | Player or console |
| `/claima bypass <on\|off>` | Toggle a session-only protection bypass. | `saltyclaims.command.claima.bypass` | Player |
| `/claima delete <owner>` | Confirm deletion of any claim/tree. | `saltyclaims.command.claima.delete` | Player or console |
| `/claima resize <owner> ...` | Resize using player selection or explicit bounds from console. | `saltyclaims.command.claima.resize` | Player or console |
| `/claima trust <owner> ...` | Manage trust in an explicit claim. | `saltyclaims.command.claima.trust` | Player or console |
| `/claima untrust <owner> <player\|public\|all>` | Remove local trust grants. | `saltyclaims.command.claima.untrust` | Player or console |
| `/claima trustlist <owner>` | Inspect local/inherited trust grants. | `saltyclaims.command.claima.trustlist` | Player or console |
| `/claima flag <owner> ...` | Edit a claim override; does not defeat server locks. | `saltyclaims.command.claima.flag` | Player or console |
| `/claima flags <owner>` | List effective flag values and sources. | `saltyclaims.command.claima.flags` | Player or console |
| `/claima balance show <player>` | Inspect starting, earned, bonus, and used allowance. | `saltyclaims.command.claima.balance.show` | Player or console |
| `/claima balance set <player> <amount>` | Set bonus allowance. | `saltyclaims.command.claima.balance.set` | Player or console |
| `/claima balance add <player> <amount>` | Add bonus allowance. | `saltyclaims.command.claima.balance.add` | Player or console |
| `/claima balance remove <player> <amount>` | Remove bonus allowance, subject to balance validation. | `saltyclaims.command.claima.balance.remove` | Player or console |
| `/claima reload` | Validate and apply configuration and messages together. | `saltyclaims.command.claima.reload` | Player or console |
| `/claima backup` | Request a consistent database backup. | `saltyclaims.command.claima.backup` | Player or console |
| `/claima delete confirm` | Confirm a bound administrative destructive action. | `saltyclaims.command.claima.delete.confirm` plus `saltyclaims.command.claima.delete` | Player or console |
| `/claima reset all` | Request deletion of every parent/child claim on this server. | `saltyclaims.command.claima.reset.all` | Player or console |
| `/claima reset all confirm` | Confirm that server-wide reset within 30 seconds. | `saltyclaims.command.claima.reset.all.confirm` plus `saltyclaims.command.claima.reset.all` | Player or console |

Each administrative action requires its exact command permission; there is no shared edit permission. Gameplay bypass does not authorize deletion, trust changes, policy configuration, or allowance changes. Configuration locks cannot be overridden with a claim flag command. Staff use the explicit `/claima` action for administrative authority rather than gaining unrelated player command capabilities.

Admin resize syntax is `/claima resize <owner> <minX> <minZ> <maxX> <maxZ> [minY maxY]`. If coordinates are omitted, a player sender's selection is used. Root claims remain full height. Trust syntax is `/claima trust <owner> <player|public> [level]`; flag syntax is `/claima flag <owner> <key> <value|reset>`. Console requires explicit coordinates for resizing. Admin resizes can exceed the owner's allowance, but still obey size, containment, overlap, and world rules.

For example, `/claima trust Alex Sam build --at world 100 64 200` grants Sam Build in Alex's claim at those coordinates. `/claima inspect here --at world 100 64 200` inspects that location without requiring a known owner name.

Deletion always uses a separate confirmation: request `/claim delete` or `/claima delete <owner>`, then use `/claim delete confirm` or `/claima delete confirm` within 30 seconds. The pending target is retained when the sender moves; a changed/deleted target or child tree invalidates confirmation. Only one deletion is pending per sender, and player/admin confirmations cannot substitute for each other. Confirmation takes no target arguments or token. Replace previously configured `saltyclaims.command.claim.confirm` and `saltyclaims.command.claima.confirm` grants with their respective `delete.confirm` nodes; the original delete permission is also checked again.

The default player prompt is `Type /claim delete confirm to delete this claim.` or `Type /claim delete confirm to delete this child claim.` The administrative prompt uses `/claima`. Deleting a parent removes its children in the same database transaction.

Server-wide resets have their own command and confirmation, without owner/location selectors or extra arguments. The request snapshots every claim; added, edited, or deleted claims invalidate it. Reset and single-claim delete requests replace each other for the same sender, but their confirmation commands cannot substitute for one another. Reset deletes all parent/child geometry, local trust, and local flags in one transaction; player earned/bonus balances and configured limits remain, and used area becomes zero. Only after saving succeeds are the empty protection state and cleared selections/previews published. Failure retains previous protection through the normal storage failure path. Existing configuration and backup files are untouched.

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
