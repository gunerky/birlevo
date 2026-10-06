param([ValidateSet('phone','tablet','narrow-large-text','dark','rtl')][string]$Mode='phone')
$ErrorActionPreference='Stop'
$taskAdb='C:/Users/user/AppData/Local/Android/Sdk/platform-tools/adb.exe'
$taskOut=Join-Path (Get-Location) 'output/development/text-documents'
function Adb { & $taskAdb -s emulator-5554 @args; if ($LASTEXITCODE -ne 0) { throw 'QA emulator command failed' } }
# Only the dedicated ofice_qa emulator is eligible for display changes.
if ((Adb emu avd name) -notcontains 'ofice_qa') { throw 'Expected ofice_qa emulator' }
try {
    if ($Mode -eq 'tablet') { Adb shell wm size 1600x2560; Adb shell wm density 240 }
    elseif ($Mode -eq 'narrow-large-text') { Adb shell wm size 720x1440; Adb shell wm density 360 }
    else { Adb shell wm size reset; Adb shell wm density reset }
    Adb shell settings put system font_scale $(if ($Mode -eq 'narrow-large-text') { '2.0' } else { '1.0' })
    Adb shell cmd uimode night $(if ($Mode -eq 'dark') { 'yes' } else { 'no' })
    $taskLocale=if ($Mode -eq 'rtl') { 'ar' } else { 'en-US' }
    $taskLog=Join-Path $taskOut "ui-$Mode.log"
    Adb shell am instrument -w -r -e audit-label $Mode -e audit-locale $taskLocale -e class 'com.example.contentstudio.TextDocumentDeviceTest#nativeLayoutAndEncodingControlsRemainUsableAfterRecreation' studio.birlevo.android.test/androidx.test.runner.AndroidJUnitRunner *> $taskLog
    if ((Get-Content $taskLog -Raw) -notmatch 'OK \(1 test\)') { throw "Document UI test failed: $Mode" }
    Adb pull "/sdcard/Android/data/studio.birlevo.android/files/qa/documents-$Mode.png" (Join-Path $taskOut "documents-$Mode.png")
} finally {
    Adb shell wm size reset
    Adb shell wm density reset
    Adb shell settings put system font_scale 1.0
    Adb shell cmd uimode night no
    Adb shell cmd locale set-app-locales studio.birlevo.android --user 0 --locales en-US
}
