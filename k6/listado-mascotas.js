// k6/listado-mascotas.js
// Prueba de carga sobre GET /api/mascotas (endpoint cacheado con Redis).
// La autenticación real del backend es por cookie HttpOnly (access_token),
// no por header Bearer con token en el body — por eso usamos el jar de
// cookies de k6 en vez de capturar un accessToken del JSON de login.

import http from 'k6/http';
import { check, sleep } from 'k6';
import { options } from './opts.js';

export { options };

const BASE_URL = __ENV.BASE_URL || 'http://127.0.0.1:8080';

export function setup() {
    const loginRes = http.post(
        `${BASE_URL}/api/auth/login`,
        JSON.stringify({ email: 'admin@biopet.ec', password: 'Admin123*' }),
        { headers: { 'Content-Type': 'application/json' } }
    );

    console.log(`Login status: ${loginRes.status}`);
    console.log(`Login body: ${loginRes.body}`);
    console.log(`Login headers: ${JSON.stringify(loginRes.headers)}`);

    check(loginRes, { 'login exitoso (200)': (r) => r.status === 200 });

    return {};
}

export default function () {
    const res = http.get(`${BASE_URL}/api/mascotas?page=0&size=10`);

    console.log(`GET status: ${res.status}`);
    console.log(`GET body: ${res.body}`);

    check(res, {
        'status es 200': (r) => r.status === 200,
    });

    sleep(0.5);
}