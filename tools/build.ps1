param(
    [ValidateSet("all", "reader", "phone")]
    [string]$App = "all",
    [string]$Sdk = "",
    [string]$StorePass = "einknav"
)

# 免 Gradle 的 APK 打包：aapt2(res) + javac + d8 + zip + zipalign + apksigner
$ErrorActionPreference = "Continue"
$Root = Split-Path -Parent $PSScriptRoot
$OutRoot = Join-Path $Root ".build"
$Ks = Join-Path $OutRoot "debug.keystore"

if (-not $Sdk) { $Sdk = $env:ANDROID_SDK_ROOT }
if (-not $Sdk) { $Sdk = $env:ANDROID_HOME }
if (-not $Sdk -or -not (Test-Path $Sdk)) {
    throw "未找到 Android SDK：请用 -Sdk 指定，或设置 ANDROID_SDK_ROOT 环境变量"
}

# --- 定位 android.jar（API 30）---
$aj = @(
    (Join-Path $Sdk "platforms\android-30\android.jar"),
    (Join-Path $Sdk "android-11\android.jar")
) | Where-Object { Test-Path $_ } | Select-Object -First 1
if (-not $aj) { throw "未找到 API 30 的 android.jar（需安装 platforms;android-30）" }

# --- 定位 build-tools（兼容 SDK Manager 与官方 zip 解包两种布局）---
$bt = ""
$btDir = Join-Path $Sdk "build-tools"
if (Test-Path $btDir) {
    $cand = Get-ChildItem $btDir -Directory | Sort-Object Name -Descending | Select-Object -First 1
    if ($cand) { $bt = $cand.FullName }
}
if (-not $bt) {
    $cand = Get-ChildItem $Sdk -Directory | Where-Object { Test-Path (Join-Path $_.FullName "aapt2.exe") } | Select-Object -First 1
    if ($cand) { $bt = $cand.FullName }
}
if (-not $bt) { throw "未找到 build-tools（需要含 aapt2.exe / d8 的目录）" }

Write-Host "SDK     : $Sdk"
Write-Host "android : $aj"
Write-Host "tools   : $bt"

New-Item -ItemType Directory -Force -Path $OutRoot | Out-Null

if (-not (Test-Path $Ks)) {
    & keytool -genkeypair -keystore $Ks -alias einknav -storepass $StorePass -keypass $StorePass `
        -keyalg RSA -keysize 2048 -validity 10000 `
        -dname "CN=E-Ink Nav,OU=dev,O=einknav,L=NA,ST=NA,C=CN" 2>$null | Out-Null
    if (-not (Test-Path $Ks)) { throw "签名密钥创建失败" }
    Write-Host "已生成签名密钥: $Ks"
}

function Build-Apk([string]$name) {
    $src = Join-Path $Root $name
    $out = Join-Path $OutRoot $name
    if (-not (Test-Path $src)) { throw "找不到源码目录 $src" }
    Remove-Item -Recurse -Force $out -ErrorAction SilentlyContinue
    New-Item -ItemType Directory -Force -Path (Join-Path $out "classes"), (Join-Path $out "res-c") | Out-Null

    $linkArgs = @()
    $resDir = Join-Path $src "res"
    if (Test-Path $resDir) {
        & (Join-Path $bt "aapt2.exe") compile --dir $resDir -o (Join-Path $out "res-c")
        if ($LASTEXITCODE -ne 0) { throw "aapt2 compile 失败 ($name)" }
        Get-ChildItem (Join-Path $out "res-c") -Filter *.flat -Recurse | ForEach-Object {
            $linkArgs += @("-R", $_.FullName)
        }
    }

    $base = Join-Path $out "base.apk"
    & (Join-Path $bt "aapt2.exe") link -o $base -I $aj `
        --manifest (Join-Path $src "AndroidManifest.xml") `
        --min-sdk-version 26 --target-sdk-version 30 --auto-add-overlay @linkArgs
    if ($LASTEXITCODE -ne 0) { throw "aapt2 link 失败 ($name)" }

    $javaFiles = Get-ChildItem (Join-Path $src "src") -Recurse -Filter *.java | ForEach-Object { $_.FullName }
    & javac -encoding UTF-8 -source 8 -target 8 -nowarn -classpath $aj `
        -d (Join-Path $out "classes") @javaFiles
    if ($LASTEXITCODE -ne 0) { throw "javac 失败 ($name)" }

    & jar cf (Join-Path $out "classes.jar") -C (Join-Path $out "classes") .
    & java -cp (Join-Path $bt "lib\d8.jar") com.android.tools.r8.D8 --release --min-api 26 `
        --lib $aj --output $out (Join-Path $out "classes.jar")
    if ($LASTEXITCODE -ne 0) { throw "d8 失败 ($name)" }

    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $zip = [System.IO.Compression.ZipFile]::Open($base, 'Update')
    $entry = $zip.CreateEntry("classes.dex", [System.IO.Compression.CompressionLevel]::Optimal)
    $st = $entry.Open()
    $bytes = [System.IO.File]::ReadAllBytes((Join-Path $out "classes.dex"))
    $st.Write($bytes, 0, $bytes.Length)
    $st.Close()
    $zip.Dispose()

    & (Join-Path $bt "zipalign.exe") -f 4 $base (Join-Path $out "aligned.apk")
    if ($LASTEXITCODE -ne 0) { throw "zipalign 失败 ($name)" }

    $final = Join-Path $out "$name.apk"
    & java -jar (Join-Path $bt "lib\apksigner.jar") sign --ks $Ks --ks-pass "pass:$StorePass" `
        --key-pass "pass:$StorePass" --ks-key-alias einknav --out $final (Join-Path $out "aligned.apk")
    if ($LASTEXITCODE -ne 0) { throw "签名失败 ($name)" }

    $size = [math]::Round((Get-Item $final).Length / 1KB, 1)
    Write-Host "OK  $final  (${size} KB)"
}

if ($App -eq "all" -or $App -eq "reader") { Build-Apk "reader" }
if ($App -eq "all" -or $App -eq "phone") { Build-Apk "phone" }
