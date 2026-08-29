import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { CodigoImpuestoSri } from './factura-api.service';

/** Espejo de TipoConceptoFacturable (com.biopet.facturacion.entity). */
export type TipoConceptoFacturable = 'CONSULTA' | 'VACUNA' | 'PROCEDIMIENTO' | 'MEDICAMENTO' | 'PRODUCTO' | 'OTRO';

/** Espejo de ConceptoFacturableResponse. */
export interface ConceptoFacturable {
  id: number;
  codigo: string;
  descripcion: string;
  tipo: TipoConceptoFacturable;
  precioUnitario: number;
  codigoImpuesto: CodigoImpuestoSri;
  codigoPorcentaje: string;
  activo: boolean;
}

/** Espejo de ConceptoFacturableRequest (mismo cuerpo para alta y edición). */
export interface ConceptoFacturableRequestPayload {
  codigo: string;
  descripcion: string;
  tipo: TipoConceptoFacturable;
  precioUnitario: number;
  codigoImpuesto: CodigoImpuestoSri;
  codigoPorcentaje: string;
}

/** Espejo de PuntoEmisionResponse. Deliberadamente sin ningún dato de SecuencialEmision. */
export interface PuntoEmision {
  id: number;
  emisorFiscalId: number;
  establecimiento: string;
  puntoEmision: string;
  direccionEstablecimiento: string | null;
  activo: boolean;
}

/** Espejo de PuntoEmisionRequest (alta: serie fiscal inmutable después). */
export interface PuntoEmisionRequestPayload {
  emisorFiscalId: number;
  establecimiento: string;
  puntoEmision: string;
  direccionEstablecimiento: string | null;
}

/** Espejo de ActualizarPuntoEmisionRequest: solo la dirección es editable. */
export interface ActualizarPuntoEmisionRequestPayload {
  direccionEstablecimiento: string | null;
}

/** Espejo de TarifaImpuestoResponse. vigenteHasta null = vigencia abierta. */
export interface TarifaImpuesto {
  id: number;
  codigoImpuesto: CodigoImpuestoSri;
  codigoPorcentaje: string;
  descripcion: string;
  tarifa: number;
  vigenteDesde: string;
  vigenteHasta: string | null;
  activo: boolean;
}

/** Espejo de TarifaImpuestoRequest: SIEMPRE abre una vigencia nueva, nunca edita una fila histórica. */
export interface TarifaImpuestoRequestPayload {
  codigoImpuesto: CodigoImpuestoSri;
  codigoPorcentaje: string;
  descripcion: string;
  tarifa: number;
  vigenteDesde: string;
}

/** Espejo de EmisorFiscalResponse. Nunca certificado/password/ambiente: eso es config de servidor. */
export interface EmisorFiscal {
  id: number;
  ruc: string;
  razonSocial: string;
  nombreComercial: string | null;
  direccionMatriz: string;
  obligadoContabilidad: boolean;
  contribuyenteEspecial: string | null;
  rimpe: boolean;
  agenteRetencionResolucion: string | null;
  activo: boolean;
}

/** Espejo de EmisorFiscalRequest (PUT hace upsert de la fila única). */
export interface EmisorFiscalRequestPayload {
  ruc: string;
  razonSocial: string;
  nombreComercial: string | null;
  direccionMatriz: string;
  obligadoContabilidad: boolean;
  contribuyenteEspecial: string | null;
  rimpe: boolean;
  agenteRetencionResolucion: string | null;
  activo: boolean;
}

/**
 * Encapsula /api/facturacion/{conceptos,puntos-emision,tarifas,emisor} (Fase
 * 8B). Un solo servicio, no cuatro: los cuatro catálogos solo se usan juntos
 * en la pantalla de Configuración fiscal y cada uno es demasiado pequeño (2-5
 * métodos) para justificar una clase — y un service propio — por separado.
 *
 * IMPORTANTE: ningún método de aquí acepta ni envía `ultimoSecuencial`,
 * `siguienteSecuencial` ni `ambiente` — esos campos no existen en los DTOs
 * reales del backend (ver PuntoEmisionRequest) y este servicio no los inventa.
 */
@Injectable({ providedIn: 'root' })
export class FacturacionConfigApiService {
  private readonly baseConceptos = '/api/facturacion/conceptos';
  private readonly basePuntos = '/api/facturacion/puntos-emision';
  private readonly baseTarifas = '/api/facturacion/tarifas';
  private readonly baseEmisor = '/api/facturacion/emisor';

  constructor(private http: HttpClient) {}

  // ---------- Conceptos facturables ----------

  listarConceptos(activo?: boolean, tipo?: TipoConceptoFacturable): Observable<ConceptoFacturable[]> {
    let params = new HttpParams();
    if (activo != null) params = params.set('activo', activo);
    if (tipo) params = params.set('tipo', tipo);
    return this.http.get<ConceptoFacturable[]>(this.baseConceptos, { params });
  }

  buscarConcepto(id: number): Observable<ConceptoFacturable> {
    return this.http.get<ConceptoFacturable>(`${this.baseConceptos}/${id}`);
  }

  crearConcepto(payload: ConceptoFacturableRequestPayload): Observable<ConceptoFacturable> {
    return this.http.post<ConceptoFacturable>(this.baseConceptos, payload);
  }

  actualizarConcepto(id: number, payload: ConceptoFacturableRequestPayload): Observable<ConceptoFacturable> {
    return this.http.put<ConceptoFacturable>(`${this.baseConceptos}/${id}`, payload);
  }

  cambiarEstadoConcepto(id: number, activo: boolean): Observable<ConceptoFacturable> {
    return this.http.patch<ConceptoFacturable>(`${this.baseConceptos}/${id}/estado`, { activo });
  }

  // ---------- Puntos de emisión ----------

  listarPuntosEmision(): Observable<PuntoEmision[]> {
    return this.http.get<PuntoEmision[]>(this.basePuntos);
  }

  crearPuntoEmision(payload: PuntoEmisionRequestPayload): Observable<PuntoEmision> {
    return this.http.post<PuntoEmision>(this.basePuntos, payload);
  }

  actualizarPuntoEmision(id: number, payload: ActualizarPuntoEmisionRequestPayload): Observable<PuntoEmision> {
    return this.http.put<PuntoEmision>(`${this.basePuntos}/${id}`, payload);
  }

  cambiarEstadoPuntoEmision(id: number, activo: boolean): Observable<PuntoEmision> {
    return this.http.patch<PuntoEmision>(`${this.basePuntos}/${id}/estado`, { activo });
  }

  // ---------- Tarifas de impuesto ----------

  listarTarifas(): Observable<TarifaImpuesto[]> {
    return this.http.get<TarifaImpuesto[]>(this.baseTarifas);
  }

  /** Siempre ABRE una vigencia nueva; el backend cierra sola la anterior si la había (ver TarifaImpuestoService). */
  crearTarifa(payload: TarifaImpuestoRequestPayload): Observable<TarifaImpuesto> {
    return this.http.post<TarifaImpuesto>(this.baseTarifas, payload);
  }

  cambiarEstadoTarifa(id: number, activo: boolean): Observable<TarifaImpuesto> {
    return this.http.patch<TarifaImpuesto>(`${this.baseTarifas}/${id}/estado`, { activo });
  }

  // ---------- Emisor fiscal ----------

  obtenerEmisor(): Observable<EmisorFiscal> {
    return this.http.get<EmisorFiscal>(this.baseEmisor);
  }

  actualizarEmisor(payload: EmisorFiscalRequestPayload): Observable<EmisorFiscal> {
    return this.http.put<EmisorFiscal>(this.baseEmisor, payload);
  }
}
