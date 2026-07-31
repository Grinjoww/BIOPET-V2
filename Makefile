# Makefile — BIOPET (tarea de reproducibilidad, Fred)
# Ubicacion: raiz del repositorio, junto a docker-compose.yml

.PHONY: up down test bench audit clean reset-db
BASH ?= bash
# Levanta el sistema completo incluyendo el modulo TLS.
# Requiere que docker-compose.tls.yml exista en la raiz del repositorio.
up:
	$(BASH) scripts/generate-dev-keystore.sh
	docker compose -f docker-compose.yml -f docker-compose.tls.yml up --build -d

# Detiene los contenedores SIN borrar volumenes.
# Los datos de PostgreSQL y Redis se conservan.
down:
	docker compose -f docker-compose.yml -f docker-compose.tls.yml down

# Ejecuta las pruebas automatizadas del backend con Maven.
test:
	cd Backend && mvn test

## Ejecuta un benchmark k6 (50 VUs / 30s) contra el endpoint de listado.
# Requiere admin@biopet.ec sembrado y el sistema levantado con make up.
# Para las 6 corridas oficiales (frio/caliente) usar los comandos documentados
# en docs/mediciones/perf/REPORT.md; este objetivo corre una unica corrida rapida.
bench:
	k6 run k6/listado-mascotas.js

# Pendiente: aun no existe auditoria de seguridad automatizada.
# Ejecuta la auditoria de seguridad OWASP y evidencia asociada (Jaime).
audit:
	@if [ -f scripts/security-evidence.sh ]; then \
		$(BASH) scripts/security-evidence.sh; \
	else \
		echo "[audit] Pendiente: falta scripts/security-evidence.sh (Jaime)."; \
	fi

# Elimina contenedores, redes y contenedores huerfanos,
# pero conserva los volumenes y los datos.
clean:
	docker compose down --remove-orphans

# DESTRUCTIVO: elimina tambien los volumenes y los datos persistentes.
reset-db:
	@echo "[reset-db] ADVERTENCIA: esto eliminara los datos persistentes de PostgreSQL y Redis."
	docker compose down -v --remove-orphans

# Ejecuta la auditoria Lighthouse (bloque C.5 de la Guia) contra el
# frontend servido por el contenedor y archiva los resultados crudos en
# docs/mediciones/lighthouse/. Requiere 'make up' corrido previamente.
lighthouse:
	bash scripts/run-lighthouse.sh