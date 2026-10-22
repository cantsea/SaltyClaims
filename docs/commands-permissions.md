# Commands and permissions

## Current availability

SaltyClaims does not currently provide any commands or permission nodes. `plugin.yml` contains no `commands` or `permissions` declarations, and the plugin entry point performs no programmatic registration.

Claim flags, trust roles, and administrative bypass behavior are also not implemented.

## Planned capabilities

The proposed feature plan includes creating, resizing, inspecting, and deleting claims; managing trusted players; editing claim flags; and administering claims. Command syntax, aliases, permission nodes, defaults, and console support have not yet been finalized. These capabilities are not available in the current plugin.

## Reference requirements

When commands are implemented, document each command's syntax, aliases, purpose, arguments, required permission, player/console availability, and examples here.

When permission nodes are implemented, document each node's exact name, allowed actions, default grant (`true`, `false`, `op`, or `not op`, where applicable), and any child permissions or administrative bypass effects here.

Keep this reference synchronized with `plugin.yml` and any programmatic registrations. Document claim ownership, trust, and flag requirements separately from server permission checks where both apply.
