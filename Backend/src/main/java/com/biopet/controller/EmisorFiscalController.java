package com.biopet.controller;

import com.biopet.facturacion.dto.EmisorFiscalRequest;
import com.biopet.facturacion.dto.EmisorFiscalResponse;
import com.biopet.facturacion.service.EmisorFiscalService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Configuracion del emisor fiscal (Fase 8B). ADMIN puede leer y actualizar;
 * AUXILIAR solo lee lo necesario para operar. Nunca expone ni acepta
 * certificado, contrasena, ruta de despliegue ni ambiente SRI -eso sigue
 * siendo exclusivamente configuracion de servidor-.
 */
@RestController
@RequestMapping("/api/facturacion/emisor")
public class EmisorFiscalController {

    private final EmisorFiscalService emisorFiscalService;

    public EmisorFiscalController(EmisorFiscalService emisorFiscalService) {
        this.emisorFiscalService = emisorFiscalService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','AUXILIAR')")
    public EmisorFiscalResponse obtener() {
        return emisorFiscalService.obtener();
    }

    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    public EmisorFiscalResponse actualizar(@Valid @RequestBody EmisorFiscalRequest request) {
        return emisorFiscalService.actualizar(request);
    }
}
