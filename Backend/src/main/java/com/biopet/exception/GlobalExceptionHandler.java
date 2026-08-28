package com.biopet.exception;

import com.biopet.facturacion.exception.AutorizacionSriInconsistenteException;
import com.biopet.facturacion.exception.CertificadoFirmaInvalidoException;
import com.biopet.facturacion.exception.ConceptoFacturableNoDisponibleException;
import com.biopet.facturacion.exception.ConfiguracionFiscalInvalidaException;
import com.biopet.facturacion.exception.DatosFacturacionInvalidosException;
import com.biopet.facturacion.exception.FacturaNoEditableException;
import com.biopet.facturacion.exception.FacturaNoEnviableException;
import com.biopet.facturacion.exception.FacturaXmlInvalidoException;
import com.biopet.facturacion.exception.FirmaElectronicaException;
import com.biopet.facturacion.exception.OrigenClinicoInvalidoException;
import com.biopet.facturacion.exception.PagosFacturaInvalidosException;
import com.biopet.facturacion.exception.SecuencialAgotadoException;
import com.biopet.facturacion.exception.SecuencialNoConfiguradoException;
import com.biopet.facturacion.exception.TarifaImpuestoAmbiguaException;
import com.biopet.facturacion.exception.TarifaImpuestoNoConfiguradaException;
import com.biopet.facturacion.exception.TitularFacturaInvalidoException;
import com.biopet.facturacion.sri.SriComunicacionException;
import com.biopet.facturacion.sri.TipoFalloSri;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(EmailDuplicadoException.class)
    public ResponseEntity<ProblemDetail> emailDuplicado(EmailDuplicadoException ex, HttpServletRequest request) {
        return problemResponse(HttpStatus.CONFLICT, ProblemType.CONFLICT, "Conflicto de datos", ex.getMessage(), request);
    }

    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ResponseEntity<ProblemDetail> noEncontrado(RecursoNoEncontradoException ex, HttpServletRequest request) {
        return problemResponse(HttpStatus.NOT_FOUND, ProblemType.NOT_FOUND, "Recurso no encontrado", ex.getMessage(), request);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ProblemDetail> credencialesInvalidas(BadCredentialsException ex, HttpServletRequest request) {
        return problemResponse(HttpStatus.UNAUTHORIZED, ProblemType.UNAUTHORIZED, "No autenticado", "Credenciales inválidas", request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> validacion(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, List<String>> errores = new LinkedHashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            errores.computeIfAbsent(fieldError.getField(), key -> new ArrayList<>())
                    .add(fieldError.getDefaultMessage());
        }

        ProblemDetail problemDetail = ProblemDetailFactory.build(
                HttpStatus.UNPROCESSABLE_ENTITY,
                ProblemType.VALIDATION,
                "Error de validación",
                "Uno o más campos contienen valores inválidos.",
                request
        );
        problemDetail.setProperty("errors", errores);

        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problemDetail);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> argumentoInvalido(IllegalArgumentException ex, HttpServletRequest request) {
        return problemResponse(HttpStatus.BAD_REQUEST, ProblemType.BAD_REQUEST, "Solicitud inválida", ex.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ProblemDetail> parametroInvalido(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        String detail = "El parámetro '" + ex.getName() + "' tiene un formato inválido.";
        return problemResponse(HttpStatus.BAD_REQUEST, ProblemType.BAD_REQUEST, "Parámetro inválido", detail, request);
    }

    @ExceptionHandler(RateLimitExcedidoException.class)
    public ResponseEntity<ProblemDetail> demasiadosIntentos(RateLimitExcedidoException ex, HttpServletRequest request) {
        String detail = (ex.getRecurso() == RateLimitExcedidoException.Recurso.REGISTRO)
                ? "Se ha excedido el número máximo de solicitudes de registro. Intente nuevamente más tarde."
                : "Se ha excedido el número máximo de intentos fallidos de inicio de sesión. Intente nuevamente más tarde.";
        ProblemDetail problemDetail = ProblemDetailFactory.build(
                HttpStatus.TOO_MANY_REQUESTS,
                ProblemType.RATE_LIMITED,
                "Demasiados intentos",
                detail,
                request
        );
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getSegundosRestantes()))
                .body(problemDetail);
    }

    private ResponseEntity<ProblemDetail> problemResponse(HttpStatus status, ProblemType type, String title,
                                                            String detail, HttpServletRequest request) {
        ProblemDetail problemDetail = ProblemDetailFactory.build(status, type, title, detail, request);
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problemDetail);
    }
    @ExceptionHandler(ExternalApiException.class)
public ResponseEntity<ProblemDetail> errorApiExterna(ExternalApiException ex, HttpServletRequest request) {
    return problemResponse(HttpStatus.BAD_GATEWAY, ProblemType.BAD_GATEWAY,
            "Servicio externo no disponible",
            "No se pudo obtener información de la especie en este momento. Intente nuevamente más tarde.",
            request);
}

    // ======================================================================
    // Facturación electrónica (Fase 8A)
    // ======================================================================

    /**
     * El SRI no dio una respuesta funcional: timeout, fallo de conexión, SOAP
     * Fault o un cuerpo que no encaja con el contrato publicado. En NINGÚN
     * caso es un rechazo del comprobante -la factura conserva clave,
     * secuencial y XML firmado-, así que el detalle es genérico y nunca
     * {@code ex.getMessage()}: no hay nada del SRI que deba llegar tal cual al
     * cliente HTTP.
     *
     * <p>Solo TIMEOUT se distingue con 504: es el único caso en el que el
     * comprobante puede haber llegado igualmente al SRI sin que BIOPET lo
     * sepa, y esa diferencia le importa a quien reintenta. El resto (fallo de
     * conexión, SOAP Fault, respuesta fuera de contrato) es 502: BIOPET actuó
     * de cliente correctamente, el servicio de aguas arriba fue el que falló.
     */
    @ExceptionHandler(SriComunicacionException.class)
    public ResponseEntity<ProblemDetail> falloComunicacionSri(SriComunicacionException ex, HttpServletRequest request) {
        if (ex.getTipo() == TipoFalloSri.TIMEOUT) {
            return problemResponse(HttpStatus.GATEWAY_TIMEOUT, ProblemType.GATEWAY_TIMEOUT,
                    "Tiempo de espera agotado",
                    "El SRI no respondió a tiempo. La factura conserva su numeración y puede reintentarse más tarde.",
                    request);
        }
        return problemResponse(HttpStatus.BAD_GATEWAY, ProblemType.BAD_GATEWAY,
                "Servicio del SRI no disponible",
                "No se pudo completar la comunicación con el SRI. La factura conserva su numeración y puede "
                        + "reintentarse más tarde.",
                request);
    }

    /**
     * Fallos de la firma electrónica: certificado no configurado o inválido, o
     * una firma que no verifica. Es un problema del SERVIDOR (configuración o
     * estado interno), nunca del cliente HTTP -por eso 500 y no 400/409-, y el
     * detalle es SIEMPRE genérico: la ruta del .p12, su contraseña o el motivo
     * criptográfico exacto no deben llegar nunca a una respuesta HTTP, aunque
     * los mensajes de estas excepciones ya son cuidadosos al respecto (ver sus
     * javadoc en el módulo de firma).
     */
    @ExceptionHandler({CertificadoFirmaInvalidoException.class, FirmaElectronicaException.class})
    public ResponseEntity<ProblemDetail> errorFirmaElectronica(RuntimeException ex, HttpServletRequest request) {
        return problemResponse(HttpStatus.INTERNAL_SERVER_ERROR, ProblemType.INTERNAL,
                "Error en la firma electrónica",
                "No se pudo completar la operación de firma electrónica. Contacte al administrador.",
                request);
    }

    /**
     * La factura (o el contador, o la configuración fiscal) no está en
     * condiciones de continuar con la operación pedida: transición de estado
     * inválida, reenvío al SRI de algo que no procede, una respuesta de
     * autorización que contradice lo ya archivado, un XML que no se puede
     * producir o validar, o un secuencial/tarifa que el sistema no puede
     * resolver ahora mismo. Todas comparten el mismo desenlace HTTP -409,
     * conflicto con el estado actual del recurso- y sus mensajes ya están
     * redactados para el usuario final (ver el javadoc de cada excepción).
     */
    @ExceptionHandler({
            FacturaNoEditableException.class,
            FacturaNoEnviableException.class,
            AutorizacionSriInconsistenteException.class,
            FacturaXmlInvalidoException.class,
            SecuencialAgotadoException.class,
            SecuencialNoConfiguradoException.class,
            ConfiguracionFiscalInvalidaException.class,
            TarifaImpuestoNoConfiguradaException.class,
            TarifaImpuestoAmbiguaException.class
    })
    public ResponseEntity<ProblemDetail> conflictoFiscal(RuntimeException ex, HttpServletRequest request) {
        return problemResponse(HttpStatus.CONFLICT, ProblemType.CONFLICT,
                "Conflicto con el estado de la factura", ex.getMessage(), request);
    }

    /**
     * Los datos que el cliente eligió para construir el borrador no son
     * válidos: un concepto dado de baja, una mascota que no es suya, unos
     * pagos que no cuadran con el total, unos datos de facturación que no le
     * pertenecen, o un origen clínico incoherente. A diferencia del grupo de
     * arriba, aquí SÍ es una elección del cliente la que falla, de ahí 400 y
     * no 409.
     */
    @ExceptionHandler({
            ConceptoFacturableNoDisponibleException.class,
            TitularFacturaInvalidoException.class,
            PagosFacturaInvalidosException.class,
            DatosFacturacionInvalidosException.class,
            OrigenClinicoInvalidoException.class
    })
    public ResponseEntity<ProblemDetail> datosFacturaInvalidos(RuntimeException ex, HttpServletRequest request) {
        return problemResponse(HttpStatus.BAD_REQUEST, ProblemType.BAD_REQUEST, "Solicitud inválida",
                ex.getMessage(), request);
    }
}
