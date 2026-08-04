param(
    [switch] $Check
)

$ErrorActionPreference = 'Stop'
$repositoryRoot = (Resolve-Path $PSScriptRoot).Path
$toolRoot = Join-Path $repositoryRoot '.build\format-tools'
$classes = Join-Path $toolRoot 'classes'
$spotlessVersion = '4.8.0'
$googleJavaFormatVersion = '1.28.0'
$slf4jVersion = '2.0.18'
$spotlessJar = Join-Path $toolRoot "spotless-lib-$spotlessVersion.jar"
$googleJavaFormatJar = Join-Path $toolRoot "google-java-format-$googleJavaFormatVersion-all-deps.jar"
$slf4jApiJar = Join-Path $toolRoot "slf4j-api-$slf4jVersion.jar"
$slf4jNopJar = Join-Path $toolRoot "slf4j-nop-$slf4jVersion.jar"
$spotlessSha256 = 'fea24fd8250f7049dcf83e9c537dca70d4eeddf27f6562cc05b73945d5586a15'
$googleJavaFormatSha256 = '32342e7c1b4600f80df3471da46aee8012d3e1445d5ea1be1fb71289b07cc735'
$slf4jApiSha256 = '44508fd1576500688c790b190acdd16fec4f8c79a3e0b900afd70503cf055f55'
$slf4jNopSha256 = '40e6be27d583d884183ca466cd20203112691f2a075a650e9e8d5c2e51aa5f49'

function Get-VerifiedArtifact {
    param(
        [Parameter(Mandatory)] [string] $Uri,
        [Parameter(Mandatory)] [string] $Target,
        [Parameter(Mandatory)] [string] $ExpectedSha256
    )

    if (Test-Path -LiteralPath $Target -PathType Leaf) {
        $actual = (Get-FileHash -LiteralPath $Target -Algorithm SHA256).Hash.ToLowerInvariant()
        if ($actual -eq $ExpectedSha256) {
            return
        }
        Remove-Item -LiteralPath $Target -Force
    }

    $download = "$Target.download"
    Remove-Item -LiteralPath $download -Force -ErrorAction SilentlyContinue
    try {
        Invoke-WebRequest -UseBasicParsing -Uri $Uri -OutFile $download
        $actual = (Get-FileHash -LiteralPath $download -Algorithm SHA256).Hash.ToLowerInvariant()
        if ($actual -ne $ExpectedSha256) {
            throw "Checksum mismatch for $Uri (expected $ExpectedSha256, got $actual)."
        }
        Move-Item -LiteralPath $download -Destination $Target -Force
    } finally {
        Remove-Item -LiteralPath $download -Force -ErrorAction SilentlyContinue
    }
}

New-Item -ItemType Directory -Force $toolRoot, $classes | Out-Null
Get-VerifiedArtifact `
    -Uri "https://repo1.maven.org/maven2/com/diffplug/spotless/spotless-lib/$spotlessVersion/spotless-lib-$spotlessVersion.jar" `
    -Target $spotlessJar `
    -ExpectedSha256 $spotlessSha256
Get-VerifiedArtifact `
    -Uri "https://repo1.maven.org/maven2/com/google/googlejavaformat/google-java-format/$googleJavaFormatVersion/google-java-format-$googleJavaFormatVersion-all-deps.jar" `
    -Target $googleJavaFormatJar `
    -ExpectedSha256 $googleJavaFormatSha256
Get-VerifiedArtifact `
    -Uri "https://repo1.maven.org/maven2/org/slf4j/slf4j-api/$slf4jVersion/slf4j-api-$slf4jVersion.jar" `
    -Target $slf4jApiJar `
    -ExpectedSha256 $slf4jApiSha256
Get-VerifiedArtifact `
    -Uri "https://repo1.maven.org/maven2/org/slf4j/slf4j-nop/$slf4jVersion/slf4j-nop-$slf4jVersion.jar" `
    -Target $slf4jNopJar `
    -ExpectedSha256 $slf4jNopSha256

& javac -encoding UTF-8 -cp $spotlessJar -d $classes (Join-Path $repositoryRoot 'tools\SpotlessJavaFormat.java')
if ($LASTEXITCODE -ne 0) { throw 'Could not compile the standalone Spotless runner.' }

$mode = if ($Check) { 'check' } else { 'apply' }
$runtimeClasspath = "$classes;$spotlessJar;$slf4jApiJar;$slf4jNopJar"
& java -cp $runtimeClasspath SpotlessJavaFormat $mode $repositoryRoot $googleJavaFormatJar
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
