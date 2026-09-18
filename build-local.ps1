param(
    [string]$Sdk = "$env:LOCALAPPDATA/Android/Sdk",
    [string]$Java = 'C:/Program Files/Android/Android Studio/jbr',
    [string]$Platform = 'android-36.1',
    [string]$BuildTools = '36.1.0',
    [string]$Work = "$PSScriptRoot/../../work/android-build",
    [string]$Apk = "$PSScriptRoot/../Sanjiaohu-y7c_0.3.2.apk",
    # Kotlin. Leave both empty to use the compiler that already sits in the
    # Gradle cache (no download); pass -Kotlin with a kotlinc distribution for
    # machines without that cache, which is what CI does.
    [string]$Kotlin = '',
    [string]$KotlinJava = ''
)
$ErrorActionPreference = 'Stop'
$tool = Join-Path $Sdk "build-tools/$BuildTools"
$android = Join-Path $Sdk "platforms/$Platform/android.jar"
$app = Join-Path $PSScriptRoot 'app/src/main'
$resolvedWork = [System.IO.Path]::GetFullPath($Work)
foreach ($part in @('classes', 'kotlin-classes', 'generated', 'dex')) {
    $cleanPath = [System.IO.Path]::GetFullPath((Join-Path $resolvedWork $part))
    if (!$cleanPath.StartsWith($resolvedWork.TrimEnd('\','/') + [System.IO.Path]::DirectorySeparatorChar, [System.StringComparison]::OrdinalIgnoreCase)) { throw 'Build cleanup target outside work directory' }
    if (Test-Path -LiteralPath $cleanPath) { Remove-Item -LiteralPath $cleanPath -Recurse -Force }
}
New-Item -ItemType Directory -Force -Path $Work,"$Work/classes","$Work/generated","$Work/dex" | Out-Null
function Check { if ($LASTEXITCODE -ne 0) { throw "Build step failed: $LASTEXITCODE" } }
# Tools that write informational notes to stderr must not abort the script under
# $ErrorActionPreference='Stop' (PowerShell 5.1 turns native stderr into errors).
# Exit code remains the source of truth via Check.
function Invoke-Tool { param([string]$Exe, [string[]]$Arguments)
    $previous = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try { & $Exe @Arguments 2>&1 | ForEach-Object { Write-Host $_ } }
    finally { $ErrorActionPreference = $previous }
}
# kotlin-compiler-embeddable needs six companion jars on its own classpath.
# A Gradle cache nests each module as <module>/<version>/<content-hash>/<file>.jar
# and this machine happens to hold more than one Kotlin version, so pin the exact
# versions that were verified here instead of globbing for whatever is newest;
# mixing 1.9.24 and 2.0.21 jars would fail in confusing ways. Missing one jar
# produces failures such as NoClassDefFoundError: kotlin/jvm/internal/Intrinsics.
function Get-KotlinEmbeddableJars {
    param([string]$Version = '2.0.21', [string]$Coroutines = '1.6.4', [string]$Trove = '1.0.20200330', [string]$Annotations = '13.0')
    $m2 = "$env:USERPROFILE/.gradle/caches/modules-2/files-2.1"
    if (!(Test-Path -LiteralPath $m2)) { throw "No Gradle cache at $m2. Pass -Kotlin <kotlinc distribution> instead." }
    $wanted = @(
        @{ module = 'org.jetbrains.kotlin/kotlin-compiler-embeddable';   file = "kotlin-compiler-embeddable-$Version.jar" },
        @{ module = 'org.jetbrains.kotlin/kotlin-stdlib';               file = "kotlin-stdlib-$Version.jar" },
        @{ module = 'org.jetbrains.kotlin/kotlin-daemon-embeddable';     file = "kotlin-daemon-embeddable-$Version.jar" },
        @{ module = 'org.jetbrains.kotlin/kotlin-script-runtime';        file = "kotlin-script-runtime-$Version.jar" },
        @{ module = 'org.jetbrains.kotlinx/kotlinx-coroutines-core-jvm'; file = "kotlinx-coroutines-core-jvm-$Coroutines.jar" },
        @{ module = 'org.jetbrains.intellij.deps/trove4j';               file = "trove4j-$Trove.jar" },
        @{ module = 'org.jetbrains/annotations';                         file = "annotations-$Annotations.jar" }
    )
    $jars = @()
    foreach ($item in $wanted) {
        $moduleDir = Join-Path $m2 $item.module
        $found = @(Get-ChildItem -LiteralPath $moduleDir -Recurse -File -ErrorAction SilentlyContinue |
                   Where-Object { $_.Name -eq $item.file })
        if ($found.Count -eq 0) { throw "Missing Kotlin compiler dependency $($item.file) under $moduleDir" }
        $jars += $found[0].FullName
    }
    return $jars
}
& "$tool/aapt2.exe" compile --dir "$app/res" -o "$Work/resources.zip"
Check
# Read/write as explicit UTF-8. Get-Content/Set-Content -Encoding utf8 under
# Windows PowerShell 5.1 round-trips through the ANSI code page and corrupts the
# non-ASCII app label (三角狐), producing an invalid manifest.
$utf8NoBom = New-Object System.Text.UTF8Encoding $false
$manifestText = [System.IO.File]::ReadAllText("$app/AndroidManifest.xml", $utf8NoBom)
$manifestText = $manifestText.Replace('<manifest ', '<manifest package="cn.jhun.sanjiaohu" ')
[System.IO.File]::WriteAllText("$Work/AndroidManifest.xml", $manifestText, $utf8NoBom)
& "$tool/aapt2.exe" link -o "$Work/unsigned.apk" -I $android --manifest "$Work/AndroidManifest.xml" -A "$app/assets" --java "$Work/generated" "$Work/resources.zip"
Check
$sources = @(Get-ChildItem -LiteralPath "$app/java","$Work/generated" -Recurse -Filter '*.java' | ForEach-Object { $_.FullName })
$kotlinSources = @(Get-ChildItem -LiteralPath "$app/java" -Recurse -Filter '*.kt' | ForEach-Object { $_.FullName })
$compileClasspath = "$android;$tool/core-lambda-stubs.jar"
$jarRoots = @()
$dexInputs = @()
if ($kotlinSources.Count -gt 0) {
    New-Item -ItemType Directory -Force -Path "$Work/kotlin-classes" | Out-Null
    # Kotlin 2.0.21 cannot parse JDK 25's version string ("IllegalArgumentException:
    # 25.0.2"), so the compiler runs on a JDK it understands unless told otherwise.
    $kotlinRuntime = $KotlinJava
    if (!$kotlinRuntime) { $kotlinRuntime = if (Test-Path 'D:/jdk22/bin/java.exe') { 'D:/jdk22' } else { $Java } }
    $env:JAVA_HOME = $kotlinRuntime
    if ($Kotlin) {
        $kotlinLauncher = Join-Path $Kotlin 'bin/kotlinc.bat'
        if (!(Test-Path -LiteralPath $kotlinLauncher)) { throw "Not a kotlinc distribution: $Kotlin" }
        $stdlib = Join-Path (Join-Path $Kotlin 'lib') 'kotlin-stdlib.jar'
        if (!(Test-Path -LiteralPath $stdlib)) { throw "No kotlin-stdlib.jar in $Kotlin/lib" }
        $kotlinArgs = @('-jvm-target','1.8','-classpath',"$stdlib;$compileClasspath",'-d',"$Work/kotlin-classes") + $kotlinSources + $sources
        Invoke-Tool $kotlinLauncher $kotlinArgs
        Check
    } else {
        $compilerJars = Get-KotlinEmbeddableJars
        $stdlib = ($compilerJars | Where-Object { $_ -match 'kotlin-stdlib' } | Select-Object -First 1)
        # The Java sources go in for symbol resolution only; classes are emitted
        # for the .kt files alone, so javac still owns the .java output.
        #
        # -no-stdlib turns off the compiler's built-in default, so kotlin-stdlib
        # has to be supplied explicitly on -classpath. Without it every stdlib
        # symbol (String.trim, Regex, @JvmField) reports "unresolved reference".
        $kotlinArgs = @('-cp',($compilerJars -join ';'),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler',
            '-no-stdlib','-no-reflect','-jvm-target','1.8',
            '-classpath',"$stdlib;$compileClasspath",'-d',"$Work/kotlin-classes") + $kotlinSources + $sources
        Invoke-Tool "$kotlinRuntime/bin/java.exe" $kotlinArgs
        Check
    }
    Write-Host "Kotlin: $($kotlinSources.Count) source file(s) compiled"
    $compileClasspath = "$Work/kotlin-classes;$stdlib;$compileClasspath"
    $jarRoots += "$Work/kotlin-classes"
    # kotlin-stdlib has to be dexed into the APK, or the app dies at runtime with
    # NoClassDefFoundError on kotlin/jvm/internal/Intrinsics.
    $dexInputs += $stdlib
}
$javacArgs = @('-encoding','UTF-8','-source','8','-target','8','-Xlint:-options','-classpath',$compileClasspath,'-bootclasspath',"$android;$tool/core-lambda-stubs.jar",'-d',"$Work/classes") + $sources
Invoke-Tool "$Java/bin/javac.exe" $javacArgs
Check
$jarArgs = @('cf', "$Work/classes.jar")
foreach ($root in (@("$Work/classes") + $jarRoots)) { $jarArgs += @('-C', $root, '.') }
Invoke-Tool "$Java/bin/jar.exe" $jarArgs
Check
$ErrorActionPreference = 'Continue'
Invoke-Tool "$Java/bin/java.exe" (@('-cp',"$tool/lib/d8.jar",'com.android.tools.r8.D8','--lib',$android,'--min-api','26','--output',"$Work/dex","$Work/classes.jar") + $dexInputs)
Check
Invoke-Tool "$Java/bin/jar.exe" @('uf',"$Work/unsigned.apk",'-C',"$Work/dex",'classes.dex')
Check
Invoke-Tool "$tool/zipalign.exe" @('-f','-p','4',"$Work/unsigned.apk","$Work/aligned.apk")
Check
if (!(Test-Path -LiteralPath "$Work/development.keystore")) {
    Invoke-Tool "$Java/bin/keytool.exe" @('-genkeypair','-keystore',"$Work/development.keystore",'-storepass','android','-keypass','android','-alias','androiddebugkey','-dname','CN=Jiangke Local Build','-keyalg','RSA','-keysize','2048','-validity','10000')
    Check
}
Invoke-Tool "$Java/bin/java.exe" @('-jar',"$tool/lib/apksigner.jar",'sign','--ks',"$Work/development.keystore",'--ks-pass','pass:android','--key-pass','pass:android','--out',$Apk,"$Work/aligned.apk")
Check
Invoke-Tool "$Java/bin/java.exe" @('-jar',"$tool/lib/apksigner.jar",'verify','--verbose',$Apk)
Check
Get-FileHash -LiteralPath $Apk -Algorithm SHA256
