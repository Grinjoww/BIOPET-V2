package com.biopet.facturacion.service;

import com.biopet.facturacion.domain.CodigoImpuestoSri;
import com.biopet.facturacion.entity.TarifaImpuesto;
import com.biopet.facturacion.exception.TarifaImpuestoAmbiguaException;
import com.biopet.facturacion.exception.TarifaImpuestoNoConfiguradaException;
import com.biopet.facturacion.repository.TarifaImpuestoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Prueba con doble del repository de la regla del resolutor: debe haber
 * EXACTAMENTE una tarifa vigente.
 *
 * <p>La consulta por fecha en si (que el SQL cubra bien los limites de
 * vigencia) se prueba contra PostgreSQL en
 * {@code FacturaBorradorServiceIntegrationTest} y
 * {@code FacturaEmisionServiceIntegrationTest}; aqui solo se fija la decision
 * que toma el servicio ante 0, 1 o N resultados.
 */
class TarifaImpuestoResolverTest {

    private static final LocalDate FECHA = LocalDate.of(2026, 9, 15);

    private TarifaImpuestoRepository repository;
    private TarifaImpuestoResolver resolver;

    @BeforeEach
    void setUp() {
        repository = mock(TarifaImpuestoRepository.class);
        resolver = new TarifaImpuestoResolver(repository);
    }

    @Test
    void conUnaUnicaTarifaVigenteLaDevuelve() {
        TarifaImpuesto tarifa = tarifa("15.00");
        when(repository.findAplicables(CodigoImpuestoSri.IVA, "4", FECHA))
                .thenReturn(List.of(tarifa));

        assertThat(resolver.resolver(CodigoImpuestoSri.IVA, "4", FECHA)).isSameAs(tarifa);
    }

    @Test
    void sinNingunaTarifaVigenteFallaComoConfiguracionAusente() {
        when(repository.findAplicables(any(), any(), any())).thenReturn(List.of());

        assertThatThrownBy(() -> resolver.resolver(CodigoImpuestoSri.IVA, "4", FECHA))
                .isInstanceOf(TarifaImpuestoNoConfiguradaException.class)
                .hasMessageContaining("2026-09-15");
    }

    @Test
    void conDosTarifasSolapadasFallaEnLugarDeQuedarseConLaPrimera() {
        // Esta es la razon de ser de la clase: un findFirst() habria devuelto
        // 12.00 en silencio y la factura se habria emitido con una tarifa
        // elegida por el orden de las filas.
        when(repository.findAplicables(any(), any(), any()))
                .thenReturn(List.of(tarifa("12.00"), tarifa("15.00")));

        assertThatThrownBy(() -> resolver.resolver(CodigoImpuestoSri.IVA, "4", FECHA))
                .isInstanceOf(TarifaImpuestoAmbiguaException.class)
                .hasMessageContaining("2")
                .hasMessageContaining("disjuntos");
    }

    @Test
    void losArgumentosSonObligatorios() {
        assertThatThrownBy(() -> resolver.resolver(null, "4", FECHA))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> resolver.resolver(CodigoImpuestoSri.IVA, null, FECHA))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> resolver.resolver(CodigoImpuestoSri.IVA, "4", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private TarifaImpuesto tarifa(String valor) {
        return TarifaImpuesto.builder()
                .codigoImpuesto(CodigoImpuestoSri.IVA)
                .codigoPorcentaje("4")
                .descripcion("Tarifa ficticia")
                .tarifa(new BigDecimal(valor))
                .vigenteDesde(LocalDate.of(2020, 1, 1))
                .activo(true)
                .build();
    }
}
