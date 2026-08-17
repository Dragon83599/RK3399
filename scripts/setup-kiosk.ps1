$ErrorActionPreference = 'Stop'
$adb = (Resolve-Path (Join-Path $PSScriptRoot '..\tools\scrcpy-win64-v4.1\adb.exe')).Path

# Keep screen on while plugged in and disable screen timeout.
& $adb shell "settings put global stay_on_while_plugged_in 3"
& $adb shell "settings put system screen_off_timeout 2147483647"

# Disable lock screen and immersive mode confirmation prompts.
& $adb shell "settings put secure lock_screen_disabled 1"
& $adb shell "settings put secure immersive_mode_confirmations confirmed"
