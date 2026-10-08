param(
    [ValidateSet('navigation','media','documents','design','recovery','layouts','accessibility')][string]$Suite = 'navigation',
    [string]$Serial = 'emulator-5554', [switch]$SkipInstall
)
$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$qaSdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { Join-Path $env:USERPROFILE 'AppData/Local/Android/Sdk' }
$qaAdb = Join-Path $qaSdk 'platform-tools/adb.exe'
$reportRoot = Join-Path $projectRoot 'output/development/workspace'
New-Item -ItemType Directory -Force -Path $reportRoot | Out-Null
function Adb { & $qaAdb -s $Serial @args; if ($LASTEXITCODE -ne 0) { throw "ADB failed: $args" } }
if (((Adb emu avd name) | Out-String) -notmatch '(?m)^ofice_qa(?:_api35)?\s*$') { throw 'Only dedicated QA emulators are allowed.' }
function TestSuite([string]$Classes, [string]$Name, [string]$Locale = 'en-US') {
    $log = Join-Path $reportRoot "$Name.log"
    Adb shell am instrument -w -r -e audit-layout $Name -e audit-locale $Locale -e audit-talkback $(if ($Suite -eq 'accessibility') { 'true' } else { 'false' }) -e class $Classes studio.birlevo.android.test/androidx.test.runner.AndroidJUnitRunner |
        Out-File $log -Encoding utf8
    $result = Get-Content -LiteralPath $log -Raw
    if ($result -notmatch 'OK \((\d+) tests?\)') { Get-Content -LiteralPath $log -Tail 30; throw "Device tests failed: $Name" }
    Write-Output "$Name : $($Matches[1]) tests passed"
}
Push-Location $projectRoot
try {
    if (-not $SkipInstall) {
        Adb install -r app/build/outputs/apk/debug/app-debug.apk
        Adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
    }
    if ($Suite -eq 'accessibility') {
        $previousServices = ((Adb shell settings get secure enabled_accessibility_services) | Out-String).Trim()
        $previousAccessibility = ((Adb shell settings get secure accessibility_enabled) | Out-String).Trim()
        $talkBackService = 'com.google.android.marvin.talkback/com.google.android.marvin.talkback.TalkBackService'
        $services = if ($previousServices -in @('null','')) { $talkBackService } else { ((@($previousServices -split ':') + @($talkBackService)) | Select-Object -Unique) -join ':' }
        Adb shell settings put secure enabled_accessibility_services $services
        Adb shell settings put secure accessibility_enabled 1
        TestSuite 'com.example.contentstudio.WorkspaceUiDeviceTest#talkBackFocusAndNativeAccessibilityActionsReachFileConversion' 'talkback'
    } elseif ($Suite -eq 'layouts') {
        foreach ($mode in @('phone','tablet','large-text','dark','rtl','tablet-large-text')) {
            if ($mode -in @('tablet','tablet-large-text')) { Adb shell wm size 1600x2560; Adb shell wm density 240 }
            elseif ($mode -eq 'large-text') { Adb shell wm size 720x1440; Adb shell wm density 360 }
            else { Adb shell wm size reset; Adb shell wm density reset }
            Adb shell settings put system font_scale $(if ($mode -in @('large-text','tablet-large-text')) { '2.0' } else { '1.0' })
            Adb shell cmd uimode night $(if ($mode -eq 'dark') { 'yes' } else { 'no' })
            TestSuite 'com.example.contentstudio.WorkspaceUiDeviceTest#adaptiveHomeAndWorkspaceKeepActionsVisibleAndRespectFontScale' $mode $(if ($mode -eq 'rtl') { 'ar' } else { 'en-US' })
        }
        TestSuite 'com.example.contentstudio.StudioDeviceTest#everyLanguageAppliesAndRtlDirectionsMatch' 'languages'
    } else {
        $classes = switch ($Suite) {
            navigation { 'UiAuditDeviceTest',
                'WorkspaceUiDeviceTest#generalPickerOffersOnlyOperationsForItsFileAndRestoresSelection',
                'WorkspaceUiDeviceTest#imageOutputInspectorBackAndCopyGuardsKeepTheSourceAndInvalidateOldResult',
                'WorkspaceUiDeviceTest#presentationEditsOneSelectedSlideAndSavesWhenReturningToTheFile',
                'WorkspaceUiDeviceTest#firstPickedMediaActionWaitsForSessionAndOpensWithOneTap',
                'WorkspaceUiDeviceTest#adaptiveHomeAndWorkspaceKeepActionsVisibleAndRespectFontScale' }
            media { 'VideoEditUiDeviceTest','VideoTextUiDeviceTest','VideoImageUiDeviceTest','VideoAudioUiDeviceTest','VideoFrameUiDeviceTest','AudioEditUiDeviceTest','ImageCompressionDeviceTest' }
            documents { 'PdfReaderDeviceTest','PdfPagesDeviceTest','PhotoPdfDeviceTest','TextDocumentDeviceTest' }
            design { 'StudioDeviceTest','PresentationDesignDeviceTest','PresentationPdfDeviceTest','OutputTransferDeviceTest','SystemBarsDeviceTest' }
            recovery { 'BackgroundProcessingDeviceTest','MediaSessionDeviceTest' }
        }
        TestSuite (($classes | ForEach-Object { "com.example.contentstudio.$_" }) -join ',') $Suite
    }
} finally {
    if ($Suite -eq 'layouts') {
        Adb shell wm size reset
        Adb shell wm density reset
        Adb shell settings put system font_scale 1.0
        Adb shell cmd uimode night no
    }
    if ($Suite -eq 'accessibility' -and $null -ne $previousServices) {
        if ($previousServices -eq 'null') { Adb shell settings delete secure enabled_accessibility_services }
        else { Adb shell settings put secure enabled_accessibility_services $previousServices }
        if ($previousAccessibility -eq 'null') { Adb shell settings delete secure accessibility_enabled }
        else { Adb shell settings put secure accessibility_enabled $previousAccessibility }
    }
    Adb pull /sdcard/Android/data/studio.birlevo.android/files/qa (Join-Path $reportRoot ('screens-' + $Suite + '-' + [DateTime]::UtcNow.Ticks))
    Pop-Location
}
