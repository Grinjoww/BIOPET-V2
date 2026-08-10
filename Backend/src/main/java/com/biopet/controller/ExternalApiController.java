package com.biopet.controller;

import com.biopet.dto.ExternalApiResponse;
import com.biopet.integration.ExternalApiService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/externa/especies")
public class ExternalApiController {

    private final ExternalApiService externalApiService;

    public ExternalApiController(ExternalApiService externalApiService) {
        this.externalApiService = externalApiService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','VETERINARIO','AUXILIAR','DUENO')")
    public ExternalApiResponse infoEspecie(@RequestParam String especie) {
        return externalApiService.obtenerInfoEspecie(especie);
    }
}