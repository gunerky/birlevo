param([string]$Serial='emulator-5554', [switch]$SkipInstall)
$ErrorActionPreference='Stop'
$qaRoot=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$qaAdb='C:/Users/user/AppData/Local/Android/Sdk/platform-tools/adb.exe'
$qaReports=Join-Path $qaRoot 'output/development/presentation-layout'
$qaEvidence=Join-Path $qaReports ('after-' + [DateTime]::UtcNow.Ticks)
New-Item -ItemType Directory -Force -Path $qaReports | Out-Null
function Adb { & $qaAdb -s $Serial @args; if($LASTEXITCODE -ne 0){throw "ADB failed: $args"} }
if(((Adb emu avd name)|Out-String) -notmatch '(?m)^ofice_qa(?:_api35)?\s*$'){throw 'Dedicated QA emulator required'}
function RunTests([string]$Name,[string]$Classes,[string]$Locale='en-US') {
    $qaLog=Join-Path $qaReports "$Name.log"
    Adb shell am instrument -w -r -e audit-layout $Name -e audit-locale $Locale -e class $Classes studio.birlevo.android.test/androidx.test.runner.AndroidJUnitRunner | Out-File -LiteralPath $qaLog -Encoding utf8
    $qaText=Get-Content -LiteralPath $qaLog -Raw
    if($qaText -notmatch 'OK \((\d+) tests?\)' -or $qaText -match 'FAILURES!!!|INSTRUMENTATION_FAILED') {
        Get-Content -LiteralPath $qaLog -Tail 35; throw "Failed: $Name"
    }
    "$Name : $($Matches[1]) tests passed"
}
$qaFont=((Adb shell settings get system font_scale)|Out-String).Trim()
$qaSize=((Adb shell wm size)|Out-String)
$qaDensity=((Adb shell wm density)|Out-String)
Push-Location $qaRoot
try {
    if(-not $SkipInstall){Adb install -r app/build/outputs/apk/debug/app-debug.apk; Adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk}
    foreach($qaMode in @('phone','large-text','tablet')) {
        if($qaMode -eq 'tablet'){Adb shell wm size 1600x2560; Adb shell wm density 240}
        elseif($qaMode -eq 'large-text'){Adb shell wm size 720x1440; Adb shell wm density 360}
        else {Adb shell wm size reset; Adb shell wm density reset}
        Adb shell settings put system font_scale $(if($qaMode -eq 'large-text'){'2.0'}else{'1.0'})
        RunTests $qaMode 'com.example.contentstudio.PresentationLayoutDeviceTest' $(switch($qaMode){'phone'{'en-US'};'large-text'{'ar'};'tablet'{'fa'}})
    }
    Adb shell wm size reset; Adb shell wm density reset; Adb shell settings put system font_scale 1.0
    RunTests 'regression' 'com.example.contentstudio.PresentationDesignDeviceTest,com.example.contentstudio.PresentationPdfDeviceTest,com.example.contentstudio.PresentationModeDeviceTest,com.example.contentstudio.PresentationPptxDeviceTest,com.example.contentstudio.SlideshowDeviceTest#presentationSnapshotKeepsTextThemeLayoutsAndOwnsCopiedImage,com.example.contentstudio.SlideshowDeviceTest#fullHdPresentationKeepsTheRequestedResolutionAndRejectsTextOverflow'
} finally {
    if($qaFont -eq 'null'){Adb shell settings delete system font_scale}else{Adb shell settings put system font_scale $qaFont}
    if($qaSize -match 'Override size: ([0-9]+x[0-9]+)'){Adb shell wm size $Matches[1]}else{Adb shell wm size reset}
    if($qaDensity -match 'Override density: ([0-9]+)'){Adb shell wm density $Matches[1]}else{Adb shell wm density reset}
    Adb pull /sdcard/Android/data/studio.birlevo.android/files/qa $qaEvidence
    "Evidence: $qaEvidence"
    Pop-Location
}
