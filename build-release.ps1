$ErrorActionPreference = "Stop"

$releaseLabel = "1.0.14-spp2"
$packageDirectoryName = "AoTD-Theory-Of-Toolbox-Scheduler-Fork"
$repositoryRoot = [IO.Path]::GetFullPath($PSScriptRoot)
$releaseDirectory = Join-Path $repositoryRoot "releases"
$archiveName = "$packageDirectoryName-$releaseLabel.zip"
$archivePath = Join-Path $releaseDirectory $archiveName
$externalChecksumPath = "$archivePath.sha256"
$utf8NoBom = [Text.UTF8Encoding]::new($false)
$releaseTimestamp = [DateTimeOffset]::new(2026, 7, 27, 0, 0, 0, [TimeSpan]::Zero)

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
            $_ -ne "SHA256SUMS.txt" -and
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
Add-Type -AssemblyName System.IO.Compression
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
