# Ashes of The Domain - Theory of Toolbox

## Getting Started

### Dependencies

| Mod | Download Source |
|------|----------------|
| **LazyLib** | [Forum Thread](https://fractalsoftworks.com/forum/index.php?topic=5444) |
| **Ashlib** | [Forum Thread](https://fractalsoftworks.com/forum/index.php?topic=30808) |
| **Building Menu Overhaul** | [Forum Thread](https://fractalsoftworks.com/forum/index.php?topic=31308) |
| **Nexerelin** | [Forum Thread](https://fractalsoftworks.com/forum/index.php?topic=9175) |
| **HMI (Hazard Mining Incorporated)** | [Forum Thread](https://fractalsoftworks.com/forum/index.php?topic=13236) |

### Installation

- Keep the original game `starfarer.api.jar`. This fork requires StarsectorPrepatcher 0.11.0+ and must not be used with the obsolete AoTD core-JAR replacement.
## Scheduler fork Stage 4

This build retains the Stage 1 semantic baseline collector and the Stage 2
no-reflection loader-safe contract. Stage 4 adds post-success delivered-market-
time events, delivered/structural generations and exact temporal barriers for
covered source-level structural mutations. Ordinary AoTD calculations consume
the current delivered state and do not force scheduler replay. See
`docs/SCHEDULER_FORK_STAGE3_TEMPORAL_CONTRACT.md`.

The legacy core-JAR requirement remains until the later clean BaseIndustry
deficit stage; Stage 4 does not alter deficit semantics.

## Scheduler fork Stage 4 audit

The audited build completes branch-local condition/commodity structural boundaries,
hardens market identity replacement and stale-result detection, and removes the
registry monitor from the hot market-id lookup. Worker offload and the clean
`BaseIndustry` path remain later stages.

## Scheduler fork Stage 6

The price/stockpile phase now uses immutable per-market inputs, two persistent
dynamic batch workers and generation-validated campaign-thread commits. The
same model can run sequentially for A/B comparison, while the legacy pipeline
remains available as a fallback. See
`docs/SCHEDULER_FORK_STAGE6_PURE_OFFLOAD.md`.

## Scheduler Fork Stage 7

Stage 7 replaces the remaining live internal-trade worker phase with pure DTO computation and introduces immutable global committed cuts. Prepatcher delivers pending market time; AoTD refreshes dirty local revisions and publishes settlement from complete market snapshots. The automatic live legacy price fallback and the full legacy price pipeline have been removed.

## Production requirement

StarsectorPrepatcher 0.11.0+ is declared as a required mod dependency. The fork also verifies the active runtime capability mask `0xff`, because a present mod directory does not prove that its javaagent was installed and activated. It fails early only when that runtime integration is inactive or incompatible.
