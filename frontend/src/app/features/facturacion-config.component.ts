import { Component, OnInit, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';

import { ProblemDetailService } from '../core/problem-detail.service';
import {
  ConceptoFacturable,
  EmisorFiscal,
  FacturacionConfigApiService,
  PuntoEmision,
  TarifaImpuesto,
  TipoConceptoFacturable,
} from './facturacion-config-api.service';
import { CodigoImpuestoSri } from './factura-api.service';
import { PageHeaderComponent } from '../shared/page-header/page-header.component';
import { IconComponent } from '../shared/icons/icon.component';

type Tab = 'emisor' | 'puntos' | 'conceptos' | 'tarifas';

/**
 * /facturas/configuracion — SOLO ADMIN (roleGuard en app.routes.ts): la
 * pantalla entera asume que quien la ve puede escribir en los cuatro
 * catálogos (ConceptoFacturableController/PuntoEmisionController/
 * TarifaImpuestoController/EmisorFiscalController, todos con
 * @PreAuthorize("hasRole('ADMIN')") en sus escrituras — AUXILIAR solo tiene
 * lectura de catálogos operativos y ya la consume desde /facturas/nueva, no
 * desde aquí).
 *
 * Ninguna de las cuatro pestañas permite editar ni mostrar un secuencial:
 * PuntoEmisionRequest no tiene ese campo (el backend lo provisiona solo, en
 * el ambiente del servidor, al crear el punto — ver PuntoEmisionService). Ni
 * TarifaImpuesto expone una edición histórica: "Nueva vigencia" siempre crea
 * una fila, nunca sobrescribe una ya cerrada.
 */
@Component({
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, FormsModule, PageHeaderComponent, IconComponent],
  template: `
  <app-page-header eyebrow="Facturación" title="Configuración fiscal" [hasActions]="false"></app-page-header>

  <p class="alert alert--danger" role="alert" aria-live="assertive" *ngIf="error()">
    <strong>Error:</strong> {{ error() }}
  </p>
  <p class="alert alert--success" role="status" aria-live="polite" *ngIf="mensajeExito()">
    <strong>Listo:</strong> {{ mensajeExito() }}
  </p>

  <div class="tabs">
    <div class="tabs__list" role="tablist" aria-label="Secciones de configuración fiscal">
      <button type="button" role="tab" id="tab-emisor" class="tab-btn" [attr.aria-selected]="tab() === 'emisor'" aria-controls="panel-emisor" (click)="tab.set('emisor')">Emisor</button>
      <button type="button" role="tab" id="tab-puntos" class="tab-btn" [attr.aria-selected]="tab() === 'puntos'" aria-controls="panel-puntos" (click)="tab.set('puntos')">Puntos de emisión</button>
      <button type="button" role="tab" id="tab-conceptos" class="tab-btn" [attr.aria-selected]="tab() === 'conceptos'" aria-controls="panel-conceptos" (click)="tab.set('conceptos')">Conceptos</button>
      <button type="button" role="tab" id="tab-tarifas" class="tab-btn" [attr.aria-selected]="tab() === 'tarifas'" aria-controls="panel-tarifas" (click)="tab.set('tarifas')">Tarifas</button>
    </div>

    <!-- ===== Emisor ===== -->
    <div id="panel-emisor" class="tab-panel" role="tabpanel" aria-labelledby="tab-emisor" *ngIf="tab() === 'emisor'">
      <span class="sr-only" role="status" aria-live="polite" *ngIf="cargandoEmisor()">Cargando emisor…</span>

      <div class="unavailable-card" *ngIf="!cargandoEmisor() && !emisorConfigurado() && !mostrarFormEmisor()">
        <p class="unavailable-card__title">Todavía no se ha configurado el emisor fiscal</p>
        <p class="unavailable-card__note">Es necesario para emitir cualquier factura (RUC, razón social y dirección matriz).</p>
        <button type="button" class="btn btn--primary" style="margin-top: var(--space-4);" (click)="abrirFormEmisor()">Configurar emisor</button>
      </div>

      <div class="panel" *ngIf="!cargandoEmisor() && emisorConfigurado() && !mostrarFormEmisor()">
        <dl class="record-meta-grid">
          <div><dt>RUC</dt><dd class="data">{{ emisor()!.ruc }}</dd></div>
          <div><dt>Razón social</dt><dd>{{ emisor()!.razonSocial }}</dd></div>
          <div><dt>Nombre comercial</dt><dd>{{ emisor()!.nombreComercial ?? '—' }}</dd></div>
          <div><dt>Dirección matriz</dt><dd>{{ emisor()!.direccionMatriz }}</dd></div>
          <div><dt>Obligado a llevar contabilidad</dt><dd>{{ emisor()!.obligadoContabilidad ? 'Sí' : 'No' }}</dd></div>
          <div><dt>RIMPE</dt><dd>{{ emisor()!.rimpe ? 'Sí' : 'No' }}</dd></div>
          <div><dt>Estado</dt><dd><span class="chip" [ngClass]="emisor()!.activo ? 'chip--success' : 'chip--neutral'">{{ emisor()!.activo ? 'Activo' : 'Inactivo' }}</span></dd></div>
        </dl>
        <button type="button" class="btn btn--secondary" style="margin-top: var(--space-4);" (click)="abrirFormEmisor()">Editar</button>
      </div>

      <form [formGroup]="emisorForm" (ngSubmit)="guardarEmisor()" novalidate *ngIf="mostrarFormEmisor()">
        <div class="field"><label for="e-ruc">RUC<span class="required-mark" aria-hidden="true">*</span></label><input id="e-ruc" type="text" formControlName="ruc" maxlength="13" /></div>
        <div class="field"><label for="e-razonSocial">Razón social<span class="required-mark" aria-hidden="true">*</span></label><input id="e-razonSocial" type="text" formControlName="razonSocial" /></div>
        <div class="field"><label for="e-nombreComercial">Nombre comercial</label><input id="e-nombreComercial" type="text" formControlName="nombreComercial" /></div>
        <div class="field"><label for="e-direccion">Dirección matriz<span class="required-mark" aria-hidden="true">*</span></label><input id="e-direccion" type="text" formControlName="direccionMatriz" /></div>
        <div class="field"><label><input type="checkbox" formControlName="obligadoContabilidad" /> Obligado a llevar contabilidad</label></div>
        <div class="field"><label><input type="checkbox" formControlName="rimpe" /> Régimen RIMPE</label></div>
        <div class="field"><label for="e-contribuyente">Resolución de contribuyente especial</label><input id="e-contribuyente" type="text" formControlName="contribuyenteEspecial" /></div>
        <div class="field"><label for="e-agente">Resolución de agente de retención</label><input id="e-agente" type="text" formControlName="agenteRetencionResolucion" /></div>
        <div class="field"><label><input type="checkbox" formControlName="activo" /> Activo</label></div>
        <div class="modal-panel__actions">
          <button type="submit" class="btn btn--primary" [disabled]="guardandoEmisor()">{{ guardandoEmisor() ? 'Guardando…' : 'Guardar' }}</button>
          <button type="button" class="btn btn--secondary" (click)="mostrarFormEmisor.set(false)" [disabled]="guardandoEmisor()">Cancelar</button>
        </div>
      </form>
    </div>

    <!-- ===== Puntos de emisión ===== -->
    <div id="panel-puntos" class="tab-panel" role="tabpanel" aria-labelledby="tab-puntos" *ngIf="tab() === 'puntos'">
      <button type="button" class="btn btn--primary" (click)="abrirCrearPunto()" *ngIf="!mostrarFormPunto()">
        <app-icon name="anadir"></app-icon>
        Nuevo punto de emisión
      </button>

      <form [formGroup]="puntoForm" (ngSubmit)="crearPunto()" novalidate *ngIf="mostrarFormPunto()">
        <p class="field-hint" *ngIf="!emisorConfigurado()">Configura primero el emisor fiscal en la pestaña «Emisor».</p>
        <div class="field"><label for="pe-establecimiento">Establecimiento (3 dígitos)<span class="required-mark" aria-hidden="true">*</span></label><input id="pe-establecimiento" type="text" formControlName="establecimiento" maxlength="3" placeholder="001" /></div>
        <div class="field"><label for="pe-punto">Punto de emisión (3 dígitos)<span class="required-mark" aria-hidden="true">*</span></label><input id="pe-punto" type="text" formControlName="puntoEmision" maxlength="3" placeholder="001" /></div>
        <div class="field"><label for="pe-direccion">Dirección del establecimiento</label><input id="pe-direccion" type="text" formControlName="direccionEstablecimiento" /></div>
        <div class="modal-panel__actions">
          <button type="submit" class="btn btn--primary" [disabled]="guardandoPunto() || !emisorConfigurado()">{{ guardandoPunto() ? 'Creando…' : 'Crear' }}</button>
          <button type="button" class="btn btn--secondary" (click)="mostrarFormPunto.set(false)" [disabled]="guardandoPunto()">Cancelar</button>
        </div>
      </form>

      <div class="table-wrap" style="margin-top: var(--space-4);">
        <table class="table-clinico">
          <caption class="sr-only">Puntos de emisión</caption>
          <thead><tr><th scope="col">Serie</th><th scope="col">Dirección</th><th scope="col">Estado</th><th scope="col"><span class="sr-only">Acciones</span></th></tr></thead>
          <tbody>
            <tr class="table-clinico__empty-row" *ngIf="!cargandoPuntos() && puntosEmision().length === 0">
              <td class="table-clinico__empty-cell" colspan="4">Sin puntos de emisión registrados.</td>
            </tr>
            <tr *ngFor="let p of puntosEmision()">
              <td data-label="Serie" class="data">{{ p.establecimiento }}-{{ p.puntoEmision }}</td>
              <td data-label="Dirección" *ngIf="editandoPuntoId() !== p.id">{{ p.direccionEstablecimiento ?? '—' }}</td>
              <td data-label="Dirección" *ngIf="editandoPuntoId() === p.id">
                <input type="text" [(ngModel)]="direccionEnEdicion" [ngModelOptions]="{ standalone: true }" />
              </td>
              <td data-label="Estado"><span class="chip" [ngClass]="p.activo ? 'chip--success' : 'chip--neutral'">{{ p.activo ? 'Activo' : 'Inactivo' }}</span></td>
              <td data-label="" class="actions">
                <ng-container *ngIf="editandoPuntoId() !== p.id">
                  <button type="button" class="btn-icon" (click)="editarDireccionPunto(p)" aria-label="Editar dirección"><app-icon name="editar"></app-icon></button>
                  <button type="button" class="btn btn--secondary btn--sm" (click)="cambiarEstadoPunto(p)">{{ p.activo ? 'Desactivar' : 'Activar' }}</button>
                </ng-container>
                <ng-container *ngIf="editandoPuntoId() === p.id">
                  <button type="button" class="btn btn--primary btn--sm" (click)="guardarDireccionPunto(p)">Guardar</button>
                  <button type="button" class="btn btn--secondary btn--sm" (click)="editandoPuntoId.set(null)">Cancelar</button>
                </ng-container>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- ===== Conceptos ===== -->
    <div id="panel-conceptos" class="tab-panel" role="tabpanel" aria-labelledby="tab-conceptos" *ngIf="tab() === 'conceptos'">
      <button type="button" class="btn btn--primary" (click)="abrirCrearConcepto()" *ngIf="!mostrarFormConcepto()">
        <app-icon name="anadir"></app-icon>
        Nuevo concepto
      </button>

      <form [formGroup]="conceptoForm" (ngSubmit)="guardarConcepto()" novalidate *ngIf="mostrarFormConcepto()">
        <div class="field"><label for="c-codigo">Código<span class="required-mark" aria-hidden="true">*</span></label><input id="c-codigo" type="text" formControlName="codigo" maxlength="25" /></div>
        <div class="field"><label for="c-descripcion">Descripción<span class="required-mark" aria-hidden="true">*</span></label><input id="c-descripcion" type="text" formControlName="descripcion" maxlength="300" /></div>
        <div class="field">
          <label for="c-tipo">Tipo<span class="required-mark" aria-hidden="true">*</span></label>
          <select id="c-tipo" formControlName="tipo">
            <option value="CONSULTA">Consulta</option>
            <option value="VACUNA">Vacuna</option>
            <option value="PROCEDIMIENTO">Procedimiento</option>
            <option value="MEDICAMENTO">Medicamento</option>
            <option value="PRODUCTO">Producto</option>
            <option value="OTRO">Otro</option>
          </select>
        </div>
        <div class="field"><label for="c-precio">Precio unitario<span class="required-mark" aria-hidden="true">*</span></label><input id="c-precio" type="number" min="0" step="0.01" formControlName="precioUnitario" /></div>
        <div class="field">
          <label for="c-impuesto">Código de impuesto<span class="required-mark" aria-hidden="true">*</span></label>
          <select id="c-impuesto" formControlName="codigoImpuesto">
            <option value="IVA">IVA</option>
            <option value="ICE">ICE</option>
            <option value="IRBPNR">IRBPNR</option>
          </select>
        </div>
        <div class="field"><label for="c-porcentaje">Código de porcentaje (SRI)<span class="required-mark" aria-hidden="true">*</span></label><input id="c-porcentaje" type="text" formControlName="codigoPorcentaje" maxlength="2" placeholder="4" /></div>
        <div class="modal-panel__actions">
          <button type="submit" class="btn btn--primary" [disabled]="guardandoConcepto()">{{ guardandoConcepto() ? 'Guardando…' : (editandoConcepto() ? 'Guardar cambios' : 'Crear') }}</button>
          <button type="button" class="btn btn--secondary" (click)="mostrarFormConcepto.set(false)" [disabled]="guardandoConcepto()">Cancelar</button>
        </div>
      </form>

      <div class="toolbar" style="margin-top: var(--space-4);">
        <label>
          <input type="checkbox" [checked]="soloActivos()" (change)="alternarSoloActivos()" />
          Mostrar solo activos
        </label>
      </div>

      <div class="table-wrap">
        <table class="table-clinico">
          <caption class="sr-only">Conceptos facturables</caption>
          <thead><tr><th scope="col">Código</th><th scope="col">Descripción</th><th scope="col">Tipo</th><th scope="col">Precio</th><th scope="col">Estado</th><th scope="col"><span class="sr-only">Acciones</span></th></tr></thead>
          <tbody>
            <tr class="table-clinico__empty-row" *ngIf="!cargandoConceptos() && conceptos().length === 0">
              <td class="table-clinico__empty-cell" colspan="6">Sin conceptos registrados.</td>
            </tr>
            <tr *ngFor="let c of conceptos()">
              <td data-label="Código" class="data">{{ c.codigo }}</td>
              <td data-label="Descripción">{{ c.descripcion }}</td>
              <td data-label="Tipo">{{ c.tipo }}</td>
              <td data-label="Precio" class="data">{{ c.precioUnitario.toFixed(2) }}</td>
              <td data-label="Estado"><span class="chip" [ngClass]="c.activo ? 'chip--success' : 'chip--neutral'">{{ c.activo ? 'Activo' : 'Inactivo' }}</span></td>
              <td data-label="" class="actions">
                <button type="button" class="btn-icon" (click)="abrirEditarConcepto(c)" aria-label="Editar"><app-icon name="editar"></app-icon></button>
                <button type="button" class="btn btn--secondary btn--sm" (click)="cambiarEstadoConcepto(c)">{{ c.activo ? 'Desactivar' : 'Activar' }}</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- ===== Tarifas ===== -->
    <div id="panel-tarifas" class="tab-panel" role="tabpanel" aria-labelledby="tab-tarifas" *ngIf="tab() === 'tarifas'">
      <button type="button" class="btn btn--primary" (click)="abrirCrearTarifa()" *ngIf="!mostrarFormTarifa()">
        <app-icon name="anadir"></app-icon>
        Nueva vigencia
      </button>

      <form [formGroup]="tarifaForm" (ngSubmit)="crearTarifa()" novalidate *ngIf="mostrarFormTarifa()">
        <p class="field-hint">
          Abre una vigencia nueva. Si ya existe una vigencia abierta para el mismo impuesto/porcentaje, el backend la
          cierra automáticamente el día anterior a esta — nunca se sobrescribe una tarifa histórica.
        </p>
        <div class="field">
          <label for="t-impuesto">Código de impuesto<span class="required-mark" aria-hidden="true">*</span></label>
          <select id="t-impuesto" formControlName="codigoImpuesto">
            <option value="IVA">IVA</option>
            <option value="ICE">ICE</option>
            <option value="IRBPNR">IRBPNR</option>
          </select>
        </div>
        <div class="field"><label for="t-porcentaje">Código de porcentaje (SRI)<span class="required-mark" aria-hidden="true">*</span></label><input id="t-porcentaje" type="text" formControlName="codigoPorcentaje" maxlength="2" placeholder="4" /></div>
        <div class="field"><label for="t-descripcion">Descripción<span class="required-mark" aria-hidden="true">*</span></label><input id="t-descripcion" type="text" formControlName="descripcion" maxlength="100" /></div>
        <div class="field"><label for="t-tarifa">Tarifa (%)<span class="required-mark" aria-hidden="true">*</span></label><input id="t-tarifa" type="number" min="0" max="100" step="0.01" formControlName="tarifa" /></div>
        <div class="field"><label for="t-vigenteDesde">Vigente desde<span class="required-mark" aria-hidden="true">*</span></label><input id="t-vigenteDesde" type="date" formControlName="vigenteDesde" /></div>
        <div class="modal-panel__actions">
          <button type="submit" class="btn btn--primary" [disabled]="guardandoTarifa()">{{ guardandoTarifa() ? 'Creando…' : 'Crear vigencia' }}</button>
          <button type="button" class="btn btn--secondary" (click)="mostrarFormTarifa.set(false)" [disabled]="guardandoTarifa()">Cancelar</button>
        </div>
      </form>

      <div class="table-wrap" style="margin-top: var(--space-4);">
        <table class="table-clinico">
          <caption class="sr-only">Histórico de tarifas de impuesto</caption>
          <thead><tr><th scope="col">Impuesto</th><th scope="col">Descripción</th><th scope="col">Tarifa</th><th scope="col">Vigencia</th><th scope="col">Estado</th><th scope="col"><span class="sr-only">Acciones</span></th></tr></thead>
          <tbody>
            <tr class="table-clinico__empty-row" *ngIf="!cargandoTarifas() && tarifas().length === 0">
              <td class="table-clinico__empty-cell" colspan="6">Sin tarifas configuradas todavía.</td>
            </tr>
            <tr *ngFor="let t of tarifas()">
              <td data-label="Impuesto" class="data">{{ t.codigoImpuesto }} · {{ t.codigoPorcentaje }}</td>
              <td data-label="Descripción">{{ t.descripcion }}</td>
              <td data-label="Tarifa" class="data">{{ t.tarifa }}%</td>
              <td data-label="Vigencia" class="data">{{ t.vigenteDesde }} → {{ t.vigenteHasta ?? 'actual' }}</td>
              <td data-label="Estado"><span class="chip" [ngClass]="t.activo ? 'chip--success' : 'chip--neutral'">{{ t.activo ? 'Activo' : 'Inactivo' }}</span></td>
              <td data-label="" class="actions">
                <button type="button" class="btn btn--secondary btn--sm" (click)="cambiarEstadoTarifa(t)">{{ t.activo ? 'Desactivar' : 'Activar' }}</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>
  `,
})
export class FacturacionConfigComponent implements OnInit {
  tab = signal<Tab>('emisor');
  error = signal('');
  mensajeExito = signal('');

  // ---------- Emisor ----------
  emisor = signal<EmisorFiscal | null>(null);
  cargandoEmisor = signal(false);
  guardandoEmisor = signal(false);
  mostrarFormEmisor = signal(false);
  get emisorConfigurado(): () => boolean {
    return () => this.emisor() !== null;
  }
  emisorForm = this.fb.group({
    ruc: ['', [Validators.required, Validators.pattern(/^\d{13}$/)]],
    razonSocial: ['', [Validators.required, Validators.maxLength(300)]],
    nombreComercial: [''],
    direccionMatriz: ['', [Validators.required, Validators.maxLength(300)]],
    obligadoContabilidad: [false],
    contribuyenteEspecial: [''],
    rimpe: [false],
    agenteRetencionResolucion: [''],
    activo: [true],
  });

  // ---------- Puntos de emisión ----------
  puntosEmision = signal<PuntoEmision[]>([]);
  cargandoPuntos = signal(false);
  guardandoPunto = signal(false);
  mostrarFormPunto = signal(false);
  editandoPuntoId = signal<number | null>(null);
  direccionEnEdicion = '';
  puntoForm = this.fb.group({
    establecimiento: ['', [Validators.required, Validators.pattern(/^\d{3}$/)]],
    puntoEmision: ['', [Validators.required, Validators.pattern(/^\d{3}$/)]],
    direccionEstablecimiento: [''],
  });

  // ---------- Conceptos ----------
  conceptos = signal<ConceptoFacturable[]>([]);
  cargandoConceptos = signal(false);
  guardandoConcepto = signal(false);
  mostrarFormConcepto = signal(false);
  editandoConcepto = signal<ConceptoFacturable | null>(null);
  soloActivos = signal(true);
  conceptoForm = this.fb.group({
    codigo: ['', [Validators.required, Validators.maxLength(25)]],
    descripcion: ['', [Validators.required, Validators.maxLength(300)]],
    tipo: ['OTRO' as TipoConceptoFacturable, [Validators.required]],
    precioUnitario: [0, [Validators.required, Validators.min(0)]],
    codigoImpuesto: ['IVA' as CodigoImpuestoSri, [Validators.required]],
    codigoPorcentaje: ['', [Validators.required, Validators.pattern(/^\d{1,2}$/)]],
  });

  // ---------- Tarifas ----------
  tarifas = signal<TarifaImpuesto[]>([]);
  cargandoTarifas = signal(false);
  guardandoTarifa = signal(false);
  mostrarFormTarifa = signal(false);
  tarifaForm = this.fb.group({
    codigoImpuesto: ['IVA' as CodigoImpuestoSri, [Validators.required]],
    codigoPorcentaje: ['', [Validators.required, Validators.pattern(/^\d{1,2}$/)]],
    descripcion: ['', [Validators.required, Validators.maxLength(100)]],
    tarifa: [0, [Validators.required, Validators.min(0), Validators.max(100)]],
    vigenteDesde: ['', [Validators.required]],
  });

  constructor(
    private fb: FormBuilder,
    private api: FacturacionConfigApiService,
    private problemDetail: ProblemDetailService
  ) {}

  ngOnInit(): void {
    this.cargarEmisor();
    this.cargarPuntos();
    this.cargarConceptos();
    this.cargarTarifas();
  }

  private mostrarExito(mensaje: string): void {
    this.mensajeExito.set(mensaje);
    setTimeout(() => {
      if (this.mensajeExito() === mensaje) this.mensajeExito.set('');
    }, 4000);
  }

  // ---------- Emisor ----------

  private cargarEmisor(): void {
    this.cargandoEmisor.set(true);
    this.api.obtenerEmisor().subscribe({
      next: (e) => {
        this.emisor.set(e);
        this.cargandoEmisor.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.cargandoEmisor.set(false);
        // 404 = todavía no configurado: no es un error de la pantalla, es el estado inicial real.
        if (err.status !== 404) this.error.set(this.problemDetail.mensaje(err));
      },
    });
  }

  abrirFormEmisor(): void {
    const actual = this.emisor();
    this.emisorForm.reset(
      actual ?? {
        ruc: '',
        razonSocial: '',
        nombreComercial: '',
        direccionMatriz: '',
        obligadoContabilidad: false,
        contribuyenteEspecial: '',
        rimpe: false,
        agenteRetencionResolucion: '',
        activo: true,
      }
    );
    this.mostrarFormEmisor.set(true);
  }

  guardarEmisor(): void {
    this.error.set('');
    if (this.emisorForm.invalid) {
      this.emisorForm.markAllAsTouched();
      return;
    }
    const v = this.emisorForm.getRawValue();
    this.guardandoEmisor.set(true);
    this.api
      .actualizarEmisor({
        ruc: v.ruc as string,
        razonSocial: v.razonSocial as string,
        nombreComercial: v.nombreComercial || null,
        direccionMatriz: v.direccionMatriz as string,
        obligadoContabilidad: !!v.obligadoContabilidad,
        contribuyenteEspecial: v.contribuyenteEspecial || null,
        rimpe: !!v.rimpe,
        agenteRetencionResolucion: v.agenteRetencionResolucion || null,
        activo: v.activo ?? true,
      })
      .subscribe({
        next: (e) => {
          this.guardandoEmisor.set(false);
          this.emisor.set(e);
          this.mostrarFormEmisor.set(false);
          this.mostrarExito('Emisor fiscal guardado.');
        },
        error: (err: HttpErrorResponse) => {
          this.guardandoEmisor.set(false);
          this.error.set(this.problemDetail.mensaje(err));
        },
      });
  }

  // ---------- Puntos de emisión ----------

  private cargarPuntos(): void {
    this.cargandoPuntos.set(true);
    this.api.listarPuntosEmision().subscribe({
      next: (res) => {
        this.puntosEmision.set(res ?? []);
        this.cargandoPuntos.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.cargandoPuntos.set(false);
        this.error.set(this.problemDetail.mensaje(err));
      },
    });
  }

  abrirCrearPunto(): void {
    this.puntoForm.reset({ establecimiento: '', puntoEmision: '', direccionEstablecimiento: '' });
    this.mostrarFormPunto.set(true);
  }

  crearPunto(): void {
    const e = this.emisor();
    this.error.set('');
    if (this.puntoForm.invalid || !e) {
      this.puntoForm.markAllAsTouched();
      return;
    }
    const v = this.puntoForm.getRawValue();
    this.guardandoPunto.set(true);
    this.api
      .crearPuntoEmision({
        emisorFiscalId: e.id,
        establecimiento: v.establecimiento as string,
        puntoEmision: v.puntoEmision as string,
        direccionEstablecimiento: v.direccionEstablecimiento || null,
      })
      .subscribe({
        next: (p) => {
          this.guardandoPunto.set(false);
          this.puntosEmision.set([...this.puntosEmision(), p]);
          this.mostrarFormPunto.set(false);
          this.mostrarExito('Punto de emisión creado.');
        },
        error: (err: HttpErrorResponse) => {
          this.guardandoPunto.set(false);
          this.error.set(this.problemDetail.mensaje(err));
        },
      });
  }

  editarDireccionPunto(p: PuntoEmision): void {
    this.editandoPuntoId.set(p.id);
    this.direccionEnEdicion = p.direccionEstablecimiento ?? '';
  }

  guardarDireccionPunto(p: PuntoEmision): void {
    this.error.set('');
    this.api.actualizarPuntoEmision(p.id, { direccionEstablecimiento: this.direccionEnEdicion || null }).subscribe({
      next: (actualizado) => {
        this.puntosEmision.set(this.puntosEmision().map((x) => (x.id === actualizado.id ? actualizado : x)));
        this.editandoPuntoId.set(null);
        this.mostrarExito('Dirección actualizada.');
      },
      error: (err: HttpErrorResponse) => this.error.set(this.problemDetail.mensaje(err)),
    });
  }

  cambiarEstadoPunto(p: PuntoEmision): void {
    this.error.set('');
    this.api.cambiarEstadoPuntoEmision(p.id, !p.activo).subscribe({
      next: (actualizado) => {
        this.puntosEmision.set(this.puntosEmision().map((x) => (x.id === actualizado.id ? actualizado : x)));
        this.mostrarExito(actualizado.activo ? 'Punto de emisión activado.' : 'Punto de emisión desactivado.');
      },
      error: (err: HttpErrorResponse) => this.error.set(this.problemDetail.mensaje(err)),
    });
  }

  // ---------- Conceptos ----------

  private cargarConceptos(): void {
    this.cargandoConceptos.set(true);
    this.api.listarConceptos(this.soloActivos() ? true : undefined).subscribe({
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

  alternarSoloActivos(): void {
    this.soloActivos.set(!this.soloActivos());
    this.cargarConceptos();
  }

  abrirCrearConcepto(): void {
    this.editandoConcepto.set(null);
    this.conceptoForm.reset({ codigo: '', descripcion: '', tipo: 'OTRO', precioUnitario: 0, codigoImpuesto: 'IVA', codigoPorcentaje: '' });
    this.mostrarFormConcepto.set(true);
  }

  abrirEditarConcepto(c: ConceptoFacturable): void {
    this.editandoConcepto.set(c);
    this.conceptoForm.reset(c);
    this.mostrarFormConcepto.set(true);
  }

  guardarConcepto(): void {
    this.error.set('');
    if (this.conceptoForm.invalid) {
      this.conceptoForm.markAllAsTouched();
      return;
    }
    const v = this.conceptoForm.getRawValue();
    const payload = {
      codigo: v.codigo as string,
      descripcion: v.descripcion as string,
      tipo: v.tipo as TipoConceptoFacturable,
      precioUnitario: v.precioUnitario as number,
      codigoImpuesto: v.codigoImpuesto as CodigoImpuestoSri,
      codigoPorcentaje: v.codigoPorcentaje as string,
    };
    const actual = this.editandoConcepto();
    this.guardandoConcepto.set(true);
    const peticion = actual ? this.api.actualizarConcepto(actual.id, payload) : this.api.crearConcepto(payload);
    peticion.subscribe({
      next: () => {
        this.guardandoConcepto.set(false);
        this.mostrarFormConcepto.set(false);
        this.mostrarExito(actual ? 'Concepto actualizado.' : 'Concepto creado.');
        this.cargarConceptos();
      },
      error: (err: HttpErrorResponse) => {
        this.guardandoConcepto.set(false);
        this.error.set(this.problemDetail.mensaje(err));
      },
    });
  }

  cambiarEstadoConcepto(c: ConceptoFacturable): void {
    this.error.set('');
    this.api.cambiarEstadoConcepto(c.id, !c.activo).subscribe({
      next: () => {
        this.mostrarExito(c.activo ? 'Concepto desactivado.' : 'Concepto activado.');
        this.cargarConceptos();
      },
      error: (err: HttpErrorResponse) => this.error.set(this.problemDetail.mensaje(err)),
    });
  }

  // ---------- Tarifas ----------

  private cargarTarifas(): void {
    this.cargandoTarifas.set(true);
    this.api.listarTarifas().subscribe({
      next: (res) => {
        this.tarifas.set(res ?? []);
        this.cargandoTarifas.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.cargandoTarifas.set(false);
        this.error.set(this.problemDetail.mensaje(err));
      },
    });
  }

  abrirCrearTarifa(): void {
    this.tarifaForm.reset({ codigoImpuesto: 'IVA', codigoPorcentaje: '', descripcion: '', tarifa: 0, vigenteDesde: '' });
    this.mostrarFormTarifa.set(true);
  }

  crearTarifa(): void {
    this.error.set('');
    if (this.tarifaForm.invalid) {
      this.tarifaForm.markAllAsTouched();
      return;
    }
    const v = this.tarifaForm.getRawValue();
    this.guardandoTarifa.set(true);
    this.api
      .crearTarifa({
        codigoImpuesto: v.codigoImpuesto as CodigoImpuestoSri,
        codigoPorcentaje: v.codigoPorcentaje as string,
        descripcion: v.descripcion as string,
        tarifa: v.tarifa as number,
        vigenteDesde: v.vigenteDesde as string,
      })
      .subscribe({
        next: () => {
          this.guardandoTarifa.set(false);
          this.mostrarFormTarifa.set(false);
          this.mostrarExito('Vigencia creada.');
          this.cargarTarifas();
        },
        error: (err: HttpErrorResponse) => {
          this.guardandoTarifa.set(false);
          this.error.set(this.problemDetail.mensaje(err));
        },
      });
  }

  cambiarEstadoTarifa(t: TarifaImpuesto): void {
    this.error.set('');
    this.api.cambiarEstadoTarifa(t.id, !t.activo).subscribe({
      next: () => {
        this.mostrarExito(t.activo ? 'Tarifa desactivada.' : 'Tarifa activada.');
        this.cargarTarifas();
      },
      error: (err: HttpErrorResponse) => this.error.set(this.problemDetail.mensaje(err)),
    });
  }
}
