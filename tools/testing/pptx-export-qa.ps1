param([ValidateSet('feature','layouts','regression')][string]$Suite = 'feature', [string]$Serial = 'emulator-5554', [switch]$SkipInstall)
$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$qaSdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { Join-Path $env:USERPROFILE 'AppData/Local/Android/Sdk' }
$qaAdb = Join-Path $qaSdk 'platform-tools/adb.exe'
$reportRoot = Join-Path $projectRoot 'output/development/pptx-export'
New-Item -ItemType Directory -Force -Path $reportRoot | Out-Null
function Adb { & $qaAdb -s $Serial @args; if ($LASTEXITCODE -ne 0) { throw "ADB failed: $args" } }
if (((Adb emu avd name) | Out-String) -notmatch '(?m)^ofice_qa(?:_api35)?\s*$') { throw 'Only dedicated QA emulators are allowed.' }
function TestSuite([string]$Classes, [string]$Name, [string]$Locale = 'en-US') {
    $log = Join-Path $reportRoot "$Name.log"
    Adb shell am instrument -w -r -e audit-layout $Name -e audit-locale $Locale -e class $Classes studio.birlevo.android.test/androidx.test.runner.AndroidJUnitRunner | Out-File $log -Encoding utf8
    $result = Get-Content -LiteralPath $log -Raw
    if ($result -notmatch 'OK \((\d+) tests?\)') { Get-Content -LiteralPath $log -Tail 45; throw "Device tests failed: $Name" }
    Write-Output "$Name : $($Matches[1]) tests passed"
}
Push-Location $projectRoot
try {
    if (-not $SkipInstall) { Adb install -r app/build/outputs/apk/debug/app-debug.apk; Adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk }
    if ($Suite -eq 'layouts') {
        $oldFont = ((Adb shell settings get system font_scale) | Out-String).Trim()
        $oldSize = ((Adb shell wm size) | Out-String); $oldDensity = ((Adb shell wm density) | Out-String)
        $oldNight = ((Adb shell cmd uimode night) | Out-String).Trim()
        foreach ($mode in @('phone','tablet','large-text','dark','rtl')) {
            if ($mode -eq 'tablet') { Adb shell wm size 1600x2560; Adb shell wm density 240 }
            elseif ($mode -eq 'large-text') { Adb shell wm size 720x1440; Adb shell wm density 360 }
            else { Adb shell wm size reset; Adb shell wm density reset }
            Adb shell settings put system font_scale $(if ($mode -eq 'large-text') { '2.0' } else { '1.0' })
            Adb shell cmd uimode night $(if ($mode -eq 'dark') { 'yes' } else { 'no' })
            TestSuite 'com.example.contentstudio.PresentationPptxUiDeviceTest' $mode $(if ($mode -eq 'rtl') { 'ar' } else { 'en-US' })
        }
    } elseif ($Suite -eq 'regression') {
        TestSuite 'com.example.contentstudio.ProjectAutosaveDeviceTest,com.example.contentstudio.PresentationDesignDeviceTest,com.example.contentstudio.PresentationPdfDeviceTest,com.example.contentstudio.PresentationModeDeviceTest,com.example.contentstudio.PresentationModeUiDeviceTest,com.example.contentstudio.BackgroundProcessingDeviceTest' 'regression'
    } else { TestSuite 'com.example.contentstudio.PresentationPptxDeviceTest,com.example.contentstudio.PresentationPptxUiDeviceTest' 'feature' }
    if ($Suite -eq 'feature') {
        $native = Join-Path $reportRoot 'native'
        New-Item -ItemType Directory -Force -Path $native | Out-Null
        foreach ($edge in @(1024,2048,4096)) { foreach ($notes in @('false','true')) {
            Adb pull "/sdcard/Android/data/studio.birlevo.android/files/qa/pptx-export/edge-$edge-notes-$notes.pptx" (Join-Path $native "edge-$edge-notes-$notes.pptx")
        } }
        Adb pull /sdcard/Android/data/studio.birlevo.android/files/qa/pptx-export/expected-project.json (Join-Path $native 'expected-project.json')
    }
    Adb pull /sdcard/Android/data/studio.birlevo.android/files/qa/pptx-export (Join-Path $reportRoot ('screens-' + $Suite + '-' + [DateTime]::UtcNow.Ticks))
} finally {
    if ($Suite -eq 'layouts') {
        if ($oldSize -match 'Override size: ([0-9]+x[0-9]+)') { Adb shell wm size $Matches[1] } else { Adb shell wm size reset }
        if ($oldDensity -match 'Override density: ([0-9]+)') { Adb shell wm density $Matches[1] } else { Adb shell wm density reset }
        if ($oldFont -eq 'null') { Adb shell settings delete system font_scale } else { Adb shell settings put system font_scale $oldFont }
        if ($oldNight -match 'Night mode: (\w+)') { Adb shell cmd uimode night $Matches[1] }
    }
    Pop-Location
}
