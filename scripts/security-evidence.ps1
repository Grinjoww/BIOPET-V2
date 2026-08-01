<#
.SYNOPSIS
    Prepara el entorno (build, keystore, validacion de compose, arranque del
    stack) para la evidencia de seguridad OWASP de BIOPET en Windows.

.DESCRIPTION
    IMPORTANTE: el script CANONICO y completo de evidencia OWASP es
    scripts/security-evidence.sh, que ademas de lo que hace este .ps1
    ejecuta la secuencia HTTP real de los seis controles (A01, A02, A03,
    A05, A07, A09) contra el stack ya levantado, con cuentas academicas
    temporales, sanitizacion de cookies/JWT, y evidencia cruda por control
    en docs/mediciones/sec/raw/. Esa logica no se duplica aqui en
    PowerShell: mantener dos implementaciones completas de ~20 peticiones
    HTTP encadenadas (cookies, JTI, rate limiting) en dos lenguajes distintos
    es en si mismo un riesgo de que ambas dejen de coincidir con el tiempo.
    Como este mismo entorno Windows ya tiene Git Bash (requerido tambien por
    scripts/validate-traceability.sh), la forma soportada de obtener la
    evidencia completa en Windows es:

        bash scripts/security-evidence.sh

    Este script .ps1 sigue siendo util por si solo para preparar el entorno
    sin salir de PowerShell: comprobacion de herramientas requeridas, "mvn
    clean verify" (pruebas + cobertura JaCoCo), generacion del keystore
    local si falta (reutilizando scripts/generate-dev-keystore.ps1),
    validacion de "docker compose config", levantamiento del stack Compose
    base + TLS, y una comprobacion basica de cabeceras HTTP 8080 / HTTPS
    8443 (solo cabeceras y codigo de estado, nunca cuerpos de login, cookies
    Set-Cookie completas ni JWT) en docs/mediciones/sec/raw/ (carpeta NO
    excluida por git salvo el propio .gitkeep: los archivos que aqui se
    generan son evidencia real).

    Este script NO ejecuta ningún comando de git de escritura (add/commit/push).
    No imprime ni guarda contraseñas ni tokens en ningún momento.

.PARAMETER StopContainers
    Si se especifica, apaga los contenedores del stack Docker al finalizar.
    Por defecto los contenedores quedan activos.

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File scripts/security-evidence.ps1

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File scripts/security-evidence.ps1 -StopContainers

.EXAMPLE
    # Evidencia completa de los seis controles OWASP (recomendado):
    bash scripts/security-evidence.sh
#>
[CmdletBinding()]
param(
    [switch]$StopContainers
)

$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = Split-Path -Parent $scriptDir
$rawDir = Join-Path $repoRoot "docs\mediciones\sec\raw"

if (-not (Test-Path $rawDir)) {
    New-Item -ItemType Directory -Path $rawDir -Force | Out-Null
}

function Write-Section {
    param([string]$Title)
    Write-Host ""
    Write-Host "=== $Title ===" -ForegroundColor Cyan
}

function Save-HeadersOnly {
    # Guarda solo cabeceras + codigo de estado HTTP de una URL. Nunca guarda
    # el cuerpo de la respuesta ni una cabecera Set-Cookie completa.
    param(
        [string]$Url,
        [string]$OutFile,
        [switch]$Insecure
    )
    $tmpBody = [System.IO.Path]::GetTempFileName()
    try {
        if ($Insecure) {
            curl.exe -sSk -D $OutFile -o $tmpBody -w "HTTP_STATUS=%{http_code}`n" $Url | Out-Null
        } else {
            curl.exe -sS -D $OutFile -o $tmpBody -w "HTTP_STATUS=%{http_code}`n" $Url | Out-Null
        }
    } finally {
        Remove-Item -Path $tmpBody -Force -ErrorAction SilentlyContinue
    }

    if (Test-Path $OutFile) {
        # Redaccion defensiva: si alguna respuesta llegara a incluir Set-Cookie,
        # nunca se guarda el valor completo de la cookie.
        $lineas = Get-Content $OutFile | ForEach-Object {
            if ($_ -match '^(?i)set-cookie:') { "Set-Cookie: [REDACTADO - no se guarda el valor]" }
            else { $_ }
        }
        Set-Content -Path $OutFile -Value $lineas
    }
}

Write-Section "1. Comprobando herramientas requeridas"
$herramientas = @("java", "mvn", "docker", "curl", "keytool")
$faltantes = @()
foreach ($h in $herramientas) {
    $cmd = Get-Command $h -ErrorAction SilentlyContinue
    if ($cmd) {
        Write-Host "OK: $h encontrado"
    } else {
        Write-Host "FALTA: $h no esta en el PATH"
        $faltantes += $h
    }
}
if ($faltantes.Count -gt 0) {
    Write-Error "Faltan herramientas requeridas: $($faltantes -join ', '). Instalalas y vuelve a intentarlo."
    exit 1
}

Write-Section "2. Ejecutando mvn clean verify (pruebas + cobertura JaCoCo)"
Push-Location (Join-Path $repoRoot "Backend")
try {
    mvn clean verify 2>&1 | Tee-Object -FilePath (Join-Path $rawDir "mvn-clean-verify.txt")
    if ($LASTEXITCODE -ne 0) {
        Write-Error "mvn clean verify fallo (codigo $LASTEXITCODE). Revisa docs/mediciones/sec/raw/mvn-clean-verify.txt"
        exit $LASTEXITCODE
    }
} finally {
    Pop-Location
}

Write-Section "3. Generando keystore local (solo si no existe; no se sobrescribe)"
& powershell -ExecutionPolicy Bypass -File (Join-Path $scriptDir "generate-dev-keystore.ps1")

Push-Location $repoRoot
try {
    $envFile = Join-Path $repoRoot ".env"
    if (-not (Test-Path $envFile)) {
        Copy-Item (Join-Path $repoRoot ".env.example") $envFile
        Write-Host "Se creo .env localmente a partir de .env.example (archivo ignorado por git, sin secretos reales)."
    }

    Write-Section "4. Validando docker compose config"
    docker compose -f docker-compose.yml -f docker-compose.tls.yml config *> (Join-Path $rawDir "docker-compose-config.txt")
    if ($LASTEXITCODE -ne 0) {
        Write-Error "docker compose config fallo. Revisa docs/mediciones/sec/raw/docker-compose-config.txt"
        exit $LASTEXITCODE
    }
    Write-Host "Configuracion combinada valida (guardada, sin valores de TLS_KEYSTORE_PASSWORD reales mas alla del valor por defecto documentado)."

    Write-Section "5. Levantando el stack Compose base + TLS"
    docker compose -f docker-compose.yml -f docker-compose.tls.yml up --build -d

    Write-Section "6. Esperando a que el backend este 'healthy'"
    $maxIntentos = 30
    $intento = 0
    $healthy = $false
    while ($intento -lt $maxIntentos) {
        $estado = (docker inspect biopet-backend --format '{{.State.Health.Status}}' 2>$null)
        if ($estado -eq "healthy") {
            $healthy = $true
            break
        }
        Start-Sleep -Seconds 2
        $intento++
    }
    if (-not $healthy) {
        Write-Warning "El backend no alcanzo estado 'healthy' en el tiempo esperado. Verifica manualmente: docker compose -f docker-compose.yml -f docker-compose.tls.yml ps"
    } else {
        Write-Host "Backend healthy."
    }

    Write-Section "7. Consultando HTTP 8080 (solo cabeceras y estado)"
    Save-HeadersOnly -Url "http://localhost:8080/actuator/health" -OutFile (Join-Path $rawDir "http-8080-headers.txt")

    Write-Section "8. Consultando HTTPS 8443 (solo cabeceras y estado)"
    Save-HeadersOnly -Url "https://localhost:8443/actuator/health" -OutFile (Join-Path $rawDir "https-8443-headers.txt") -Insecure

    docker compose -f docker-compose.yml -f docker-compose.tls.yml ps *> (Join-Path $rawDir "docker-compose-ps.txt")

    Write-Host "Cabeceras y estados guardados en docs/mediciones/sec/raw/ (sin cuerpos de respuesta, sin cookies completas, sin JWT)."
} finally {
    Pop-Location
}

Write-Section "Evidencia completa de los seis controles OWASP"
Write-Host "Este script .ps1 solo prepara el entorno y cabeceras basicas. Para A01, A02,"
Write-Host "A03, A05, A07 y A09 con peticiones HTTP reales (login, rate limiting, IDOR,"
Write-Host "inyeccion, blacklist Redis, logs de auditoria), ejecuta el script canonico:"
Write-Host ""
Write-Host "    bash scripts/security-evidence.sh"
Write-Host ""
Write-Host "- Protocolo TLS y cipher exactos: 'openssl s_client -connect localhost:8443 -tls1_3'."
Write-Host "- SAN del certificado: 'openssl s_client -connect localhost:8443 -tls1_3 2>NUL | openssl x509 -noout -text'."
Write-Host "- Estado visual detallado de los contenedores: 'docker compose -f docker-compose.yml -f docker-compose.tls.yml ps' (ya guardado en docs/mediciones/sec/raw/docker-compose-ps.txt, pero conviene revisarlo visualmente)."

if ($StopContainers) {
    Write-Section "Apagando contenedores (solicitado con -StopContainers)"
    Push-Location $repoRoot
    docker compose -f docker-compose.yml -f docker-compose.tls.yml down
    Pop-Location
} else {
    Write-Host ""
    Write-Host "Los contenedores siguen activos (por defecto no se apagan)."
    Write-Host "Para apagarlos: powershell -ExecutionPolicy Bypass -File scripts/security-evidence.ps1 -StopContainers"
    Write-Host "o manualmente:  docker compose -f docker-compose.yml -f docker-compose.tls.yml down"
}

Write-Host ""
Write-Host "Evidencia generada en: docs/mediciones/sec/raw/"
Write-Host "Este script no ejecuta git add, git commit ni git push."
