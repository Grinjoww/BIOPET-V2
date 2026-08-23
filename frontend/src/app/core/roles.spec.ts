import { humanizarRol, navGroupsParaRol, rutaInicioParaRol } from './roles';

/** Aplana todos los NavItem de todos los grupos, para buscar por path. */
function paths(rol: Parameters<typeof navGroupsParaRol>[0]): string[] {
  return navGroupsParaRol(rol)
    .flatMap((g) => g.items)
    .map((i) => i.path);
}

function label(rol: Parameters<typeof navGroupsParaRol>[0], path: string): string | undefined {
  return navGroupsParaRol(rol)
    .flatMap((g) => g.items)
    .find((i) => i.path === path)?.label;
}

describe('roles (navegación por rol — solo wayfinding de UI)', () => {
  it('ADMIN ve las 7 secciones: Panel, Mascotas, Citas, Consultas, Vacunas, Usuarios y Perfil', () => {
    const rutas = paths('ROLE_ADMIN');
    expect(rutas).toEqual(
      jasmine.arrayContaining(['/panel', '/mascotas', '/citas', '/consultas', '/vacunas', '/usuarios', '/perfil'])
    );
  });

  it('ni VETERINARIO ni AUXILIAR ven Usuarios (solo ADMIN puede administrar cuentas)', () => {
    expect(paths('ROLE_VETERINARIO')).not.toContain('/usuarios');
    expect(paths('ROLE_AUXILIAR')).not.toContain('/usuarios');
    // Sí conservan el resto de la clínica.
    expect(paths('ROLE_VETERINARIO')).toEqual(jasmine.arrayContaining(['/panel', '/mascotas', '/citas']));
  });

  it('DUENO no ve Panel ni Usuarios, y ve sus 4 secciones filtradas por propiedad con el label "Mis..." consistente (Mascotas/Citas/Consultas/Vacunas)', () => {
    const rutasDueno = paths('ROLE_DUENO');
    expect(rutasDueno).not.toContain('/panel');
    expect(rutasDueno).not.toContain('/usuarios');
    expect(rutasDueno).toEqual(jasmine.arrayContaining(['/mascotas', '/citas', '/consultas', '/vacunas', '/perfil']));

    // Las 4 secciones están igual de filtradas por propiedad en el backend
    // (MascotaService/CitaService/ConsultaService/VacunaService), así que
    // el label debe ser consistente para las 4 -corregido en esta fase:
    // antes Consultas/Vacunas quedaban con la etiqueta genérica pese a
    // estar filtradas igual que Mascotas/Citas-.
    expect(label('ROLE_DUENO', '/mascotas')).toBe('Mis mascotas');
    expect(label('ROLE_DUENO', '/citas')).toBe('Mis citas');
    expect(label('ROLE_DUENO', '/consultas')).toBe('Mis consultas');
    expect(label('ROLE_DUENO', '/vacunas')).toBe('Mis vacunas');
  });

  it('ADMIN/VETERINARIO/AUXILIAR mantienen los labels clínicos generales (no "Mis...", el listado es global)', () => {
    for (const rol of ['ROLE_ADMIN', 'ROLE_VETERINARIO', 'ROLE_AUXILIAR'] as const) {
      expect(label(rol, '/mascotas')).withContext(rol).toBe('Mascotas');
      expect(label(rol, '/citas')).withContext(rol).toBe('Citas');
      expect(label(rol, '/consultas')).withContext(rol).toBe('Consultas');
      expect(label(rol, '/vacunas')).withContext(rol).toBe('Vacunas');
    }
  });

  it('humanizarRol traduce los 4 roles reales y cae a "Usuario" ante cualquier otro valor', () => {
    expect(humanizarRol('ROLE_ADMIN')).toBe('Administrador');
    expect(humanizarRol('ROLE_VETERINARIO')).toBe('Veterinario');
    expect(humanizarRol('ROLE_AUXILIAR')).toBe('Auxiliar');
    expect(humanizarRol('ROLE_DUENO')).toBe('Dueño');
    expect(humanizarRol(undefined)).toBe('Usuario');
  });

  it('rutaInicioParaRol manda a /panel a los 3 roles con acceso real al Panel, y a /mascotas a DUENO', () => {
    expect(rutaInicioParaRol('ROLE_ADMIN')).toBe('/panel');
    expect(rutaInicioParaRol('ROLE_VETERINARIO')).toBe('/panel');
    expect(rutaInicioParaRol('ROLE_AUXILIAR')).toBe('/panel');
    expect(rutaInicioParaRol('ROLE_DUENO')).toBe('/mascotas');
  });
});
