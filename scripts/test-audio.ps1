param(
    [string]$Wav = (Join-Path $PSScriptRoot '..\assets\test-tone-1khz.wav')
)

$ErrorActionPreference = 'Stop'
$adb = (Resolve-Path (Join-Path $PSScriptRoot '..\tools\scrcpy-win64-v4.1\adb.exe')).Path
$remote = '/data/local/tmp/test-tone-1khz.wav'

& $adb push (Resolve-Path $Wav).Path $remote
& $adb shell "tinyplay $remote"
