import { Component, OnInit, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { AuthService } from '../core/auth.service';
import { ProblemDetailService } from '../core/problem-detail.service';
import { UsuarioSeleccionable, UsuarioSeleccionableApiService } from '../core/usuario-seleccionable-api.service';
import { EstadoFactura, Factura, FacturaApiService, FiltrosFacturas } from './factura-api.service';
import { chipClaseEstadoFactura, chipClaseEstadoRecepcion, etiquetaEstadoFactura, etiquetaEstadoRecepcion, numeroComprobante } from './factura-presentacion';
import { PageHeaderComponent } from '../shared/page-header/page-header.component';
import { IconComponent } from '../shared/icons/icon.component';

const TAMANIO_PAGINA = 10;
const FILAS_SKELETON = 4;

/**
 * IMPORTANTE — reglas reales de FacturaController/FacturaConsultaService
 * (auditadas, no inventadas):
 *
 *  - GET /api/facturas: los 4 roles pueden leer. Para DUENO, el backend
 *    IGNORA cualquier `estado`/`usuarioId` que se envíe y fuerza siempre
 *    AUTORIZADA + su propio id. Para VETERINARIO, devuelve solo facturas con
 *    al menos una línea de origen clínico asignado a él (`usuarioId` que se
 *    envíe también se ignora). Para ADMIN/AUXILIAR, el listado es completo y
 *    ambos filtros se respetan tal cual.
 *  - POST /api/facturas (crear borrador): solo ADMIN/AUXILIAR.
 *
 * Este componente no decide qué facturas ve cada rol: consume tal cual lo
 * que llega en `content`. Los filtros de dueño/mascota solo se OFRECEN para
 * ADMIN/AUXILIAR porque para los otros dos roles el backend los ignora de
 * todas formas — mostrarlos igual sería un control que no hace nada.
 */
@Component({
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink, PageHeaderComponent, IconComponent],
  template: `
  <app-page-header
    eyebrow="Facturación"
    title="Facturas"
    [description]="descripcion"
    [hasActions]="puedeCrear || esAdmin">
    <a routerLink="/facturas/configuracion" class="btn btn--secondary" *ngIf="esAdmin">
      Configuración fiscal
    </a>
    <a routerLink="/facturas/nueva" class="btn btn--primary" *ngIf="puedeCrear">
      <app-icon name="anadir"></app-icon>
      Nueva factura
    </a>
  </app-page-header>

  <div class="toolbar" role="toolbar" aria-label="Acciones de facturas">
    <button type="button" class="btn btn--ghost btn--sm" (click)="cargar()" [disabled]="cargando()">
      Actualizar
    </button>
    <button
      type="button"
      class="btn btn--secondary btn--sm"
      (click)="alternarFiltros()"
      [attr.aria-expanded]="mostrarFiltros()"
      *ngIf="tieneFiltrosAvanzados">
      {{ mostrarFiltros() ? 'Ocultar filtros' : 'Filtros' }}
    </button>
  </div>

  <p class="alert alert--danger" role="alert" aria-live="assertive" *ngIf="error()">
    <strong>Error:</strong> {{ error() }}
  </p>

  <!-- ===== Filtros ===== -->
  <section *ngIf="mostrarFiltros()" class="panel" aria-labelledby="filtros-titulo">
    <h2 id="filtros-titulo" class="panel__title-inline">Filtros</h2>
    <form [formGroup]="filtrosForm" (ngSubmit)="aplicarFiltros()" novalidate>
      <div class="field">
        <label for="f-estado">Estado</label>
        <select id="f-estado" formControlName="estado">
          <option [ngValue]="null">Todos los estados</option>
          <option value="BORRADOR">Borrador</option>
          <option value="EMITIDA">Emitida</option>
          <option value="AUTORIZADA">Autorizada</option>
          <option value="RECHAZADA">Rechazada</option>
        </select>
      </div>

      <div class="field">
        <label for="f-fecha">Fecha de emisión</label>
        <input id="f-fecha" type="date" formControlName="fechaEmision" />
      </div>

      <div class="field" *ngIf="esAdmin || puedeCrear">
        <label for="f-duenio">Dueño</label>
        <select id="f-duenio" formControlName="usuarioId" [disabled]="cargandoDuenios()">
          <option [ngValue]="null">Todos los dueños</option>
          <option *ngFor="let d of duenios()" [ngValue]="d.id">{{ d.nombre }} — {{ d.email }}</option>
        </select>
      </div>

      <div class="modal-panel__actions">
        <button type="submit" class="btn btn--primary btn--sm">Aplicar filtros</button>
        <button type="button" class="btn btn--secondary btn--sm" (click)="limpiarFiltros()">Limpiar</button>
      </div>
    </form>
  </section>

  <!-- ===== Listado ===== -->
  <span class="sr-only" role="status" aria-live="polite" *ngIf="cargando()">Cargando facturas…</span>

  <div class="table-wrap">
    <table class="table-clinico">
      <caption class="sr-only">Listado de facturas</caption>
      <thead>
        <tr>
          <th scope="col">Número</th>
          <th scope="col">Fecha</th>
          <th scope="col">Comprador</th>
          <th scope="col">Mascota</th>
          <th scope="col">Total</th>
          <th scope="col">Estado</th>
          <th scope="col">SRI</th>
          <th scope="col">Autorización</th>
        </tr>
      </thead>
      <tbody>
        <ng-container *ngIf="cargando()">
          <tr class="skeleton-row" *ngFor="let fila of filasSkeleton" aria-hidden="true">
            <td><span class="skeleton-block"></span></td>
            <td><span class="skeleton-block"></span></td>
            <td><span class="skeleton-block"></span></td>
            <td><span class="skeleton-block"></span></td>
            <td><span class="skeleton-block"></span></td>
            <td><span class="skeleton-block"></span></td>
            <td><span class="skeleton-block"></span></td>
            <td><span class="skeleton-block"></span></td>
          </tr>
        </ng-container>

        <tr class="table-clinico__empty-row" *ngIf="!cargando() && !error() && facturas().length === 0">
          <td class="table-clinico__empty-cell" colspan="8">
            <p class="table-clinico__empty-title">Sin facturas todavía</p>
            <p class="table-clinico__empty-text" *ngIf="puedeCrear">
              Crea la primera factura para empezar a facturar consultas, vacunas y productos.
            </p>
            <p class="table-clinico__empty-text" *ngIf="esDueno">
              Cuando la clínica emita y autorice una factura a tu nombre, aparecerá aquí.
            </p>
            <p class="table-clinico__empty-text" *ngIf="!puedeCrear && !esDueno">
              Cuando exista una factura relacionada contigo, aparecerá aquí.
            </p>
            <a routerLink="/facturas/nueva" class="btn btn--primary" *ngIf="puedeCrear">
              <app-icon name="anadir"></app-icon>
              Nueva factura
            </a>
          </td>
        </tr>

        <ng-container *ngIf="!cargando()">
          <tr *ngFor="let f of facturas()">
            <td data-label="Número">
              <a class="record-link" [routerLink]="['/facturas', f.id]" [attr.aria-label]="'Ver factura ' + (numero(f))">
                {{ numero(f) }}
              </a>
            </td>
            <td data-label="Fecha" class="data">{{ f.fechaEmision ?? '—' }}</td>
            <td data-label="Comprador">{{ f.compradorRazonSocial ?? 'Sin comprador asignado' }}</td>
            <td data-label="Mascota">{{ f.mascotaNombre ?? '—' }}</td>
            <td data-label="Total" class="data">{{ total(f) }}</td>
            <td data-label="Estado">
              <span class="chip" [ngClass]="chipClaseEstadoFactura(f.estado)">{{ etiquetaEstadoFactura(f.estado) }}</span>
            </td>
            <td data-label="SRI">
              <span *ngIf="f.estadoRecepcion as er; else sinSri" class="chip" [ngClass]="chipClaseEstadoRecepcion(er)">
                {{ etiquetaEstadoRecepcion(er) }}
              </span>
              <ng-template #sinSri>—</ng-template>
            </td>
            <td data-label="Autorización" class="data">{{ f.numeroAutorizacion ?? '—' }}</td>
          </tr>
        </ng-container>
      </tbody>
    </table>
  </div>

  <!-- ===== Paginación ===== -->
  <nav class="pagination" aria-label="Paginación de facturas" *ngIf="!cargando() && totalPaginas() > 0">
    <button type="button" class="btn btn--secondary btn--sm" (click)="irAPagina(pagina() - 1)" [disabled]="pagina() === 0">
      Anterior
    </button>
    <span class="pagination__status" aria-live="polite">
      Página {{ pagina() + 1 }} de {{ totalPaginas() }} ({{ totalElementos() }} facturas)
    </span>
    <button type="button" class="btn btn--secondary btn--sm" (click)="irAPagina(pagina() + 1)" [disabled]="pagina() + 1 >= totalPaginas()">
      Siguiente
    </button>
  </nav>
  `,
})
export class FacturasComponent implements OnInit {
  readonly etiquetaEstadoFactura = etiquetaEstadoFactura;
  readonly chipClaseEstadoFactura = chipClaseEstadoFactura;
  readonly etiquetaEstadoRecepcion = etiquetaEstadoRecepcion;
  readonly chipClaseEstadoRecepcion = chipClaseEstadoRecepcion;
  readonly filasSkeleton = Array.from({ length: FILAS_SKELETON });

  facturas = signal<Factura[]>([]);
  pagina = signal(0);
  totalPaginas = signal(0);
  totalElementos = signal(0);

  cargando = signal(false);
  error = signal('');

  mostrarFiltros = signal(false);
  filtrosActivos: FiltrosFacturas = {};

  duenios = signal<UsuarioSeleccionable[]>([]);
  cargandoDuenios = signal(false);
  private dueniosCargados = false;

  filtrosForm = this.fb.group({
    estado: [null as EstadoFactura | null],
    fechaEmision: [''],
    usuarioId: [null as number | null],
  });

  constructor(
    private api: FacturaApiService,
    private auth: AuthService,
    private problemDetail: ProblemDetailService,
    private usuarioSeleccionableApi: UsuarioSeleccionableApiService,
    private fb: FormBuilder
  ) {}

  ngOnInit(): void {
    this.cargar();
  }

  // ---------- Permisos (solo UX — la regla real vive en el backend) ----------

  /** POST /api/facturas: @PreAuthorize("hasAnyRole('ADMIN','AUXILIAR')"). */
  get puedeCrear(): boolean {
    const rol = this.auth.usuarioActual()?.rol;
    return rol === 'ROLE_ADMIN' || rol === 'ROLE_AUXILIAR';
  }

  get esAdmin(): boolean {
    return this.auth.usuarioActual()?.rol === 'ROLE_ADMIN';
  }

  get esDueno(): boolean {
    return this.auth.usuarioActual()?.rol === 'ROLE_DUENO';
  }

  /** Para DUENO/VETERINARIO el backend ignora estado/usuarioId: ofrecer ese filtro sería un control inerte. */
  get tieneFiltrosAvanzados(): boolean {
    return this.puedeCrear || this.esAdmin;
  }

  get descripcion(): string {
    if (this.esDueno) return 'Tus facturas autorizadas.';
    if (this.puedeCrear) return 'Borradores, emisión y seguimiento fiscal de las facturas de la clínica.';
    return 'Facturas con al menos una línea de una consulta o cita asignada a ti.';
  }

  numero(f: Factura): string {
    if (f.establecimiento && f.puntoEmision && f.secuencial != null) {
      return numeroComprobante(f.establecimiento, f.puntoEmision, f.secuencial);
    }
    return 'Borrador #' + f.id;
  }

  total(f: Factura): string {
    if (f.importeTotal == null) return '—';
    return `${f.importeTotal.toFixed(2)} ${f.moneda ?? 'USD'}`;
  }

  // ---------- Listado / paginación ----------

  cargar(): void {
    this.error.set('');
    this.cargando.set(true);
    this.api.listar(this.pagina(), TAMANIO_PAGINA, this.filtrosActivos).subscribe({
      next: (res) => {
        this.facturas.set(res.content ?? []);
        this.totalPaginas.set(res.totalPages ?? 0);
        this.totalElementos.set(res.totalElements ?? 0);
        this.cargando.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.facturas.set([]);
        this.cargando.set(false);
        this.error.set(this.problemDetail.mensaje(err));
      },
    });
  }

  irAPagina(nuevaPagina: number): void {
    if (nuevaPagina < 0 || nuevaPagina >= this.totalPaginas()) return;
    this.pagina.set(nuevaPagina);
    this.cargar();
  }

  // ---------- Filtros ----------

  alternarFiltros(): void {
    const nuevoValor = !this.mostrarFiltros();
    this.mostrarFiltros.set(nuevoValor);
    if (nuevoValor) this.cargarDuenios();
  }

  private cargarDuenios(forzar = false): void {
    if (this.dueniosCargados && !forzar) return;
    this.cargandoDuenios.set(true);
    this.usuarioSeleccionableApi.listarDuenios().subscribe({
      next: (res) => {
        this.duenios.set(res ?? []);
        this.dueniosCargados = true;
        this.cargandoDuenios.set(false);
      },
      error: () => this.cargandoDuenios.set(false),
    });
  }

  aplicarFiltros(): void {
    const v = this.filtrosForm.getRawValue();
    this.filtrosActivos = {
      estado: v.estado ?? undefined,
      fechaEmision: v.fechaEmision || undefined,
      usuarioId: v.usuarioId ?? undefined,
    };
    this.pagina.set(0);
    this.cargar();
  }

  limpiarFiltros(): void {
    this.filtrosForm.reset({ estado: null, fechaEmision: '', usuarioId: null });
    this.filtrosActivos = {};
    this.pagina.set(0);
    this.cargar();
  }
}
