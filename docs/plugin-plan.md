# SaltyClaims plugin plan

## Status and scope

Original design and roadmap. The first implementation now includes root and child claims, GP-style trust, native flags, SQLite, selection, commands, and protection adapters. [Code documentation](code-documentation.md), [Commands and permissions](commands-permissions.md), and [Protection coverage](protection-matrix.md) describe current behavior. Items labeled proposed/deferred below remain design context, not a guarantee that every roadmap detail is implemented.

Implementation choices: 10,000 starting blocks, configurable root/child limits, no playtime accrual or spacing setting yet, one globally pending storage operation rather than a multi-request reservation queue, manual backups/retention, and programmatic per-subcommand permissions. Live Paper startup/multiplayer protection acceptance and detailed server profiling are still required. Source was written independently; no GP code was copied.

Confirmed requirements are single-server storage, lightweight protection, full-height rectangular root claims, native flags, GP-style trust, and later subdivisions/child claims that can be 3D. The hierarchy has exactly one subdivision level: a root can contain children, but a child cannot contain another child. Other choices below are proposed defaults, not additional confirmed requirements.

Deliver usable root claims first, then child claims as a separate milestone. Design persistence and authorization for both from the beginning.

## GriefPrevention reference

Use upstream GriefPrevention as a behavior and protection-coverage reference:

- [ClaimPermission source](https://github.com/GriefPrevention/GriefPrevention/blob/master/src/main/java/me/ryanhamshire/GriefPrevention/ClaimPermission.java) defines cumulative Access, Container, Build, and Manage levels, with owner-only Edit authority.
- [Claim source](https://github.com/GriefPrevention/GriefPrevention/blob/master/src/main/java/me/ryanhamshire/GriefPrevention/Claim.java) supports parent trust inheritance and restricted subdivisions; its subdivisions do not themselves have children.
- [Command reference](https://docs.griefprevention.com/commands/) documents familiar trust and claim-tool modes.
- [Player event handling source](https://github.com/GriefPrevention/GriefPrevention/blob/master/src/main/java/me/ryanhamshire/GriefPrevention/PlayerEventHandler.java) is a reference for interaction coverage and claiming-tool handling.

The upstream documentation describes ongoing v17+ changes. These are references reviewed on 2026-10-22, not a promise of compatibility with every GP version. Pin the reference revision when implementing each subsystem. SaltyClaims-specific rules below take precedence over assumed GP parity. No GP source code is copied by this plan.

## 1. Claiming workflow

### Root claims

- A root claim owns an inclusive X/Z rectangle across the world's entire buildable height.
- Proposed default tool: golden shovel for selection and resizing, stick for inspection; both configurable.
- Select opposite corners, preview borders, dimensions, and cost, then commit a validated claim. Clicking an existing corner enters resize mode.
- Inspection reports claim ID, owner, root/child context, and effective access, with temporary client-side boundary visuals. It never changes real blocks.
- Commands provide alternatives for selecting corners, creating, resizing, inspecting, listing, and deleting claims.
- Sessions are per player; cancel or expire on disconnect, world change, explicit cancellation, or timeout. Pending sessions are not persistent claims.
- Creation and resizing validate world availability, world border, configured world rules, dimensions, area budget, claim count, and overlaps, including reserved pending changes.
- Root claims cannot share any block. Adjacent rectangles are valid when their inclusive bounds do not intersect. Optional minimum spacing is configurable.
- Players can walk into claims by default. Access trust controls interactions, not physical entry. Entry bans are outside the initial scope.

### Claim allowance

Propose GP-style block accounting: root cost is `(maxX - minX + 1) * (maxZ - minZ + 1)`, calculated with checked wide arithmetic. Height does not affect cost.

Maintain starting allowance, earned allowance, and administrator bonus separately. Used area is derived from committed root claims. Reserve budget for pending creations/expansions; refund committed shrinkage/deletion exactly once. Claim and balance changes must be transactionally consistent.

Make playtime accrual optional, with a configurable cap and explicit activity criteria; do not persist every tick. Default amounts, accrual policy, and claim-count limits remain open. Economy integration and automatic first-chest claims are deferred.

## 2. Child claims and subdivisions

Support exactly one subdivision level: root -> child. A child cannot be a parent. Deeper nesting is outside the plugin's scope, not a deferred feature.

- Support a full-height subdivision or a bounded 3D child selected using two actual block positions, including Y.
- Each child has a stable ID and parent ID, uses the parent's world, and remains fully inside the root.
- Creation, loading, and reparenting validation must reject any child whose parent is itself a child.
- Siblings cannot intersect in 3D. Stacked children may share an X/Z footprint when their inclusive Y ranges do not intersect. A full-height child conflicts with any sibling occupying its footprint.
- A location resolves to its containing child, otherwise the root. This is deterministic because siblings cannot overlap.
- Children do not cost additional claim blocks; apply configurable count and minimum-size limits.
- The root owner retains ultimate control. Children do not have separate ownership or balances. Proposed first implementation permits only the owner or authorized staff to create, resize, delete, or configure children.
- A root resize that would exclude a child is rejected. It never silently clips or deletes children.
- Deleting a child removes its local trust and flags and restores the root's effective rules in that volume.
- Deleting a root with children requires explicit confirmation and removes the entire tree in one transaction. Inspect/delete commands distinguish the current child from the root.

### Trust inheritance

Children default to inheriting trust. For a subject, effective trust is the highest applicable grant among child, inherited root, and public grants. A weaker child grant does not reduce a stronger inherited grant.

A restricted child stops inheriting root trust grants, including root public grants. Local grants and local public trust still apply. The root owner and authorized staff remain able to manage it. Trust restriction does not reset flag inheritance.

Untrust removes a local grant only. If inherited or public access remains, the command must explain its source and suggest restricting the child when appropriate. Do not introduce hidden per-player deny entries in the first version.

## 3. Trust levels

| Level | Proposed authority |
| --- | --- |
| Visitor | Walking through the claim; no protected interactions by default. |
| Access | Doors, gates, buttons, levers, beds, and explicitly classified basic interactions. |
| Container | Access plus containers, crafting/utility inventories, approved crop harvesting/replanting, and animal interaction. |
| Build | Container plus building, breaking, buckets, and changing protected blocks/entities. |
| Manage | Build plus managing Access, Container, and Build grants in that claim scope. |
| Owner | All local management, including flags, boundaries, child configuration, deletion, and granting/revoking Manage. |

Visitor and Owner are derived states, not assignable trust levels. Store explicit stable Access/Container/Build/Manage identifiers, not Java enum positions.

Manage is proposed as GP-inspired permission delegation, not co-ownership. Managers cannot grant Manage, remove peer managers, change ownership/bounds/flags, or manage outside their authorized scope. The owner alone delegates management, apart from explicit staff authority.

Support named players by UUID and an explicit PUBLIC subject. Never represent public access as a fabricated player UUID. Public can receive Access, Container, or Build, never Manage. Resolve offline names without blocking gameplay threads, and reject unresolved targets clearly.

Permission nodes gate use of plugin features. They do not confer trust in arbitrary claims. Exact classification of animals, crops, decorative entities, vehicles, and utility blocks belongs in the protection matrix, so the broad role names do not hide exceptions.

## 4. Native flags

### Flag model

Each flag definition contains a stable key, typed value, description, default, edit permission, actor/event classification, and validation. Explicit overrides live on roots or children; absence means inheritance. Commands expose effective value and its source.

Use two value families:

- Actor access: `public`, `access`, `container`, `build`, `manage`, `owner`, or `deny`. These set the required trust threshold for an action. `deny` blocks that action even for the owner until the rule is changed, except for an explicitly authorized staff protection bypass.
- Environmental behavior: `allow` or `deny`, without a player trust check. Owners do not automatically exempt their own explosions or fire from these settings.

Recommended initial registry:

| Flag | Kind | Proposed default |
| --- | --- | --- |
| `block-break`, `block-place` | Actor access | `build` |
| `container-use` | Actor access | `container` |
| `door-use`, `switch-use`, `bed-use` | Actor access | `access` |
| `utility-use`, `crop-harvest`, `crop-replant` | Actor access | `container` |
| `bucket-use` | Actor access | `build` |
| `animal-interact` | Actor access | `container` |
| `animal-damage`, `decoration-edit`, `vehicle-edit` | Actor access | `build` |
| `pvp` | Environmental/combat policy | `deny` |
| `explosion-block-damage` | Environmental | `deny` |
| `fire-spread`, `mob-block-grief` | Environmental | `deny` |
| `fluid-crossing`, `piston-crossing`, `hopper-crossing` | Boundary policy | `deny` |

These are proposed defaults. Explosion entity damage, hostile mobs, vehicle riding, ignition, projectile effects, dispenser actions, and other variants must receive explicit entries in the protection matrix before release. Do not let an event fall through merely because its interaction differs from a common case.

### Precedence and authority

For each flag: server-enforced value -> child override -> root override -> world default -> global default. A server-enforced value wins everywhere and cannot be overridden by owners. Missing child flags inherit root values even when child trust is restricted.

Editing requires both claim ownership/staff authority and the flag's permission node. Manage trust alone cannot edit flags. Metadata management is independent of action flags, so an owner can fix a `deny` flag without being locked out of the command.

Server-enforced policies remain in force during ordinary staff protection bypass. Keep administrative policy editing separate from bypassing gameplay trust.

### Cross-boundary actions

Resolve all affected regions, not just the player's position or machine origin. Distinct root/child IDs form distinct protection boundaries, including vertically stacked children.

- Pistons: check every moved block, destination, extension/retraction, and destroyed block. Crossing requires source and destination crossing policy to permit it; check destination placement/destruction policy as applicable.
- Fluids: check source/destination rules on each flow across regions. Wilderness permission is taken from world configuration.
- Hoppers and other automated inventory transfers: check both inventories and both boundary policies. No automation receives player trust implicitly.
- Explosions: filter damage per affected block by its region, preserving allowed effects elsewhere. Entity effects require separate policy checks.
- PvP: proposed rule requires permission at attacker and victim positions, with attributable projectile/pet/effect sources checked too. Projectile launch context must not disappear when it crosses a boundary.
- Multi-block and multi-target actions: require permission for all affected locations, or filter individual effects only where the event API supports safe partial handling.

Allowing a crossing flag permits the crossing itself; it does not implicitly grant unrelated building or container access. The protection matrix must define the treatment of actorless actions and wilderness for each event.

## 5. Protection implementation

Use one authorization service that accepts action, actor/source, locations, and event context, and returns an allow/deny decision plus a reason and policy source. Event listeners translate Paper events into this service; commands use the same management rules.

Create a tracked protection matrix before listeners ship. Cover blocks and multi-block structures, inventories, doors/redstone controls, buckets/fluids, crops, entities, vehicles, projectiles, fire, explosions, pistons, and inventory automation. Include item frames, armor stands, lecterns, signs, mounts, fishing, splash/lingering effects, and dispenser interactions where relevant to the supported server version.

Respect prior event cancellation and never un-cancel another plugin's denial. Rate-limit denial messages. Avoid tasks per claim or per block, world-wide scans, persistent per-block records, or database queries in event handlers. Support the selected Paper version first; Folia and other region-thread models require a separate implementation decision.

## 6. Architecture and persistence

Proposed packages under `org.saltymc`:

| Package | Responsibility |
| --- | --- |
| `claim` | Bounds, roots/children, validation, claim service, indexes, and allowance accounting. |
| `trust` | Subjects, levels, inheritance, and delegation rules. |
| `flag` | Typed registry, defaults, overrides, policy resolution. |
| `protection` | Shared authorization and Paper event adapters. |
| `storage` | SQLite repository, migrations, immutable mutations, and serialized writes. |
| `command` | Commands, completion, explicit scope selection, and explanations. |
| `selection` | Tool state and temporary border visualization. |
| `config` | Validated world rules, defaults, messages, and feature settings. |
| `api` | Small read-only query surface and committed change events when needed. |

Keep domain tests independent of a running server. Do not build a multi-backend persistence framework before there is a need.

Follow [Storage design](storage-design.md): load into memory, reserve pending mutations, commit in a background transaction, then publish on the server thread and acknowledge. Root/child/trust/flag/balance changes must remain atomic where related.

On unrecoverable startup load/migration failure, propose fail-closed maintenance mode: deny ordinary player logins and gameplay mutations while allowing console and explicitly authorized recovery staff. Never run with an apparently empty claims map after a failed load. Runtime save failure retains loaded protection and rejects unsafe mutations.

## 7. Commands and administration

Confirmed command roots are `/claim` for players and `/claima` for administration, with all actions exposed as subcommands. No standalone GP-style aliases are planned. Every executable subcommand, including nested leaves, requires its own permission: `saltyclaims.command.claim.<path>` or `saltyclaims.command.claima.<path>`. Do not combine multiple actions under a shared edit/use gate. Help/completion filter permissions; execution and selection tools enforce the relevant capability independently. See [Commands and permissions](commands-permissions.md) for planned syntax and permission defaults.

Contextual commands resolve the current child, otherwise root; explicit root scope or claim UUID is available. Show the selected scope before destructive operations. Console uses explicit UUIDs. Confirmation tokens bind actor, target, revision, action, and expiry so a stale confirmation cannot delete a changed claim tree.

Staff receive separate permissions for every administrative subcommand, including inspect, bypass, delete, resize, trust, flag, individual balance operations, configuration reload, backups, and confirmation. Confirming rechecks both its own permission and the original action permission. Bypass is an explicit session toggle, off by default and reset on reconnect. Log staff mutations and bypass sessions; log mutating bypass actions with actor and claim, with bounded log rotation.

Configuration reload validates a replacement configuration before applying it. Unsupported changes that require restart are reported rather than partially applied. Do not implement whole-plugin reload.

## 8. Delivery milestones and acceptance

1. **Foundation:** restore Gradle wrapper, pin dependencies, verify target versions, add CI and minimal test setup. Acceptance: reproducible build and server load.
2. **Durable root claims:** schema, migrations, memory index, selection, create/resize/delete/list/inspect, proposed allowance model once chosen. Acceptance: correct negative-coordinate/boundary behavior, overlap and concurrent-budget rejection, persistence after restart, failed writes preserve state.
3. **Trust and core protection:** four assignable roles, public subject, delegation, shared authorization, basic block/inventory/entity protection. Acceptance: complete trust matrix, no privilege escalation, owner and staff rules verified.
4. **Native flags and protection coverage:** registry, defaults/locks, edit/explain commands, environmental/boundary adapters. Acceptance: flag precedence tests, multi-location attack tests, tracked event coverage and real-server two-player checks. Root-only release can ship after this milestone is hardened.
5. **Child claims:** full-height and 3D selection, child lookup, stacked siblings, inheritance/restriction, root mutation safeguards. Acceptance: upstairs/downstairs isolation, inherited trust explanations, transactional tree deletion, cross-child automation tests.
6. **Release hardening:** consistent backup/restore, migration recovery, load failure handling, large-dataset lookup/heap measurements, bounded queue/load testing, documentation and release packaging.

Benchmark representative claim counts and distributions, including a large root and many stacked children. Record lookup latency, tick impact, startup time, and heap rather than asserting a performance target without measurements.

## 9. Deferred and open choices

Deferred: independent child owners, economy, renting, transfers, external database services, GP data import, automatic expiry, first-chest claiming, chat moderation, siege, and terrain restoration. Child-inside-child nesting is excluded.

Still choose: allowance amounts/accrual rules, minimum sizes/counts, configurable spacing, flag defaults exposed to ordinary owners, backup retention, and exact recovery-staff access. Proposed child/Manage rules in this plan should be reviewed before implementing those milestones.
