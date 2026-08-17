param(
    [Parameter(Mandatory = $true)][string]$Project,
    [Parameter(Mandatory = $true)][string]$OutApk
)

$ErrorActionPreference = 'Stop'

$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$project = (Resolve-Path $Project).Path
$sdk = Join-Path $root 'tools\android-sdk\android-9'
$androidJar = Join-Path $sdk 'android.jar'
$manifest = Join-Path $project 'AndroidManifest.xml'
$resDir = Join-Path $project 'res'
$buildDir = Join-Path $project 'build'
$resZip = Join-Path $buildDir 'res.zip'
$unsignedApk = Join-Path $buildDir 'unsigned.apk'
$alignedApk = Join-Path $buildDir 'aligned.apk'
$genDir = Join-Path $buildDir 'gen'
$classesDir = Join-Path $buildDir 'classes'
$dexDir = Join-Path $buildDir 'dex'
$classesDex = Join-Path $dexDir 'classes.dex'

if (-not [System.IO.Path]::IsPathRooted($OutApk)) {
    $OutApk = Join-Path $root $OutApk
}
$outDir = Split-Path -Parent $OutApk
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

if (-not (Test-Path $androidJar)) {
    throw "缺少 $androidJar，请先下载并解压 Android SDK 28。"
}
if (-not (Test-Path (Join-Path $sdk 'lib\dx.jar'))) {
    throw "缺少 $sdk\lib\dx.jar，请先解压 build-tools 28。"
}

New-Item -ItemType Directory -Force -Path $buildDir, $genDir, $classesDir, $dexDir | Out-Null

Write-Host '==> 编译资源'
& (Join-Path $root 'tools\aapt2\aapt2.exe') compile --dir $resDir -o $resZip
if ($LASTEXITCODE -ne 0) {
    throw 'aapt2 compile 失败'
}

& (Join-Path $root 'tools\aapt2\aapt2.exe') link -o $unsignedApk -I $androidJar --manifest $manifest --java $genDir $resZip
if ($LASTEXITCODE -ne 0) {
    throw 'aapt2 link 失败'
}

Write-Host '==> 编译 Java'
$sourceFiles = @(Get-ChildItem -Path (Join-Path $project 'src') -Recurse -Filter *.java | Select-Object -ExpandProperty FullName)
$generatedFiles = @(Get-ChildItem -Path $genDir -Recurse -Filter *.java -ErrorAction SilentlyContinue | Select-Object -ExpandProperty FullName)
$allFiles = @($sourceFiles) + @($generatedFiles)
& javac -encoding UTF-8 -source 1.8 -target 1.8 -classpath $androidJar -d $classesDir $allFiles
if ($LASTEXITCODE -ne 0) {
    throw 'javac 失败'
}

Write-Host '==> 生成 dex'
& java -jar (Join-Path $sdk 'lib\dx.jar') --dex --output=$classesDex $classesDir
if ($LASTEXITCODE -ne 0) {
    throw 'dx 失败'
}

Push-Location $dexDir
try {
    & jar uf $unsignedApk classes.dex
    if ($LASTEXITCODE -ne 0) {
        throw '添加 classes.dex 失败'
    }
} finally {
    Pop-Location
}

Write-Host '==> 对齐并签名'
& (Join-Path $sdk 'zipalign.exe') -f 4 $unsignedApk $alignedApk
if ($LASTEXITCODE -ne 0) {
    throw 'zipalign 失败'
}

$keystore = Join-Path $root 'apk\debug.keystore'
& java -jar (Join-Path $sdk 'lib\apksigner.jar') sign `
    --ks $keystore `
    --ks-key-alias androiddebugkey `
    --ks-pass pass:android `
    --key-pass pass:android `
    --out $OutApk $alignedApk
if ($LASTEXITCODE -ne 0) {
    throw 'apksigner 失败'
}

Write-Host "==> 完成: $OutApk"
