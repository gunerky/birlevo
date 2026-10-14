param([ValidateSet('Phone','LargeText','DarkTablet')][string]$Profile='Phone',
    [string]$Serial='emulator-5554',[switch]$SkipInstall)
$ErrorActionPreference='Stop'
$qaRoot=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$qaAdb='C:/Users/user/AppData/Local/Android/Sdk/platform-tools/adb.exe'
$qaReports=Join-Path $qaRoot 'output/development/office-integration'
New-Item -ItemType Directory -Force -Path $qaReports | Out-Null
function Adb { & $qaAdb -s $Serial @args; if($LASTEXITCODE -ne 0){throw "ADB failed: $args"} }
if(((Adb emu avd name)|Out-String) -notmatch '(?m)^ofice_qa\s*$'){throw 'Dedicated ofice_qa required'}
$qaFont=((Adb shell settings get system font_scale)|Out-String).Trim()
$qaSize=((Adb shell wm size)|Out-String)
$qaDensity=((Adb shell wm density)|Out-String)
$qaNight=((Adb shell cmd uimode night)|Out-String)
Push-Location $qaRoot
try {
    if(-not $SkipInstall){Adb install -r app/build/outputs/apk/debug/app-debug.apk;Adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk}
    Adb shell am force-stop studio.birlevo.android
    Adb shell wm size reset; Adb shell wm density reset
    Adb shell settings put system font_scale 1.0; Adb shell cmd uimode night no
    $qaClass='com.example.contentstudio.OfficeDocumentDeviceTest'
    if($Profile -eq 'LargeText') {
        Adb shell settings put system font_scale 2.0
        $qaMethods=@('arabicWordUiHasTranslatedConsentAndAccessibleActions',
            'workbookUiRequiresConsentInvalidatesOutputAndPreservesPickerSource',
            'persianWorkbookInspectorAndPlainPreviewWorkAtConfiguredWindowSize')
        $qaClass=($qaMethods | ForEach-Object {"com.example.contentstudio.OfficeDocumentDeviceTest#$_"}) -join ','
    } elseif($Profile -eq 'DarkTablet') {
        Adb shell wm size 2560x1600; Adb shell wm density 240
        Adb shell cmd uimode night yes
        $qaClass='com.example.contentstudio.OfficeDocumentDeviceTest#persianWorkbookInspectorAndPlainPreviewWorkAtConfiguredWindowSize'
    }
    $qaLog=Join-Path $qaReports "$Profile.log"
    Adb shell am instrument -w -r -e class $qaClass studio.birlevo.android.test/androidx.test.runner.AndroidJUnitRunner | Out-File -LiteralPath $qaLog -Encoding utf8
    $qaResult=Get-Content -LiteralPath $qaLog -Raw
    if($qaResult -notmatch 'OK \((\d+) tests?\)' -or $qaResult -match 'FAILURES!!!|INSTRUMENTATION_FAILED'){
        Get-Content -LiteralPath $qaLog -Tail 45;throw "Office $Profile QA failed"
    }
    "$Profile : $($Matches[1]) emulator tests passed"
    $qaScreens=Join-Path $qaReports "screens-$Profile"
    New-Item -ItemType Directory -Force -Path $qaScreens | Out-Null
    # Pull named files directly: no directory nesting or accidental reuse of stale root images.
    $qaNames=if($Profile -eq 'DarkTablet'){@('workbook-persian-inspector','workbook-persian-preview')}
        else{@('workbook-inspector','workbook-preview','word-arabic','workbook-persian-inspector','workbook-persian-preview')}
    foreach($qaName in $qaNames){Adb pull "/sdcard/Android/data/studio.birlevo.android/files/qa/office-integration/$qaName.png" (Join-Path $qaScreens "$qaName.png")}
} finally {
    if($qaFont -eq 'null'){Adb shell settings delete system font_scale}else{Adb shell settings put system font_scale $qaFont}
    if($qaSize -match 'Override size:\s*(\d+x\d+)'){Adb shell wm size $Matches[1]}else{Adb shell wm size reset}
    if($qaDensity -match 'Override density:\s*(\d+)'){Adb shell wm density $Matches[1]}else{Adb shell wm density reset}
    if($qaNight -match 'Night mode:\s*(\w+)'){Adb shell cmd uimode night $Matches[1]}
    Pop-Location
}
