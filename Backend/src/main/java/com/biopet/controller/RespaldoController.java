package com.biopet.controller;

import com.biopet.backup.dto.RespaldoConfiguracionRequest;
import com.biopet.backup.dto.RespaldoConfiguracionResponse;
import com.biopet.backup.dto.RespaldoHistorialResponse;
import com.biopet.backup.service.RespaldoConfiguracionService;
import com.biopet.backup.service.RespaldoEjecucionService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Modulo administrativo de respaldos PostgreSQL (Proyecto Final de
 * Administracion de Bases de Datos). Exclusivo de ROLE_ADMIN: ningun otro
 * rol tiene un caso de uso legitimo para disparar/configurar respaldos.
 *
 * <p>Sin relacion alguna con facturacion electronica/SRI: no lee ni escribe
 * ninguna tabla fiscal, y RespaldoEjecucionService excluye deliberadamente
 * el archivo binario del cuerpo de cualquier respuesta -el .dump se
 * descarga por fuera de la API, directamente del almacenamiento
 * configurado (BACKUP_STORAGE_PATH), nunca por HTTP.
 */
@RestController
@RequestMapping("/api/admin/respaldos")
public class RespaldoController {

    private final RespaldoConfiguracionService configuracionService;
    private final RespaldoEjecucionService ejecucionService;

    public RespaldoController(RespaldoConfiguracionService configuracionService,
                               RespaldoEjecucionService ejecucionService) {
        this.configuracionService = configuracionService;
        this.ejecucionService = ejecucionService;
    }

    @GetMapping("/configuracion")
    @PreAuthorize("hasRole('ADMIN')")
    public RespaldoConfiguracionResponse obtenerConfiguracion() {
        return configuracionService.obtener();
    }

    @PutMapping("/configuracion")
    @PreAuthorize("hasRole('ADMIN')")
    public RespaldoConfiguracionResponse guardarConfiguracion(@Valid @RequestBody RespaldoConfiguracionRequest request,
                                                                @AuthenticationPrincipal UserDetails userDetails) {
        return configuracionService.guardar(request, userDetails.getUsername());
    }

    /**
     * 202: el respaldo queda EN_PROCESO al responder -la ejecucion real
     * corre en segundo plano (RespaldoEjecucionService.ejecutarManual). El
     * cliente confirma el resultado consultando /historial.
     */
    @PostMapping("/ejecutar")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasRole('ADMIN')")
    public RespaldoHistorialResponse ejecutarManual(@AuthenticationPrincipal UserDetails userDetails) {
        return ejecucionService.ejecutarManual(userDetails.getUsername());
    }

    @GetMapping("/historial")
    @PreAuthorize("hasRole('ADMIN')")
    public Page<RespaldoHistorialResponse> historial(
            @PageableDefault(size = 20, sort = "iniciadoEn", direction = Sort.Direction.DESC) Pageable pageable) {
        return ejecucionService.historial(pageable);
    }
}
