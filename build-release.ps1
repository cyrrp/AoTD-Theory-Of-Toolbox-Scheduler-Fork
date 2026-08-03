$ErrorActionPreference = "Stop"

$releaseLabel = "1.0.14-spp9"
$packageDirectoryName = "AoTD-Theory-Of-Toolbox-Scheduler-Fork"
$repositoryRoot = [IO.Path]::GetFullPath($PSScriptRoot)
$releaseDirectory = Join-Path $repositoryRoot "releases"
$archiveName = "$packageDirectoryName-$releaseLabel.zip"
$archivePath = Join-Path $releaseDirectory $archiveName
$externalChecksumPath = "$archivePath.sha256"
$utf8NoBom = [Text.UTF8Encoding]::new($false)
$releaseTimestamp = [DateTimeOffset]::new(2026, 8, 4, 0, 0, 0, [TimeSpan]::Zero)

if ($releaseLabel -cnotmatch '^\d+\.\d+\.\d+-spp\d+$') {
    throw "Fork release label must use {upstream-version}-spp{patch}: $releaseLabel"
}
$modInfo = Get-Content -LiteralPath (Join-Path $repositoryRoot 'mod_info.json') -Raw -Encoding UTF8 | ConvertFrom-Json
$updateInfo = Get-Content -LiteralPath (Join-Path $repositoryRoot 'aotd_tot.version') -Raw -Encoding UTF8 | ConvertFrom-Json
$updateLabel = '{0}.{1}.{2}' -f $updateInfo.modVersion.major,
    $updateInfo.modVersion.minor, $updateInfo.modVersion.patch
if ($modInfo.version -cne $releaseLabel -or $updateLabel -cne $releaseLabel) {
    throw "Fork version metadata mismatch: release=$releaseLabel; mod_info=$($modInfo.version); update=$updateLabel"
}
$ashlibDependency = @($modInfo.dependencies | Where-Object { $_.id -ceq 'ashlib' })
if ($ashlibDependency.Count -ne 1 -or $ashlibDependency[0].version -cne '2.2.3') {
    throw "The Domain UI hotfix requires exactly one AshLib dependency at minimum version 2.2.3."
}
$prepatcherDependency = @($modInfo.dependencies | Where-Object { $_.id -ceq 'starsector_prepatcher' })
if ($prepatcherDependency.Count -ne 1 -or $prepatcherDependency[0].version -cne '0.17.2') {
    throw "Scheduler Fork 1.0.14-spp9 requires exactly one Prepatcher dependency at version 0.17.2."
}
if ($updateInfo.directDownloadURL -notlike "*/$archiveName") {
    throw "Fork update URL does not end in the canonical archive name: $archiveName"
}
foreach ($document in @('README.md', 'CHANGELOG.md')) {
    $documentText = Get-Content -LiteralPath (Join-Path $repositoryRoot $document) -Raw -Encoding UTF8
    if ($documentText.IndexOf($releaseLabel, [StringComparison]::Ordinal) -lt 0) {
        throw "Fork release identifier is missing from ${document}: $releaseLabel"
    }
}

function Get-RepositoryPayload {
    $tracked = @(& git -C $repositoryRoot ls-files)
    if ($LASTEXITCODE -ne 0) {
        throw "git ls-files failed for $repositoryRoot"
    }

    $untracked = @(& git -C $repositoryRoot ls-files --others --exclude-standard)
    if ($LASTEXITCODE -ne 0) {
        throw "git ls-files --others failed for $repositoryRoot"
    }

    @($tracked + $untracked |
        Where-Object {
            $_ -and
            $_ -ne ".gitattributes" -and
            $_ -ne ".gitignore" -and
            $_ -ne "AGENTS.md" -and
            $_ -ne "SHA256SUMS.txt" -and
            -not $_.StartsWith(".build/", [StringComparison]::Ordinal) -and
            -not $_.StartsWith("releases/", [StringComparison]::Ordinal)
        } |
        Sort-Object -Unique)
}

$payload = @(Get-RepositoryPayload)
if ($payload.Count -eq 0) {
    throw "No release payload files were found."
}

$requiredFiles = @(
    "jars/AoTDToolboxTheory.jar",
    "mod_info.json",
    "README.md",
    "CHANGELOG.md",
    "LICENSE"
)
foreach ($relativePath in $requiredFiles) {
    if ($payload -notcontains $relativePath) {
        throw "Required release file is missing: $relativePath"
    }
}

$forbiddenJarEntries = @(
    'data/kaysaar/aotd/tot/scripts/submarket/aotd/AoTDLocalResourcesSubmarketPlugin$1.class',
    'data/kaysaar/aotd/tot/scripts/submarket/nex/AoTDxNexLocalResourcesSubmarketPlugin$1.class'
)
$requiredJarEntries = @(
    'data/kaysaar/aotd/tot/ui/core/DomainTabListener.class',
    'data/kaysaar/aotd/tot/ui/core/DomainTabListener$1.class',
    'data/kaysaar/aotd/tot/ui/DomainUIPanel.class'
)
$requiredJarSymbols = @{
    'data/kaysaar/aotd/tot/compat/PrepatcherContract.class' = @(
        '1.0.14-spp9'
    )
    'data/kaysaar/aotd/tot/compat/SchedulerBridge.class' = @(
        'AOTD_SCHEDULER_BRIDGE_V9'
    )
    'data/kaysaar/aotd/tot/scripts/economy/AoTDEconomy.class' = @(
        'dispatchPrepatcherUiEconomyStep'
    )
}
$forbiddenJarSymbols = @{
    'data/kaysaar/aotd/tot/compat/PrepatcherContract.class' = @(
        'ABI_VERSION',
        'CAPABILITY_UI_CALL_CONTEXTS'
    )
    'data/kaysaar/aotd/tot/compat/SchedulerBridge.class' = @(
        'consumeOpeningMarket',
        'consumeDetachedCargoOpen',
        'consumeUiMarketMutation',
        'consumeUiMarketMutationPayload'
    )
}
Add-Type -AssemblyName System.IO.Compression
$jarPath = Join-Path $repositoryRoot 'jars\AoTDToolboxTheory.jar'
$jarStream = [IO.File]::OpenRead($jarPath)
try {
    $jar = [IO.Compression.ZipArchive]::new(
        $jarStream,
        [IO.Compression.ZipArchiveMode]::Read,
        $false)
    try {
        $jarEntries = @($jar.Entries | ForEach-Object FullName)
        foreach ($entry in $requiredJarEntries) {
            if ($jarEntries -notcontains $entry) {
                throw "AoTD JAR is missing required Domain UI class: $entry"
            }
        }
        foreach ($entry in $forbiddenJarEntries) {
            if ($jarEntries -contains $entry) {
                throw "AoTD JAR contains a stale class from the removed tooltip implementation: $entry"
            }
        }
        $latin1 = [Text.Encoding]::GetEncoding(28591)
        foreach ($entryName in @($requiredJarSymbols.Keys + $forbiddenJarSymbols.Keys |
                Sort-Object -Unique)) {
            $classEntry = $jar.GetEntry($entryName)
            if ($null -eq $classEntry) {
                throw "AoTD JAR is missing current contract class: $entryName"
            }
            $classStream = $classEntry.Open()
            $classBytes = [IO.MemoryStream]::new()
            try {
                $classStream.CopyTo($classBytes)
                $classText = $latin1.GetString($classBytes.ToArray())
            } finally {
                $classBytes.Dispose()
                $classStream.Dispose()
            }
            if ($requiredJarSymbols.ContainsKey($entryName)) {
                foreach ($symbol in @($requiredJarSymbols[$entryName])) {
                    if ($classText.IndexOf($symbol, [StringComparison]::Ordinal) -lt 0) {
                        throw "AoTD JAR contract class is stale; missing $symbol in $entryName"
                    }
                }
            }
            if ($forbiddenJarSymbols.ContainsKey($entryName)) {
                foreach ($symbol in @($forbiddenJarSymbols[$entryName])) {
                    if ($classText.IndexOf($symbol, [StringComparison]::Ordinal) -ge 0) {
                        throw "AoTD JAR contract class retains removed symbol $symbol in $entryName"
                    }
                }
            }
        }
    } finally {
        $jar.Dispose()
    }
} finally {
    $jarStream.Dispose()
}

$checksumLines = foreach ($relativePath in $payload) {
    $sourcePath = Join-Path $repositoryRoot ($relativePath.Replace("/", [IO.Path]::DirectorySeparatorChar))
    if (-not (Test-Path -LiteralPath $sourcePath -PathType Leaf)) {
        throw "Payload file is missing from disk: $relativePath"
    }
    $hash = (Get-FileHash -LiteralPath $sourcePath -Algorithm SHA256).Hash.ToLowerInvariant()
    "$hash  $relativePath"
}
[IO.File]::WriteAllText(
    (Join-Path $repositoryRoot "SHA256SUMS.txt"),
    (($checksumLines -join "`n") + "`n"),
    $utf8NoBom)

[IO.Directory]::CreateDirectory($releaseDirectory) | Out-Null
$archiveStream = [IO.File]::Open(
    $archivePath,
    [IO.FileMode]::Create,
    [IO.FileAccess]::Write,
    [IO.FileShare]::None)
try {
    $zip = [IO.Compression.ZipArchive]::new(
        $archiveStream,
        [IO.Compression.ZipArchiveMode]::Create,
        $false,
        $utf8NoBom)
    try {
        foreach ($relativePath in @($payload + "SHA256SUMS.txt")) {
            $platformRelativePath = $relativePath.Replace(
                "/",
                [IO.Path]::DirectorySeparatorChar)
            $sourcePath = Join-Path $repositoryRoot $platformRelativePath
            $entry = $zip.CreateEntry(
                "$packageDirectoryName/$relativePath",
                [IO.Compression.CompressionLevel]::Optimal)
            $entry.LastWriteTime = $releaseTimestamp
            $inputStream = [IO.File]::OpenRead($sourcePath)
            $outputStream = $entry.Open()
            try {
                $inputStream.CopyTo($outputStream)
            } finally {
                $outputStream.Dispose()
                $inputStream.Dispose()
            }
        }
    } finally {
        $zip.Dispose()
    }
} finally {
    $archiveStream.Dispose()
}

$archiveHash = (Get-FileHash -LiteralPath $archivePath -Algorithm SHA256).Hash.ToLowerInvariant()
[IO.File]::WriteAllText(
    $externalChecksumPath,
    "$archiveHash  $archiveName`n",
    $utf8NoBom)

Write-Host "Release archive: $archivePath"
Write-Host "SHA-256: $archiveHash"
