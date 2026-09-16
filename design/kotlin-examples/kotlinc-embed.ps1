# Runs the Kotlin compiler out of the Gradle cache, without downloading a
# kotlinc distribution. Verified working on this machine.
#
# Why a script: kotlin-compiler-embeddable needs several companion jars on its
# own classpath (stdlib, daemon, script-runtime, coroutines, trove4j,
# annotations), and must run on JDK 22 -- Kotlin 2.0.21 cannot parse JDK 25's
# version string and dies with "IllegalArgumentException: 25.0.2".
param(
    [Parameter(Mandatory = $true)][string[]]$Sources,
    [Parameter(Mandatory = $true)][string]$Output,
    [string]$Classpath = '',
    [string]$Java = 'D:\jdk22'
)
$ErrorActionPreference = 'Stop'
$m2 = "$env:USERPROFILE\.gradle\caches\modules-2\files-2.1"
$k = "$m2\org.jetbrains.kotlin"

$compilerCp = @(
    "$k\kotlin-compiler-embeddable\2.0.21\79346ed53db48b18312a472602eb5c057070c54d\kotlin-compiler-embeddable-2.0.21.jar",
    "$k\kotlin-stdlib\2.0.21\618b539767b4899b4660a83006e052b63f1db551\kotlin-stdlib-2.0.21.jar",
    "$k\kotlin-daemon-embeddable\2.0.21\c9e933b23287de9b5a17e2116b4657bb91aea72c\kotlin-daemon-embeddable-2.0.21.jar",
    "$k\kotlin-script-runtime\2.0.21\c9b044380ad41f89aa89aa896c2d32a8c0b2129d\kotlin-script-runtime-2.0.21.jar",
    "$m2\org.jetbrains.kotlinx\kotlinx-coroutines-core-jvm\1.6.4\2c997cd1c0ef33f3e751d3831929aeff1390cb30\kotlinx-coroutines-core-jvm-1.6.4.jar",
    "$m2\org.jetbrains.intellij.deps\trove4j\1.0.20200330\3afb14d5f9ceb459d724e907a21145e8ff394f02\trove4j-1.0.20200330.jar",
    "$m2\org.jetbrains\annotations\13.0\919f0dfe192fb4e063e7dacadee7f8bb9a2672a9\annotations-13.0.jar"
)
foreach ($jar in $compilerCp) {
    if (!(Test-Path -LiteralPath $jar)) { throw "Missing compiler jar: $jar" }
}

$stdlib = "$k\kotlin-stdlib\2.0.21\618b539767b4899b4660a83006e052b63f1db551\kotlin-stdlib-2.0.21.jar"
if (!$Classpath) { $Classpath = $stdlib }

New-Item -ItemType Directory -Force -Path $Output | Out-Null
$args = @(
    '-cp', ($compilerCp -join ';'),
    'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler',
    '-no-stdlib', '-no-reflect',
    '-classpath', $Classpath,
    '-d', $Output
) + $Sources
& "$Java\bin\java.exe" @args
if ($LASTEXITCODE -ne 0) { throw "kotlinc failed: $LASTEXITCODE" }
