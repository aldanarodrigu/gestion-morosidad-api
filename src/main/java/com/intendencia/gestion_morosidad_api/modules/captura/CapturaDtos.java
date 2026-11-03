package com.intendencia.gestion_morosidad_api.modules.captura;

import com.intendencia.gestion_morosidad_api.shared.enums.OrigenDatos;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;

public final class CapturaDtos {
    public record Cuenta(@NotBlank @Size(max=50) String cm, @NotNull OrigenDatos origen) {}
    public record Crear(@NotBlank @Size(max=100) String nombre, @NotEmpty @Size(max=100) List<@NotNull @Valid Cuenta> cuentas) {}
    public record Resumen(Long id, String nombre, LocalDateTime fecha, String usuario, int cantidadCuentas) {}
    public record Detalle(Long id, String nombre, LocalDateTime fecha, String usuario, List<EstadoCuenta> cuentas) {}
    public record EstadoCuenta(String cm, OrigenDatos origen, String numeroPadron, String tipoPadron,
        String nombre, String documento, BigDecimal importe, String estado, String segmento,
        LocalDateTime fechaSincronizacion, LocalDate fechaUltimoCobro, BigDecimal importeUltimoCobro) {}
}
