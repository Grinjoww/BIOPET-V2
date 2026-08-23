// Karma config para la primera suite de tests frontend de BIOPET V2.
// Restaura la configuración ESTÁNDAR que `ng new` genera por defecto en
// Angular 17 (Karma + Jasmine + karma-coverage): ver el informe de esta
// fase para la justificación completa de por qué se eligió esto en vez del
// builder experimental @angular-devkit/build-angular:jest.
//
// Esta máquina no tiene Google Chrome instalado, pero sí Microsoft Edge
// (Chromium). karma-chrome-launcher acepta cualquier binario Chromium via
// CHROME_BIN, así que si esa variable no está ya definida (p. ej. en CI, que
// normalmente sí trae Chrome) se apunta a Edge automáticamente. No afecta a
// ningún entorno donde CHROME_BIN o Chrome real ya estén disponibles.
const fs = require('fs');

if (!process.env.CHROME_BIN) {
  const edgeCandidates = [
    'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe',
    'C:\\Program Files\\Microsoft\\Edge\\Application\\msedge.exe',
  ];
  const edge = edgeCandidates.find((p) => fs.existsSync(p));
  if (edge) {
    process.env.CHROME_BIN = edge;
  }
}

module.exports = function (config) {
  config.set({
    basePath: '',
    frameworks: ['jasmine', '@angular-devkit/build-angular'],
    plugins: [
      require('karma-jasmine'),
      require('karma-chrome-launcher'),
      require('karma-jasmine-html-reporter'),
      require('karma-coverage'),
      require('@angular-devkit/build-angular/plugins/karma'),
    ],
    client: {
      jasmine: {},
      clearContext: false, // deja visible el output de Jasmine HTML Reporter
    },
    jasmineHtmlReporter: {
      suppressAll: true, // elimina los logs duplicados en la consola
    },
    coverageReporter: {
      dir: require('path').join(__dirname, './coverage/biopet-frontend'),
      subdir: '.',
      reporters: [{ type: 'html' }, { type: 'text-summary' }, { type: 'lcovonly' }],
    },
    reporters: ['progress', 'kjhtml'],
    port: 9876,
    colors: true,
    logLevel: config.LOG_INFO,
    autoWatch: true,
    browsers: ['ChromeHeadlessCI'],
    customLaunchers: {
      ChromeHeadlessCI: {
        base: 'ChromeHeadless',
        // --no-sandbox: necesario en muchos entornos CI/contenedor sin
        // privilegios de sandboxing de Chromium disponibles.
        flags: ['--no-sandbox', '--disable-gpu'],
      },
    },
    singleRun: false,
    restartOnFileChange: true,
  });
};
