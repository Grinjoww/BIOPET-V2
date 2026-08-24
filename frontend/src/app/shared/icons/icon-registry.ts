/**
 * Registro tipado de iconos de BIOPET V2.
 *
 * Sistema propio, sin dependencia externa: cada icono se describe como
 * datos (líneas, círculos, rutas, polilíneas) sobre una rejilla de 24x24,
 * trazo 1.5px, `currentColor`, sin relleno. IconComponent los renderiza.
 *
 * Añadir un icono nuevo: agregar su nombre a IconName y su geometría a
 * ICONS. Con `strictTemplates` activo, usar un nombre que no exista aquí
 * falla en compilación, no en producción.
 */
export type IconName =
  | 'panel'
  | 'mascotas'
  | 'citas'
  | 'consultas'
  | 'vacunas'
  | 'usuarios'
  | 'perfil'
  | 'logout'
  | 'menu'
  | 'close'
  | 'editar'
  | 'eliminar'
  | 'anadir'
  | 'check'
  | 'chevron-left'
  | 'chevron-right';

export interface IconShape {
  paths?: string[];
  lines?: [number, number, number, number][];
  circles?: [number, number, number][];
  polylines?: string[];
  rects?: [number, number, number, number, number?][];
}

export const ICONS: Record<IconName, IconShape> = {
  panel: {
    rects: [
      [3, 3, 8, 8, 1.5],
      [13, 3, 8, 8, 1.5],
      [3, 13, 8, 8, 1.5],
      [13, 13, 8, 8, 1.5],
    ],
  },
  mascotas: {
    // Huella minimalista: un único trazo funcional de navegación, no
    // decoración repetida (se evita a propósito el "huellitas por todas
    // partes"). Construida con los mismos círculos sin relleno que el
    // resto del set, para que no desentone con la línea de 1.5px.
    circles: [
      [12, 16, 3],
      [6.5, 10, 1.6],
      [10.5, 6.5, 1.6],
      [13.5, 6.5, 1.6],
      [17.5, 10, 1.6],
    ],
  },
  citas: {
    rects: [[3, 4, 18, 17, 2]],
    lines: [
      [3, 9, 21, 9],
      [8, 2, 8, 6],
      [16, 2, 16, 6],
    ],
  },
  consultas: {
    rects: [[5, 4, 14, 18, 2]],
    paths: ['M9 4h6a1 1 0 0 1 1 1v1H8V5a1 1 0 0 1 1-1Z'],
    lines: [
      [8, 11, 16, 11],
      [8, 15, 13, 15],
    ],
  },
  vacunas: {
    lines: [
      [3, 21, 9, 15],
      [9, 15, 15, 9],
      [15, 9, 21, 3],
      [13, 7, 17, 11],
      [16, 4, 20, 8],
    ],
  },
  usuarios: {
    circles: [
      [8, 8, 3],
      [16, 9, 2.5],
    ],
    paths: ['M2 20c0-3.3 2.7-6 6-6s6 2.7 6 6', 'M14.5 14.2c2.6.4 4.5 2.6 4.5 5.3'],
  },
  perfil: {
    circles: [[12, 8, 4]],
    paths: ['M4 20c0-4.4 3.6-8 8-8s8 3.6 8 8'],
  },
  logout: {
    paths: ['M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4'],
    lines: [[9, 12, 21, 12]],
    polylines: ['17 7 21 12 17 17'],
  },
  menu: {
    lines: [
      [3, 6, 21, 6],
      [3, 12, 21, 12],
      [3, 18, 21, 18],
    ],
  },
  close: {
    lines: [
      [6, 6, 18, 18],
      [18, 6, 6, 18],
    ],
  },
  editar: {
    lines: [[12, 20, 21, 20]],
    paths: ['M16.5 3.5a2.121 2.121 0 0 1 3 3L7 19l-4 1 1-4Z'],
  },
  eliminar: {
    paths: [
      'M4 7h16',
      'M9 7V4a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v3',
      'M6 7l1 13a1 1 0 0 0 1 1h8a1 1 0 0 0 1-1l1-13',
    ],
    lines: [
      [10, 11, 10, 17],
      [14, 11, 14, 17],
    ],
  },
  anadir: {
    lines: [
      [12, 5, 12, 19],
      [5, 12, 19, 12],
    ],
  },
  check: {
    polylines: ['5 13 10 18 19 6'],
  },
  'chevron-left': {
    polylines: ['15 6 9 12 15 18'],
  },
  'chevron-right': {
    polylines: ['9 6 15 12 9 18'],
  },
};
