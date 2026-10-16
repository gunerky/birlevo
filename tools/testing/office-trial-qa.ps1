param([string]$Serial='emulator-5554',[switch]$SkipInstall,[switch]$Roundtrip)
$ErrorActionPreference='Stop'
$qaRoot=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$qaAdb='C:/Users/user/AppData/Local/Android/Sdk/platform-tools/adb.exe'
$qaReports=Join-Path $qaRoot 'output/development/office-trial'
New-Item -ItemType Directory -Force -Path $qaReports | Out-Null
function Adb { & $qaAdb -s $Serial @args; if($LASTEXITCODE -ne 0){throw "ADB failed: $args"} }
if(((Adb emu avd name)|Out-String) -notmatch '(?m)^ofice_qa(?:_api35)?\s*$'){throw 'Dedicated QA required'}
Push-Location $qaRoot
try {
    if($Roundtrip) {
        $qaEdited=Join-Path $qaReports 'independent'
        Adb push (Join-Path $qaEdited 'edited.docx') /sdcard/Android/data/studio.birlevo.android/files/qa/office-trial/edited.docx
        Adb push (Join-Path $qaEdited 'edited.xlsx') /sdcard/Android/data/studio.birlevo.android/files/qa/office-trial/edited.xlsx
        $qaClass='com.example.contentstudio.OfficeTrialDeviceTest#hostEditedOutputsCanBeReopenedByTheAndroidProbe'
        $qaLog=Join-Path $qaReports 'roundtrip.log'
        Adb shell am instrument -w -r -e office-roundtrip true -e class $qaClass studio.birlevo.android.test/androidx.test.runner.AndroidJUnitRunner | Out-File -LiteralPath $qaLog -Encoding utf8
    } else {
        if(-not $SkipInstall){Adb install -r app/build/outputs/apk/debug/app-debug.apk;Adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk}
        $qaMethods=@('wordParagraphTableUnicodeAndLossesSurviveWithoutOpeningExternalContent',
            'workbookSheetsSparseCellsAndFormulaCachesAreDistinctFromCalculation',
            'outputsRoundtripLocallyAndTextCannotBecomeExecutableFormula',
            'missingFormulaCacheIsNotInventedAndSharedStringsPreserveLeadingZeros',
            'xmlDtdDepthAndPackageTraversalAreRejected',
            'inflatedPayloadDuplicateEntriesMacrosAndEscapingRelationshipsAreRejected',
            'cancellationAndInvalidOutputNeverLeavePartialFilesOrTouchSources',
            'applicationRoutesOfficeFilesToTheValidatedEngine')
        $qaClass=(($qaMethods | ForEach-Object {"com.example.contentstudio.OfficeTrialDeviceTest#$_"}) +
            'com.example.contentstudio.TextDocumentDeviceTest') -join ','
        $qaLog=Join-Path $qaReports 'feature.log'
        Adb shell am instrument -w -r -e class $qaClass studio.birlevo.android.test/androidx.test.runner.AndroidJUnitRunner | Out-File -LiteralPath $qaLog -Encoding utf8
    }
    $qaResult=Get-Content -LiteralPath $qaLog -Raw
    if($qaResult -notmatch 'OK \((\d+) tests?\)' -or $qaResult -match 'FAILURES!!!|INSTRUMENTATION_FAILED'){
        Get-Content -LiteralPath $qaLog -Tail 45;throw 'Office probe QA failed'
    }
    "$($Matches[1]) emulator tests passed"
    if(-not $Roundtrip){
        $qaNative=Join-Path $qaReports 'native'
        New-Item -ItemType Directory -Force -Path $qaNative | Out-Null
        foreach($qaName in @('native.docx','native.xlsx','word-report.json','sheet-report.json')){
            Adb pull "/sdcard/Android/data/studio.birlevo.android/files/qa/office-trial/$qaName" (Join-Path $qaNative $qaName)
        }
    }
} finally {Pop-Location}
