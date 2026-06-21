$ErrorActionPreference = "Stop"

$ProjectDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$SdkDir = if ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } elseif ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { "C:\Program Files (x86)\Android\android-sdk" }
$BuildToolsDir = Join-Path $SdkDir "build-tools\36.0.0"
if (-not (Test-Path $BuildToolsDir)) {
    $BuildToolsDir = Join-Path $SdkDir "build-tools\35.0.0"
}
$PlatformJar = Join-Path $SdkDir "platforms\android-35\android.jar"
if (-not (Test-Path $PlatformJar)) {
    $PlatformJar = Join-Path $SdkDir "platforms\android-34\android.jar"
}

$Aapt2 = Join-Path $BuildToolsDir "aapt2.exe"
$D8 = Join-Path $BuildToolsDir "d8.bat"
$Zipalign = Join-Path $BuildToolsDir "zipalign.exe"
$Apksigner = Join-Path $BuildToolsDir "apksigner.bat"
$JavaHome = if ($env:JAVA_HOME) { $env:JAVA_HOME } elseif (Test-Path "C:\Program Files\Android\openjdk\jdk-21.0.8") { "C:\Program Files\Android\openjdk\jdk-21.0.8" } else { "C:\Program Files\Android\jdk\jdk-8.0.302.8-hotspot\jdk8u302-b08" }
$Javac = Join-Path $JavaHome "bin\javac.exe"
$Keytool = Join-Path $JavaHome "bin\keytool.exe"

$BuildDir = Join-Path $ProjectDir "build"
$GenDir = Join-Path $BuildDir "gen"
$ObjDir = Join-Path $BuildDir "obj"
$DexDir = Join-Path $BuildDir "dex"
$OutDir = Join-Path $ProjectDir "out"
$Unsigned = Join-Path $BuildDir "AgriPrice-unsigned.apk"
$Aligned = Join-Path $BuildDir "AgriPrice-aligned.apk"
$FinalApk = Join-Path $OutDir "AgriPrice.apk"
$ManifestText = Get-Content (Join-Path $ProjectDir "AndroidManifest.xml") -Raw -Encoding UTF8
$VersionName = if ($ManifestText -match 'android:versionName="([^"]+)"') { $Matches[1] } else { "dev" }
$VersionedApk = Join-Path $OutDir "AgriPrice_v$VersionName.apk"
$Keystore = Join-Path $BuildDir "debug.keystore"

New-Item -ItemType Directory -Force -Path $BuildDir, $GenDir, $ObjDir, $DexDir, $OutDir | Out-Null
Get-ChildItem $ObjDir -Recurse -ErrorAction SilentlyContinue | Remove-Item -Recurse -Force
Get-ChildItem $DexDir -Recurse -ErrorAction SilentlyContinue | Remove-Item -Recurse -Force

function Invoke-Checked($Command, $Arguments) {
    & $Command @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Command failed: $Command $($Arguments -join ' ')"
    }
}

Push-Location $ProjectDir
Invoke-Checked $Aapt2 @("compile", "--dir", "res", "-o", (Join-Path $BuildDir "resources.zip"))
Invoke-Checked $Aapt2 @("link", "-o", $Unsigned, "-I", $PlatformJar, "--manifest", "AndroidManifest.xml", "--java", $GenDir, (Join-Path $BuildDir "resources.zip"))
Pop-Location

$Sources = @()
$Sources += Get-ChildItem (Join-Path $ProjectDir "src") -Recurse -Filter *.java | ForEach-Object { $_.FullName }
$Sources += Get-ChildItem $GenDir -Recurse -Filter *.java | ForEach-Object { $_.FullName }
$JavacArgs = @("-encoding", "UTF-8", "-source", "8", "-target", "8", "-bootclasspath", $PlatformJar, "-d", $ObjDir)
$JavacArgs += $Sources
Invoke-Checked $Javac $JavacArgs
$ClassFiles = Get-ChildItem $ObjDir -Recurse -Filter *.class | ForEach-Object { $_.FullName }
$D8Args = @("--release", "--min-api", "23", "--lib", $PlatformJar, "--output", $DexDir)
$D8Args += $ClassFiles
Invoke-Checked $D8 $D8Args

Copy-Item $Unsigned $Aligned -Force
Push-Location $DexDir
Invoke-Checked "jar" @("uf", $Aligned, "classes.dex")
Pop-Location
Invoke-Checked $Zipalign @("-f", "4", $Aligned, $FinalApk)

if (-not (Test-Path $Keystore)) {
    Invoke-Checked $Keytool @("-genkeypair", "-v", "-keystore", $Keystore, "-storepass", "android", "-keypass", "android", "-alias", "androiddebugkey", "-keyalg", "RSA", "-keysize", "2048", "-validity", "10000", "-dname", "CN=Android Debug,O=Android,C=US")
}
Invoke-Checked $Apksigner @("sign", "--ks", $Keystore, "--ks-pass", "pass:android", "--key-pass", "pass:android", "--out", $FinalApk, $FinalApk)
Invoke-Checked $Apksigner @("verify", $FinalApk)
Copy-Item $FinalApk $VersionedApk -Force

Write-Host "APK path: $FinalApk"
Write-Host "Versioned APK path: $VersionedApk"
