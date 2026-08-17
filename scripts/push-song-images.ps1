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

$devices = & $adb devices
if (-not ($devices | Where-Object { $_ -match '\tdevice$' })) {
    throw '未检测到 adb 设备，请连接 USB 调试线并确认设备已开机。'
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
