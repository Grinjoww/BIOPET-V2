package com.biopet.repository;

import java.time.Instant;
import java.time.LocalDate;

public interface HistorialClinico {
    String getMascota();
    String getEspecie();
    String getRaza();
    String getDuenio();
    Long getNConsultas();
    Instant getUltimaConsulta();
    String getUltimaVacuna();
    LocalDate getProximaVacuna();
    Long getNCitas();
}
