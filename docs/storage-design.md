# Storage design

## Scope and status

Claims belong to one server at a time. Shared multi-server access is not required.

Initial claims are full-height rectangles in the X/Z plane. A later release will add subdivisions or child claims that can have bounded 3D volumes.

The hierarchy is limited to root -> child. A child's parent must be a root; a child can never contain another child. Enforce this invariant when loading and mutating claim data, with database validation as a second layer when implementing the schema.

This document describes the planned implementation. The current plugin does not yet implement storage.

## Storage layout

Use SQLite at `plugins/SaltyClaims/claims.db` for persistent claim data. Keep administrator configuration and default flag values in YAML. Do not introduce an external database service for the initial release.

The initial design uses SQLite's standard rollback journal with `synchronous=FULL`, foreign-key enforcement on the connection, and one background database worker. WAL can be reconsidered if the access pattern changes.

## Logical schema

| Table | Responsibility |
| --- | --- |
| `claims` | Claim UUID, owner UUID, world UUID, optional parent claim UUID, bounds, and creation/modification timestamps. |
| `claim_trust` | Claim/player UUID pairs and granted roles. Exact role semantics remain undecided. |
| `claim_flags` | Claim UUID, stable flag key, and explicitly configured value. |
| `schema_migrations` | Applied schema versions. |

Add `player_data` only when a feature requires persistent player-level data, such as claim allowance.

For the proposed GP-style allowance system, player records separate starting, earned, and bonus allowance; used area derives from committed roots. Claim mutations and related allowance changes must be transactionally consistent. See [Plugin plan](plugin-plan.md) for proposed feature semantics.

Trust records use an explicit subject type (`PLAYER` or `PUBLIC`) and a stable level (`access`, `container`, `build`, `manage`), with at most one local grant per claim/subject. A public subject is not a fake player UUID. Child records also persist a trust-inheritance mode; restricting trust does not stop flag inheritance.

### Bounds and future child claims

The proposed claim record includes:

- `min_x`, `max_x`, `min_z`, and `max_z`: inclusive integer block bounds, normalized so each minimum is no greater than its maximum.
- `parent_claim_id`: nullable reference to another claim. Initial claims always have no parent.
- `min_y` and `max_y`: nullable integer block bounds. Both are absent for full-height claims; both are present for bounded 3D children, with `min_y <= max_y`.

Represent full height explicitly through absent Y bounds, rather than storing a world's current height limits or artificial extreme values. Full-height claims therefore continue to cover the world's buildable height if world height limits change.

The initial release accepts only parentless, full-height rectangles. Reserve the parent and Y fields in the storage model for the later child-claim feature; no child creation or inheritance behavior is implemented initially.

When child claims are introduced, require the same world as their parent, complete containment in the parent volume, and an acyclic parent relationship. Root claims cannot overlap in X/Z. Ancestor/descendant overlap is intentional. Ordinary block bounds are inclusive, so two regions cannot own the same boundary block unless they are in a permitted ancestor/descendant relationship.

One child level is a confirmed requirement. The plugin plan additionally proposes no sibling 3D intersection, root ownership for all children, optional trust inheritance/restriction, independent flag inheritance, rejection of root resizes that exclude children, and confirmed transactional tree deletion. Stacked 3D siblings can share X/Z when their Y ranges are disjoint. Child ownership is derived from the root rather than maintained as an independently editable duplicate owner. These additional rules are proposed for the later child milestone.

Use primary keys, uniqueness constraints, and foreign keys to enforce record identity and prevent orphaned trust or flag entries. Delete related claim data in the same transaction as its claim.

Use UUIDs for player and world identity. Display names are optional cached metadata. Use stable textual flag keys, not enum ordinals. Validate flag values against the typed flag registry.

Store only explicit flag overrides. Missing overrides inherit the applicable configuration default; do not copy defaults into every claim.

## Read path

Load and validate persistent data before enabling claim operations and protection listeners. Build in-memory indexes by claim ID, owner UUID, and world/chunk. A location lookup checks candidate claims from the chunk index against exact bounds.

The initial lookup matches root claims by X/Z regardless of Y. Future child lookups can use the same chunk candidates and additionally test Y bounds and ancestry. A single flat no-overlap rule must not be reused for future parent/child regions.

Protection events must not perform database or filesystem I/O. Do not store per-block ownership records. Apply claim mutations and update all indexes on the server thread; the database worker receives immutable save data and does not access live Bukkit objects.

## Mutation path

1. Validate the request on the server thread against committed data and pending reservations.
2. Reserve affected claims or boundaries so conflicting requests cannot proceed. Overlap checks must account for pending creations and resizes.
3. Submit immutable mutation data to the database worker and commit all related changes in one transaction.
4. On successful commit, publish the change and update indexes on the server thread, then release reservations and report success.
5. On failure, release reservations, retain the previous in-memory state, and report the failure.

Existing protection remains in force until a successful change is published. New claims become active after commit and publication. Bound the pending work queue and reject new mutations clearly if storage cannot keep up.

## Startup, shutdown, and recovery

- Never interpret a database read or migration failure as an empty claims database. Report the error explicitly. The plugin plan proposes maintenance mode that denies normal logins and gameplay mutations rather than enabling an unprotected server; exact recovery access remains to be specified.
- Keep loaded protection active if later writes fail, and reject mutations that cannot be persisted safely.
- Validate loaded records, including bounds and overlapping claims; do not silently discard invalid records. Claims for unavailable worlds retain their world UUID and must not be reassigned by world name.
- Use versioned, transactional schema migrations and create a consistent backup before changing an existing database schema.
- On shutdown, stop accepting mutations and drain accepted storage work before closing the connection, with a bounded wait and explicit error reporting if work remains.
- Produce backups through a supported consistent database backup mechanism or after fully closing the database. Avoid copying an actively written database file directly.

## Decisions still open

- Review proposed child-claim and Manage delegation rules in the plugin plan before implementing those milestones.
- Final native flag defaults and event-specific action classifications.
- Claim allowance model and whether player-level records are needed.
- Exact maintenance/recovery behavior when protection data cannot be loaded.
- Backup retention and administrator-facing backup controls.
