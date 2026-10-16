package com.intendencia.gestion_morosidad_api.modules.deuda.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.intendencia.gestion_morosidad_api.modules.deuda.dto.*;
import com.intendencia.gestion_morosidad_api.modules.contribuyente.dto.ContribuyenteResponse;
import com.intendencia.gestion_morosidad_api.modules.padron.dto.PadronResponse;
import com.intendencia.gestion_morosidad_api.modules.tributo.dto.TributoResponse;
import com.intendencia.gestion_morosidad_api.modules.accion.dto.AccionResponse;
import com.intendencia.gestion_morosidad_api.modules.segmento.dto.SegmentoMoraResponse;
import com.intendencia.gestion_morosidad_api.shared.dto.PaginaResponse;
import com.intendencia.gestion_morosidad_api.shared.enums.OrigenDatos;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.IntStream;
import org.apache.poi.ss.usermodel.*;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.*;

class CarteraExportacionServiceTest {
    private final DeudaService deudas = mock(DeudaService.class);
    private final CarteraAgrupadaService grupos = mock(CarteraAgrupadaService.class);
    private final CarteraExportacionService servicio = new CarteraExportacionService(deudas, grupos);
    private final DeudaFiltro filtro = new DeudaFiltro(null, null, "001", null, 3L, null, true, "CIU", OrigenDatos.SUCIVE);
    private final Sort orden = Sort.by(Sort.Direction.DESC, "importe");
    private final SegmentoMoraResponse tardia = new SegmentoMoraResponse(3L, "Mora Tardía", null, 3);

    private DeudaResponse deuda(long id) {
        var persona = new ContribuyenteResponse("00042", "=Nombre de prueba", "00123456");
        return DeudaResponse.builder().id(id).contribuyenteId(7L).contribuyente(persona)
                .padron(new PadronResponse("00042", "00078", "CIU", null, null, null, persona, OrigenDatos.SUCIVE))
                .tributos(List.of(new TributoResponse(1L, "2800", null), new TributoResponse(2L, "2801", "Tasa")))
                .importe(new BigDecimal("1234.56")).diasAtraso(400L).segmentoMora(tardia)
                .cantidadGestiones(2L).accionesSugeridas(List.of(new AccionResponse(1L, "Llamada", true),
                        new AccionResponse(2L, "Email", true))).conContacto(true).convenio(true).build();
    }

    @Test
    void exportaTodasLasPaginasConFiltrosColumnasYTiposExcel() throws Exception {
        Sort estable = orden.and(Sort.by("id"));
        when(deudas.listar(filtro, PageRequest.of(0, 500, estable))).thenReturn(new PaginaResponse<>(
                IntStream.range(0, 500).mapToObj(i -> deuda(i)).toList(), 0, 500, 501, 2));
        when(deudas.listar(filtro, PageRequest.of(1, 500, estable))).thenReturn(new PaginaResponse<>(List.of(deuda(501)), 1, 500, 501, 2));
        try (var libro = WorkbookFactory.create(new ByteArrayInputStream(servicio.exportar(filtro, orden, false)))) {
            var hoja = libro.getSheet("Cartera");
            assertEquals(501, hoja.getLastRowNum());
            assertEquals(14, hoja.getRow(0).getLastCellNum());
            assertEquals("PadTipo", hoja.getRow(0).getCell(5).getStringCellValue());
            assertEquals("Gestiones", hoja.getRow(0).getCell(10).getStringCellValue());
            var r = hoja.getRow(501);
            assertEquals("00042", r.getCell(0).getStringCellValue());
            assertEquals("00078", r.getCell(1).getStringCellValue());
            assertEquals(CellType.STRING, r.getCell(2).getCellType());
            assertEquals("=Nombre de prueba", r.getCell(2).getStringCellValue());
            assertEquals("00123456", r.getCell(3).getStringCellValue());
            assertEquals("SUCIVE", r.getCell(4).getStringCellValue());
            assertEquals("2800, Tasa", r.getCell(6).getStringCellValue());
            assertEquals(1234.56, r.getCell(9).getNumericCellValue(), 0.001);
            assertEquals(2, r.getCell(10).getNumericCellValue());
            assertEquals("Llamada, Email", r.getCell(11).getStringCellValue());
            assertEquals("Sí", r.getCell(12).getStringCellValue());
            assertEquals("Con datos", r.getCell(13).getStringCellValue());
            assertNotNull(hoja.getPaneInformation());
        }
        verifyNoInteractions(grupos);
        verify(deudas, times(2)).listar(eq(filtro), any());
    }

    @Test
    void agrupadaExportaTodasLasPaginasDePersonasYSusDeudasCompletas() throws Exception {
        var g = new DeudasAgrupadasResponse("DOC:00123456", "00123456", List.of("Persona"),
                new BigDecimal("2469.12"), 400L, tardia, List.of(deuda(1), deuda(2)));
        when(grupos.listar(filtro, PageRequest.of(0, 500, orden))).thenReturn(new PaginaResponse<>(List.of(g), 0, 500, 501, 2));
        when(grupos.listar(filtro, PageRequest.of(1, 500, orden))).thenReturn(new PaginaResponse<>(List.of(g), 1, 500, 501, 2));
        try (var libro = WorkbookFactory.create(new ByteArrayInputStream(servicio.exportar(filtro, orden, true)))) {
            assertEquals(2, libro.getNumberOfSheets());
            var resumen = libro.getSheet("Contribuyentes");
            assertEquals(2, resumen.getLastRowNum());
            assertEquals("00123456", resumen.getRow(1).getCell(0).getStringCellValue());
            assertEquals(2, resumen.getRow(1).getCell(2).getNumericCellValue());
            assertEquals("Mora Tardía", resumen.getRow(1).getCell(3).getStringCellValue());
            assertEquals(2469.12, resumen.getRow(1).getCell(5).getNumericCellValue(), 0.001);
            assertEquals(4, libro.getSheet("Cartera").getLastRowNum());
        }
        verifyNoInteractions(deudas);
    }

    @Test
    void carteraVaciaConservaEncabezadosSinFilasInventadas() throws Exception {
        when(deudas.listar(any(), any())).thenReturn(new PaginaResponse<>(List.of(), 0, 500, 0, 0));
        try (var libro = WorkbookFactory.create(new ByteArrayInputStream(servicio.exportar(filtro, Sort.unsorted(), false)))) {
            assertEquals(0, libro.getSheetAt(0).getLastRowNum());
            assertEquals("CM", libro.getSheetAt(0).getRow(0).getCell(0).getStringCellValue());
        }
    }
}
