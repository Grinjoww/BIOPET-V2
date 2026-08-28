package com.biopet.facturacion.dto;

import com.biopet.facturacion.domain.AmbienteSri;
import com.biopet.facturacion.entity.EstadoAutorizacionSri;
import com.biopet.facturacion.entity.EstadoFactura;
import com.biopet.facturacion.entity.EstadoRecepcionSri;
import com.biopet.facturacion.entity.TipoDocumentoFactura;
import com.biopet.facturacion.entity.TipoIdentificacionSri;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Representacion de una factura para el exterior. Nunca la entidad JPA.
 *
 * <p>Deliberadamente NO incluye: {@code byte[]} de ningun documento, el XML
 * completo, la ruta o password del certificado, ni ningun dato que no viva ya
 * en {@code Factura}. {@code documentosDisponibles} son solo los TIPOS que
 * existen (metadatos), nunca su contenido -eso se pide aparte, por
 * {@code GET /api/facturas/{id}/documentos/{tipo}}-.
 *
 * <p>Campos planos con prefijo ({@code compradorX}, {@code mascotaX}), mismo
 * criterio que {@code CitaResponse} en el resto del proyecto: sin records
 * anidados.
 */
public record FacturaResponse(
        Long id,
        EstadoFactura estado,
        Long usuarioId,

        // Numeracion fiscal (null hasta emitir)
        AmbienteSri ambiente,
        String establecimiento,
        String puntoEmision,
        Long secuencial,
        String codigoNumerico,
        String claveAcceso,
        LocalDate fechaEmision,

        // Comprador (snapshot)
        TipoIdentificacionSri compradorTipoIdentificacion,
        String compradorIdentificacion,
        String compradorRazonSocial,
        String compradorDireccion,
        String compradorEmail,
        String compradorTelefono,

        // Mascota (opcional)
        Long mascotaId,
        String mascotaNombre,

        // Lineas y pagos
        List<FacturaDetalleResponse> detalles,
        List<FacturaPagoResponse> pagos,

        // Totales
        BigDecimal totalSinImpuestos,
        BigDecimal totalDescuento,
        BigDecimal totalImpuestos,
        BigDecimal importeTotal,
        String moneda,

        // Estado frente al SRI
        EstadoRecepcionSri estadoRecepcion,
        EstadoAutorizacionSri estadoAutorizacion,
        String numeroAutorizacion,
        Instant fechaAutorizacion,
        Instant proximoIntentoEn,
        Integer intentosAutorizacion,

        // Documentos: solo los TIPOS ya generados, nunca su contenido.
        List<TipoDocumentoFactura> documentosDisponibles,

        Instant creadoEn,
        Instant actualizadoEn
) {
}
