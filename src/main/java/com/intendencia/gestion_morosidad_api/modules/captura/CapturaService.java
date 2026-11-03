package com.intendencia.gestion_morosidad_api.modules.captura;

import static com.intendencia.gestion_morosidad_api.modules.captura.CapturaDtos.*;
import com.intendencia.gestion_morosidad_api.modules.padron.repository.PadronRepository;
import com.intendencia.gestion_morosidad_api.modules.deuda.repository.DeudaRepository;
import com.intendencia.gestion_morosidad_api.modules.deuda.entity.EstadoDeuda;
import com.intendencia.gestion_morosidad_api.shared.exception.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@RequiredArgsConstructor
public class CapturaService {
    private final CapturaRepository capturas;
    private final PadronRepository padrones;
    private final DeudaRepository deudas;
    private final Clock clock;

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public Detalle crear(Crear peticion, String usuario) {
        var claves = new HashSet<String>();
        var filas = new ArrayList<EstadoCuenta>();
        for (var cuenta : peticion.cuentas()) {
            String cm = cuenta.cm().trim();
            if (!claves.add(cuenta.origen() + ":" + cm)) throw new ReglaNegocioException("Hay CM repetidos para el mismo origen.");
            var p = padrones.findByCmAndOrigen(cm,cuenta.origen()).orElseThrow(() -> new RecursoNoEncontradoException("No existe el CM " + cm + " en " + cuenta.origen()));
            var d = deudas.findByPadronCmAndPadronOrigen(cm,cuenta.origen()).orElse(null);
            boolean pendiente = d != null && d.getEstado() != EstadoDeuda.CANCELADA && d.getImporte() != null && d.getImporte().signum() > 0;
            var persona = p.getContribuyente();
            filas.add(new EstadoCuenta(cm,cuenta.origen(),p.getNumeroPadron(),p.getTipoPadron(),persona == null ? null : persona.getNombre(),persona == null ? null : persona.getDocumento(),
                pendiente ? d.getImporte() : BigDecimal.ZERO,pendiente ? d.getEstado().name() : "SIN_DEUDA_PENDIENTE",
                pendiente && d.getSegmentoMora() != null ? d.getSegmentoMora().getNombre() : null,
                d == null ? null : d.getFechaSincronizacion(),d == null ? null : d.getFechaUltimoCobro(),d == null ? null : d.getImporteUltimoCobro()));
        }
        LocalDateTime fecha = LocalDateTime.now(clock);
        Long id = capturas.crear(peticion.nombre().trim(),fecha,usuario);
        filas.forEach(f -> capturas.guardarCuenta(id,f));
        return new Detalle(id,peticion.nombre().trim(),fecha,usuario,List.copyOf(filas));
    }
    @Transactional(readOnly=true)
    public List<Resumen> listar() { return capturas.listar(); }
    @Transactional(readOnly=true, isolation=Isolation.REPEATABLE_READ)
    public Detalle obtener(Long id) { return capturas.obtener(id).orElseThrow(() -> new RecursoNoEncontradoException("No existe la captura " + id)); }
}
