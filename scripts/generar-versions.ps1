<#
.SYNOPSIS
    Regenera docs/entorno/versions.txt con las versiones exactas del entorno
    de desarrollo BIOPET (F16). No escribe valores sensibles (credenciales,
    tokens, claves).

.DESCRIPTION
    Captura en vivo las versiones de: Docker, Docker Compose, JDK 21 (Temurin),
    Node, npm, k6, Python/numpy/scipy y las versiones exactas de Angular del
    package-lock.json. Los SO de los contenedores se capturan con docker run
    (requiere Docker arriba; si no, se marcan como "no capturado").

    La fecha se escribe en ISO 8601 local. El archivo resultante no contiene
    credenciales, contrasenas ni rutas de usuario.

.PARAMETER OutFile
    Ruta del archivo de salida. Default: docs/entorno/versions.txt

.EXAMPLE
    ./scripts/generar-versions.ps1
.EXAMPLE
    ./scripts/generar-versions.ps1 -OutFile docs/entorno/versions.txt
#>
[CmdletBinding()]
param(
    [string]$OutFile = "docs/entorno/versions.txt"
)

$ErrorActionPreference = "Stop"

$fecha = Get-Date -Format "yyyy-MM-ddTHH:mm:sszzz"
$salida = New-Object System.Collections.Generic.List[string]

$salida.Add("# Versiones exactas del entorno de desarrollo - BIOPET (F16)")
$salida.Add("#")
$salida.Add("# Generado con scripts/generar-versions.ps1 - NO editar a mano.")
$salida.Add("# Revisar commit asociado en el historial del repo.")
$salida.Add("")
$salida.Add("Fecha (ISO 8601): $fecha")
$salida.Add("Commit:            $(git rev-parse --short HEAD 2>$null)")
$salida.Add("")
$salida.Add("## Host (Windows)")
$salida.Add("- SO:               Windows 10/11 (PowerShell 5.1)")
$salida.Add("")
$salida.Add("## Docker / contenedores")
$salida.Add("- Docker:           $((docker --version) -replace 'Docker version ', '')")
$salida.Add("- Docker Compose:   $((docker compose version) -replace 'Docker Compose version ', '')")
$salida.Add("")

# SO de las imagenes runtime (si Docker esta arriba; si no, se marca)
$imagenes = @(
    @{ nombre = "postgres:16-alpine";              etapa = "postgres runtime" },
    @{ nombre = "redis:7-alpine";                  etapa = "redis runtime" },
    @{ nombre = "eclipse-temurin:21-jre-alpine";   etapa = "backend runtime" },
    @{ nombre = "nginx:1.25-alpine";               etapa = "frontend runtime" },
    @{ nombre = "maven:3.9-eclipse-temurin-21";    etapa = "backend build" },
    @{ nombre = "node:20-alpine";                  etapa = "frontend build" }
)

$salida.Add("## SO de los contenedores (imagenes, /etc/os-release)")
foreach ($img in $imagenes) {
    $os = docker run --rm --entrypoint sh $img.nombre -c "grep PRETTY_NAME /etc/os-release" 2>$null
    if ($LASTEXITCODE -eq 0 -and $os) {
        $pretty = $os -replace 'PRETTY_NAME="([^"]+)".*', '$1'
        $salida.Add("- $($img.nombre) ($($img.etapa)) -> $pretty")
    } else {
        $salida.Add("- $($img.nombre) ($($img.etapa)) -> no capturado (Docker apagado o imagen ausente)")
    }
}
$salida.Add("")

# JDK local: el proyecto requiere JDK 21 (Temurin/Adoptium). Se usa JAVA_HOME
# si apunta a un JDK 21; si no, se busca el JDK 21 instalado en Adoptium.
# Nota: java -version escribe a stderr; con ErrorActionPreference=Stop y 2>&1
# el record de error nativo detendria el script en PS 5.1, por eso se captura
# con try/catch y redireccion a archivo temporal.
$javaHome = $null
function Get-JavaVersionLine([string]$javahome) {
    # cmd.exe redirige el stderr de java sin pasarlo por PS 5.1 (evita
    # NativeCommandError terminante con ErrorActionPreference=Stop)
    $tmp = Join-Path $env:TEMP "biopet-java-version.txt"
    cmd /c "`"$javahome\bin\java.exe`" -version 2>`"$tmp`"" | Out-Null
    $primera = (Get-Content $tmp -ErrorAction SilentlyContinue | Select-Object -First 1)
    Remove-Item $tmp -Force -ErrorAction SilentlyContinue
    return $primera
}
if ($env:JAVA_HOME -and (Test-Path "$env:JAVA_HOME\bin\java.exe")) {
    $jv = Get-JavaVersionLine $env:JAVA_HOME
    if ($jv -match 'version "21\.') {
        $javaHome = $env:JAVA_HOME
    }
}
if (-not $javaHome) {
    $jdk21 = Get-ChildItem "C:\Program Files\Eclipse Adoptium" -Directory -Filter "jdk-21*" `
        -ErrorAction SilentlyContinue | Sort-Object Name -Descending | Select-Object -First 1
    if ($jdk21) {
        $javaHome = $jdk21.FullName
    }
}
$javaVersion = "no capturado"
if ($javaHome -and (Test-Path "$javaHome\bin\java.exe")) {
    $javaVersion = (Get-JavaVersionLine $javaHome) -replace 'openjdk version "([^"]+)".*', '$1'
}
$salida.Add("## JDK (local, Eclipse Adoptium Temurin 21 LTS)")
$salida.Add("- JAVA_HOME:        $javaHome")
$salida.Add("- java:             openjdk version $javaVersion")
$salida.Add("")

# Node / npm / Angular (lockfile; se lee con python porque PS 5.1 no soporta
# claves con '@' en ConvertFrom-Json)
$lockPath = "frontend/package-lock.json"
$angCli = $angCore = $ts = "no capturado"
if (Test-Path $lockPath) {
    $tmpPy = Join-Path $env:TEMP "biopet-versions-lock.py"
    @'
import json
with open("frontend/package-lock.json", encoding="utf-8") as f:
    lock = json.load(f)
p = lock.get("packages", {})
print(p.get("node_modules/@angular/cli", {}).get("version", ""))
print(p.get("node_modules/@angular/core", {}).get("version", ""))
print(p.get("node_modules/typescript", {}).get("version", ""))
'@ | Set-Content -Path $tmpPy -Encoding UTF8
    $vers = python $tmpPy 2>$null
    if ($vers -and $vers.Count -eq 3) {
        $angCli = $vers[0]
        $angCore = $vers[1]
        $ts = $vers[2]
    }
    Remove-Item $tmpPy -Force -ErrorAction SilentlyContinue
}
$salida.Add("## Frontend (local y contenedor de build)")
$salida.Add("- Node (local):     $(node --version 2>$null)")
$salida.Add("- npm (local):      $(npm --version 2>$null)")
$salida.Add("- Angular CLI:      $angCli (package-lock.json)")
$salida.Add("- Angular core:     $angCore (package-lock.json)")
$salida.Add("- TypeScript:       $ts (package-lock.json)")
$salida.Add("")

# k6 y Python
$salida.Add("## Pruebas de rendimiento (k6)")
$salida.Add("- k6:               $(k6 version 2>$null)")
$salida.Add("")
$salida.Add("## Analisis estadistico (Python, scripts/perf-analysis.py)")
$salida.Add("- Python:           $(python --version 2>$null)")
$salida.Add("- numpy:            $((python -c 'import numpy; print(numpy.__version__)' 2>$null))")
$salida.Add("- scipy:            $((python -c 'import scipy; print(scipy.__version__)' 2>$null))")
$salida.Add("")
$salida.Add("## Base de datos / cache (imagenes docker-compose.yml)")
$salida.Add("- postgres:16-alpine           (Alpine Linux v3.24)")
$salida.Add("- redis:7-alpine               (Alpine Linux v3.21)")

$salida | Set-Content -Path $OutFile -Encoding UTF8
Write-Host "Archivo generado: $OutFile"
Write-Host "(sin valores sensibles: credenciales, tokens o claves no se capturan)"