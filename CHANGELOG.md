# Changelog

All notable changes to the Scheduler Fork are documented here.

## 1.0.14-spp1 - 2026-07-26

First public release of the maintained Scheduler Fork. The Starsector-facing
runtime version remains `1.0.14`; `spp1` identifies this fork package and its
Prepatcher integration revision.

### Scheduler correctness

- Separated queue dirty bits from causal result validity. Each market now
  maintains revisions for structure, materialized state, price input,
  stockpiles, accessibility, trade input and temporal state.
- Limited work-ticket validation to the domains consumed by that operation.
  Accessibility-only or trade-only changes no longer invalidate unrelated
  price work, while structural and relevant input changes still reject stale
  results.
- Added targeted registry repair, atomic complete-registry publication,
  detailed commit statuses and invariant diagnostics.
- Made external trade matching deterministic and capped transfers by the
  actual available surplus.
- Corrected maximum-demand and stockpile setter behavior.

### Runtime lifecycle

- Added process-local `campaignEpoch`, `economyEpoch` and monotonic
  `batchRevision` stamps to price work, immutable calculation batches,
  committed trade cuts and global economy boundaries.
- Invalidated stale work across game load, economy replacement, development
  reload, reset and shutdown. Old results cannot publish into a new campaign
  or economy instance, even when a market object is reused.
- Made the worker executor restartable. Known futures are cancelled at
  lifecycle boundaries, the old executor is shut down and a new generation is
  created lazily on the next submission.
- Added a cooperative save barrier. Both successful and failed saves release
  workers through the same lifecycle path.
- Made pre-epoch `AoTDFinishEconomyUpdateTask` instances from older saves
  discard their stale work safely.

### Prepatcher integration

- Raised the minimum StarsectorPrepatcher version to `0.12.0` and the required
  production capability mask to `0x1ff`, including runtime epoch
  coordination.
- Updated `SchedulerBridge` to schema V6 and made capability-dependent
  operations read the active runtime mask instead of relying permanently on
  the startup snapshot.
- Added one-time market-generation resynchronization when
  `NATIVE_DELIVERY_EVENTS` is lost after a runtime `LinkageError`.
- Switched price capture immediately to dirty-state fallback after that
  downgrade. Capabilities missing from the startup negotiation cannot be
  enabled dynamically.
- Extended scheduler diagnostics with initial and live masks, lost
  capabilities, refresh/downgrade counts and resynchronization statistics.

### Verification recorded for this release

- Worker epoch, registry epoch, save barrier, trade epoch and legacy-task
  compatibility harnesses passed.
- Scheduler bridge transformation, runtime capability downgrade and delivery
  listener fail-stop checks passed.
- Bytecode verification with `java -Xverify:all` passed for the scheduler
  harnesses.
- The complete source tree, including the clean `BaseIndustry` source, compiles
  with Java 17 against the Starsector and compatibility-mod classpath.
- The release archive includes the complete checksummed source/runtime payload
  under one top-level mod directory.

## Earlier releases

Earlier release history is available in the Git history and release notes.
