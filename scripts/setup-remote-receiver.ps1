param(
    [string]$Apk = (Join-Path $PSScriptRoot '..\apk\remote-receiver.apk'),
    [string]$Serial = 'K71V7BTYKP',
    [switch]$SkipBuild,
    [switch]$RestartFramework
)

$ErrorActionPreference = 'Stop'

$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$adb = (Resolve-Path (Join-Path $root 'tools\scrcpy-win64-v4.1\adb.exe')).Path

if (-not $SkipBuild) {
    & (Join-Path $PSScriptRoot 'build-apk.ps1') `
        -Project (Join-Path $root 'remote-receiver') `
        -OutApk $Apk
}

$devices = & $adb devices
if (-not ($devices | Where-Object { $_ -match ("^" + $Serial + "\s+device$") })) {
    throw '未检测到 adb 设备，请连接 USB 调试线并确认设备已开机。'
}

& $adb -s $Serial root | Out-Null
& $adb -s $Serial wait-for-device

Write-Host '==> 安装接收端'
& $adb -s $Serial install -r $Apk
if ($LASTEXITCODE -ne 0) {
    throw '接收端 APK 安装失败'
}

& $adb -s $Serial shell "am force-stop com.zysj.speaker.remote"
& $adb -s $Serial shell "appops set com.zysj.speaker.remote SYSTEM_ALERT_WINDOW allow"
& $adb -s $Serial shell "settings put secure enabled_accessibility_services com.zysj.speaker.remote/com.zysj.speaker.remote.TouchAccessibilityService"
& $adb -s $Serial shell "settings put secure accessibility_enabled 1"
& $adb -s $Serial shell "settings put secure enabled_notification_listeners com.zysj.speaker.remote/com.zysj.speaker.remote.MediaNotificationListener"
& $adb -s $Serial shell "cmd notification allow_listener com.zysj.speaker.remote/com.zysj.speaker.remote.MediaNotificationListener"
& $adb -s $Serial shell "am start -n com.zysj.speaker.remote/.MainActivity"
Start-Sleep -Seconds 2

if ($RestartFramework) {
    Write-Host '==> 重启 framework 使无障碍服务生效'
    & $adb -s $Serial shell "stop"
    & $adb -s $Serial wait-for-device
    & $adb -s $Serial shell "start"
    Start-Sleep -Seconds 12
}

$accDump = (& $adb -s $Serial shell "dumpsys accessibility" | Out-String)
if ($accDump -match '遥控接收端') {
    Write-Host '无障碍注入：已启用'
} else {
    Write-Host '无障碍注入：未启用'
    if (-not $RestartFramework) {
        Write-Host '提示：首次安装后请重启一次系统（或加 -RestartFramework 参数重跑本脚本），悬浮光标和点击注入才会生效。'
    }
}

Write-Host '==> 启动 root 输入通道'
& $adb -s $Serial push (Join-Path $PSScriptRoot 'input_helper.sh') /data/local/tmp/input_helper.sh | Out-Null
& $adb -s $Serial shell "chmod 755 /data/local/tmp/input_helper.sh"
& $adb -s $Serial shell "pkill -f input_helper.sh" 2>$null | Out-Null
& $adb -s $Serial shell "setsid sh /data/local/tmp/input_helper.sh >/dev/null 2>&1 &"
Start-Sleep -Milliseconds 800

$ipLine = & $adb -s $Serial shell "ip -4 addr show wlan0" | Where-Object { $_ -match 'inet ' } | Select-Object -First 1
$ip = ''
if ($ipLine -match 'inet\s+([\d.]+)') {
    $ip = $Matches[1]
}
Write-Host "接收端已启动: http://${ip}:8080"

& $adb -s $Serial forward tcp:18080 tcp:8080 | Out-Null
Start-Sleep -Milliseconds 500
try {
    $status = Invoke-RestMethod -Uri 'http://127.0.0.1:18080/api/status' -TimeoutSec 5
    Write-Host ("状态: ok={0} 音量={1}/{2} 应用={3} 输入通道={4}" -f $status.ok, $status.volume, $status.maxVolume, $status.app, $status.inputHelper)
} catch {
    Write-Host 'HTTP 状态检查暂未通过，接收端可能仍在启动。'
}

try {
    $navDump = Invoke-RestMethod -Uri 'http://127.0.0.1:18080/api/cmd?cmd=nav_dump' -Method Post -TimeoutSec 5
    if ($navDump.windows.Count -lt 2) {
        Write-Host '提示：无障碍窗口读取未生效，后台键可能不稳定。请加 -RestartFramework 重跑一次本脚本，或重启一次系统。'
    }
} catch {
    Write-Host '提示：后台键功能检查暂未通过，请加 -RestartFramework 重跑一次本脚本，或重启一次系统。'
}
