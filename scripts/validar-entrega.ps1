[CmdletBinding()]
param(
    [switch]$Offline,

    [ValidatePattern('^emulator-[0-9]+$')]
    [string]$EmulatorSerial
)

$ErrorActionPreference = 'Stop'
$project = Split-Path -Parent $PSScriptRoot
$appId = 'com.grafito.finanzas'
$buildId = [Guid]::NewGuid().ToString('N').Substring(0, 12)
$apk = Join-Path $project "build/franca-$buildId.pending.apk"

function Assert-CommandSucceeded([string]$Operation) {
    if ($LASTEXITCODE -ne 0) {
        throw "$Operation falló (código $LASTEXITCODE)."
    }
}

Push-Location -LiteralPath $project
try {
    $sdkEntry = Get-Content -LiteralPath 'local.properties' -ErrorAction SilentlyContinue |
        Where-Object { $_ -match '^sdk\.dir\s*=' } | Select-Object -First 1
    $sdk = if ($sdkEntry) {
        [regex]::Replace(($sdkEntry -split '=', 2)[1].Trim(), '\\([:\\ ])', '$1')
    } elseif ($env:ANDROID_HOME) {
        $env:ANDROID_HOME
    } else {
        $env:ANDROID_SDK_ROOT
    }
    if (-not $sdk -or -not (Test-Path -LiteralPath $sdk -PathType Container)) {
        throw 'Configura la ruta del SDK de Android en local.properties o ANDROID_HOME.'
    }
    $buildTools = Get-ChildItem -LiteralPath (Join-Path $sdk 'build-tools') -Directory |
        Where-Object { $_.Name -match '^\d+\.\d+\.\d+$' -and [version]$_.Name -ge [version]'35.0.0' } |
        Sort-Object { [version]$_.Name } -Descending | Select-Object -First 1
    if (-not $buildTools) { throw 'Instala Android SDK Build-Tools 35.0.0 o posterior.' }

    $python = Get-Command python, python3 -CommandType Application -All -ErrorAction SilentlyContinue |
        Where-Object { $_.Source -notlike '*\Microsoft\WindowsApps\*' } | Select-Object -First 1
    if (-not $python) { throw 'Se requiere Python 3 en PATH para comprobar la alineación de las bibliotecas.' }

    $gradleArgs = @('--console=plain', "-Pfranca.buildId=$buildId",
        ':app:testValidationUnitTest', ':app:lintValidation', ':app:assembleDebug')
    if ($Offline) { $gradleArgs += '--offline' }
    if ($EmulatorSerial) { $gradleArgs += ':app:assembleValidation', ':app:assembleValidationAndroidTest' }

    & .\gradlew.bat @gradleArgs
    Assert-CommandSucceeded 'Compilación, pruebas unitarias y Lint'

    Copy-Item -LiteralPath 'app/build/outputs/apk/debug/app-debug.apk' -Destination $apk
    & (Join-Path $buildTools.FullName 'apksigner.bat') verify $apk
    Assert-CommandSucceeded 'Verificación de la firma'
    & (Join-Path $buildTools.FullName 'zipalign.exe') -c -P 16 4 $apk
    Assert-CommandSucceeded 'Alineación ZIP de 16 KB'
    & $python.Source (Join-Path $PSScriptRoot 'verify_apk_16kb.py') $apk
    Assert-CommandSucceeded 'Alineación ELF de 16 KB'

    $metadata = & (Join-Path $buildTools.FullName 'aapt2.exe') dump badging $apk
    Assert-CommandSucceeded 'Lectura de la versión del APK'
    $escapedAppId = [regex]::Escape($appId)
    $versionPattern = "^package: name='$escapedAppId' versionCode='\d+' versionName='(\d+\.\d+\.\d+\+$buildId)'"
    $version = [regex]::Match(($metadata -join "`n"), "(?m)$versionPattern").Groups[1].Value
    if (-not $version) { throw 'El APK no tiene el paquete y el identificador de compilación esperados.' }

    if ($EmulatorSerial) {
        $adb = Join-Path $sdk 'platform-tools/adb.exe'
        $validationApp = "$appId.validation"
        if ((& $adb -s $EmulatorSerial get-state) -ne 'device') { throw "El emulador $EmulatorSerial no está disponible." }
        & $adb -s $EmulatorSerial install --no-streaming -r 'app/build/outputs/apk/validation/app-validation.apk'
        Assert-CommandSucceeded 'Instalación de la variante de validación'
        & $adb -s $EmulatorSerial install --no-streaming -r 'app/build/outputs/apk/androidTest/validation/app-validation-androidTest.apk'
        Assert-CommandSucceeded 'Instalación de las pruebas instrumentadas'

        & $adb -s $EmulatorSerial shell am instrument -w "$validationApp.test/androidx.test.runner.AndroidJUnitRunner" |
            Tee-Object -Variable result
        Assert-CommandSucceeded 'Ejecución de las pruebas instrumentadas'
        if (-not ($result -match '^OK \([1-9]\d* tests?\)$')) { throw 'Fallaron las pruebas instrumentadas.' }
    }

    $deliveryDirectory = Join-Path $project 'build/entregas'
    $null = New-Item -Path $deliveryDirectory -ItemType Directory -Force
    $fileName = "Franca-$version.apk"
    $delivery = Join-Path $deliveryDirectory $fileName
    $hash = (Get-FileHash -LiteralPath $apk -Algorithm SHA256).Hash.ToLowerInvariant()
    Move-Item -LiteralPath $apk -Destination $delivery
    Set-Content -LiteralPath "$delivery.sha256" -Value "$hash  $fileName" -Encoding Ascii
    Write-Output "APK validado: $delivery"
    Write-Output "SHA-256: $hash"
} finally {
    if (Test-Path -LiteralPath $apk) { Remove-Item -LiteralPath $apk }
    Pop-Location
}
