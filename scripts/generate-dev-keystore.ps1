<#
.SYNOPSIS
    Genera el keystore PKCS12 de desarrollo/academico para HTTPS local de BIOPET.

.DESCRIPTION
    Crea Backend/certs/biopet-dev.p12 con un certificado autofirmado (SAN:
    DNS:localhost, IP:127.0.0.1). Es idempotente: si el keystore ya existe,
    no lo sobrescribe salvo que se use -Force.

    Este certificado es exclusivamente para entorno local/academico.
    NUNCA debe usarse en produccion.

.PARAMETER Alias
    Alias de la clave dentro del keystore. Por defecto "biopet-local".

.PARAMETER Force
    Sobrescribe el keystore si ya existe.

.EXAMPLE
    ./scripts/generate-dev-keystore.ps1

.EXAMPLE
    ./scripts/generate-dev-keystore.ps1 -Force
#>
[CmdletBinding()]
param(
    [string]$Alias = "biopet-local",
    [switch]$Force
)

$ErrorActionPreference = "Stop"

$password = $env:TLS_KEYSTORE_PASSWORD
if ([string]::IsNullOrEmpty($password)) {
    $password = "changeit"
}

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = Split-Path -Parent $scriptDir
$certsDir = Join-Path $repoRoot "Backend\certs"
$keystorePath = Join-Path $certsDir "biopet-dev.p12"

$keytoolCmd = Get-Command keytool -ErrorAction SilentlyContinue
if (-not $keytoolCmd) {
    Write-Error "keytool no esta disponible en el PATH. Instala un JDK 21 (incluye keytool) y vuelve a intentarlo."
    exit 1
}

if (-not (Test-Path $certsDir)) {
    New-Item -ItemType Directory -Path $certsDir -Force | Out-Null
}

if ((Test-Path $keystorePath) -and (-not $Force)) {
    Write-Host "El keystore ya existe: $keystorePath"
    Write-Host "No se modifico nada. Usa -Force para regenerarlo."
    exit 0
}

if (Test-Path $keystorePath) {
    Remove-Item $keystorePath -Force
}

& keytool -genkeypair `
    -alias $Alias `
    -keyalg RSA `
    -keysize 2048 `
    -validity 3650 `
    -storetype PKCS12 `
    -keystore $keystorePath `
    -storepass $password `
    -keypass $password `
    -dname "CN=localhost, OU=BIOPET Dev, O=BIOPET, L=Local, ST=Local, C=EC" `
    -ext "SAN=dns:localhost,ip:127.0.0.1"

if ($LASTEXITCODE -ne 0) {
    Write-Error "keytool fallo al generar el keystore (codigo $LASTEXITCODE)."
    exit $LASTEXITCODE
}

Write-Host ""
Write-Host "Keystore de desarrollo creado: $keystorePath"
Write-Host "Alias: $Alias"
Write-Host "Certificado autofirmado, solo para uso local/academico. No usar en produccion."
Write-Host ""
Write-Host "Siguiente paso:"
Write-Host "  docker compose -f docker-compose.yml -f docker-compose.tls.yml up --build"
