param([Parameter(Mandatory)][string]$Serial)
$ErrorActionPreference = 'Stop'
$qaRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$qaAdb = Join-Path $env:LOCALAPPDATA 'Android/Sdk/platform-tools/adb.exe'
$qaOutput = Join-Path $qaRoot 'output/development/home-ad-flow/age-ui'
New-Item -ItemType Directory -Force $qaOutput | Out-Null
function Adb { & $qaAdb -s $Serial @args; if ($LASTEXITCODE -ne 0) { throw "ADB failed: $args" } }
if (((Adb emu avd name) | Out-String) -notmatch '(?m)^ofice_qa\s*$' -or
    ((Adb shell getprop ro.build.version.sdk) | Out-String).Trim() -ne '36') { throw 'Dedicated API 36 QA emulator required' }
$qaFont = ((Adb shell settings get system font_scale) | Out-String).Trim()
$qaSize = ((Adb shell wm size) | Out-String)
$qaDensity = ((Adb shell wm density) | Out-String)
$qaNight = ((Adb shell cmd uimode night) | Out-String).Trim()
if ($qaNight -notmatch '^Night mode: (no|yes|auto|custom)$') { throw 'Unknown previous night mode' }
$qaNightMode = $Matches[1]
Push-Location $qaRoot
try {
    Adb install -r app/build/outputs/apk/debug/app-debug.apk
    Adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
    Get-FileHash app/build/outputs/apk/debug/app-debug.apk | Format-List | Out-File (Join-Path $qaOutput 'environment.txt')
    foreach ($qaMode in @('phone','large-dark','rtl-large-dark','tablet')) {
        $qaLocale = if ($qaMode -eq 'rtl-large-dark') { 'ar' } else { 'en-US' }
        if ($qaMode -eq 'tablet') { Adb shell wm size 1600x2560; Adb shell wm density 240 }
        else { Adb shell wm size 720x1440; Adb shell wm density 360 }
        Adb shell settings put system font_scale $(if ($qaMode -match 'large') { '2.0' } else { '1.0' })
        Adb shell cmd uimode night $(if ($qaMode -match 'dark') { 'yes' } else { 'no' })
        $qaLog = Join-Path $qaOutput "$qaMode.log"
        Adb shell am instrument -w -r -e ad-account-network true -e ad-account-region eea -e ad-ui-locale $qaLocale -e class 'com.example.contentstudio.AdAccountPrivacyDeviceTest#homePreviewAgeChoicesAreAccessibleAndPersistAcrossNewActivity' studio.birlevo.android.test/androidx.test.runner.AndroidJUnitRunner *> $qaLog
        $qaText = Get-Content -LiteralPath $qaLog -Raw
        if ($qaText -notmatch 'OK \(1 test\)' -or $qaText -match 'FAILURES!!!|INSTRUMENTATION_FAILED|INSTRUMENTATION_STATUS_CODE: -[34]') {
            Get-Content -LiteralPath $qaLog -Tail 45
            throw "Age UI QA failed or skipped: $qaMode"
        }
        Adb pull /sdcard/Android/data/studio.birlevo.android/files/qa/account-privacy (Join-Path $qaOutput $qaMode) | Out-Null
        "$qaMode : passed"
    }
} finally {
    try {
        if ($qaSize -match 'Override size: (\d+x\d+)') { Adb shell wm size $Matches[1] }
        else { Adb shell wm size reset }
        if ($qaDensity -match 'Override density: (\d+)') { Adb shell wm density $Matches[1] }
        else { Adb shell wm density reset }
        if ($qaFont -eq 'null') { Adb shell settings delete system font_scale }
        else { Adb shell settings put system font_scale $qaFont }
        Adb shell cmd uimode night $qaNightMode
    } finally { Pop-Location }
}
