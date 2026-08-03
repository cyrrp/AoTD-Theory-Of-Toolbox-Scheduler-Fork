# Repository instructions

## Fork release version naming

Fork release identifiers must use exactly the form
`{upstream-mod-version}-spp{fork-patch-number}` (for example, `1.0.14-spp6`).
Do not append feature, capability, milestone, or implementation suffixes such as
`-mutation-reasons` or `-targeted-commodities`.

Use the same canonical release identifier in `PrepatcherContract.FORK_VERSION`,
`mod_info.json`, `aotd_tot.version`, release scripts and filenames, README, and
CHANGELOG. Represent protocol compatibility through the bridge schema and
capability constants, never through extra text in the release version.

## Capability composition

One behavior gets one capability. Do not expose implementation stages, payload fields, or individual transformers as separate capabilities.
