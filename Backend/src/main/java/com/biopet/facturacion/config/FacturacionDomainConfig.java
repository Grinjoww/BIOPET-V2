package com.biopet.facturacion.config;

import com.biopet.facturacion.domain.CalculoFacturaService;
import com.biopet.facturacion.domain.ClaveAccesoGenerator;
import com.biopet.facturacion.domain.CodigoNumericoGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Publica como beans las tres clases puras del nucleo fiscal de la Fase 2.
 *
 * <p>Se hace aqui, y no anotando esas clases con {@code @Component}, para no
 * tocar la Fase 2: {@code com.biopet.facturacion.domain} es codigo sin Spring,
 * sin JPA y sin red, que se instancia con {@code new} y se prueba sin contexto.
 * Meterle anotaciones de framework le quitaria justo la propiedad que lo hace
 * facil de razonar. La decision de convertirlas en beans es de la capa de
 * servicio, y por tanto vive en la capa de servicio.
 *
 * <p>Efecto util adicional: {@link CodigoNumericoGenerator} pasa a ser un punto
 * de inyeccion, de modo que un test puede sustituirlo por uno determinista y
 * afirmar la clave de acceso exacta. Sin este bean haria falta un
 * {@code if (perfil == test)} dentro del servicio, que es justo lo que no
 * queremos.
 */
@Configuration
public class FacturacionDomainConfig {

    /** Sin estado y sin dependencias: una sola instancia sirve para todo. */
    @Bean
    public CalculoFacturaService calculoFacturaService() {
        return new CalculoFacturaService();
    }

    @Bean
    public ClaveAccesoGenerator claveAccesoGenerator() {
        return new ClaveAccesoGenerator();
    }

    /**
     * Constructor por defecto = {@code SecureRandom}. El codigo numerico es,
     * segun la Ficha v2.34, "un mecanismo para brindar seguridad al emisor":
     * debe ser impredecible en produccion.
     */
    @Bean
    public CodigoNumericoGenerator codigoNumericoGenerator() {
        return new CodigoNumericoGenerator();
    }
}
