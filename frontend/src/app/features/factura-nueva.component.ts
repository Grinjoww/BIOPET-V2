import { Component, OnInit, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { ProblemDetailService } from '../core/problem-detail.service';
import { UsuarioSeleccionable, UsuarioSeleccionableApiService } from '../core/usuario-seleccionable-api.service';
import { Mascota, MascotaApiService } from './mascota-api.service';
import {
  DetalleFacturaRequestPayload,
  Factura,
  FacturaApiService,
  FormaPagoSri,
  PagoFacturaRequestPayload,
} from './factura-api.service';
import {
  ConceptoFacturable,
  FacturacionConfigApiService,
} from './facturacion-config-api.service';
import { DatosFacturacion, DatosFacturacionApiService, DatosFacturacionRequestPayload } from './datos-facturacion-api.service';
import { PageHeaderComponent } from '../shared/page-header/page-header.component';
import { IconComponent } from '../shared/icons/icon.component';
import { etiquetaFormaPago } from './factura-presentacion';

const TAMANIO_PAGINA_SELECTOR_MASCOTAS = 200;

/** Una línea todavía sin guardar en el backend, solo mientras el usuario arma el borrador en esta pantalla. */
interface LineaEnEdicion {
  concepto: ConceptoFacturable;
  cantidad: number;
  descuento: number;
}

/** Un pago todavía sin guardar en el backend. */
interface PagoEnEdicion {
  formaPago: FormaPagoSri;
  total: number;
}

/**
 * /facturas/nueva — solo ADMIN/AUXILIAR (roleGuard en app.routes.ts).
 *
 * Flujo real contra el backend (Fase 4-8A): NO existe un único POST que
 * cree una factura completa. Cada paso de este formulario llama al endpoint
 * que realmente existe, EN ORDEN, sobre el borrador ya creado:
 *
 *   1. POST /api/facturas                    -> abre el BORRADOR
 *   2. POST /api/facturas/{id}/comprador      -> fija el comprador
 *   3. PUT  /api/facturas/{id}/detalles       -> sustituye TODAS las líneas
 *   4. PUT  /api/facturas/{id}/pagos          -> sustituye TODOS los pagos
 *
 * Ningún id se escribe a mano: dueño, mascota, datos de facturación y
 * conceptos se cargan de catálogos reales (GET /api/usuarios/duenios,
 * GET /api/mascotas, GET /api/usuarios/{id}/datos-facturacion,
 * GET /api/facturacion/conceptos?activo=true). El precio/impuesto que se ve
 * aquí es SOLO vista previa: el backend recalcula todo desde el catálogo y
 * la tarifa vigente al guardar (FacturaCalculador), así que nunca se envía
 * un precio ni un valor de impuesto calculado en el cliente.
 *
 * Emitir/firmar/enviar al SRI NO ocurre aquí: una vez creado el borrador con
 * comprador, líneas y pagos, el flujo continúa en /facturas/:id (sección de
 * acciones fiscales), que es donde vive esa responsabilidad.
 */
@Component({
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, FormsModule, RouterLink, PageHeaderComponent, IconComponent],
  template: `
  <app-page-header
    eyebrow="Facturación"
    [breadcrumb]="[{ label: 'Facturas', path: '/facturas' }, { label: 'Nueva factura' }]"
    title="Nueva factura"
    [hasActions]="false">
  </app-page-header>

  <p class="alert alert--danger" role="alert" aria-live="assertive" *ngIf="error()">
    <strong>Error:</strong> {{ error() }}
  </p>
  <p class="alert alert--success" role="status" aria-live="polite" *ngIf="mensajeExito()">
    <strong>Listo:</strong> {{ mensajeExito() }}
  </p>

  <!-- ===== Paso 1: Datos generales ===== -->
  <section class="panel panel--record" aria-labelledby="paso1-titulo">
    <div class="panel__title">
      <h2 id="paso1-titulo">1. Datos generales</h2>
    </div>

    <form [formGroup]="datosGeneralesForm" (ngSubmit)="crearBorrador()" novalidate *ngIf="!factura()">
      <div class="field">
        <label for="f-usuarioId">Dueño<span class="required-mark" aria-hidden="true">*</span></label>
        <select id="f-usuarioId" formControlName="usuarioId" [disabled]="cargandoDuenios()" (change)="onDuenioSeleccionado()">
          <option [ngValue]="null" disabled>
            {{ cargandoDuenios() ? 'Cargando dueños…' : 'Selecciona un dueño' }}
          </option>
          <option *ngFor="let d of duenios()" [ngValue]="d.id">{{ d.nombre }} — {{ d.email }}</option>
        </select>
        <p class="field-error" *ngIf="tieneErrorGeneral('usuarioId')">{{ mensajeErrorGeneral('usuarioId') }}</p>
      </div>

      <div class="field">
        <label for="f-mascotaId">Mascota (opcional)</label>
        <select id="f-mascotaId" formControlName="mascotaId" [disabled]="cargandoMascotas() || mascotasDelDuenio().length === 0">
          <option [ngValue]="null">Sin mascota asociada</option>
          <option *ngFor="let m of mascotasDelDuenio()" [ngValue]="m.id">{{ m.nombre }} — {{ m.especie }}</option>
        </select>
        <p class="field-hint" *ngIf="!cargandoMascotas() && datosGeneralesForm.value.usuarioId && mascotasDelDuenio().length === 0">
          Este dueño no tiene mascotas registradas todavía.
        </p>
      </div>

      <div class="field">
        <label for="f-fechaEmision">Fecha de emisión<span class="required-mark" aria-hidden="true">*</span></label>
        <input id="f-fechaEmision" type="date" formControlName="fechaEmision" />
        <p class="field-error" *ngIf="tieneErrorGeneral('fechaEmision')">{{ mensajeErrorGeneral('fechaEmision') }}</p>
      </div>

      <button type="submit" class="btn btn--primary" [disabled]="creandoBorrador()">
        {{ creandoBorrador() ? 'Creando borrador…' : 'Crear borrador' }}
      </button>
    </form>

    <dl class="record-meta-grid" *ngIf="factura() as f">
      <div>
        <dt>Dueño</dt>
        <dd>{{ duenioSeleccionadoNombre }}</dd>
      </div>
      <div>
        <dt>Mascota</dt>
        <dd>{{ f.mascotaNombre ?? 'Sin mascota asociada' }}</dd>
      </div>
      <div>
        <dt>Fecha de emisión</dt>
        <dd class="data">{{ f.fechaEmision }}</dd>
      </div>
    </dl>
  </section>

  <ng-container *ngIf="factura() as f">
    <!-- ===== Paso 2: Comprador ===== -->
    <section class="panel panel--record" aria-labelledby="paso2-titulo">
      <div class="panel__title"><h2 id="paso2-titulo">2. Comprador</h2></div>

      <p class="field-hint" *ngIf="f.compradorRazonSocial">
        Comprador actual: <strong>{{ f.compradorRazonSocial }}</strong> ({{ f.compradorTipoIdentificacion }} {{ f.compradorIdentificacion }})
      </p>

      <span class="sr-only" role="status" aria-live="polite" *ngIf="cargandoDatosFacturacion()">Cargando datos de facturación…</span>
      <div class="table-wrap" *ngIf="!cargandoDatosFacturacion() && datosFacturacion().length > 0">
        <table class="table-clinico">
          <caption class="sr-only">Identidades tributarias del dueño seleccionado</caption>
          <thead>
            <tr><th scope="col">Identificación</th><th scope="col">Razón social</th><th scope="col">Predeterminado</th><th scope="col"><span class="sr-only">Acción</span></th></tr>
          </thead>
          <tbody>
            <tr *ngFor="let d of datosFacturacion()">
              <td data-label="Identificación">{{ d.tipoIdentificacion }} · {{ d.identificacion }}</td>
              <td data-label="Razón social">{{ d.razonSocial }}</td>
              <td data-label="Predeterminado">{{ d.predeterminado ? 'Sí' : '—' }}</td>
              <td data-label="" class="actions">
                <button type="button" class="btn btn--secondary btn--sm" [disabled]="guardandoComprador()" (click)="seleccionarComprador(d)">
                  Usar estos datos
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <p class="field-hint" *ngIf="!cargandoDatosFacturacion() && datosFacturacion().length === 0">
        Este dueño todavía no tiene datos de facturación registrados.
      </p>

      <button type="button" class="btn btn--ghost btn--sm" (click)="mostrarNuevoComprador.set(!mostrarNuevoComprador())">
        {{ mostrarNuevoComprador() ? 'Cancelar' : '+ Registrar nuevos datos de facturación' }}
      </button>

      <form [formGroup]="compradorForm" (ngSubmit)="crearYSeleccionarComprador()" novalidate *ngIf="mostrarNuevoComprador()">
        <div class="field">
          <label for="c-tipo">Tipo de identificación<span class="required-mark" aria-hidden="true">*</span></label>
          <select id="c-tipo" formControlName="tipoIdentificacion">
            <option value="CEDULA">Cédula</option>
            <option value="RUC">RUC</option>
            <option value="PASAPORTE">Pasaporte</option>
            <option value="CONSUMIDOR_FINAL">Consumidor final</option>
            <option value="IDENTIFICACION_EXTERIOR">Identificación del exterior</option>
          </select>
        </div>
        <div class="field">
          <label for="c-identificacion">Identificación<span class="required-mark" aria-hidden="true">*</span></label>
          <input id="c-identificacion" type="text" formControlName="identificacion" />
        </div>
        <div class="field">
          <label for="c-razonSocial">Razón social / Nombres<span class="required-mark" aria-hidden="true">*</span></label>
          <input id="c-razonSocial" type="text" formControlName="razonSocial" />
        </div>
        <div class="field">
          <label for="c-direccion">Dirección</label>
          <input id="c-direccion" type="text" formControlName="direccion" />
        </div>
        <div class="field">
          <label for="c-email">Email de facturación</label>
          <input id="c-email" type="email" formControlName="emailFacturacion" />
        </div>
        <div class="field">
          <label for="c-telefono">Teléfono</label>
          <input id="c-telefono" type="text" formControlName="telefono" />
        </div>
        <button type="submit" class="btn btn--primary" [disabled]="guardandoComprador() || compradorForm.invalid">
          {{ guardandoComprador() ? 'Guardando…' : 'Crear y usar estos datos' }}
        </button>
      </form>
    </section>

    <!-- ===== Paso 3: Conceptos ===== -->
    <section class="panel panel--record" aria-labelledby="paso3-titulo">
      <div class="panel__title"><h2 id="paso3-titulo">3. Conceptos</h2></div>

      <div class="table-wrap" *ngIf="f.detalles.length > 0">
        <table class="table-clinico">
          <caption class="sr-only">Líneas ya guardadas en el borrador</caption>
          <thead><tr><th scope="col">Concepto</th><th scope="col">Cantidad</th><th scope="col">Subtotal</th></tr></thead>
          <tbody>
            <tr *ngFor="let d of f.detalles">
              <td data-label="Concepto">{{ d.descripcion }}</td>
              <td data-label="Cantidad" class="data">{{ d.cantidad }}</td>
              <td data-label="Subtotal" class="data">{{ d.precioTotalSinImpuesto.toFixed(2) }}</td>
            </tr>
          </tbody>
        </table>
      </div>

      <span class="label form-section-label">Agregar línea</span>
      <div class="field">
        <label for="l-concepto">Concepto</label>
        <select id="l-concepto" [(ngModel)]="conceptoSeleccionado" [ngModelOptions]="{ standalone: true }" [disabled]="cargandoConceptos()">
          <option [ngValue]="null" disabled>{{ cargandoConceptos() ? 'Cargando conceptos…' : 'Selecciona un concepto' }}</option>
          <option *ngFor="let c of conceptos()" [ngValue]="c">{{ c.codigo }} — {{ c.descripcion }} ({{ c.precioUnitario.toFixed(2) }} · {{ c.codigoImpuesto }})</option>
        </select>
      </div>
      <div class="field">
        <label for="l-cantidad">Cantidad</label>
        <input id="l-cantidad" type="number" min="0.000001" step="0.000001" [(ngModel)]="cantidadNueva" [ngModelOptions]="{ standalone: true }" />
      </div>
      <button type="button" class="btn btn--secondary btn--sm" [disabled]="!conceptoSeleccionado || !cantidadNueva || cantidadNueva <= 0" (click)="agregarLinea()">
        <app-icon name="anadir" [size]="16"></app-icon>
        Agregar línea
      </button>

      <div class="table-wrap" *ngIf="lineasPendientes().length > 0" style="margin-top: var(--space-4);">
        <table class="table-clinico">
          <caption class="sr-only">Líneas pendientes de guardar</caption>
          <thead><tr><th scope="col">Concepto</th><th scope="col">Cantidad</th><th scope="col">Subtotal estimado</th><th scope="col"><span class="sr-only">Quitar</span></th></tr></thead>
          <tbody>
            <tr *ngFor="let l of lineasPendientes(); let i = index">
              <td data-label="Concepto">{{ l.concepto.descripcion }}</td>
              <td data-label="Cantidad" class="data">{{ l.cantidad }}</td>
              <td data-label="Subtotal estimado" class="data">{{ (l.concepto.precioUnitario * l.cantidad).toFixed(2) }}</td>
              <td data-label="" class="actions">
                <button type="button" class="btn-icon btn-icon--danger" (click)="quitarLinea(i)" aria-label="Quitar línea">
                  <app-icon name="eliminar" [size]="16"></app-icon>
                </button>
              </td>
            </tr>
          </tbody>
        </table>
        <p class="field-hint">
          Subtotal estimado, sin impuestos. El backend calcula el impuesto y el total definitivo con la tarifa
          vigente en la fecha de emisión al guardar las líneas.
        </p>
      </div>

      <div class="modal-panel__actions">
        <button type="button" class="btn btn--primary" [disabled]="lineasPendientes().length === 0 || guardandoDetalles()" (click)="guardarDetalles()">
          {{ guardandoDetalles() ? 'Guardando…' : 'Guardar líneas' }}
        </button>
      </div>
    </section>

    <!-- ===== Paso 4: Pagos ===== -->
    <section class="panel panel--record" aria-labelledby="paso4-titulo">
      <div class="panel__title"><h2 id="paso4-titulo">4. Pagos</h2></div>

      <div class="table-wrap" *ngIf="f.pagos.length > 0">
        <table class="table-clinico">
          <caption class="sr-only">Pagos ya guardados en el borrador</caption>
          <thead><tr><th scope="col">Forma de pago</th><th scope="col">Valor</th></tr></thead>
          <tbody>
            <tr *ngFor="let p of f.pagos">
              <td data-label="Forma de pago">{{ etiquetaFormaPago(p.formaPago) }}</td>
              <td data-label="Valor" class="data">{{ p.total.toFixed(2) }}</td>
            </tr>
          </tbody>
        </table>
      </div>

      <span class="label form-section-label">Agregar pago</span>
      <div class="field">
        <label for="p-forma">Forma de pago</label>
        <select id="p-forma" [(ngModel)]="formaPagoNueva" [ngModelOptions]="{ standalone: true }">
          <option [ngValue]="null" disabled>Selecciona una forma de pago</option>
          <option value="SIN_UTILIZACION_SISTEMA_FINANCIERO">Sin utilización del sistema financiero</option>
          <option value="TARJETA_DEBITO">Tarjeta de débito</option>
          <option value="TARJETA_CREDITO">Tarjeta de crédito</option>
          <option value="DINERO_ELECTRONICO">Dinero electrónico</option>
          <option value="TARJETA_PREPAGO">Tarjeta prepago</option>
          <option value="COMPENSACION_DEUDAS">Compensación de deudas</option>
          <option value="OTROS_CON_SISTEMA_FINANCIERO">Otros con utilización del sistema financiero</option>
          <option value="ENDOSO_TITULOS">Endoso de títulos</option>
        </select>
      </div>
      <div class="field">
        <label for="p-total">Valor</label>
        <input id="p-total" type="number" min="0.01" step="0.01" [(ngModel)]="totalNuevo" [ngModelOptions]="{ standalone: true }" />
      </div>
      <button type="button" class="btn btn--secondary btn--sm" [disabled]="!formaPagoNueva || !totalNuevo || totalNuevo <= 0" (click)="agregarPago()">
        <app-icon name="anadir" [size]="16"></app-icon>
        Agregar pago
      </button>

      <div class="table-wrap" *ngIf="pagosPendientes().length > 0" style="margin-top: var(--space-4);">
        <table class="table-clinico">
          <caption class="sr-only">Pagos pendientes de guardar</caption>
          <thead><tr><th scope="col">Forma de pago</th><th scope="col">Valor</th><th scope="col"><span class="sr-only">Quitar</span></th></tr></thead>
          <tbody>
            <tr *ngFor="let p of pagosPendientes(); let i = index">
              <td data-label="Forma de pago">{{ etiquetaFormaPago(p.formaPago) }}</td>
              <td data-label="Valor" class="data">{{ p.total.toFixed(2) }}</td>
              <td data-label="" class="actions">
                <button type="button" class="btn-icon btn-icon--danger" (click)="quitarPago(i)" aria-label="Quitar pago">
                  <app-icon name="eliminar" [size]="16"></app-icon>
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <div class="modal-panel__actions">
        <button type="button" class="btn btn--primary" [disabled]="pagosPendientes().length === 0 || guardandoPagos()" (click)="guardarPagos()">
          {{ guardandoPagos() ? 'Guardando…' : 'Guardar pagos' }}
        </button>
      </div>
    </section>

    <!-- ===== Paso 5: Revisión ===== -->
    <section class="panel profile-panel" aria-labelledby="paso5-titulo">
      <div class="panel__title"><h2 id="paso5-titulo">5. Revisión</h2></div>
      <dl class="profile-info">
        <div class="profile-info__row"><dt class="label">Subtotal</dt><dd class="data">{{ f.totalSinImpuestos?.toFixed(2) ?? '—' }}</dd></div>
        <div class="profile-info__row"><dt class="label">Impuestos</dt><dd class="data">{{ f.totalImpuestos?.toFixed(2) ?? '—' }}</dd></div>
        <div class="profile-info__row"><dt class="label"><strong>Total</strong></dt><dd class="data"><strong>{{ f.importeTotal?.toFixed(2) ?? '—' }} {{ f.moneda }}</strong></dd></div>
      </dl>
      <a [routerLink]="['/facturas', f.id]" class="btn btn--primary" style="margin-top: var(--space-4);">
        Ir a la factura
      </a>
    </section>
  </ng-container>
  `,
})
export class FacturaNuevaComponent implements OnInit {
  readonly etiquetaFormaPago = etiquetaFormaPago;

  error = signal('');
  mensajeExito = signal('');

  factura = signal<Factura | null>(null);
  creandoBorrador = signal(false);

  duenios = signal<UsuarioSeleccionable[]>([]);
  cargandoDuenios = signal(false);
  duenioSeleccionadoNombre = '';

  todasLasMascotas: Mascota[] = [];
  mascotasDelDuenio = signal<Mascota[]>([]);
  cargandoMascotas = signal(false);

  datosGeneralesForm = this.fb.group({
    usuarioId: [null as number | null, [Validators.required, Validators.min(1)]],
    mascotaId: [null as number | null],
    fechaEmision: [this.hoyIso(), [Validators.required]],
  });

  // ---------- Comprador ----------
  datosFacturacion = signal<DatosFacturacion[]>([]);
  cargandoDatosFacturacion = signal(false);
  guardandoComprador = signal(false);
  mostrarNuevoComprador = signal(false);
  compradorForm = this.fb.group({
    tipoIdentificacion: ['CEDULA', [Validators.required]],
    identificacion: ['', [Validators.required, Validators.maxLength(20)]],
    razonSocial: ['', [Validators.required, Validators.maxLength(300)]],
    direccion: [''],
    telefono: [''],
    emailFacturacion: ['', [Validators.email]],
  });

  // ---------- Conceptos ----------
  conceptos = signal<ConceptoFacturable[]>([]);
  cargandoConceptos = signal(false);
  conceptoSeleccionado: ConceptoFacturable | null = null;
  cantidadNueva: number | null = 1;
  lineasPendientes = signal<LineaEnEdicion[]>([]);
  guardandoDetalles = signal(false);

  // ---------- Pagos ----------
  formaPagoNueva: FormaPagoSri | null = null;
  totalNuevo: number | null = null;
  pagosPendientes = signal<PagoEnEdicion[]>([]);
  guardandoPagos = signal(false);

  constructor(
    private fb: FormBuilder,
    private facturaApi: FacturaApiService,
    private mascotaApi: MascotaApiService,
    private usuarioSeleccionableApi: UsuarioSeleccionableApiService,
    private datosFacturacionApi: DatosFacturacionApiService,
    private configApi: FacturacionConfigApiService,
    private problemDetail: ProblemDetailService
  ) {}

  ngOnInit(): void {
    this.cargarDuenios();
    this.cargarMascotas();
    this.cargarConceptos();
  }

  private hoyIso(): string {
    return new Date().toISOString().slice(0, 10);
  }

  // ---------- Paso 1 ----------

  private cargarDuenios(): void {
    this.cargandoDuenios.set(true);
    this.usuarioSeleccionableApi.listarDuenios().subscribe({
      next: (res) => {
        this.duenios.set(res ?? []);
        this.cargandoDuenios.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.cargandoDuenios.set(false);
        this.error.set(this.problemDetail.mensaje(err));
      },
    });
  }

  private cargarMascotas(): void {
    this.cargandoMascotas.set(true);
    this.mascotaApi.listar(0, TAMANIO_PAGINA_SELECTOR_MASCOTAS, 'nombre,asc').subscribe({
      next: (res) => {
        this.todasLasMascotas = res.content ?? [];
        this.cargandoMascotas.set(false);
      },
      error: () => this.cargandoMascotas.set(false),
    });
  }

  private cargarConceptos(): void {
    this.cargandoConceptos.set(true);
    this.configApi.listarConceptos(true).subscribe({
      next: (res) => {
        this.conceptos.set(res ?? []);
        this.cargandoConceptos.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.cargandoConceptos.set(false);
        this.error.set(this.problemDetail.mensaje(err));
      },
    });
  }

  onDuenioSeleccionado(): void {
    const id = this.datosGeneralesForm.value.usuarioId;
    this.mascotasDelDuenio.set(id ? this.todasLasMascotas.filter((m) => m.duenioId === id) : []);
    this.datosGeneralesForm.patchValue({ mascotaId: null });
  }

  tieneErrorGeneral(campo: string): boolean {
    const control = this.datosGeneralesForm.get(campo);
    return !!control && control.invalid && (control.touched || control.dirty);
  }

  mensajeErrorGeneral(campo: string): string {
    const control = this.datosGeneralesForm.get(campo);
    if (control?.hasError('required') || control?.hasError('min')) return 'Este campo es obligatorio.';
    return 'Valor inválido.';
  }

  crearBorrador(): void {
    this.error.set('');
    if (this.datosGeneralesForm.invalid) {
      this.datosGeneralesForm.markAllAsTouched();
      return;
    }
    const v = this.datosGeneralesForm.getRawValue();
    this.duenioSeleccionadoNombre = this.duenios().find((d) => d.id === v.usuarioId)?.nombre ?? '';

    this.creandoBorrador.set(true);
    this.facturaApi
      .crear({ usuarioId: v.usuarioId as number, mascotaId: v.mascotaId, fechaEmision: v.fechaEmision as string })
      .subscribe({
        next: (f) => {
          this.creandoBorrador.set(false);
          this.factura.set(f);
          this.mostrarExito('Borrador creado. Continúa con el comprador.');
          this.cargarDatosFacturacion(v.usuarioId as number);
        },
        error: (err: HttpErrorResponse) => {
          this.creandoBorrador.set(false);
          this.error.set(this.problemDetail.mensaje(err));
        },
      });
  }

  // ---------- Paso 2: Comprador ----------

  private cargarDatosFacturacion(usuarioId: number): void {
    this.cargandoDatosFacturacion.set(true);
    this.datosFacturacionApi.listar(usuarioId).subscribe({
      next: (res) => {
        this.datosFacturacion.set(res ?? []);
        this.cargandoDatosFacturacion.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.cargandoDatosFacturacion.set(false);
        this.error.set(this.problemDetail.mensaje(err));
      },
    });
  }

  seleccionarComprador(datos: DatosFacturacion): void {
    const f = this.factura();
    if (!f) return;
    this.error.set('');
    this.guardandoComprador.set(true);
    this.facturaApi.seleccionarComprador(f.id, datos.id).subscribe({
      next: (actualizada) => {
        this.guardandoComprador.set(false);
        this.factura.set(actualizada);
        this.mostrarExito('Comprador asignado.');
      },
      error: (err: HttpErrorResponse) => {
        this.guardandoComprador.set(false);
        this.error.set(this.problemDetail.mensaje(err));
      },
    });
  }

  crearYSeleccionarComprador(): void {
    const f = this.factura();
    const usuarioId = this.datosGeneralesForm.value.usuarioId;
    if (!f || !usuarioId || this.compradorForm.invalid) {
      this.compradorForm.markAllAsTouched();
      return;
    }
    const v = this.compradorForm.getRawValue();
    const payload: DatosFacturacionRequestPayload = {
      tipoIdentificacion: v.tipoIdentificacion as DatosFacturacionRequestPayload['tipoIdentificacion'],
      identificacion: v.identificacion as string,
      razonSocial: v.razonSocial as string,
      direccion: v.direccion || null,
      telefono: v.telefono || null,
      emailFacturacion: v.emailFacturacion || null,
    };

    this.error.set('');
    this.guardandoComprador.set(true);
    this.datosFacturacionApi.crear(usuarioId, payload).subscribe({
      next: (datos) => {
        this.datosFacturacion.set([...this.datosFacturacion(), datos]);
        this.mostrarNuevoComprador.set(false);
        this.compradorForm.reset({ tipoIdentificacion: 'CEDULA', identificacion: '', razonSocial: '', direccion: '', telefono: '', emailFacturacion: '' });
        this.seleccionarComprador(datos);
      },
      error: (err: HttpErrorResponse) => {
        this.guardandoComprador.set(false);
        this.error.set(this.problemDetail.mensaje(err));
      },
    });
  }

  // ---------- Paso 3: Conceptos ----------

  agregarLinea(): void {
    if (!this.conceptoSeleccionado || !this.cantidadNueva || this.cantidadNueva <= 0) return;
    this.lineasPendientes.set([...this.lineasPendientes(), { concepto: this.conceptoSeleccionado, cantidad: this.cantidadNueva, descuento: 0 }]);
    this.conceptoSeleccionado = null;
    this.cantidadNueva = 1;
  }

  quitarLinea(indice: number): void {
    this.lineasPendientes.set(this.lineasPendientes().filter((_, i) => i !== indice));
  }

  guardarDetalles(): void {
    const f = this.factura();
    if (!f || this.lineasPendientes().length === 0) return;

    const payload: DetalleFacturaRequestPayload[] = this.lineasPendientes().map((l) => ({
      conceptoFacturableId: l.concepto.id,
      cantidad: l.cantidad,
      descuento: l.descuento || null,
      origenTipo: null,
      origenId: null,
    }));

    this.error.set('');
    this.guardandoDetalles.set(true);
    this.facturaApi.reemplazarDetalles(f.id, payload).subscribe({
      next: (actualizada) => {
        this.guardandoDetalles.set(false);
        this.factura.set(actualizada);
        this.lineasPendientes.set([]);
        this.mostrarExito('Líneas guardadas. El backend ya calculó el impuesto y el subtotal real.');
      },
      error: (err: HttpErrorResponse) => {
        this.guardandoDetalles.set(false);
        this.error.set(this.problemDetail.mensaje(err));
      },
    });
  }

  // ---------- Paso 4: Pagos ----------

  agregarPago(): void {
    if (!this.formaPagoNueva || !this.totalNuevo || this.totalNuevo <= 0) return;
    this.pagosPendientes.set([...this.pagosPendientes(), { formaPago: this.formaPagoNueva, total: this.totalNuevo }]);
    this.formaPagoNueva = null;
    this.totalNuevo = null;
  }

  quitarPago(indice: number): void {
    this.pagosPendientes.set(this.pagosPendientes().filter((_, i) => i !== indice));
  }

  guardarPagos(): void {
    const f = this.factura();
    if (!f || this.pagosPendientes().length === 0) return;

    const payload: PagoFacturaRequestPayload[] = this.pagosPendientes().map((p) => ({
      formaPago: p.formaPago,
      total: p.total,
      plazo: null,
      unidadTiempo: null,
    }));

    this.error.set('');
    this.guardandoPagos.set(true);
    this.facturaApi.reemplazarPagos(f.id, payload).subscribe({
      next: (actualizada) => {
        this.guardandoPagos.set(false);
        this.factura.set(actualizada);
        this.pagosPendientes.set([]);
        this.mostrarExito('Pagos guardados.');
      },
      error: (err: HttpErrorResponse) => {
        this.guardandoPagos.set(false);
        this.error.set(this.problemDetail.mensaje(err));
      },
    });
  }

  private mostrarExito(mensaje: string): void {
    this.mensajeExito.set(mensaje);
    setTimeout(() => {
      if (this.mensajeExito() === mensaje) this.mensajeExito.set('');
    }, 4000);
  }
}
