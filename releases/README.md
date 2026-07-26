# Local release artifacts

Run `..\build-release.ps1` from this directory, or `.\build-release.ps1` from
the repository root.

For the first public Scheduler Fork release the command creates:

```text
AoTD-Theory-Of-Toolbox-Scheduler-Fork-1.0.14-spp1.zip
AoTD-Theory-Of-Toolbox-Scheduler-Fork-1.0.14-spp1.zip.sha256
```

Upload both files to the GitHub release tagged `v1.0.14-spp1`. Generated ZIP
and checksum files are ignored by Git; this instruction file remains tracked.
