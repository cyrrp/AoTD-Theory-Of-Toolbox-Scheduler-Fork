# Changelog

All notable changes to the Scheduler Fork are documented here.

## Unreleased

## 1.0.14-spp11 - 2026-08-10

- Requires StarsectorPrepatcher 0.18.1 and continues to require AshLib 2.2.3. Scheduler Bridge V10
  and its required/declared capability masks remain `0xbff`/`0xfff`.
- New saves no longer contain the live `ReachEconomyStepper.tasks` object graph, worker DTOs,
  futures, boundary handles or process-local timing origins. The fork stores only the interrupted
  stage and stable IDs of markets still awaiting that stage. Loading and both save outcomes now
  discard the invalid process-local graph and lazily construct the same safe semantic suffix.
- An interrupted main price pass records whether no market was committed, an exact ordered set of
  markets remains, or only listener notification remains. It rebuilds transient global commodity
  data from every market but applies price/stockpile results only to the unattempted set, so neither
  completed work nor monthly mutations run twice. Completed Update/Immigration markets are also
  excluded, a partial trade snapshot is recaptured atomically, open cuts/tickets are released, and
  calendar/month-end cadence is preserved. Old spp10 task graphs use a conservative migration path.
- Post-immigration trade capture now reuses the already committed supply/demand aggregate without
  rescanning industries when the registry is ready and the dedicated materialized-input generation
  plus market-size proofs match. Trade, accessibility and faction-only changes do not invalidate
  this proof. Market growth, stale/mixed generations or missing proof use one whole-market live
  calculation, then coalesce one normal materialized refresh so the fast path recovers instead of
  remaining permanently disabled; committed and live revisions are never mixed.
- Multi-frame trade batches now carry an exact runtime proof for market identity, trade-input
  revision, size, faction, accessibility and spaceport eligibility. Inputs that change while a
  batch is being prepared are recaptured once before the atomic publication; a later mismatch
  retains the previous complete cut and leaves all affected registry work queued for retry.
- Empty-registry load passes now defer their guaranteed-failing per-market registry commits with
  one lifecycle check while retaining the atomic trade-manager publication.
- UpdateMarketAgain now skips industry traversal when a market owes only downstream
  price/stockpile/accessibility/trade work. Month-end changes to AoTD's own pending-industry state
  explicitly invalidate the materialized domain, preserving reconciliation without making every
  ordinary price pass scan industries.
- A failed `onGameLoad` path can no longer leave the runtime-task load guard active forever or
  revive a stale serialized task graph. The fail-safe restart clears the graph and process-local
  baseline, releases the guard in `finally`, and logs its own failure without replacing the primary
  load exception.
- Canonical commodity lists now rebuild their serialized lookup maps during restore, and invalid UI
  sprites are rejected centrally by both dimensions while their runtime blacklist is retried after
  campaign and dev-mode reloads.
- Registry diagnostics now report the count plus total, maximum and latest nanoseconds spent
  holding the global market-registry lock for atomic post-immigration trade publication attempts.
- `signalRestoreComplete()` deliberately marks every economy market dirty after every save with
  derived-economy, price, stockpile, accessibility, trade and global-revision work. Starsector
  rebuilds every industry's supply and demand during save cleanup, so preserving correct derived
  state requires one full scheduled economy recalculation; this is an explicit save-time
  performance cost.

## 1.0.14-spp10 - 2026-08-10

- Requires StarsectorPrepatcher 0.18.0 and continues to require AshLib 2.2.3.
- Stopped refreshing supply/demand while Starsector is still restoring industries. Prepatcher now
  sends one completion signal after the complete economy restore/reapply pass; the fork coalesces
  affected markets and lets its normal scheduler publish one atomic refresh per market.
- Restored the upstream "not ready yet" behavior specifically for unavailable industry
  supply/demand snapshots. This transient state keeps the previous committed revision without an
  exception, ERROR storm, quarantine or false commit; calculation-script failures remain visible.
- Removed serialized references to derived per-industry supply/demand stats while retaining the
  last committed aggregates and gameplay modifiers, reducing stale save-state graphs and forcing a
  clean scheduled rebuild after load.
- Updated the exact compatibility contract to Scheduler Bridge V10, fork `1.0.14-spp10`, required
  mask `0xbff` and complete declared mask `0xfff`.

- Corrected the warehouse LPC icon lookup to use Starsector's
  `misc.cargoFighterChip` sprite instead of the nonexistent `ui.fighter_lpc` key.
- Made Domain and Economy subsections truly lazy. Hidden warehouses, commodity tables, star-system
  holdings and trade contracts are no longer constructed while another Command tab or subsection
  is active; the selected subsection is created on first display and refreshed normally afterward.
- Made data-driven Domain icons fail locally: missing item specs and broken storage, condition or
  custom-entity sprites are skipped and logged once instead of aborting the whole Command UI.
- Corrected reversed parent/child removal in star-system holdings and market-condition refreshes,
  and added safe fallback selection for stale remembered subsection names.

## 1.0.14-spp9 - 2026-08-04

- Requires StarsectorPrepatcher 0.17.2 and continues to require AshLib 2.2.3.
- Made post-commit UI economy diagnostics fail-silent: coordinator publication and semantic
  baseline failures can no longer make an already committed local refresh report failure and
  trigger a duplicate global fallback.
- Hardened optional semantic-baseline and refresh diagnostics across economy worker paths so
  logger, snapshot, counter and flush failures have no semantic authority.
- Kept Scheduler Bridge schema V9 and capability masks `0x3ff`/`0x7ff` unchanged; only the exact
  `1.0.14-spp9` contract registers.

## 1.0.14-spp8 - 2026-08-03

- Requires StarsectorPrepatcher 0.17.1 and AshLib 2.2.3.
- Restored the compiled Domain tab listener, panel and transitive UI classes that were present in
  the official AoTD archive but missing from the fork JAR. This fixes the fatal
  `NoClassDefFoundError: DomainTabListener` during campaign load.
- AshLib 2.2.3's command-tab tracker no longer initializes custom
  strategic-view plugins twice.
- Made Domain/Economy panel sizing tolerate a temporarily unavailable replacement button and fall
  back to the host component width instead of dereferencing `null`.
- Added release-time checks for the Domain listener/panel bytecode and the AshLib 2.2.3 dependency.

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
- Unified vanilla and Nexerelin stockpile limits behind one calculation. Open and military markets
  now use the current remaining deficit and increase with stability, while black markets retain
  the inverse stability curve; selling enough goods resolves a shortage consistently in both
  configurations.
- Prevented wonder-free colony decivilization from replacing an active faction-wide Grand Wonder
  stability penalty with a zero-value temporary modifier, and stopped applying the penalty to the
  colony being removed.
- Reset process-local core-UI state at every campaign load while preserving the legacy public flag
  for binary compatibility, preventing one save's UI state from affecting another save.
- Made market UI listener registration and dispatch safe against concurrent or callback-driven
  modification, and retained callback causes in logs and propagated failures.

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
