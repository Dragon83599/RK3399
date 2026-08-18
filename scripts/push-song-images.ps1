param(
    [string]$ImageDir = 'E:\BaiduNetdiskDownload\宋',
    [string]$RemoteDir = '/sdcard/Pictures/Song'
)

$ErrorActionPreference = 'Stop'
$adb = (Resolve-Path (Join-Path $PSScriptRoot '..\tools\scrcpy-win64-v4.1\adb.exe')).Path
$env:ANDROID_SDK_HOME = $env:USERPROFILE

if (-not (Test-Path -LiteralPath $ImageDir)) {
    throw "图片目录不存在: $ImageDir"
}

$deviceLines = @($devices | Where-Object { $_ -match '\tdevice$' })
if ($deviceLines.Count -eq 0) {
    throw '未检测到 adb 设备，请连接 USB 调试线并确认设备已开机。'
}
$rkLine = $deviceLines | Where-Object { $_ -match 'rk3399|K71V7BTYKP' } | Select-Object -First 1
if ($rkLine) {
    $env:ANDROID_SERIAL = ($rkLine -split "`t")[0]
} elseif ($deviceLines.Count -eq 1) {
    $env:ANDROID_SERIAL = ($deviceLines[0] -split "`t")[0]
} else {
    throw '检测到多台 adb 设备且未找到 RK3399，请只连接板子或指定 ANDROID_SERIAL。'
}

$files = @(Get-ChildItem -LiteralPath $ImageDir -Recurse -File |
    Where-Object { $_.Extension -in '.jpg', '.jpeg', '.png', '.webp' })
Write-Host "找到 $($files.Count) 张图片，开始推送到 $RemoteDir"

& $adb shell "mkdir -p $RemoteDir"
$index = 0
foreach ($file in $files) {
    $index++
    Write-Progress -Activity '推送图片到设备' -Status $file.Name -PercentComplete (($index / $files.Count) * 100)
    & $adb push $file.FullName "$RemoteDir/$($file.Name)"
    if ($LASTEXITCODE -ne 0) {
        throw "adb push 失败: $($file.FullName)"
    }
}
Write-Progress -Activity '推送图片到设备' -Completed

& $adb shell "sync"
Write-Host '图片推送完成。'
