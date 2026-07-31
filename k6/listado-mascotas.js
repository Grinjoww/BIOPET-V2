import http from 'k6/http';
import { check, sleep } from 'k6';
import { options } from './opts.js';

export { options };

const BASE_URL = __ENV.BASE_URL || 'https://localhost:8443';

export function setup() {
    const loginRes = http.post(
        `${BASE_URL}/api/auth/login`,
        JSON.stringify({ email: 'admin@biopet.ec', password: 'Admin123*' }),
        { headers: { 'Content-Type': 'application/json' } }
    );

    check(loginRes, { 'login exitoso (200)': (r) => r.status === 200 });

    const setCookieHeader = loginRes.headers['Set-Cookie'];
    const match = setCookieHeader.match(/access_token=([^;]+)/);
    const token = match ? match[1] : null;

    return { token };
}

export default function (data) {
    const jar = http.cookieJar();
    jar.set(BASE_URL, 'access_token', data.token, { secure: true, path: '/' });

    const res = http.get(`${BASE_URL}/api/mascotas?page=0&size=10`);

    check(res, {
        'status es 200': (r) => r.status === 200,
    });

    sleep(0.5);
}