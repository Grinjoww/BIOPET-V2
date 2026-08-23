import { registerLocaleData } from '@angular/common';
import localeEsEc from '@angular/common/locales/es-EC';
import { LOCALE_ID } from '@angular/core';
import { bootstrapApplication } from '@angular/platform-browser';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { AppComponent } from './app/app.component';
import { routes } from './app/app.routes';
import { httpErrorInterceptor } from './app/core/http-error.interceptor';

// Sin esto, el pipe `date` (usado en Citas/Consultas/Especie de la ficha
// de mascota) cae al locale por defecto de Angular (en-US) y muestra
// fechas en inglés ("Aug 24, 2026") en un producto que es enteramente en
// español — encontrado durante el QA visual de esta fase.
registerLocaleData(localeEsEc, 'es-EC');

bootstrapApplication(AppComponent, {
  providers: [
    { provide: LOCALE_ID, useValue: 'es-EC' },
    // withComponentInputBinding: liga route.data directamente a @Input() del
    // componente de la ruta, sin leer ActivatedRoute a mano. Ya no lo usa
    // ningún componente (lo usaba ComingSoonComponent, eliminado por código
    // huérfano en la fase de higiene); se deja provisto por si una fase
    // futura vuelve a necesitarlo.
    provideRouter(routes, withComponentInputBinding()),
    provideHttpClient(withInterceptors([httpErrorInterceptor]))
  ]
}).catch((err) => console.error(err));