# Métricas JaCoCo archivadas — Entrega Final v1.0.0

Generado automáticamente por `scripts/archive-jacoco-evidence.sh` a partir
de `Backend/target/site/jacoco/jacoco.xml` (no editado a mano).

| Métrica | Cubierto | No cubierto | Cobertura | Umbral `pom.xml` |
|---|---:|---:|---:|---:|
| LINE | 885 | 79 | 91.80 % | 0.70 |
| BRANCH | 181 | 47 | 79.39 % | 0.70 |
| COMPLEXITY | 299 | 77 | 79.52 % | 0.60 |

Reproducción:

```bash
cd Backend
mvn clean verify
cd ..
bash scripts/archive-jacoco-evidence.sh
```

Archivos:

- `jacoco.xml` — reporte XML completo generado por JaCoCo.
- `jacoco.csv` — reporte CSV por clase generado por JaCoCo.
- `html/index.html` — reporte HTML navegable completo (con desglose por paquete y clase).
