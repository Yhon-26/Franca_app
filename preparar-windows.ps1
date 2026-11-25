$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$target = Join-Path $projectRoot 'gradle\wrapper\gradle-wrapper.jar'
$url = 'https://raw.githubusercontent.com/gradle/gradle/v9.8.2/gradle/wrapper/gradle-wrapper.jar'
$expected = '3b8a25775a69158b5ad2b1d17a80a88dc7b40a352a73eb27d06c612a7ce68e98'
try {
    if (-not (Test-Path $target)) {
        Write-Host 'Descargando wrapper oficial de Gradle 9.8.2...'
        Invoke-WebRequest -Uri $url -OutFile $target -UseBasicParsing
    }
    $actual = (Get-FileHash -Path $target -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($actual -ne $expected) {
        throw "El checksum del wrapper no coincide con Gradle 9.8.2. No se ejecutará ni eliminará el archivo existente."
    }
    Write-Host 'Wrapper verificado. Abre GrafitoFinanzas en Android Studio.'
} catch {
    Write-Error $_
    exit 1
}
