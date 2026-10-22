# Running claim tests

Run `/claima test` in-game or from the server console. Permission: `saltyclaims.command.claima.test`, default op. No arguments or confirmation are needed. Only one test can run at a time.

The command replies when finished with a log path under `plugins/SaltyClaims/test-reports/`. Logs are timestamped in UTC and have unique names, so repeated runs keep separate reports. Reports are retained until manually removed.

## What it measures

- Generated grids of 100, 1,000 and 10,000 main claims, with a 3D child on every fourth main claim and independent child trust.
- A grid of 1,000 larger 128 x 128 claims to exercise index coverage across multiple chunks.
- One main claim with 100 stacked 3D child claims in the same chunk.
- Index rebuilds, mixed hit/miss/boundary/child lookups, and lookup plus trust/flag policy checks.
- Claim creation, resizing, trust edits and deletion preparation, including the current full index rebuild path.
- SQLite initial saves, a single-claim delta save, deletion of owned claims, round-trip loading and reopening the database.
- Correct child-first lookup and policy results, rejection of overlaps/non-owner deletion, and persistence correctness.

The log includes plugin/server/Java/OS versions, available processor count, heap limits, online-player and live-claim counts, and starting TPS/average-tick-time snapshots. It includes no real claim coordinates, player names or UUIDs. Heap readings describe the whole JVM without forcing garbage collection; they do not measure retained plugin memory.

## Isolation and limits

All generated claims are separate immutable states, and SQLite tests use uniquely created temporary directories inside `test-reports/`. They never open the real `claims.db`, mutate live claim state, change blocks, load chunks, spawn entities, or render particles. Temporary databases are closed and removed after the run, including ordinary failures. An abrupt server/process stop may leave a `benchmark-work-*` directory containing only synthetic test data.

The suite runs on a dedicated background worker, separate from the live storage worker. No Bukkit/player/world APIs are called from that worker; server information is captured on the server thread before dispatch. Background work still consumes CPU, memory and disk bandwidth, so run controlled comparisons on the test server with similar player activity.

The workload has fixed caps and a two-minute soft deadline checked between operations. A database operation or validation already in progress can extend beyond the deadline; this is not a hard real-time kill. Plugin shutdown interrupts the worker. The command reports a failed/partial test when it cannot finish or a check fails; inspect the log for the reason. Failure does not put live storage into maintenance or replace existing protection.

## Comparing results

1. Use the same server hardware, Java version, heap settings and comparable player activity for both builds.
2. Run once to warm up, then keep three to five reports from each build.
3. Compare lookup, index rebuild and edit-preparation timings at each dataset size. Look at repeated results, not one unusually fast/slow sample.
4. Keep the report filename with the plugin build being compared.

Timings use microseconds per operation. Lookup/policy operations are measured in batches: median/p95/max summarize **batch averages**, not the tail latency of individual player events. Edit timings have seven measured samples after two warmups; their p95 is effectively the slowest sample. SQLite operations have one sample each, including full durability settings.

`PASS` means correctness checks completed. There is no automatic performance pass/fail threshold. These are synthetic background-thread measurements, not direct main-thread MSPT, real player load, event-adapter coverage, particle performance or Bedrock/Geyser acceptance. Starting TPS/tick values are context snapshots, not a measurement of the test's impact. Use live play and server profiling for those remaining checks.
