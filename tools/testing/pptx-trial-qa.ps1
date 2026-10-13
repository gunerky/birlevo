param([string]$Serial = 'emulator-5554', [switch]$SkipInstall)
$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$qaSdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { Join-Path $env:USERPROFILE 'AppData/Local/Android/Sdk' }
$qaAdb = Join-Path $qaSdk 'platform-tools/adb.exe'
$reportRoot = Join-Path $projectRoot 'output/development/pptx-trial'
New-Item -ItemType Directory -Force -Path $reportRoot | Out-Null
function Adb { & $qaAdb -s $Serial @args; if ($LASTEXITCODE -ne 0) { throw "ADB failed: $args" } }
if (((Adb emu avd name) | Out-String) -notmatch '(?m)^ofice_qa(?:_api35)?\s*$') { throw 'Only dedicated QA emulators are allowed.' }
Push-Location $projectRoot
try {
    if (-not $SkipInstall) {
        Adb install -r app/build/outputs/apk/debug/app-debug.apk
        Adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
    }
    $log = Join-Path $reportRoot 'device.log'
    Adb shell am instrument -w -r -e class com.example.contentstudio.PptxTrialDeviceTest studio.birlevo.android.test/androidx.test.runner.AndroidJUnitRunner | Out-File $log -Encoding utf8
    $result = Get-Content -LiteralPath $log -Raw
    if ($result -notmatch 'OK \((\d+) tests?\)') { Get-Content -LiteralPath $log -Tail 45; throw 'PPTX trial failed.' }
    Write-Output "PPTX native trial: $($Matches[1]) tests passed"
    $nativeRoot = Join-Path $reportRoot 'native'
    New-Item -ItemType Directory -Force -Path $nativeRoot | Out-Null
    # Explicit files keep repeat runs from nesting an extra pptx-trial directory.
    foreach ($file in @('source.png','expected-project.json','measurements.json','classic-editable.pptx','classic-slide_image.pptx','ocean-editable.pptx','ocean-slide_image.pptx','warm-editable.pptx','warm-slide_image.pptx')) {
        Adb pull "/sdcard/Android/data/studio.birlevo.android/files/qa/pptx-trial/$file" (Join-Path $nativeRoot $file)
    }
} finally { Pop-Location }
