import {
  EstadoAutorizacionSri,
  EstadoFactura,
  EstadoRecepcionSri,
  FormaPagoSri,
  TipoDocumentoFactura,
  TipoIdentificacionSri,
} from './factura-api.service';

/**
 * Helpers de presentación VISUAL para el módulo de facturación (mismo
 * criterio que shared/presentacion.ts para Mascotas/Vacunas/Citas). Ninguno
 * de estos valores es un estado inventado: todos traducen a español o a una
 * clase de chip los CUATRO enums reales del backend
 * (EstadoFactura/EstadoRecepcionSri/EstadoAutorizacionSri/FormaPagoSri/
 * TipoIdentificacionSri/TipoDocumentoFactura) — nunca se agrega un valor que
 * el backend no pueda producir.
 */

export function etiquetaEstadoFactura(estado: EstadoFactura): string {
  switch (estado) {
    case 'BORRADOR':
      return 'Borrador';
    case 'EMITIDA':
      return 'Emitida';
    case 'AUTORIZADA':
      return 'Autorizada';
    case 'RECHAZADA':
      return 'Rechazada';
  }
}

export function chipClaseEstadoFactura(estado: EstadoFactura): string {
  switch (estado) {
    case 'BORRADOR':
      return 'chip--neutral';
    case 'EMITIDA':
      return 'chip--info';
    case 'AUTORIZADA':
      return 'chip--success';
    case 'RECHAZADA':
      return 'chip--danger';
  }
}

export function etiquetaEstadoRecepcion(estado: EstadoRecepcionSri): string {
  return estado === 'RECIBIDA' ? 'Recibida' : 'Devuelta';
}

export function chipClaseEstadoRecepcion(estado: EstadoRecepcionSri): string {
  return estado === 'RECIBIDA' ? 'chip--success' : 'chip--danger';
}

export function etiquetaEstadoAutorizacion(estado: EstadoAutorizacionSri): string {
  switch (estado) {
    case 'PPR':
      return 'En procesamiento';
    case 'AUT':
      return 'Autorizado';
    case 'NAT':
      return 'No autorizado';
  }
}

export function chipClaseEstadoAutorizacion(estado: EstadoAutorizacionSri): string {
  switch (estado) {
    case 'PPR':
      return 'chip--warning';
    case 'AUT':
      return 'chip--success';
    case 'NAT':
      return 'chip--danger';
  }
}

/**
 * Copiado literal de la TABLA 24 (Ficha v2.34) que ya usa el backend en
 * FormaPagoSri.descripcion() — no es una traducción libre.
 */
export function etiquetaFormaPago(forma: FormaPagoSri): string {
  switch (forma) {
    case 'SIN_UTILIZACION_SISTEMA_FINANCIERO':
      return 'Sin utilización del sistema financiero';
    case 'COMPENSACION_DEUDAS':
      return 'Compensación de deudas';
    case 'TARJETA_DEBITO':
      return 'Tarjeta de débito';
    case 'DINERO_ELECTRONICO':
      return 'Dinero electrónico';
    case 'TARJETA_PREPAGO':
      return 'Tarjeta prepago';
    case 'TARJETA_CREDITO':
      return 'Tarjeta de crédito';
    case 'OTROS_CON_SISTEMA_FINANCIERO':
      return 'Otros con utilización del sistema financiero';
    case 'ENDOSO_TITULOS':
      return 'Endoso de títulos';
  }
}

export function etiquetaTipoIdentificacion(tipo: TipoIdentificacionSri): string {
  switch (tipo) {
    case 'RUC':
      return 'RUC';
    case 'CEDULA':
      return 'Cédula';
    case 'PASAPORTE':
      return 'Pasaporte';
    case 'CONSUMIDOR_FINAL':
      return 'Consumidor final';
    case 'IDENTIFICACION_EXTERIOR':
      return 'Identificación del exterior';
  }
}

export function etiquetaTipoDocumento(tipo: TipoDocumentoFactura): string {
  switch (tipo) {
    case 'XML_GENERADO':
      return 'XML generado';
    case 'XML_FIRMADO':
      return 'XML firmado';
    case 'XML_AUTORIZADO':
      return 'XML autorizado';
    case 'RIDE_PDF':
      return 'RIDE (PDF)';
  }
}

/**
 * Gating REAL de las acciones fiscales (corrección pre-commit de la Fase 9):
 * cada condición está tomada literalmente de la precondición que el backend
 * ya aplica, no de una regla inventada en el cliente.
 *
 * <ul>
 *   <li>{@link puedeGenerarXml}/{@link puedeFirmar}/{@link puedeEnviarSri}
 *       exigen {@code estado === 'EMITIDA'} porque
 *       {@code FacturaSriService#enviar} lanza
 *       {@code FacturaNoEnviableException} para cualquier otro estado salvo
 *       AUTORIZADA (que ahí es un no-op idempotente, nunca una accion que
 *       tenga sentido volver a ofrecer) — y {@code FacturaXmlService#generarXml}
 *       /{@code FacturaFirmaService#firmarFactura} solo tienen sentido dentro
 *       de ese mismo tramo del pipeline.</li>
 *   <li>Cada una además exige el documento predecesor y la ausencia del
 *       documento que ella misma produce, vía {@code documentosDisponibles}
 *       real: nunca se ofrece Firmar sin XML_GENERADO, ni Enviar sin
 *       XML_FIRMADO, y una vez generado un documento su acción deja de
 *       mostrarse -el paso ya "resuelto" se ve en el pipeline visual con su
 *       marca de completado, no en un botón redundante-.</li>
 *   <li>{@link puedeSincronizarSri} es deliberadamente MÁS AMPLIO que "solo
 *       PPR": refleja la precondición real de
 *       {@code FacturaSriEstadoService#prepararSincronizacion} (estado
 *       distinto de BORRADOR + clave de acceso ya asignada) y de
 *       {@code FacturaSriService#sincronizar} (único corte: ya AUTORIZADA).
 *       El backend NO exige {@code estadoAutorizacion === 'PPR'} para
 *       aceptar la llamada -de hecho ni siquiera excluye RECHAZADA, que puede
 *       venir tanto de una devolucion definitiva en recepcion como de un NAT
 *       en autorizacion-, y "sincronizar" nunca reenvia nada, solo consulta:
 *       no hay ningun caso en el que ofrecerlo sea inseguro.
 *
 *       <p>Limitacion real y deliberada: {@code FacturaResponse} no distingue
 *       "nunca se intento enviar" de "se intento y fallo con un timeout/error
 *       tecnico antes de obtener respuesta de recepcion" (ambos casos dejan
 *       {@code estadoRecepcion}/{@code estadoAutorizacion} en {@code null});
 *       esa distincion solo vive en la bitacora
 *       ({@code GET /eventos-sri}, ADMIN/AUXILIAR), que este helper NO
 *       consulta a proposito -acoplaria el gating de un boton a una llamada
 *       HTTP adicional y a un endpoint con audiencia distinta-. Por eso el
 *       criterio se apoya solo en `estado`/`claveAcceso`, que es exactamente
 *       lo mismo que ya comprueba el backend antes de aceptar la peticion.</li>
 * </ul>
 */
export function puedeGenerarXml(estado: EstadoFactura, documentosDisponibles: TipoDocumentoFactura[]): boolean {
  return estado === 'EMITIDA' && !documentosDisponibles.includes('XML_GENERADO');
}

export function puedeFirmar(estado: EstadoFactura, documentosDisponibles: TipoDocumentoFactura[]): boolean {
  return (
    estado === 'EMITIDA' &&
    documentosDisponibles.includes('XML_GENERADO') &&
    !documentosDisponibles.includes('XML_FIRMADO')
  );
}

export function puedeEnviarSri(estado: EstadoFactura, documentosDisponibles: TipoDocumentoFactura[]): boolean {
  return estado === 'EMITIDA' && documentosDisponibles.includes('XML_FIRMADO');
}

export function puedeSincronizarSri(estado: EstadoFactura, claveAcceso: string | null): boolean {
  return estado !== 'AUTORIZADA' && claveAcceso != null;
}

/**
 * "001-001-000000001" a partir de establecimiento/puntoEmision/secuencial
 * REALES de la factura (nunca inventados): los tres son `null` hasta que se
 * emite, así que el llamador debe comprobarlo antes (ver
 * `Factura.secuencial`). No es un campo nuevo ni se persiste: es formato
 * visual puro, igual que `formatearExpediente` en shared/presentacion.ts.
 */
export function numeroComprobante(establecimiento: string, puntoEmision: string, secuencial: number): string {
  return `${establecimiento}-${puntoEmision}-${String(secuencial).padStart(9, '0')}`;
}

/**
 * Pasos del pipeline visual (sección 9 del encargo): se derivan de campos
 * reales de `Factura`, nunca de una bandera guardada en el cliente. El orden
 * es el del ciclo de vida real; RECHAZADA se representa aparte (ver
 * `pasoRechazado`) porque puede llegar desde más de un punto del pipeline
 * (recepción DEVUELTA o autorización NAT).
 */
export type PasoPipelineFactura =
  | 'borrador'
  | 'emitida'
  | 'xml-generado'
  | 'firmada'
  | 'enviada-sri'
  | 'autorizada';

export interface EstadoPasoPipeline {
  paso: PasoPipelineFactura;
  etiqueta: string;
  completado: boolean;
  actual: boolean;
}

const ETIQUETAS_PASO: Record<PasoPipelineFactura, string> = {
  borrador: 'Borrador',
  emitida: 'Emitida',
  'xml-generado': 'XML generado',
  firmada: 'Firmada',
  'enviada-sri': 'Enviada al SRI',
  autorizada: 'Autorizada',
};

/**
 * @param documentosDisponibles `Factura.documentosDisponibles` tal cual la
 *        devuelve el backend.
 */
export function pasosPipeline(
  estado: EstadoFactura,
  documentosDisponibles: TipoDocumentoFactura[],
  estadoRecepcion: EstadoRecepcionSri | null
): EstadoPasoPipeline[] {
  const tieneXmlGenerado = documentosDisponibles.includes('XML_GENERADO');
  const tieneXmlFirmado = documentosDisponibles.includes('XML_FIRMADO');
  // "Enviada al SRI" es un hecho (hubo al menos una respuesta de recepción),
  // no un estado propio de Factura: se deriva de que exista estadoRecepcion.
  const fueEnviada = estadoRecepcion != null;
  const autorizada = estado === 'AUTORIZADA';

  const completados: Record<PasoPipelineFactura, boolean> = {
    borrador: true,
    emitida: estado !== 'BORRADOR',
    'xml-generado': tieneXmlGenerado,
    firmada: tieneXmlFirmado,
    'enviada-sri': fueEnviada,
    autorizada,
  };

  const orden: PasoPipelineFactura[] = ['borrador', 'emitida', 'xml-generado', 'firmada', 'enviada-sri', 'autorizada'];
  // El paso "actual" es el primero de la lista, en orden, que todavía no está
  // completado; si todos lo están (AUTORIZADA), el actual es el último.
  const indiceActual = orden.findIndex((p) => !completados[p]);
  const actual = indiceActual === -1 ? orden[orden.length - 1] : orden[Math.max(0, indiceActual - 1)];

  return orden.map((paso) => ({
    paso,
    etiqueta: ETIQUETAS_PASO[paso],
    completado: completados[paso],
    actual: paso === actual,
  }));
}
