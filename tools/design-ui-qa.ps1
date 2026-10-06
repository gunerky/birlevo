param(
    [ValidateSet('phone','tablet','narrow-large-text','dark')][string]$Mode='phone',
    [string]$Version='0.1.5'
)
$ErrorActionPreference='Stop'
$qaAdb='C:/Users/user/AppData/Local/Android/Sdk/platform-tools/adb.exe'
$folder=Join-Path (Get-Location) "output/development/$Version/qa/$Mode-final"
New-Item -ItemType Directory -Force $folder | Out-Null
function Adb { & $qaAdb -s emulator-5554 @args; if($LASTEXITCODE -ne 0){throw 'Emulator command failed'} }
if($Mode -eq 'tablet'){Adb shell wm size 1600x2560; Adb shell wm density 240; Adb shell settings put system font_scale 1.0}
elseif($Mode -eq 'narrow-large-text'){Adb shell wm size 720x1440; Adb shell wm density 360; Adb shell settings put system font_scale 2.0}
else {Adb shell wm size reset; Adb shell wm density reset; Adb shell settings put system font_scale 1.0}
Adb shell cmd uimode night $(if($Mode -eq 'dark'){'yes'}else{'no'})
Adb install -r app/build/outputs/apk/debug/app-debug.apk
Adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
$classes='com.example.contentstudio.StudioDeviceTest#homeAdaptsToAvailableWidthAndActionsRemainReachable'
if($Mode -ne 'dark'){$classes+=',com.example.contentstudio.PresentationDesignDeviceTest#designChoicesAutosaveSurviveRecreationAndReachPdf'}
Adb shell am instrument -w -r -e class $classes studio.birlevo.android.test/androidx.test.runner.AndroidJUnitRunner *> (Join-Path $folder 'instrumentation.log')
if((Get-Content (Join-Path $folder 'instrumentation.log') -Raw) -notmatch 'OK \(\d+ tests?\)'){throw "UI checks failed for $Mode"}
Adb pull /sdcard/Android/data/studio.birlevo.android/files/qa $folder
Write-Output "UI checks passed: $Mode"
