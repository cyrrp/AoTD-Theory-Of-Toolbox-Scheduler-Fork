# Changelog

All notable changes to the Scheduler Fork are documented here.

## 1.0.14-spp7 - 2026-08-03

- Restored standard economy semantics: `AoTDEconomy.nextStep(...)`, `doubleStep()`, `tripleStep()`
  and `AoTDReachEconomy.nextStep(...)` always execute global all-market work, with vanilla
  one/two/three-step multiplicity preserved.
- Added one public final dispatcher for exact Prepatcher market-open, Cargo and market-mutation
  intents; standard steps no longer infer UI work from `currentlyOpenMarket`, null payloads or
  legacy context consumers.
- Removed market-open and Cargo context handoffs. Exact call-site guards now invoke the dispatcher
  directly and preserve the original virtual global call for rejection, error, missing
  barrier/capability and global-topology scopes.
- Kept mutation reason/scope/affected IDs in the Prepatcher one-shot setter/helper handoff and
  retained the existing `MarketRegistry` scheduler and targeted commodity rebuild.
- Restricted registration to exact spp7 with the exact current declared mask; spp4-spp6,
  unreviewed revisions and partial declarations are logged and rejected wholesale.
- Removed legacy bridge context consumers and the redundant numeric ABI parameter. The current V9
  bridge shape, canonical fork version and exact capability declaration are the only startup
  compatibility inputs.
- Made the required UI dispatcher capability independent of optional Prepatcher switches; safe
  profile activation receives `0x3ff`, while optional UI market-mutation refresh extends it to
  `0x7ff`.

## 1.0.14-spp6 - 2026-07-29

- Unified the complete UI market-mutation refresh under one optional capability and extended its
  Scheduler Bridge schema V9 payload.
- Added loader-neutral mutation payloads carrying packed reason/scope plus sorted affected
  commodity IDs.
- Added filtered `AoTDCommodityMarketData` global/econ-group rebuild for exact trade, free-port and
  supported industry paths.
- Suppressed all-commodity callbacks inside targeted local main tasks and published the sorted
  affected commodity set exactly once after the final local commit.
- Preserved the required production capability mask `0x3ff`; old bridge schemas and missing
  optional capability retain the original global scheduler path.
- Standardized all fork metadata and bridge markers on the canonical `1.0.14-spp6` identifier.

## 1.0.14-spp5 - 2026-07-29

- Requires StarsectorPrepatcher `0.15.0`.
- Added Scheduler Bridge schema V8 and an optional UI market-mutation capability. The required
  production mask remains `0x3ff`; a full V8 negotiation reports `0x7ff`.
- Added loader-neutral `consumeUiMarketMutation(Object)` with packed causal reason and refresh scope.
- `AoTDEconomy.tripleStep()` maps proven local immigration/incentive/stockpile policy scopes to
  existing `MarketRegistry` dirty masks and runs the existing immediate single-market refresh.
- Preserved the revision-gated live-market path and detached Cargo/LOOT early skip; unsupported
  or unsafe mutations retain the global fallback.

## 1.0.14-spp4 - 2026-07-28

- Requires StarsectorPrepatcher `0.13.1`.
- Added bridge schema V7 and required production capability mask `0x3ff`.
- Added a one-shot, finally-cleared detached-Cargo signal from the exact vanilla
  `fake_market` call site. `AoTDEconomy.tripleStep()` returns before global work only for that
  signal; real markets and other callers are unchanged.
- Added a condition-only market guard before `isLiveMarket()`. Opening an uninhabited planet now
  skips the global AoTD economy pipeline while preserving vanilla market-open callbacks and the
  later publication of `currentlyOpenMarket`.
- Extended the verified synthetic cargo context from detached `CARGO` to generated `LOOT` transfer
  panels used by ruins and salvage. The loot is created before the panel opens; only the unrelated
  global `tripleStep()` is suppressed.
- Added owner-local counters for condition-only and synthetic cargo skips. No market, cargo, UI or
  classloader reference is retained and no new serialized state was introduced.
- Replaced both AoTD Local Resources tooltip comparators with a call-local row
  snapshot. Each commodity limit is resolved once and sorting compares saved
  integers only.
- Added a read-only `peekSupplyDemandData()` path for tooltip rendering. Missing
  committed state falls back to the existing correctness path once.
- Added no persistent market/commodity cache and no new serialized instance state.

## 1.0.14-spp2 - 2026-07-27

- Added a revision-gated, single-market UI economy refresh path.
- Market opening now receives the target market before vanilla publishes
  `currentlyOpenMarket`, through a finally-cleared Prepatcher context.
- Removed global `commodity × econGroup` construction from synchronous UI steps.
- Limited immigration trade snapshots to the market whose UI is being opened.
- Coalesced the immediately following Cargo `tripleStep` when no registry revision changed.
- Deferred global internal-trade settlement to the normal economy cadence while
  preserving one `economyUpdated` listener boundary.
- Corrected subset registry auditing for local post-immigration snapshots.

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
