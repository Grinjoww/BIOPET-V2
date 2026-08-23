import {
  calcularEdad,
  chipClaseEstadoCita,
  chipClaseEstadoVacuna,
  estadoVacuna,
  etiquetaEstadoCita,
  etiquetaEstadoVacuna,
  fechaHoraLocalAInstant,
  formatearExpediente,
  instantAFechaHoraLocal,
} from './presentacion';

describe('presentacion (helpers puros de presentación clínica)', () => {
  it('formatearExpediente rellena Mascota.id a 6 dígitos sin inventar un campo nuevo', () => {
    expect(formatearExpediente(1)).toBe('EXP-000001');
    expect(formatearExpediente(123456)).toBe('EXP-123456');
  });

  describe('con la fecha del sistema fijada (2026-06-15) para ser determinista', () => {
    const HOY = new Date(2026, 5, 15); // 15 de junio de 2026, hora local

    beforeEach(() => jasmine.clock().install());
    afterEach(() => jasmine.clock().uninstall());

    it('calcularEdad cubre años, meses, "menos de 1 mes" y el borde del cumpleaños', () => {
      jasmine.clock().mockDate(HOY);

      // Exactamente 3 años cumplidos hoy.
      expect(calcularEdad('2023-06-15')).toBe('3 años');
      // El cumpleaños es MAÑANA: técnicamente todavía no cumple, sigue en 2 años.
      expect(calcularEdad('2023-06-16')).toBe('2 años');
      // 4 meses.
      expect(calcularEdad('2026-02-15')).toBe('4 meses');
      // Nacido hace 2 semanas: menos de 1 mes.
      expect(calcularEdad('2026-06-01')).toBe('Menos de 1 mes');
    });

    it('estadoVacuna clasifica vencida/próxima/al-día/sin-programar en los límites correctos, con etiqueta y chip consistentes', () => {
      jasmine.clock().mockDate(HOY);

      const casos: Array<{ proximaFecha: string | null; esperado: ReturnType<typeof estadoVacuna> }> = [
        { proximaFecha: '2026-06-14', esperado: 'vencida' }, // ayer
        { proximaFecha: '2026-06-15', esperado: 'proxima' }, // hoy mismo (0 días)
        { proximaFecha: '2026-07-15', esperado: 'proxima' }, // exactamente 30 días: límite inclusivo
        { proximaFecha: '2026-07-16', esperado: 'al-dia' }, // 31 días: ya fuera del límite
        { proximaFecha: null, esperado: 'sin-programar' },
      ];

      const etiquetaPorEstado = {
        vencida: 'Refuerzo vencido',
        proxima: 'Refuerzo próximo',
        'al-dia': 'Al día',
        'sin-programar': 'Sin refuerzo programado',
      } as const;
      const chipPorEstado = {
        vencida: 'chip--danger',
        proxima: 'chip--warning',
        'al-dia': 'chip--success',
        'sin-programar': 'chip--neutral',
      } as const;

      for (const caso of casos) {
        const estado = estadoVacuna(caso.proximaFecha);
        expect(estado).withContext(`proximaFecha=${caso.proximaFecha}`).toBe(caso.esperado);
        expect(etiquetaEstadoVacuna(estado)).toBe(etiquetaPorEstado[estado]);
        expect(chipClaseEstadoVacuna(estado)).toBe(chipPorEstado[estado]);
      }
    });
  });

  it('etiquetaEstadoCita/chipClaseEstadoCita cubren exhaustivamente los 3 valores reales de EstadoCita', () => {
    const esperado: Record<'PROGRAMADA' | 'COMPLETADA' | 'CANCELADA', { etiqueta: string; chip: string }> = {
      PROGRAMADA: { etiqueta: 'Programada', chip: 'chip--info' },
      COMPLETADA: { etiqueta: 'Completada', chip: 'chip--success' },
      CANCELADA: { etiqueta: 'Cancelada', chip: 'chip--neutral' },
    };

    (Object.keys(esperado) as Array<keyof typeof esperado>).forEach((estado) => {
      expect(etiquetaEstadoCita(estado)).toBe(esperado[estado].etiqueta);
      expect(chipClaseEstadoCita(estado)).toBe(esperado[estado].chip);
    });
  });

  describe('conversión UTC (Instant) ↔ datetime-local (Citas V2)', () => {
    it('instantAFechaHoraLocal refleja los componentes LOCALES del mismo Date, sin depender de la zona horaria del entorno de test', () => {
      // Se construye con componentes locales explícitos (no parseando un
      // string UTC), así el valor esperado se deriva del mismo objeto Date
      // en vez de codificar una hora fija que cambiaría de resultado en
      // otro huso horario.
      const d = new Date(2026, 5, 15, 14, 30);
      const iso = d.toISOString();

      const local = instantAFechaHoraLocal(iso);

      const pad = (n: number) => String(n).padStart(2, '0');
      const esperado = `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
      expect(local).toBe(esperado);
    });

    it('el roundtrip datetime-local → Instant → datetime-local no introduce un desplazamiento doble', () => {
      const localOriginal = '2026-06-15T14:30';

      const instant = fechaHoraLocalAInstant(localOriginal);
      // Debe ser un Instant UTC válido y parseable (termina en "Z").
      expect(instant.endsWith('Z')).toBeTrue();
      expect(Number.isNaN(new Date(instant).getTime())).toBeFalse();

      const localDeVuelta = instantAFechaHoraLocal(instant);
      expect(localDeVuelta).toBe(localOriginal);
    });
  });
});
