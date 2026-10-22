# SaltyClaims documentation

SaltyClaims is a Paper Minecraft plugin providing SQLite-backed full-height root claims, one-level 3D/full-height subdivisions, trust levels, and native claim flags. The initial implementation builds and has automated tests; live multiplayer event acceptance is still required.

- [Code documentation](code-documentation.md): source layout, lifecycle, build configuration, and implementation status.
- [Commands and permissions](commands-permissions.md): available commands and permission nodes.
- [Messages](messages.md): configurable responses, the global prefix, placeholders, and reload behavior.
- [Configuration versions and recovery](configuration-versioning.md): independent file histories, strict validation, safe saves, backups, and future change requirements.
- [Storage design](storage-design.md): single-server persistence, invariants, and save behavior.
- [Protection coverage](protection-matrix.md): implemented event adapters and live acceptance checklist.
- [Running claim tests](testing.md): `/claima test`, benchmark reports, scenarios, comparisons and measurement limits.
- [Plugin plan](plugin-plan.md): original design, implementation status, and deferred features.

This directory is tracked in Git. Keep it aligned with the implementation; label proposed behavior explicitly. Local AI working notes belong in the ignored root `AI/` directory.
