import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { PageResponse } from './mascota-api.service';

/**
 * Espejos EXACTOS de los enums reales del backend (com.biopet.facturacion.*).
 * Jackson los serializa por el NOMBRE de la constante (sin @JsonValue en
 * ninguno de ellos), nunca por su código SRI de 1-2 dígitos: por eso estas
 * uniones usan 'TARJETA_DEBITO', no '16'.
 */
export type EstadoFactura = 'BORRADOR' | 'EMITIDA' | 'AUTORIZADA' | 'RECHAZADA';
export type EstadoRecepcionSri = 'RECIBIDA' | 'DEVUELTA';
export type EstadoAutorizacionSri = 'PPR' | 'AUT' | 'NAT';
export type TipoDocumentoFactura = 'XML_GENERADO' | 'XML_FIRMADO' | 'XML_AUTORIZADO' | 'RIDE_PDF';
export type OrigenDetalleFactura = 'CONSULTA' | 'VACUNA' | 'CITA';
export type OperacionSri = 'RECEPCION' | 'AUTORIZACION';
export type ResultadoEventoSri = 'RECIBIDA' | 'DEVUELTA' | 'AUT' | 'NAT' | 'PPR' | 'ERROR_TECNICO' | 'TIMEOUT';
export type AmbienteSri = 'PRUEBAS' | 'PRODUCCION';
export type CodigoImpuestoSri = 'IVA' | 'ICE' | 'IRBPNR';
export type TipoIdentificacionSri = 'RUC' | 'CEDULA' | 'PASAPORTE' | 'CONSUMIDOR_FINAL' | 'IDENTIFICACION_EXTERIOR';

/**
 * Los 8 códigos vigentes de la TABLA 24 (FormaPagoSri.java). Igual que los
 * demás enums, viaja por nombre de constante, no por código de 2 dígitos.
 */
export type FormaPagoSri =
  | 'SIN_UTILIZACION_SISTEMA_FINANCIERO'
  | 'COMPENSACION_DEUDAS'
  | 'TARJETA_DEBITO'
  | 'DINERO_ELECTRONICO'
  | 'TARJETA_PREPAGO'
  | 'TARJETA_CREDITO'
  | 'OTROS_CON_SISTEMA_FINANCIERO'
  | 'ENDOSO_TITULOS';

/** Espejo de FacturaDetalleResponse. Solo lectura. */
export interface FacturaDetalle {
  linea: number;
  conceptoFacturableId: number | null;
  codigoPrincipal: string;
  descripcion: string;
  cantidad: number;
  precioUnitario: number;
  descuento: number;
  precioTotalSinImpuesto: number;
  impuestoCodigo: CodigoImpuestoSri;
  impuestoCodigoPorcentaje: string;
  impuestoTarifa: number;
  baseImponible: number;
  impuestoValor: number;
  origenTipo: OrigenDetalleFactura | null;
  origenId: number | null;
}

/** Espejo de FacturaPagoResponse. Solo lectura. */
export interface FacturaPago {
  formaPago: FormaPagoSri;
  total: number;
  plazo: number | null;
  unidadTiempo: string | null;
}

/** Espejo de FacturaEventoSriResponse (GET /api/facturas/{id}/eventos-sri). */
export interface FacturaEventoSri {
  id: number;
  operacion: OperacionSri;
  resultado: ResultadoEventoSri;
  /** JSON estructurado tal cual lo expone el backend (JsonNode); forma libre. */
  mensajes: unknown;
  duracionMs: number | null;
  intento: number;
  creadoEn: string;
}

/**
 * Espejo EXACTO de FacturaResponse (campos planos con prefijo, sin records
 * anidados — mismo criterio documentado en el backend). Nunca se inventa
 * ningún campo adicional; lo que el backend no expone (XML, certificado,
 * password) tampoco existe aquí.
 */
export interface Factura {
  id: number;
  estado: EstadoFactura;
  usuarioId: number | null;

  ambiente: AmbienteSri | null;
  establecimiento: string | null;
  puntoEmision: string | null;
  secuencial: number | null;
  codigoNumerico: string | null;
  claveAcceso: string | null;
  fechaEmision: string | null;

  compradorTipoIdentificacion: TipoIdentificacionSri | null;
  compradorIdentificacion: string | null;
  compradorRazonSocial: string | null;
  compradorDireccion: string | null;
  compradorEmail: string | null;
  compradorTelefono: string | null;

  mascotaId: number | null;
  mascotaNombre: string | null;

  detalles: FacturaDetalle[];
  pagos: FacturaPago[];

  totalSinImpuestos: number | null;
  totalDescuento: number | null;
  totalImpuestos: number | null;
  importeTotal: number | null;
  moneda: string | null;

  estadoRecepcion: EstadoRecepcionSri | null;
  estadoAutorizacion: EstadoAutorizacionSri | null;
  numeroAutorizacion: string | null;
  fechaAutorizacion: string | null;
  proximoIntentoEn: string | null;
  intentosAutorizacion: number | null;

  documentosDisponibles: TipoDocumentoFactura[];

  creadoEn: string;
  actualizadoEn: string;
}

/** Espejo de CrearFacturaRequest. */
export interface CrearFacturaRequestPayload {
  usuarioId: number;
  mascotaId: number | null;
  fechaEmision: string;
}

/** Espejo de ActualizarFacturaRequest. */
export interface ActualizarFacturaRequestPayload {
  mascotaId: number | null;
  fechaEmision: string;
}

/** Espejo de DetalleFacturaRequest: SIN precio, SIN impuesto, SIN descripción — eso lo pone el backend. */
export interface DetalleFacturaRequestPayload {
  conceptoFacturableId: number;
  cantidad: number;
  descuento: number | null;
  origenTipo: OrigenDetalleFactura | null;
  origenId: number | null;
}

/** Espejo de PagoFacturaRequest. */
export interface PagoFacturaRequestPayload {
  formaPago: FormaPagoSri;
  total: number;
  plazo: number | null;
  unidadTiempo: string | null;
}

/** Filtros opcionales de GET /api/facturas — reflejan literalmente los @RequestParam de FacturaController. */
export interface FiltrosFacturas {
  estado?: EstadoFactura;
  usuarioId?: number;
  mascotaId?: number;
  fechaEmision?: string;
}

/**
 * Encapsula TODO el pipeline REST de /api/facturas (Fase 8A). El componente
 * de UI nunca arma la URL de un documento ni decide cómo mapear un
 * ProblemDetail: solo llama a estos métodos y reacciona a la respuesta real.
 *
 * Ninguna acción de este servicio actualiza el estado de una Factura en el
 * cliente: cada método SIEMPRE recibe la Factura completa recién releída del
 * backend (ver el javadoc de FacturaController sobre por qué cada escritura
 * se relee, no se reconstruye a mano) y es responsabilidad del componente
 * volcarla tal cual — nunca "simular" una transición de estado en el cliente.
 */
@Injectable({ providedIn: 'root' })
export class FacturaApiService {
  private readonly base = '/api/facturas';

  constructor(private http: HttpClient) {}

  // ---------- Consulta ----------

  listar(pagina: number, tamano: number, filtros: FiltrosFacturas = {}, sort = 'id,desc'): Observable<PageResponse<Factura>> {
    let params = new HttpParams().set('page', pagina).set('size', tamano).set('sort', sort);
    if (filtros.estado) params = params.set('estado', filtros.estado);
    if (filtros.usuarioId != null) params = params.set('usuarioId', filtros.usuarioId);
    if (filtros.mascotaId != null) params = params.set('mascotaId', filtros.mascotaId);
    if (filtros.fechaEmision) params = params.set('fechaEmision', filtros.fechaEmision);
    return this.http.get<PageResponse<Factura>>(this.base, { params });
  }

  buscar(id: number): Observable<Factura> {
    return this.http.get<Factura>(`${this.base}/${id}`);
  }

  // ---------- Borrador ----------

  crear(payload: CrearFacturaRequestPayload): Observable<Factura> {
    return this.http.post<Factura>(this.base, payload);
  }

  actualizar(id: number, payload: ActualizarFacturaRequestPayload): Observable<Factura> {
    return this.http.put<Factura>(`${this.base}/${id}`, payload);
  }

  seleccionarComprador(id: number, datosFacturacionId: number): Observable<Factura> {
    return this.http.post<Factura>(`${this.base}/${id}/comprador`, { datosFacturacionId });
  }

  /** PUT: sustituye TODAS las líneas del borrador de una vez (el backend no ofrece alta/baja línea a línea). */
  reemplazarDetalles(id: number, detalles: DetalleFacturaRequestPayload[]): Observable<Factura> {
    return this.http.put<Factura>(`${this.base}/${id}/detalles`, { detalles });
  }

  /** PUT: sustituye TODAS las formas de pago de una vez. */
  reemplazarPagos(id: number, pagos: PagoFacturaRequestPayload[]): Observable<Factura> {
    return this.http.put<Factura>(`${this.base}/${id}/pagos`, { pagos });
  }

  // ---------- Pipeline fiscal ----------

  /**
   * Nunca envía "ambiente": PRUEBAS/PRODUCCION lo resuelve el backend
   * (SriAmbienteProperties). EmitirFacturaRequest solo acepta puntoEmisionId.
   */
  emitir(id: number, puntoEmisionId: number): Observable<Factura> {
    return this.http.post<Factura>(`${this.base}/${id}/emitir`, { puntoEmisionId });
  }

  generarXml(id: number): Observable<Factura> {
    return this.http.post<Factura>(`${this.base}/${id}/generar-xml`, {});
  }

  firmar(id: number): Observable<Factura> {
    return this.http.post<Factura>(`${this.base}/${id}/firmar`, {});
  }

  enviarSri(id: number): Observable<Factura> {
    return this.http.post<Factura>(`${this.base}/${id}/enviar-sri`, {});
  }

  sincronizarSri(id: number): Observable<Factura> {
    return this.http.post<Factura>(`${this.base}/${id}/sincronizar-sri`, {});
  }

  // ---------- Documentos y bitácora ----------

  /**
   * Descarga los bytes EXACTOS ya persistidos: nunca se regenera ni se
   * parsea el XML en el cliente, solo se pide como Blob y se entrega al
   * navegador tal cual. Solo debe llamarse para un tipo que ya figure en
   * `Factura.documentosDisponibles` (el propio componente lo comprueba antes).
   */
  descargarDocumento(id: number, tipo: TipoDocumentoFactura): Observable<Blob> {
    return this.http.get(`${this.base}/${id}/documentos/${tipo}`, { responseType: 'blob' });
  }

  /**
   * RIDE (representación impresa, Fase 10). A diferencia de
   * {@link descargarDocumento}, este endpoint SÍ puede generar el PDF la
   * primera vez que se pide (backend idempotente: la primera llamada genera y
   * persiste, las siguientes devuelven los mismos bytes) — nunca se genera
   * nada aquí en el cliente, solo se pide el resultado como Blob.
   */
  descargarRide(id: number): Observable<Blob> {
    return this.http.get(`${this.base}/${id}/ride`, { responseType: 'blob' });
  }

  /** Restringido en el backend a ADMIN/AUXILIAR; el componente solo lo llama si el rol corresponde. */
  eventosSri(id: number): Observable<FacturaEventoSri[]> {
    return this.http.get<FacturaEventoSri[]>(`${this.base}/${id}/eventos-sri`);
  }
}
