# Makefile — BIOPET (tarea de reproducibilidad, Fred)
# Ubicacion: raiz del repositorio, junto a docker-compose.yml

.PHONY: up down test bench audit clean reset-db

# Levanta todo el sistema (postgres, redis, backend, frontend)
# y construye las imagenes si hace falta.
up:
	docker compose up --build -d

# Detiene los contenedores SIN borrar volumenes.
# Los datos de PostgreSQL y Redis se conservan.
down:
	docker compose down

# Ejecuta las pruebas automatizadas del backend con Maven.
test:
	cd Backend && mvn test

# Pendiente: aun no existen scripts de rendimiento con k6.
bench:
	@echo "[bench] Pendiente: los scripts de k6 todavia no estan implementados en este repositorio."

# Pendiente: aun no existe auditoria de seguridad automatizada.
audit:
	@echo "[audit] Pendiente: la auditoria OWASP automatizada todavia no esta implementada en este repositorio."

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