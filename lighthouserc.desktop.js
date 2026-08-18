/**
 * lighthouserc.desktop.js — Configuración de Lighthouse CI (perfil desktop) para BIOPET.
 *
 * Complementa a lighthouserc.js (perfil móvil, el exigido literalmente por el
 * bloque C.5 de la Guía). Este archivo agrega la corrida de escritorio
 * mencionada como pendiente en el Makefile (target "lighthouse") y en
 * docs/mediciones/lighthouse/README.md ("las evidencias definitivas
 * mobile+desktop todavía están siendo cerradas"): mismas URLs, mismos
 * umbrales, mismo numberOfRuns, únicamente cambia el formFactor auditado.
 *
 * Perfil: formFactor 'desktop' + throttling 'devtools' con los valores RTT
 * 40ms / throughput 10240 kbps / cpuSlowdownMultiplier 1, que es exactamente
 * el preset "desktop" que trae Lighthouse por defecto (lighthouse-core
 * config/constants.js, desktopConfig). No se inventan valores nuevos: es el
 * mismo preset que --preset=desktop aplicaría vía Lighthouse CLI.
 *
 * Cómo se ejecuta (ver scripts/run-lighthouse.sh):
 *   1. make up
 *   2. bash scripts/run-lighthouse.sh   (corre ambos perfiles: móvil y desktop)
 */
module.exports = {
  ci: {
    collect: {
      // Mismas rutas reales auditadas en el perfil móvil (lighthouserc.js);
      // ver ese archivo para la justificación de por qué no se audita '/'.
      url: [
        'http://localhost:4200/login',
        'http://localhost:4200/mascotas',
      ],
      numberOfRuns: 3,
      settings: {
        formFactor: 'desktop',
        screenEmulation: {
          mobile: false,
          width: 1350,
          height: 940,
          deviceScaleFactor: 1,
          disabled: false,
        },
        throttling: {
          rttMs: 40,
          throughputKbps: 10240,
          cpuSlowdownMultiplier: 1,
          requestLatencyMs: 0,
          downloadThroughputKbps: 0,
          uploadThroughputKbps: 0,
        },
        // Mismo motivo que en lighthouserc.js: el contenedor de desarrollo
        // sirve HTTP/1.1 vía Nginx sin TLS local.
        skipAudits: ['uses-http2'],
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
      outputDir: './.lighthouseci-desktop',
    },
  },
};
