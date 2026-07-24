# Ashes of The Domain — Theory of Toolbox: Scheduler Fork

Scheduler-focused fork of **AoTD — Theory of Toolbox** for Starsector
`0.98a-RC8`. The current release is `1.0.14`.

The fork keeps the original game `starfarer.api.jar`; it does not require or
ship an AoTD replacement for any Starsector core JAR.

## What this fork changes

- publishes market state atomically and validates work with domain-specific
  revision vectors;
- moves price, stockpile and trade calculations to workers using immutable
  inputs and campaign-thread commits;
- rejects stale results across campaign or economy replacement with runtime
  epochs;
- restarts workers safely around load, reset, save and shutdown boundaries;
- refreshes Prepatcher capabilities at runtime and resynchronizes market
  generations before falling back when native delivery events become
  unavailable.

## Requirements

The runtime dependencies declared by `mod_info.json` are:

| Mod | Minimum version |
| --- | --- |
| StarsectorPrepatcher | 0.11.3 |
| LazyLib | 3.0 |
| AshLib | 2.2.0 |
| Building Menu Overhaul | 2.1.0 |

Nexerelin and Hazard Mining Incorporated integrations are enabled when those
mods are present, but neither is declared as a required dependency.

## Installation

1. Keep the original Starsector core JARs, especially
   `starsector-core/starfarer.api.jar`.
2. Remove any obsolete AoTD core-JAR replacement left by an older build.
3. Install the required dependencies listed above.
4. Place this directory under `Starsector/mods/` and enable the mod.

At startup the fork requires an active, compatible Prepatcher javaagent and the
production capability mask `0x1ff`. Merely having the Prepatcher mod directory
installed is not sufficient. If the native delivery callback is lost later at
runtime, the fork performs a one-time generation resynchronization and switches
price capture to its dirty-state fallback.

## Repository layout

- `src/` — Java sources;
- `data/` and `graphics/` — Starsector data and assets;
- `jars/AoTDToolboxTheory.jar` — distributable release JAR referenced by
  `mod_info.json`;
- `CHANGELOG.md` — release history and consolidated scheduler implementation
  notes;
- `SHA256SUMS.txt` — checksums for the repository payload.

The repository intentionally excludes local IDE metadata, module files,
temporary compiler output and intermediate patch JARs.

## Development

There is currently no repository-local Gradle or Maven build. Configure a
Java 17 project with `src/` as the source root and add the Starsector API plus
the required and compatibility mod JARs to the compile classpath. Build output
should stay outside the repository; replace
`jars/AoTDToolboxTheory.jar` only when preparing a distributable build.

See [CHANGELOG.md](CHANGELOG.md) for implementation details and release
verification notes.

## Integrity

`SHA256SUMS.txt` uses the standard `sha256sum` format. From a shell that
provides GNU coreutils, verify the payload with:

```text
sha256sum -c SHA256SUMS.txt
```

## License

See [LICENSE](LICENSE).
