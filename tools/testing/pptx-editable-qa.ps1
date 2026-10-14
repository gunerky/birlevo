param([string]$Serial='emulator-5554',[switch]$SkipInstall)
$ErrorActionPreference='Stop'
$qaRoot=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$qaAdb='C:/Users/user/AppData/Local/Android/Sdk/platform-tools/adb.exe'
$qaReports=Join-Path $qaRoot 'output/development/pptx-editable'
New-Item -ItemType Directory -Force -Path $qaReports | Out-Null
function Adb { & $qaAdb -s $Serial @args; if($LASTEXITCODE -ne 0){throw "ADB failed: $args"} }
if(((Adb emu avd name)|Out-String) -notmatch '(?m)^ofice_qa(?:_api35)?\s*$'){throw 'Dedicated QA required'}
function RunTests([string]$Name,[string]$Classes,[string]$Locale='en-US') {
    $qaLog=Join-Path $qaReports "$Name.log"
    Adb shell am instrument -w -r -e audit-layout $Name -e audit-locale $Locale -e class $Classes studio.birlevo.android.test/androidx.test.runner.AndroidJUnitRunner | Out-File -LiteralPath $qaLog -Encoding utf8
    $qaResult=Get-Content -LiteralPath $qaLog -Raw
    if($qaResult -notmatch 'OK \((\d+) tests?\)' -or $qaResult -match 'FAILURES!!!|INSTRUMENTATION_FAILED') {Get-Content -LiteralPath $qaLog -Tail 30;throw "Failed: $Name"}
    "$Name : $($Matches[1]) tests passed"
}
$qaFont=((Adb shell settings get system font_scale)|Out-String).Trim()
$qaSize=((Adb shell wm size)|Out-String)
$qaDensity=((Adb shell wm density)|Out-String)
Push-Location $qaRoot
try {
    if(-not $SkipInstall){Adb install -r app/build/outputs/apk/debug/app-debug.apk;Adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk}
    RunTests 'feature' 'com.example.contentstudio.PptxBidiDeviceTest,com.example.contentstudio.EditablePptxDeviceTest,com.example.contentstudio.PresentationPptxDeviceTest,com.example.contentstudio.PresentationPptxUiDeviceTest'
    $qaUi='com.example.contentstudio.PresentationPptxUiDeviceTest#editableModeRestoresAndSwitchingModesInvalidatesTheResult,com.example.contentstudio.PresentationPptxUiDeviceTest#unsupportedScriptKeepsAppearanceAvailableAndExplainsEditableRestriction,com.example.contentstudio.PresentationPptxUiDeviceTest#rtlEditableModeDisclosesWordGroupsAndCreatesRestorableResult'
    Adb shell wm size 720x1440;Adb shell wm density 360;Adb shell settings put system font_scale 2.0
    RunTests 'large-text-ar' $qaUi 'ar'
    Adb shell wm size 1600x2560;Adb shell wm density 240;Adb shell settings put system font_scale 1.0
    RunTests 'tablet-fa' $qaUi 'fa'
} finally {
    if($qaFont -eq 'null'){Adb shell settings delete system font_scale}else{Adb shell settings put system font_scale $qaFont}
    if($qaSize -match 'Override size: ([0-9]+x[0-9]+)'){Adb shell wm size $Matches[1]}else{Adb shell wm size reset}
    if($qaDensity -match 'Override density: ([0-9]+)'){Adb shell wm density $Matches[1]}else{Adb shell wm density reset}
    Adb pull /sdcard/Android/data/studio.birlevo.android/files/qa/pptx-bidi (Join-Path $qaReports ('rtl-' + [DateTime]::UtcNow.Ticks))
    Adb pull /sdcard/Android/data/studio.birlevo.android/files/qa/pptx-editable (Join-Path $qaReports ('native-' + [DateTime]::UtcNow.Ticks))
    Adb pull /sdcard/Android/data/studio.birlevo.android/files/qa/pptx-export (Join-Path $qaReports ('screens-' + [DateTime]::UtcNow.Ticks))
    Pop-Location
}
