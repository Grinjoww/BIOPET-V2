// k6/opts.js
// Configuracion compartida: 50 VUs, ramp-up de 5s, carga sostenida de 30s.
// Sin datos aleatorios generados por el script, por lo que no aplica semilla fija aqui;
// la reproducibilidad de esta prueba depende del estado del cache (frio/caliente),
// controlado externamente antes de cada corrida (ver README de esta carpeta).

export const options = {
    insecureSkipTLSVerify: true,
    stages: [
        { duration: '5s', target: 50 },   // ramp-up
        { duration: '30s', target: 50 },  // carga sostenida (medicion real)
    ],
    thresholds: {
        http_req_failed: ['rate<0.01'],
    },
};