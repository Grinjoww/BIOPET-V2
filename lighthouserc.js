/**
 * lighthouserc.js — Configuración de Lighthouse CI para BIOPET.
 *
 * Exigido por el bloque C.5 de la Guía de la Tercera Entrega:
 *   "Se ejecuta npx lhci autorun contra el frontend Angular servido por el
 *   contenedor. Los umbrales mínimos declarados en lighthouserc.js son:
 *   Performance >= 80, Accessibility >= 90, Best practices >= 90, SEO >= 90."
 *
 * Requisito relacionado: REQ-NF-007 (SRS.md) / HU-008, HU-020.
 *
 * Cómo se ejecuta (ver scripts/run-lighthouse.sh para el flujo completo):
 *   1. make up                      (o docker compose up -d) — el frontend
 *      debe estar sirviendo en http://localhost:4200, contra el contenedor
 *      real, no contra "ng serve".
 *   2. bash scripts/run-lighthouse.sh
 *
 * Perfil: por defecto, cuando no se fuerza formFactor "desktop", Lighthouse
 * usa el perfil móvil con throttling simulado equivalente a "Slow 4G" +
 * CPU 4x slowdown. Es intencional no sobreescribir throttling aquí: es
 * exactamente el perfil que exige el bloque C.5, y sobreescribirlo con
 * valores manuales sería reinventar un preset que Lighthouse ya aplica por
 * defecto.
 */
module.exports = {
  ci: {
    collect: {
      // Rutas reales de la SPA que un usuario visita sin sesión (login) y
      // ya autenticado (mascotas). No se audita '/', porque solo redirige.
      url: [
        'http://localhost:4200/login',
        'http://localhost:4200/mascotas',
      ],
      numberOfRuns: 3,
      settings: {
        preset: 'mobile',
        throttlingMethod: 'simulate',
        // La pantalla de Mascotas depende de la cookie de sesión (authGuard
        // hace GET /api/usuarios/me y redirige a /login si no hay sesión).
        // Sin backend/sesión real, Lighthouse audita la página de login a
        // la que redirige — igual de válido para Performance/Accessibility/
        // Best Practices/SEO, que es lo que este bloque mide.
        skipAudits: ['uses-http2'], // el contenedor de desarrollo sirve HTTP/1.1 vía Nginx sin TLS local
      },
    },
    assert: {
      assertions: {
        'categories:performance': ['error', { minScore: 0.8 }],
        'categories:accessibility': ['error', { minScore: 0.9 }],
        'categories:best-practices': ['error', { minScore: 0.9 }],
        'categories:seo': ['error', { minScore: 0.9 }],
      },
    },
    upload: {
      target: 'filesystem',
      outputDir: './.lighthouseci',
    },
  },
};
