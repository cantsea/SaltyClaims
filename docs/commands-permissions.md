# Commands and permissions

## Current availability

SaltyClaims does not currently provide any commands or permission nodes. `plugin.yml` contains no `commands` or `permissions` declarations, and the plugin entry point performs no programmatic registration.

Claim flags, trust roles, and administrative bypass behavior are also not implemented.

## Planned capabilities

The tables below are a proposed interface for the [plugin plan](plugin-plan.md), not available commands. Permission names and defaults are planning choices until implemented.

Confirmed command structure: `/claim <subcommand>` for players and `/claima <subcommand>` for administration. Every executable subcommand, including nested subcommands, has its own permission node. No standalone GP command aliases are planned.

Player command nodes use `saltyclaims.command.claim.<path>`; administrative command nodes use `saltyclaims.command.claima.<path>`. A root command or grouping node must not impose an additional blanket permission that prevents a player from using an individually granted subcommand. Bare roots show only authorized help, subject to their respective help permission. Help and completion filter unavailable commands, while execution always checks permissions independently.

### Player commands

Player commands require a player sender. Context resolves to the child containing the player, otherwise its root. Management commands support an explicit claim UUID or root scope; targeting a claim never bypasses authorization. Read-only list/inspection output must avoid exposing private trust details to unrelated players.

| Proposed command | Purpose | Permission | Claim authority |
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
| `/claim trust <player|public> [level]` | Set a local grant; omitted level means Build. | `saltyclaims.command.claim.trust` | Owner or scoped Manage; delegation limits apply |
| `/claim untrust <player|public|all>` | Remove local grants; explain remaining inherited/public access. | `saltyclaims.command.claim.untrust` | Owner or scoped Manage; cannot remove peer Manage grants |
| `/claim trustlist` | Show local and inherited grants and sources. | `saltyclaims.command.claim.trustlist` | Owner or scoped Manage |
| `/claim flags` | List effective flag values and their sources. | `saltyclaims.command.claim.flags` | Owner or scoped Manage |
| `/claim flag <key> <value|reset>` | Set or remove a local flag override. | `saltyclaims.command.claim.flag` plus `saltyclaims.flag.<key>` | Owner; server locks still apply |
| `/claim explain <action> [player]` | Explain a policy decision at this location. | `saltyclaims.command.claim.explain` | Owner or scoped Manage; staff may inspect others |
| `/claim mode basic` | Switch selection to root claims. | `saltyclaims.command.claim.mode.basic` | Checked when committing |
| `/claim mode child <full|3d>` | Switch to child selection; later milestone. | `saltyclaims.command.claim.mode.child` | Owner of containing root |
| `/claim child create` | Create a child inside the root using the selection; later milestone. | `saltyclaims.command.claim.child.create` | Owner |
| `/claim child restrict <on|off>` | Disable/enable inherited root trust; later milestone. | `saltyclaims.command.claim.child.restrict` | Owner |

Trust levels are `access`, `container`, `build`, and `manage`. Public cannot receive Manage. Managers may grant/revoke only Access, Container, and Build within their scope. If `/claim untrust all` is used by a manager, it removes only grants the manager is authorized to remove and reports retained Manage grants. Inherited grants are never deleted through child scope.

Children use the normal info, resize, delete, trust, and flag commands once selected, each with its own command permission and the same claim authority checks. Creating a child additionally requires that the selected parent is a root, never another child. No child command is enabled before the child milestone.

GP-style actions are exposed through `/claim` subcommands only. For example, `/claim trust Alex access` grants Access and `/claim trust Alex manage` grants Manage, with the latter remaining owner-only. Selection tools must enforce the corresponding create/resize/child-create permissions too, so a tool cannot bypass a denied command capability.

### Staff and console commands

| Proposed command | Purpose | Permission | Senders |
| --- | --- | --- | --- |
| `/claima help` | Show available administrative commands. | `saltyclaims.command.claima.help` | Player or console |
| `/claima inspect <id>` | Inspect full claim/trust/flag state. | `saltyclaims.command.claima.inspect` | Player or console |
| `/claima bypass <on|off>` | Toggle a session-only protection bypass. | `saltyclaims.command.claima.bypass` | Player |
| `/claima delete <id>` | Confirm deletion of any claim/tree. | `saltyclaims.command.claima.delete` | Player or console |
| `/claima resize <id> ...` | Resize using player selection or explicit bounds from console. | `saltyclaims.command.claima.resize` | Player or console |
| `/claima trust <id> ...` | Manage trust in an explicit claim. | `saltyclaims.command.claima.trust` | Player or console |
| `/claima flag <id> ...` | Edit a claim override; does not defeat server locks. | `saltyclaims.command.claima.flag` | Player or console |
| `/claima balance show <player>` | Inspect allowance after accounting is specified. | `saltyclaims.command.claima.balance.show` | Player or console |
| `/claima balance set <player> <amount>` | Set bonus allowance. | `saltyclaims.command.claima.balance.set` | Player or console |
| `/claima balance add <player> <amount>` | Add bonus allowance. | `saltyclaims.command.claima.balance.add` | Player or console |
| `/claima balance remove <player> <amount>` | Remove bonus allowance, subject to balance validation. | `saltyclaims.command.claima.balance.remove` | Player or console |
| `/claima reload` | Validate and apply supported configuration changes. | `saltyclaims.command.claima.reload` | Player or console |
| `/claima backup` | Request a consistent database backup. | `saltyclaims.command.claima.backup` | Player or console |
| `/claima confirm <token>` | Confirm a bound administrative destructive action. | `saltyclaims.command.claima.confirm` plus original action's permission | Player or console |

Each administrative action requires its exact command permission; there is no shared edit permission. Gameplay bypass does not authorize deletion, trust changes, policy configuration, or allowance changes. Configuration locks cannot be overridden with a claim flag command. Staff use the explicit `/claima` action for administrative authority rather than gaining unrelated player command capabilities.

### Proposed permission defaults

| Node | Default | Meaning |
| --- | --- | --- |
| Each concrete `saltyclaims.command.claim.<path>` node listed above | `true` individually | Its exact player subcommand, subject to claim authority and limits. |
| Each concrete `saltyclaims.command.claima.<path>` node listed above | `op` individually | Its exact administrative subcommand. |
| `saltyclaims.flag.<key>` | `true` for each initially registered flag | Per-flag editing eligibility; concrete nodes must be declared/documented when the registry is implemented. |
| `saltyclaims.admin.recovery` | `op` | Proposed login access during storage maintenance; does not bypass maintenance mutation restrictions. |

Do not assume arbitrary `*` nodes exist. If aggregate permission nodes are introduced, declare their children and document them. Player permissions do not grant access to somebody else's claim. Defaults and owner-editable flags remain reviewable before implementation.

## Reference requirements

When commands are implemented, document each command's syntax, aliases, purpose, arguments, required permission, player/console availability, and examples here.

When permission nodes are implemented, document each node's exact name, allowed actions, default grant (`true`, `false`, `op`, or `not op`, where applicable), and any child permissions or administrative bypass effects here.

Keep this reference synchronized with `plugin.yml` and any programmatic registrations. Document claim ownership, trust, and flag requirements separately from server permission checks where both apply.
