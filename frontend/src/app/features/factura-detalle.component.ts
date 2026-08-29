import { Component, OnInit, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';

import { AuthService } from '../core/auth.service';
import { ProblemDetailService } from '../core/problem-detail.service';
import { Factura, FacturaApiService, FacturaEventoSri, TipoDocumentoFactura } from './factura-api.service';
import { PuntoEmision, FacturacionConfigApiService } from './facturacion-config-api.service';
import { PageHeaderComponent } from '../shared/page-header/page-header.component';
import { IconComponent } from '../shared/icons/icon.component';
import {
  chipClaseEstadoAutorizacion,
  chipClaseEstadoFactura,
  chipClaseEstadoRecepcion,
  etiquetaEstadoAutorizacion,
  etiquetaEstadoFactura,
  etiquetaEstadoRecepcion,
  etiquetaFormaPago,
  etiquetaTipoDocumento,
  numeroComprobante,
  pasosPipeline,
  puedeEnviarSri,
  puedeFirmar,
  puedeGenerarXml,
  puedeSincronizarSri,
} from './factura-presentacion';

type AccionFiscal = 'emitir' | 'generar-xml' | 'firmar' | 'enviar-sri' | 'sincronizar-sri';

/**
 * Ficha de factura (/facturas/:id). Cada dato viene de GET /api/facturas/{id}
 * (FacturaResponse) ya auditado en la Fase 8A; la bitácora SRI viene aparte
 * de GET /api/facturas/{id}/eventos-sri, solo para ADMIN/AUXILIAR.
 *
 * Ninguna acción fiscal actualiza el estado a mano: cada botón llama al
 * endpoint real y SIEMPRE vuelca en `factura` la respuesta completa que
 * devuelve el backend (que ya es una relectura fresca — ver el javadoc de
 * FacturaController sobre `mapearParaRespuestaDeEscritura`). No hay ningún
 * camino en este componente que "simule" una transición de estado.
 */
@Component({
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, PageHeaderComponent, IconComponent],
  template: `
  <app-page-header
    eyebrow="Facturación"
    [breadcrumb]="[{ label: 'Facturas', path: '/facturas' }, { label: factura() ? numero(factura()!) : 'Factura' }]"
    [title]="factura() ? numero(factura()!) : 'Factura'"
    [hasActions]="false">
  </app-page-header>

  <span class="sr-only" role="status" aria-live="polite" *ngIf="cargando()">Cargando factura…</span>

  <div class="panel" *ngIf="cargando()" aria-hidden="true">
    <span class="skeleton-block" style="width:40%"></span>
  </div>

  <div class="panel" *ngIf="!cargando() && error()">
    <p class="alert alert--danger" role="alert" aria-live="assertive">
      <strong>Error:</strong> {{ error() }}
    </p>
    <a routerLink="/facturas" class="btn btn--secondary">
      <app-icon name="chevron-left"></app-icon>
      Volver a Facturas
    </a>
  </div>

  <ng-container *ngIf="!cargando() && !error() && factura() as f">
    <p class="alert alert--success" role="status" aria-live="polite" *ngIf="mensajeExito()">
      <strong>Listo:</strong> {{ mensajeExito() }}
    </p>
    <p class="alert alert--danger" role="alert" aria-live="assertive" *ngIf="errorAccion()">
      <strong>Error:</strong> {{ errorAccion() }}
    </p>

    <!-- ===== Cabecera ===== -->
    <section class="panel panel--record" aria-labelledby="cabecera-titulo">
      <h2 id="cabecera-titulo" class="sr-only">Cabecera</h2>
      <div class="record-header">
        <dl class="record-meta-grid">
          <div>
            <dt>Número</dt>
            <dd class="data">{{ numero(f) }}</dd>
          </div>
          <div>
            <dt>Fecha de emisión</dt>
            <dd class="data">{{ f.fechaEmision ?? '—' }}</dd>
          </div>
          <div>
            <dt>Estado</dt>
            <dd><span class="chip" [ngClass]="chipClaseEstadoFactura(f.estado)">{{ etiquetaEstadoFactura(f.estado) }}</span></dd>
          </div>
          <div *ngIf="f.claveAcceso">
            <dt>Clave de acceso</dt>
            <dd class="data" style="word-break: break-all;">{{ f.claveAcceso }}</dd>
          </div>
          <div *ngIf="f.numeroAutorizacion">
            <dt>Número de autorización</dt>
            <dd class="data" style="word-break: break-all;">{{ f.numeroAutorizacion }}</dd>
          </div>
          <div *ngIf="f.fechaAutorizacion">
            <dt>Fecha de autorización</dt>
            <dd class="data">{{ f.fechaAutorizacion | date: 'medium' }}</dd>
          </div>
        </dl>
      </div>
    </section>

    <!-- ===== Pipeline visual ===== -->
    <section class="panel" aria-labelledby="pipeline-titulo">
      <h2 id="pipeline-titulo" class="panel__title-inline">Progreso fiscal</h2>
      <ol class="factura-pipeline">
        <li
          *ngFor="let paso of pasos(f)"
          class="factura-pipeline__paso"
          [class.factura-pipeline__paso--completado]="paso.completado"
          [class.factura-pipeline__paso--actual]="paso.actual">
          <span class="factura-pipeline__marca" aria-hidden="true">
            <app-icon *ngIf="paso.completado" name="check" [size]="14"></app-icon>
          </span>
          {{ paso.etiqueta }}
        </li>
      </ol>
      <p class="field-hint" *ngIf="f.estado === 'RECHAZADA'">
        Esta factura fue rechazada por el SRI en algún punto del proceso (recepción devuelta o autorización no
        concedida). Revisa la bitácora SRI para más detalle.
      </p>
    </section>

    <!-- ===== Comprador ===== -->
    <section class="panel" aria-labelledby="comprador-titulo">
      <h2 id="comprador-titulo" class="panel__title-inline">Comprador</h2>
      <dl class="record-meta-grid" *ngIf="f.compradorRazonSocial; else sinComprador">
        <div>
          <dt>Identificación</dt>
          <dd class="data">{{ f.compradorTipoIdentificacion }} · {{ f.compradorIdentificacion }}</dd>
        </div>
        <div>
          <dt>Razón social / Nombres</dt>
          <dd>{{ f.compradorRazonSocial }}</dd>
        </div>
        <div *ngIf="f.compradorDireccion">
          <dt>Dirección</dt>
          <dd>{{ f.compradorDireccion }}</dd>
        </div>
        <div *ngIf="f.compradorEmail">
          <dt>Email</dt>
          <dd>{{ f.compradorEmail }}</dd>
        </div>
        <div *ngIf="f.compradorTelefono">
          <dt>Teléfono</dt>
          <dd>{{ f.compradorTelefono }}</dd>
        </div>
      </dl>
      <ng-template #sinComprador>
        <p class="field-hint">Este borrador todavía no tiene comprador seleccionado.</p>
      </ng-template>
    </section>

    <!-- ===== Mascota ===== -->
    <section class="panel" aria-labelledby="mascota-titulo" *ngIf="f.mascotaId">
      <h2 id="mascota-titulo" class="panel__title-inline">Mascota</h2>
      <a class="record-link" [routerLink]="['/mascotas', f.mascotaId]">{{ f.mascotaNombre }}</a>
    </section>

    <!-- ===== Detalles ===== -->
    <section class="panel" aria-labelledby="detalles-titulo">
      <h2 id="detalles-titulo" class="panel__title-inline">Detalles</h2>
      <div class="table-wrap" *ngIf="f.detalles.length > 0; else sinDetalles">
        <table class="table-clinico">
          <caption class="sr-only">Líneas de la factura</caption>
          <thead>
            <tr>
              <th scope="col">Concepto</th>
              <th scope="col">Cantidad</th>
              <th scope="col">Precio unitario</th>
              <th scope="col">Impuesto</th>
              <th scope="col">Subtotal</th>
            </tr>
          </thead>
          <tbody>
            <tr *ngFor="let d of f.detalles">
              <td data-label="Concepto">{{ d.descripcion }}</td>
              <td data-label="Cantidad" class="data">{{ d.cantidad }}</td>
              <td data-label="Precio unitario" class="data">{{ d.precioUnitario.toFixed(2) }}</td>
              <td data-label="Impuesto" class="data">{{ d.impuestoCodigo }} {{ d.impuestoTarifa }}% ({{ d.impuestoValor.toFixed(2) }})</td>
              <td data-label="Subtotal" class="data">{{ d.precioTotalSinImpuesto.toFixed(2) }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <ng-template #sinDetalles>
        <p class="field-hint">Este borrador todavía no tiene líneas agregadas.</p>
      </ng-template>
    </section>

    <!-- ===== Totales ===== -->
    <section class="panel profile-panel" aria-labelledby="totales-titulo">
      <h2 id="totales-titulo" class="panel__title-inline">Totales</h2>
      <dl class="profile-info">
        <div class="profile-info__row">
          <dt class="label">Subtotal</dt>
          <dd class="data">{{ formatoMoneda(f.totalSinImpuestos, f.moneda) }}</dd>
        </div>
        <div class="profile-info__row">
          <dt class="label">Descuento</dt>
          <dd class="data">{{ formatoMoneda(f.totalDescuento, f.moneda) }}</dd>
        </div>
        <div class="profile-info__row">
          <dt class="label">Impuestos</dt>
          <dd class="data">{{ formatoMoneda(f.totalImpuestos, f.moneda) }}</dd>
        </div>
        <div class="profile-info__row">
          <dt class="label"><strong>Total</strong></dt>
          <dd class="data"><strong>{{ formatoMoneda(f.importeTotal, f.moneda) }}</strong></dd>
        </div>
      </dl>
    </section>

    <!-- ===== Pagos ===== -->
    <section class="panel" aria-labelledby="pagos-titulo">
      <h2 id="pagos-titulo" class="panel__title-inline">Pagos</h2>
      <div class="table-wrap" *ngIf="f.pagos.length > 0; else sinPagos">
        <table class="table-clinico">
          <caption class="sr-only">Formas de pago de la factura</caption>
          <thead>
            <tr><th scope="col">Forma de pago</th><th scope="col">Valor</th></tr>
          </thead>
          <tbody>
            <tr *ngFor="let p of f.pagos">
              <td data-label="Forma de pago">{{ etiquetaFormaPago(p.formaPago) }}</td>
              <td data-label="Valor" class="data">{{ p.total.toFixed(2) }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <ng-template #sinPagos>
        <p class="field-hint">Este borrador todavía no tiene pagos registrados.</p>
      </ng-template>
    </section>

    <!-- ===== Estado SRI ===== -->
    <section class="panel" aria-labelledby="sri-titulo" *ngIf="f.estado !== 'BORRADOR'">
      <h2 id="sri-titulo" class="panel__title-inline">Estado SRI</h2>
      <dl class="record-meta-grid">
        <div>
          <dt>Recepción</dt>
          <dd>
            <span *ngIf="f.estadoRecepcion as er; else sinRecepcion" class="chip" [ngClass]="chipClaseEstadoRecepcion(er)">
              {{ etiquetaEstadoRecepcion(er) }}
            </span>
            <ng-template #sinRecepcion>Todavía no enviada al SRI</ng-template>
          </dd>
        </div>
        <div>
          <dt>Autorización</dt>
          <dd>
            <span *ngIf="f.estadoAutorizacion as ea; else sinAutorizacion" class="chip" [ngClass]="chipClaseEstadoAutorizacion(ea)">
              {{ etiquetaEstadoAutorizacion(ea) }}
            </span>
            <ng-template #sinAutorizacion>—</ng-template>
          </dd>
        </div>
        <div *ngIf="f.proximoIntentoEn">
          <dt>Próximo intento</dt>
          <dd class="data">{{ f.proximoIntentoEn | date: 'medium' }}</dd>
        </div>
        <div *ngIf="f.intentosAutorizacion != null">
          <dt>Intentos de autorización</dt>
          <dd class="data">{{ f.intentosAutorizacion }}</dd>
        </div>
      </dl>
    </section>

    <!-- ===== Documentos ===== -->
    <section class="panel" aria-labelledby="documentos-titulo" *ngIf="f.estado !== 'BORRADOR'">
      <h2 id="documentos-titulo" class="panel__title-inline">Documentos</h2>
      <div class="toolbar" *ngIf="documentosVisibles(f).length > 0; else sinDocumentos">
        <button
          type="button"
          class="btn btn--secondary btn--sm"
          *ngFor="let tipo of documentosVisibles(f)"
          [disabled]="descargando() === tipo"
          (click)="descargar(f, tipo)">
          <app-icon name="descargar" [size]="16"></app-icon>
          {{ descargando() === tipo ? 'Descargando…' : etiquetaTipoDocumento(tipo) }}
        </button>
      </div>
      <ng-template #sinDocumentos>
        <p class="field-hint" *ngIf="esDueno">Todavía no está disponible el comprobante autorizado.</p>
        <p class="field-hint" *ngIf="!esDueno">Todavía no se ha generado ningún documento.</p>
      </ng-template>
    </section>

    <!-- ===== Acciones fiscales ===== -->
    <section class="panel" aria-labelledby="acciones-titulo" *ngIf="puedeOperar">
      <h2 id="acciones-titulo" class="panel__title-inline">Acciones fiscales</h2>

      <!-- Confirmación de emisión: requiere elegir punto de emisión -->
      <div *ngIf="confirmandoEmision()" class="field">
        <p class="alert alert--warning" role="alert">
          <strong>Atención:</strong> una vez emitida, la información fiscal queda congelada. La numeración, el
          comprador y las líneas ya no podrán modificarse.
        </p>
        <label for="f-punto-emision">Punto de emisión<span class="required-mark" aria-hidden="true">*</span></label>
        <select id="f-punto-emision" [(ngModel)]="puntoEmisionSeleccionado" [disabled]="cargandoPuntos()">
          <option [ngValue]="null" disabled>
            {{ cargandoPuntos() ? 'Cargando puntos de emisión…' : 'Selecciona un punto de emisión' }}
          </option>
          <option *ngFor="let p of puntosEmision()" [ngValue]="p.id">{{ p.establecimiento }}-{{ p.puntoEmision }} — {{ p.direccionEstablecimiento || 'Sin dirección' }}</option>
        </select>
        <div class="modal-panel__actions">
          <button
            type="button"
            class="btn btn--primary"
            [disabled]="!puntoEmisionSeleccionado || accionEnCurso()"
            (click)="confirmarEmision(f)">
            {{ accionEnCurso() === 'emitir' ? 'Emitiendo…' : 'Sí, emitir factura' }}
          </button>
          <button type="button" class="btn btn--secondary" (click)="cancelarEmision()" [disabled]="accionEnCurso() === 'emitir'">
            Cancelar
          </button>
        </div>
      </div>

      <!-- Confirmación de envío al SRI -->
      <div *ngIf="confirmandoEnvioSri()" class="field">
        <p class="alert alert--info" role="alert">
          ¿Enviar esta factura al SRI ahora? Si el servicio tarda o no responde, la factura conserva su numeración y
          podrá sincronizarse más tarde.
        </p>
        <div class="modal-panel__actions">
          <button type="button" class="btn btn--primary" [disabled]="accionEnCurso() !== null" (click)="ejecutar(f, 'enviar-sri')">
            {{ accionEnCurso() === 'enviar-sri' ? 'Enviando…' : 'Sí, enviar al SRI' }}
          </button>
          <button type="button" class="btn btn--secondary" (click)="confirmandoEnvioSri.set(false)" [disabled]="accionEnCurso() !== null">
            Cancelar
          </button>
        </div>
      </div>

      <div class="toolbar" *ngIf="!confirmandoEmision() && !confirmandoEnvioSri()">
        <button type="button" class="btn btn--primary" *ngIf="f.estado === 'BORRADOR'" [disabled]="accionEnCurso() !== null" (click)="abrirConfirmarEmision()">
          Emitir
        </button>
        <button type="button" class="btn btn--secondary" *ngIf="puedeGenerarXml(f.estado, f.documentosDisponibles)" [disabled]="accionEnCurso() !== null" (click)="ejecutar(f, 'generar-xml')">
          {{ accionEnCurso() === 'generar-xml' ? 'Generando…' : 'Generar XML' }}
        </button>
        <button type="button" class="btn btn--secondary" *ngIf="puedeFirmar(f.estado, f.documentosDisponibles)" [disabled]="accionEnCurso() !== null" (click)="ejecutar(f, 'firmar')">
          {{ accionEnCurso() === 'firmar' ? 'Firmando…' : 'Firmar' }}
        </button>
        <button type="button" class="btn btn--secondary" *ngIf="puedeEnviarSri(f.estado, f.documentosDisponibles)" [disabled]="accionEnCurso() !== null" (click)="confirmandoEnvioSri.set(true)">
          Enviar al SRI
        </button>
        <button type="button" class="btn btn--secondary" *ngIf="puedeSincronizarSri(f.estado, f.claveAcceso)" [disabled]="accionEnCurso() !== null" (click)="ejecutar(f, 'sincronizar-sri')">
          {{ accionEnCurso() === 'sincronizar-sri' ? 'Sincronizando…' : 'Sincronizar SRI' }}
        </button>
      </div>
    </section>

    <!-- ===== Bitácora SRI ===== -->
    <section class="panel" aria-labelledby="bitacora-titulo" *ngIf="puedeOperar">
      <h2 id="bitacora-titulo" class="panel__title-inline">Bitácora SRI</h2>
      <button type="button" class="btn btn--ghost btn--sm" (click)="alternarBitacora(f.id)" [attr.aria-expanded]="mostrarBitacora()">
        {{ mostrarBitacora() ? 'Ocultar bitácora' : 'Ver bitácora' }}
      </button>

      <div *ngIf="mostrarBitacora()">
        <span class="sr-only" role="status" aria-live="polite" *ngIf="cargandoBitacora()">Cargando bitácora…</span>
        <p class="field-hint" *ngIf="!cargandoBitacora() && eventosSri().length === 0">Sin intentos registrados todavía.</p>
        <div class="table-wrap" *ngIf="!cargandoBitacora() && eventosSri().length > 0">
          <table class="table-clinico">
            <caption class="sr-only">Bitácora de intentos contra el SRI</caption>
            <thead>
              <tr><th scope="col">Operación</th><th scope="col">Resultado</th><th scope="col">Intento</th><th scope="col">Fecha</th></tr>
            </thead>
            <tbody>
              <ng-container *ngFor="let e of eventosSri()">
                <tr>
                  <td data-label="Operación">{{ e.operacion }}</td>
                  <td data-label="Resultado" class="data">{{ e.resultado }}</td>
                  <td data-label="Intento" class="data">{{ e.intento }}</td>
                  <td data-label="Fecha" class="data">{{ e.creadoEn | date: 'medium' }}</td>
                </tr>
                <tr *ngIf="e.mensajes">
                  <td colspan="4" data-label="Mensajes">
                    <details>
                      <summary>Mensajes</summary>
                      <pre class="factura-bitacora__mensajes">{{ e.mensajes | json }}</pre>
                    </details>
                  </td>
                </tr>
              </ng-container>
            </tbody>
          </table>
        </div>
      </div>
    </section>
  </ng-container>
  `,
})
export class FacturaDetalleComponent implements OnInit {
  readonly etiquetaEstadoFactura = etiquetaEstadoFactura;
  readonly chipClaseEstadoFactura = chipClaseEstadoFactura;
  readonly etiquetaEstadoRecepcion = etiquetaEstadoRecepcion;
  readonly chipClaseEstadoRecepcion = chipClaseEstadoRecepcion;
  readonly etiquetaEstadoAutorizacion = etiquetaEstadoAutorizacion;
  readonly chipClaseEstadoAutorizacion = chipClaseEstadoAutorizacion;
  readonly etiquetaFormaPago = etiquetaFormaPago;
  readonly etiquetaTipoDocumento = etiquetaTipoDocumento;
  readonly numero = (f: Factura) =>
    f.establecimiento && f.puntoEmision && f.secuencial != null
      ? numeroComprobante(f.establecimiento, f.puntoEmision, f.secuencial)
      : 'Borrador #' + f.id;
  readonly pasos = (f: Factura) => pasosPipeline(f.estado, f.documentosDisponibles, f.estadoRecepcion);
  readonly puedeGenerarXml = puedeGenerarXml;
  readonly puedeFirmar = puedeFirmar;
  readonly puedeEnviarSri = puedeEnviarSri;
  readonly puedeSincronizarSri = puedeSincronizarSri;

  factura = signal<Factura | null>(null);
  cargando = signal(true);
  error = signal('');
  mensajeExito = signal('');
  errorAccion = signal('');

  accionEnCurso = signal<AccionFiscal | null>(null);
  confirmandoEmision = signal(false);
  confirmandoEnvioSri = signal(false);
  puntosEmision = signal<PuntoEmision[]>([]);
  cargandoPuntos = signal(false);
  puntoEmisionSeleccionado: number | null = null;

  descargando = signal<TipoDocumentoFactura | null>(null);

  mostrarBitacora = signal(false);
  cargandoBitacora = signal(false);
  eventosSri = signal<FacturaEventoSri[]>([]);
  private bitacoraCargada = false;

  constructor(
    private route: ActivatedRoute,
    private api: FacturaApiService,
    private configApi: FacturacionConfigApiService,
    private auth: AuthService,
    private problemDetail: ProblemDetailService
  ) {}

  ngOnInit(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    this.cargar(id);
  }

  get esDueno(): boolean {
    return this.auth.usuarioActual()?.rol === 'ROLE_DUENO';
  }

  /** Acciones fiscales + bitácora: @PreAuthorize("hasAnyRole('ADMIN','AUXILIAR')") en TODOS los endpoints de escritura/eventos-sri. */
  get puedeOperar(): boolean {
    const rol = this.auth.usuarioActual()?.rol;
    return rol === 'ROLE_ADMIN' || rol === 'ROLE_AUXILIAR';
  }

  private cargar(id: number): void {
    this.cargando.set(true);
    this.error.set('');
    this.api.buscar(id).subscribe({
      next: (f) => {
        this.factura.set(f);
        this.cargando.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.cargando.set(false);
        this.error.set(this.problemDetail.mensaje(err));
      },
    });
  }

  formatoMoneda(valor: number | null, moneda: string | null): string {
    if (valor == null) return '—';
    return `${valor.toFixed(2)} ${moneda ?? 'USD'}`;
  }

  // ---------- Documentos ----------

  /** DUENO: solo XML_AUTORIZADO, aunque documentosDisponibles trajera más (FacturaConsultaService ya se lo filtra). */
  documentosVisibles(f: Factura): TipoDocumentoFactura[] {
    if (this.esDueno) return f.documentosDisponibles.filter((t) => t === 'XML_AUTORIZADO');
    return f.documentosDisponibles.filter((t) => t !== 'RIDE_PDF');
  }

  descargar(f: Factura, tipo: TipoDocumentoFactura): void {
    this.errorAccion.set('');
    this.descargando.set(tipo);
    this.api.descargarDocumento(f.id, tipo).subscribe({
      next: (blob) => {
        this.descargando.set(null);
        const nombreArchivo = `${f.claveAcceso ?? 'factura-' + f.id}-${tipo.toLowerCase()}.xml`;
        this.guardarBlob(blob, nombreArchivo);
      },
      error: (err: HttpErrorResponse) => {
        this.descargando.set(null);
        this.errorAccion.set(this.problemDetail.mensaje(err));
      },
    });
  }

  /** Bytes exactos del backend, entregados tal cual al navegador: sin parsear ni regenerar el XML aquí. */
  private guardarBlob(blob: Blob, nombreArchivo: string): void {
    const url = URL.createObjectURL(blob);
    const enlace = document.createElement('a');
    enlace.href = url;
    enlace.download = nombreArchivo;
    document.body.appendChild(enlace);
    enlace.click();
    enlace.remove();
    URL.revokeObjectURL(url);
  }

  // ---------- Acciones fiscales ----------

  abrirConfirmarEmision(): void {
    this.errorAccion.set('');
    this.confirmandoEmision.set(true);
    this.puntoEmisionSeleccionado = null;
    this.cargandoPuntos.set(true);
    this.configApi.listarPuntosEmision().subscribe({
      next: (puntos) => {
        this.puntosEmision.set((puntos ?? []).filter((p) => p.activo));
        this.cargandoPuntos.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.cargandoPuntos.set(false);
        this.errorAccion.set(this.problemDetail.mensaje(err));
      },
    });
  }

  cancelarEmision(): void {
    this.confirmandoEmision.set(false);
    this.puntoEmisionSeleccionado = null;
  }

  confirmarEmision(f: Factura): void {
    if (!this.puntoEmisionSeleccionado) return;
    this.errorAccion.set('');
    this.accionEnCurso.set('emitir');
    this.api.emitir(f.id, this.puntoEmisionSeleccionado).subscribe({
      next: (actualizada) => {
        this.accionEnCurso.set(null);
        this.confirmandoEmision.set(false);
        this.factura.set(actualizada);
        this.mostrarExito('Factura emitida correctamente.');
      },
      error: (err: HttpErrorResponse) => {
        this.accionEnCurso.set(null);
        this.errorAccion.set(this.problemDetail.mensaje(err));
      },
    });
  }

  /**
   * Cubre generar-xml / firmar / enviar-sri / sincronizar-sri: las cuatro
   * comparten la misma forma (sin cuerpo, devuelven la Factura releída) y el
   * mismo manejo de error — no hay motivo para 4 métodos casi idénticos.
   */
  ejecutar(f: Factura, accion: Exclude<AccionFiscal, 'emitir'>): void {
    this.errorAccion.set('');
    this.accionEnCurso.set(accion);

    const peticion =
      accion === 'generar-xml'
        ? this.api.generarXml(f.id)
        : accion === 'firmar'
        ? this.api.firmar(f.id)
        : accion === 'enviar-sri'
        ? this.api.enviarSri(f.id)
        : this.api.sincronizarSri(f.id);

    peticion.subscribe({
      next: (actualizada) => {
        this.accionEnCurso.set(null);
        this.confirmandoEnvioSri.set(false);
        this.factura.set(actualizada);
        this.mostrarExito(this.mensajeExitoPara(accion));
      },
      error: (err: HttpErrorResponse) => {
        this.accionEnCurso.set(null);
        this.confirmandoEnvioSri.set(false);
        // 502/504 del SRI ya llegan con un detail redactado por el backend
        // ("La factura conserva su numeración y puede reintentarse más
        // tarde") — nunca se reinterpreta aquí como "factura rechazada".
        this.errorAccion.set(this.problemDetail.mensaje(err));
      },
    });
  }

  private mensajeExitoPara(accion: Exclude<AccionFiscal, 'emitir'>): string {
    switch (accion) {
      case 'generar-xml':
        return 'XML generado correctamente.';
      case 'firmar':
        return 'Factura firmada correctamente.';
      case 'enviar-sri':
        return 'Factura enviada al SRI.';
      case 'sincronizar-sri':
        return 'Sincronización con el SRI completada.';
    }
  }

  private mostrarExito(mensaje: string): void {
    this.mensajeExito.set(mensaje);
    setTimeout(() => {
      if (this.mensajeExito() === mensaje) this.mensajeExito.set('');
    }, 4000);
  }

  // ---------- Bitácora ----------

  alternarBitacora(facturaId: number): void {
    const nuevoValor = !this.mostrarBitacora();
    this.mostrarBitacora.set(nuevoValor);
    if (nuevoValor && !this.bitacoraCargada) {
      this.cargandoBitacora.set(true);
      this.api.eventosSri(facturaId).subscribe({
        next: (eventos) => {
          this.eventosSri.set(eventos ?? []);
          this.bitacoraCargada = true;
          this.cargandoBitacora.set(false);
        },
        error: (err: HttpErrorResponse) => {
          this.cargandoBitacora.set(false);
          this.errorAccion.set(this.problemDetail.mensaje(err));
        },
      });
    }
  }
}
