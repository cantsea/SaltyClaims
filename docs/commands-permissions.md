# Commands and permissions

## Current availability

`plugin.yml` registers `/claim`, `/claima`, and `/unclaim`. `ClaimCommands.registerPermissions()` registers each executable path and its default programmatically. There is no shared root permission.

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
| `/claim help` | Explain tools, corner selection, child height, resizing and resets, then show authorized commands. | `saltyclaims.command.claim.help` | None |
| `/claim resize` | Apply the selection to the current claim. | `saltyclaims.command.claim.resize` | Owner |
| `/claim cancel` | Clear a selection or pending confirmation. | `saltyclaims.command.claim.cancel` | Sender's own session |
| `/claim info [owner]` | Show owner, both corners, size/area, and grouped trust from this claim and its main claim. | `saltyclaims.command.claim.info` | Public summary; trust details only for owner/Manage |
| `/claim list` | List owned parents and their children with dimensions, area, both corners, and allowance. | `saltyclaims.command.claim.list` | Own claims |
| `/unclaim` | Immediately remove the child here, otherwise the main claim and its children; no confirmation. | `saltyclaims.command.unclaim` | Owner; player only |
| `/unclaim all` | Immediately remove all owned claims and children across all worlds; no confirmation. | `saltyclaims.command.unclaim.all` | Owner; player only |
| `/claim trust <player\|public> [level]` | Set a local grant; omitted level means Build. | `saltyclaims.command.claim.trust` | Owner or scoped Manage; delegation limits apply |
| `/claim untrust <player\|public\|all>` | Remove local grants; explain remaining inherited/public access. | `saltyclaims.command.claim.untrust` | Owner or scoped Manage; cannot remove peer Manage grants |
| `/claim trustlist` | Show local and inherited grants and sources. | `saltyclaims.command.claim.trustlist` | Owner or scoped Manage |
| `/claim flags` | List effective flag values and their sources. | `saltyclaims.command.claim.flags` | Owner or scoped Manage |
| `/claim flag <key> <value\|reset>` | Set or remove a local flag override. | `saltyclaims.command.claim.flag` plus `saltyclaims.flag.<key>` | Owner; server locks still apply |
| `/claim child create` | Create a child inside the selected root. | `saltyclaims.command.claim.child.create` | Owner |
| `/claim child restrict <on\|off>` | Disable/enable inherited root trust. | `saltyclaims.command.claim.child.restrict` | Owner |

Trust levels are `access`, `container`, `build`, and `manage`. Public cannot receive Manage. Managers may grant/revoke only Access, Container, and Build within their scope. If `/claim untrust all` is used by a manager, it removes only grants the manager is authorized to remove and reports retained Manage grants. Inherited grants are never deleted through child scope.

Every ordinary trust/untrust action targets the child containing the sender, otherwise the parent. Explicit `--root` remains available when deliberately editing parent grants. No bulk trust commands are provided. Parent inheritance can still provide higher effective access than a child grant; restricting a child disables that inheritance.

Info shows normalized inclusive corner 1 (minimum X/Y/Z) and corner 2 (maximum X/Y/Z), rather than remembering click order. Full-height claims show labeled X/Z coordinates; bounded child claims also show Y. Owners and managers see the owner plus separate local/inherited lists for Access, Container, Build, and Manage, with `none` for empty levels. Restricted children show inheritance as disabled. List output places each parent before its children and uses two short lines with dimensions, area, world and both corners. Child areas are descriptive and are not charged again.

Children use the normal info, resize, delete, trust, and flag commands once selected, each with its own command permission and the same claim authority checks. Creating a child requires that the selected parent is a root, never another child.

Regular selection uses the stick (`tools.selection`), child selection the bone (`tools.child-selection`), and inspection the feather (`tools.inspection`), with three distinct configurable items. The child tool selects the owning parent from the first click, so it must start inside the sender's parent claim. Switching away from either claim tool clears both corners, selected parent/resize state and its particle preview immediately. Returning to the tool starts a new selection. Cancelled item switches preserve selection. The mode commands and their `saltyclaims.command.claim.mode.basic` / `mode.child` permissions are removed. Tool actions retain their normal create/child-create/resize permissions.

Parent selections always produce full-height rectangles regardless of corner Y. Child selections and tool resizes infer height from the two corners. An admin resize with explicit coordinates uses its supplied/existing Y bounds.

Selecting an owned corner with the matching tool shows the entire old border rather than a single opposite block. It remains the displayed size while choosing the new corner, subject to the configured particle duration. A rejected attempt clears the selection and preview. The replacement border is shown only after a successful resize save.

Right-clicking claimed land with the selection tool shows its borders and owner name and clears any in-progress selection. Existing owned corners can still start a resize, and child selection can use unoccupied parts of its selected parent. An existing child interior cannot start another child selection. Final mutation validation continues checking overlaps across the whole area, including claims between two otherwise valid corners.

GP-style actions are exposed through `/claim` subcommands only. For example, `/claim trust Alex access` grants Access and `/claim trust Alex manage` grants Manage, with the latter remaining owner-only. Right-clicking the second tool corner immediately attempts creation or resizing through the same validation and save path as commands. Insufficient allowance, overlap, invalid geometry, and missing permissions reject the operation; both corners and the selected parent/resize target are cleared, so the next click starts a new selection. Selection tools enforce the corresponding create/resize/child-create permission.

`/claim help` (also shown by bare `/claim`) explains claiming with the configured regular, child and inspection tools. Item names are shown in readable lowercase text, and the instructions cover two corners, inferred child height, resizing and automatic selection resets. Available commands follow, filtered by their individual permissions. `/claim tool` and its permission are removed, along with `/claim pos1`, `/claim pos2`, `/claim create`, and `/claim explain`. Help does not give items or start a selection. The existing `saltyclaims.command.claim.create` permission remains registered (default true) exclusively to authorize regular tool creation; the other removed command permissions are no longer registered.

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

Player removal uses `/unclaim` or `/unclaim all`, without confirmation or owner/location selectors. The two permissions are independent: granting `.all` does not require the base command permission. Replace old `saltyclaims.command.claim.delete`/`delete.confirm` grants with the appropriate unclaim permissions; old player delete paths/nodes are removed. Only owned claims can be removed, even with Manage trust or bypass. Balances are preserved and used area is recomputed. Successful persistence updates protection and clears the sender's selection/preview; failed transactions preserve existing claims.

Administrative deletion still uses `/claima delete <owner>` followed by `/claima delete confirm` within 30 seconds. It binds the sender, original permission, and unchanged target tree; movement does not retarget it. Deleting a parent removes its children in one transaction. Administrative reset retains its separate confirmation.

Server-wide resets have their own command and confirmation, without owner/location selectors or extra arguments. The request snapshots every claim; added, edited, or deleted claims invalidate it. Reset and single-claim delete requests replace each other for the same sender, but their confirmation commands cannot substitute for one another. Reset deletes all parent/child geometry, local trust, and local flags in one transaction; player earned/bonus balances and configured limits remain, and used area becomes zero. Only after saving succeeds are the empty protection state and cleared selections/previews published. Failure retains previous protection through the normal storage failure path. Existing configuration and backup files are untouched.

### Permission defaults

| Node | Default | Meaning |
| --- | --- | --- |
| `saltyclaims.command.unclaim` | `true` | Immediately remove the sender's claim at their position. |
| `saltyclaims.command.unclaim.all` | `true` | Immediately remove every claim owned by the sender across worlds; independent of the base node. |
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
