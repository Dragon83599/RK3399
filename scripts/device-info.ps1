$ErrorActionPreference = 'Stop'
$adb = (Resolve-Path (Join-Path $PSScriptRoot '..\tools\scrcpy-win64-v4.1\adb.exe')).Path

& $adb shell "getprop | grep -E 'ro\.(board|product|build|rk|serialno)'"
& $adb shell "cat /proc/asound/cards"
& $adb shell "wm size; wm density"
& $adb shell "ip -brief addr show"
& $adb shell "pm list packages -3"
