# Makefile — BIOPET (Entrega Final v1.0.0)
# Ubicacion: raiz del repositorio, junto a docker-compose.yml
#
# Referencia de reproducibilidad: GNU Make + Bash sobre Linux (mismo
# entorno que .github/workflows/ci.yml, runners ubuntu-latest). En Windows
# se ejecuta desde Git Bash/WSL con "make" disponible; no se usa ningun
# comando exclusivo de PowerShell/cmd.exe dentro de este archivo.
#
# "make all" reutiliza exactamente los mismos scripts/gates que CI (no
# duplica logica): scripts/validate-traceability.sh,
# scripts/audit-sql-dynamic.sh, scripts/check-spotbugs-sql-findings.sh,
# scripts/run-zap-baseline.sh, scripts/check-zap-high-severity.sh, y
# "mvn clean verify" (JaCoCo LINE/BRANCH >= 70% configurado como gate real
# en Backend/pom.xml, jacoco-maven-plugin, execution "check").
#
# Fail-fast: cada target de este Makefile termina con codigo distinto de
# cero si su validacion falla (ningun "|| true" oculta un fallo
# obligatorio). "make all" (y "make zap") expresan el orden como
# prerequisitos normales de Make (NO como llamadas recursivas via
# "$(MAKE)"): GNU Make ya aborta el build completo en el primer
# prerequisito que falle, sin construir los siguientes. Se usa
# ".NOTPARALLEL" (soportado desde GNU Make 3.81, la version de GnuWin32)
# para que el orden listado se respete incluso si alguien invoca
# "make -j".
#
# Nota de shell: NO se fuerza "SHELL := bash" de forma global. Cada
# recipe usa la sintaxis comun a cmd.exe (GNU Make/GnuWin32 en Windows,
# shell por defecto) y a sh/bash (Linux/GitHub Actions): "cd X && comando",
# sin "[ -f ... ]" ni "if" POSIX. Los targets que necesitan un script .sh
# lo invocan de forma explicita con "bash scripts/...", que ambos shells
# saben ejecutar como un programa mas (Git Bash esta primero en PATH en
# Windows).

.PHONY: all up down clean reset-db \
	backend test jacoco jacoco-report frontend traceability \
	sql-audit security-static zap zap-scan zap-gate \
	audit bench lighthouse

.NOTPARALLEL:

# Solo una variable de conveniencia para los dos usos explicitos de bash
# que ya existian (targets "up" y "audit", sin tocar su logica); NO se usa
# para forzar SHELL de forma global (ver nota de shell arriba).
BASH ?= bash

# =============================================================================
# make all — validacion tecnica completa y reproducible, en orden logico.
# Requiere Docker Desktop disponible (backend: Testcontainers; zap: stack
# real de docker-compose). No borra volumenes ni datos persistentes.
#
# Orden (prerequisitos de Make, no llamadas "$(MAKE)"):
#   1. backend          4. sql-audit
#   2. frontend         5. security-static
#   3. traceability     6. zap
# =============================================================================
all: backend frontend traceability sql-audit security-static zap
	@echo ""
	@echo "make all: TODAS las validaciones obligatorias pasaron."

# =============================================================================
# Backend
# =============================================================================

# Validacion completa del backend equivalente al job "backend-test" de CI:
# compila, corre las 205+ pruebas (incluye Testcontainers: PostgreSQL real
# via Docker, Flyway V1->V6), y el gate JaCoCo LINE/BRANCH >= 70% ya
# enlazado a la fase "verify" en Backend/pom.xml. Falla (exit != 0) si
# cualquier prueba falla, si Flyway no aplica limpio, o si JaCoCo no
# alcanza el umbral: ninguna de esas tres condiciones se ignora aqui.
backend:
	cd Backend && mvn clean verify

# Solo pruebas (sin JaCoCo check ni build completo de verify). Se conserva
# tal cual ya existia para no romper el uso previo de "make test".
test:
	cd Backend && mvn test

# Imprime el resumen real de cobertura ya generado por "make backend"
# (Backend/target/site/jacoco/jacoco.csv), sumando LINE y BRANCH de todas
# las clases y recalculando el porcentaje real desde el CSV — no un valor
# fijo. Falla si el reporte no existe (hay que correr "make backend"
# primero) o si, por algun motivo, el porcentaje recalculado cae por
# debajo de 70% pese a que jacoco-maven-plugin ya deberia haberlo detenido
# en "make backend". Logica en scripts/jacoco-summary.sh (evita heredocs
# multilinea dentro de una receta de Make, que no son fiables sin
# .ONESHELL).
jacoco: jacoco-report

jacoco-report:
	bash scripts/jacoco-summary.sh

# =============================================================================
# Frontend
# =============================================================================

# Instalacion reproducible (npm ci contra frontend/package-lock.json, que
# ya forma parte del repositorio -- igual que el job "frontend-build" de
# CI; sin actualizar dependencias) + build de produccion de Angular
# ("ng build --configuration production", definido en frontend/package.json).
# Falla si Angular no compila. Sin condicional "[ -f ... ]" ni "if" POSIX:
# esa sintaxis requiere un shell POSIX y falla en GNU Make/GnuWin32 sobre
# Windows, donde la receta puede ejecutarse via cmd.exe ("No se esperaba
# -f en este momento.").
frontend:
	cd frontend && npm ci && npm run build

# =============================================================================
# Trazabilidad
# =============================================================================

# Reutiliza exactamente el script del job "traceability" de CI: valida que
# todo REQ-F-/REQ-NF- del SRS tenga fila en la matriz de trazabilidad y
# respaldo (historia/caso de uso/prueba), y que las referencias HU-/CU- de
# la matriz existan realmente. No es una comprobacion de existencia de
# archivo: analiza el contenido real de SRS.md, matriz.csv,
# HistoriasUsuario.md y CasosDeUso.md.
traceability:
	bash scripts/validate-traceability.sh

# =============================================================================
# Auditoria SQL dinamica
# =============================================================================

# Reutiliza exactamente scripts/audit-sql-dynamic.sh (mismo usado por el
# job "sql-audit" de CI): audita todos los db/procs/*.sql en busca de SQL
# dinamico construido de forma insegura. No se cambia el criterio de
# deteccion existente.
sql-audit:
	bash scripts/audit-sql-dynamic.sh

# =============================================================================
# Analisis estatico de seguridad (SpotBugs + Find Security Bugs)
# =============================================================================

# Reproduce exactamente los pasos del job "security-static" de CI:
#   1. compila el backend (sin tests, solo para tener bytecode que analizar);
#   2. corre SpotBugs + Find Security Bugs (goal standalone, sin alterar
#      "mvn clean verify": el plugin esta en Backend/pom.xml sin <executions>);
#   3. aplica el gate real ya aprobado por el proyecto:
#      scripts/check-spotbugs-sql-findings.sh falla SOLO si hay hallazgos
#      SQL_* (SQL_NONCONSTANT_STRING_PASSED_TO_EXECUTE y variantes de
#      inyeccion). El hallazgo conocido y documentado
#      SPRING_CSRF_PROTECTION_DISABLED (ver
#      docs/mediciones/sec/static-analysis/README.md) NO se convierte en
#      fallo global aqui: ese criterio ya es una decision aprobada del
#      proyecto y este target no lo cambia. El XML completo con todos los
#      hallazgos, sin filtrar, queda en Backend/target/spotbugsXml.xml.
security-static:
	cd Backend && mvn -q compile
	cd Backend && mvn com.github.spotbugs:spotbugs-maven-plugin:4.10.3.0:spotbugs
	bash scripts/check-spotbugs-sql-findings.sh Backend/target/spotbugsXml.xml

# =============================================================================
# OWASP ZAP Baseline Scan
# =============================================================================

# Reutiliza scripts/run-zap-baseline.sh (levanta postgres+redis+backend con
# docker compose si no estan ya "healthy", corre zap-baseline.py contra el
# backend real dentro de la red de Docker Compose, archiva HTML/XML/JSON en
# docs/mediciones/sec/zap/) y luego scripts/check-zap-high-severity.sh como
# gate obligatorio: falla SOLO si hay al menos una alerta de riesgo ALTO
# real (riskcode=3). Medium/Low/Info se conservan integros en el reporte
# archivado, sin ocultarse, y no bloquean el build (misma politica que CI).
#
# Cleanup seguro: run-zap-baseline.sh SOLO detiene ("docker compose stop")
# los contenedores que el propio script levanto si no estaban ya arriba;
# nunca ejecuta "down -v" ni borra volumenes/datos. Si el stack ya estaba
# corriendo antes (por "make up"), se deja tal cual estaba.
#
# zap-gate depende explicitamente de zap-scan (zap-gate: zap-scan), y zap
# depende de zap-gate: construir "zap" arrastra primero zap-scan y despues
# zap-gate, en ese orden, sin llamadas recursivas "$(MAKE)" ni duplicar el
# escaneo. ".NOTPARALLEL" (arriba) evita que "make -j" intente correr
# zap-scan y zap-gate a la vez.
zap: zap-gate

zap-scan:
	bash scripts/run-zap-baseline.sh

zap-gate: zap-scan
	bash scripts/check-zap-high-severity.sh

# =============================================================================
# Docker: levantar / bajar el stack completo (sin tocar datos)
# =============================================================================

# Levanta el sistema completo incluyendo el modulo TLS.
# Requiere que docker-compose.tls.yml exista en la raiz del repositorio.
up:
	"$(BASH)" scripts/generate-dev-keystore.sh
	docker compose -f docker-compose.yml -f docker-compose.tls.yml up --build -d

# Detiene los contenedores SIN borrar volumenes.
# Los datos de PostgreSQL y Redis se conservan.
down:
	docker compose -f docker-compose.yml -f docker-compose.tls.yml down

# =============================================================================
# Otros targets ya existentes (sin cambios de comportamiento)
# =============================================================================

## Ejecuta un benchmark k6 (50 VUs / 30s) contra el endpoint de listado.
# Requiere admin@biopet.ec sembrado y el sistema levantado con make up.
# Para las 6 corridas oficiales (frio/caliente) usar los comandos documentados
# en docs/mediciones/perf/REPORT.md; este objetivo corre una unica corrida rapida.
bench:
	k6 run k6/listado-mascotas.js

# Ejecuta la auditoria de seguridad OWASP y evidencia asociada (evidencia
# agregada de mediciones/sec, distinta del gate "sql-audit" de arriba).
audit:
	@if [ -f scripts/security-evidence.sh ]; then \
		"$(BASH)" scripts/security-evidence.sh; \
	else \
		echo "[audit] Pendiente: falta scripts/security-evidence.sh (Jaime)."; \
	fi

# Elimina contenedores, redes y contenedores huerfanos,
# pero conserva los volumenes y los datos.
clean:
	docker compose down --remove-orphans

# DESTRUCTIVO: elimina tambien los volumenes y los datos persistentes.
# NO se invoca desde "make all" ni desde ningun otro target de este archivo.
reset-db:
	@echo "[reset-db] ADVERTENCIA: esto eliminara los datos persistentes de PostgreSQL y Redis."
	docker compose down -v --remove-orphans

# Ejecuta la auditoria Lighthouse (bloque C.5 de la Guia) contra el
# frontend servido por el contenedor y archiva los resultados crudos en
# docs/mediciones/lighthouse/. Requiere 'make up' corrido previamente.
# Corre los dos perfiles exigidos (movil via lighthouserc.js, desktop via
# lighthouserc.desktop.js); ver scripts/run-lighthouse.sh.
# NO forma parte de "make all": queda como validacion manual, no como gate
# obligatorio de CI (ver .github/workflows/ci.yml).
lighthouse:
	bash scripts/run-lighthouse.sh
