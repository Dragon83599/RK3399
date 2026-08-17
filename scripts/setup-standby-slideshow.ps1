param(
    [string]$ImageDir = 'E:\BaiduNetdiskDownload\宋',
    [string]$Apk = (Join-Path $PSScriptRoot '..\apk\standby.apk'),
    [switch]$SkipImages,
    [switch]$Preview
)

$ErrorActionPreference = 'Stop'
$adb = (Resolve-Path (Join-Path $PSScriptRoot '..\tools\scrcpy-win64-v4.1\adb.exe')).Path
$env:ANDROID_SDK_HOME = $env:USERPROFILE

if (-not (Test-Path $Apk)) {
    Write-Host '未找到 standby.apk，先自动构建。'
    & (Join-Path $PSScriptRoot 'build-standby-apk.ps1')
}

$devices = & $adb devices
if (-not ($devices | Where-Object { $_ -match '\tdevice$' })) {
    throw '未检测到 adb 设备，请连接 USB 调试线并确认设备已开机。'
}

& $adb root | Out-Null
& $adb wait-for-device

if (-not $SkipImages -and (Test-Path -LiteralPath $ImageDir)) {
    & (Join-Path $PSScriptRoot 'push-song-images.ps1') -ImageDir $ImageDir
} elseif (-not $SkipImages) {
    Write-Host "图片目录不存在，跳过推图: $ImageDir"
}

Write-Host '==> 安装应用'
& $adb shell "settings put global verifier_verify_adb_installs 0"
& $adb install -r $Apk
if ($LASTEXITCODE -ne 0) {
    throw 'APK 安装失败'
}

& $adb shell "pm grant com.zysj.standby android.permission.READ_EXTERNAL_STORAGE"

Write-Host '==> 启用待机屏保'
$settings = @(
    'screensaver_enabled 1',
    'screensaver_activate_on_sleep 1',
    'screensaver_activate_on_dock 1',
    'screensaver_activate_on_plugged_in 1',
    'screensaver_components com.zysj.standby/com.zysj.standby.SongDreamService',
    'screensaver_default_component com.zysj.standby/com.zysj.standby.SongDreamService'
)
foreach ($setting in $settings) {
    & $adb shell "settings put secure $setting"
}

# Let the screen time out into the dream instead of turning off.
& $adb shell "settings put global stay_on_while_plugged_in 0"
& $adb shell "settings put system screen_off_timeout 60000"

$hasDreamsCmd = & $adb shell "command -v dreams-cmd"
if ($hasDreamsCmd -match 'dreams-cmd') {
    Write-Host '==> 立即启动屏保测试'
    & $adb shell "dreams-cmd start"
} elseif ($Preview) {
    Write-Host '==> 启动预览界面'
    & $adb shell "am start -n com.zysj.standby/.MainActivity"
} else {
    Write-Host '已启用待机屏保。设备空闲 60 秒后会自动进入轮播；可手动打开“宋画屏保”预览。'
}

Write-Host '完成。'
