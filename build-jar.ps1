param(
    [string] $GameRoot,
    [string] $OutputPath
)

$ErrorActionPreference = 'Stop'

$repositoryRoot = [IO.Path]::GetFullPath($PSScriptRoot)
if ([string]::IsNullOrWhiteSpace($GameRoot)) {
    $GameRoot = Join-Path $repositoryRoot '..\..'
}
$GameRoot = [IO.Path]::GetFullPath($GameRoot)

if ([string]::IsNullOrWhiteSpace($OutputPath)) {
    $OutputPath = Join-Path $repositoryRoot 'jars\AoTDToolboxTheory.jar'
} elseif (-not [IO.Path]::IsPathRooted($OutputPath)) {
    $OutputPath = Join-Path $repositoryRoot $OutputPath
}
$OutputPath = [IO.Path]::GetFullPath($OutputPath)

$sourceRoot = Join-Path $repositoryRoot 'src'
$buildRoot = [IO.Path]::GetFullPath((Join-Path $repositoryRoot '.build\jar-build'))
$expectedBuildPrefix = $repositoryRoot.TrimEnd(
    [IO.Path]::DirectorySeparatorChar,
    [IO.Path]::AltDirectorySeparatorChar) + [IO.Path]::DirectorySeparatorChar
if (-not $buildRoot.StartsWith($expectedBuildPrefix, [StringComparison]::OrdinalIgnoreCase)) {
    throw "Refusing to clean a build directory outside the repository: $buildRoot"
}

$coreRoot = Join-Path $GameRoot 'starsector-core'
$compileClasspath = @(
    (Join-Path $coreRoot 'starfarer.api.jar'),
    (Join-Path $coreRoot 'starfarer_obf.jar'),
    (Join-Path $coreRoot 'fs.common_obf.jar'),
    (Join-Path $coreRoot 'lwjgl.jar'),
    (Join-Path $coreRoot 'lwjgl_util.jar'),
    (Join-Path $coreRoot 'log4j-1.2.9.jar'),
    (Join-Path $coreRoot 'json.jar'),
    (Join-Path $coreRoot 'xstream-1.4.10.jar'),
    (Join-Path $GameRoot 'mods\LazyLib\jars\LazyLib.jar'),
    (Join-Path $GameRoot 'mods\Ashlib_\jars\ashlib.jar'),
    (Join-Path $GameRoot 'mods\Building Menu Overhaul\jars\bmo.jar'),
    (Join-Path $GameRoot 'mods\Nexerelin\jars\ExerelinCore.jar'),
    (Join-Path $GameRoot 'mods\HMI\jars\HMI.jar'),
    (Join-Path $GameRoot 'mods\Ashes of  The Domain- Vaults of Knowledge\jars\Vok.jar'),
    (Join-Path $GameRoot 'mods\Ashes of  The Domain -Seats Of Power\jars\AodCapitals.jar')
)

$missingDependencies = @($compileClasspath | Where-Object {
    -not (Test-Path -LiteralPath $_ -PathType Leaf)
})
if ($missingDependencies.Count -gt 0) {
    throw "Required compile-time JARs are missing:`n$($missingDependencies -join "`n")"
}

$javac = Get-Command javac -CommandType Application -ErrorAction Stop | Select-Object -First 1
$jar = Get-Command jar -CommandType Application -ErrorAction Stop | Select-Object -First 1
$javacVersion = (& $javac.Source -version 2>&1).ToString()
if ($LASTEXITCODE -ne 0 -or $javacVersion -notmatch '^javac 17(?:\.|$)') {
    throw "JDK 17 javac is required; found: $javacVersion"
}

if (Test-Path -LiteralPath $buildRoot) {
    Remove-Item -LiteralPath $buildRoot -Recurse -Force
}
$classesDirectory = Join-Path $buildRoot 'classes'
[IO.Directory]::CreateDirectory($classesDirectory) | Out-Null

$sources = @(Get-ChildItem -LiteralPath $sourceRoot -Recurse -File -Filter '*.java' |
    Sort-Object FullName)
if ($sources.Count -eq 0) {
    throw "No Java sources were found under $sourceRoot"
}

$utf8NoBom = [Text.UTF8Encoding]::new($false)
$sourceList = Join-Path $buildRoot 'sources.args'
$sourceArguments = @($sources | ForEach-Object {
    '"' + $_.FullName.Replace('\', '/') + '"'
})
[IO.File]::WriteAllLines($sourceList, [string[]] $sourceArguments, $utf8NoBom)

$classpath = $compileClasspath -join [IO.Path]::PathSeparator
& $javac.Source -encoding UTF-8 -source 17 -target 17 `
    -classpath $classpath -d $classesDirectory "@$sourceList"
if ($LASTEXITCODE -ne 0) {
    throw 'AoTD Toolbox Theory source compilation failed.'
}

$pendingJar = Join-Path $buildRoot 'AoTDToolboxTheory.jar'
& $jar.Source --create --file $pendingJar --no-manifest `
    --date=2000-01-01T00:00:00Z -C $classesDirectory .
if ($LASTEXITCODE -ne 0) {
    throw 'AoTD Toolbox Theory JAR creation failed.'
}

$requiredEntries = @(
    'data/kaysaar/aotd/tot/compat/PrepatcherContract.class',
    'data/kaysaar/aotd/tot/compat/SchedulerBridge.class',
    'data/kaysaar/aotd/tot/scripts/economy/AoTDEconomy.class',
    'data/kaysaar/aotd/tot/ui/core/DomainTabListener.class'
)
$jarEntries = @(& $jar.Source --list --file $pendingJar)
if ($LASTEXITCODE -ne 0) {
    throw 'Could not inspect the newly built AoTD Toolbox Theory JAR.'
}
foreach ($entry in $requiredEntries) {
    if ($jarEntries -cnotcontains $entry) {
        throw "Newly built JAR is missing required class: $entry"
    }
}

$outputDirectory = Split-Path -Parent $OutputPath
[IO.Directory]::CreateDirectory($outputDirectory) | Out-Null
$stagedOutput = Join-Path $outputDirectory (
    '.' + [IO.Path]::GetFileName($OutputPath) + ".pending-$PID")
Move-Item -LiteralPath $pendingJar -Destination $stagedOutput -Force
Move-Item -LiteralPath $stagedOutput -Destination $OutputPath -Force

Write-Host "Compiled $($sources.Count) Java sources with $javacVersion."
Write-Host "JAR: $OutputPath"
