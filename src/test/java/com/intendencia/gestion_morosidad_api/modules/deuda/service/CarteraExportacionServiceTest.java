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
                .padron(new PadronResponse("00042", "00078", "CIU", null, null, null, persona, OrigenDatos.SUCIVE, "SAA0123"))
                .tributos(List.of(new TributoResponse(1L, "2800", null), new TributoResponse(2L, "2801", "Tasa")))
                .cantidadFacturasVencidas(5).cantidadFacturasConDeuda(3).anioDesde(2021).anioHasta(2026).cantidadAniosDeuda(2).aniosDeuda("2021, 2026")
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
            assertEquals(21, hoja.getRow(0).getLastCellNum());
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
            assertEquals("Matrícula", hoja.getRow(0).getCell(14).getStringCellValue());
            assertEquals("SAA0123", r.getCell(14).getStringCellValue());
            assertEquals(5, r.getCell(15).getNumericCellValue());
            assertEquals(3, r.getCell(16).getNumericCellValue());
            assertEquals(2021, r.getCell(17).getNumericCellValue());
            assertEquals(2026, r.getCell(18).getNumericCellValue());
            assertEquals(2, r.getCell(19).getNumericCellValue());
            assertEquals("2021, 2026", r.getCell(20).getStringCellValue());
            assertNotNull(hoja.getPaneInformation());
        }
        verifyNoInteractions(grupos);
        verify(deudas, times(2)).listar(eq(filtro), any());
    }

    private DeudasAgrupadasResponse grupo(int numero) {
        String documento = "001" + String.format(java.util.Locale.ROOT, "%04d", numero);
        var persona = new ContribuyenteResponse("CM-" + numero, "Persona " + numero, documento, OrigenDatos.SUCIVE);
        var filas = IntStream.rangeClosed(1, 2).mapToObj(i -> {
            long id = numero * 2L + i;
            return deuda(id).toBuilder().contribuyenteId(numero + 1L).contribuyente(persona)
                    .padron(new PadronResponse("CM-" + id, "PAD-" + id, "CIU", null, null, null,
                            persona, OrigenDatos.SUCIVE, "MAT-" + id)).build();
        }).toList();
        return new DeudasAgrupadasResponse("DOC:" + documento, documento, List.of(persona.nombre()),
                new BigDecimal("2469.12"), 400L, tardia, filas);
    }

    @Test
    void agrupadaExportaTodasLasPaginasDePersonasYSusDeudasCompletas() throws Exception {
        var primeraPagina = IntStream.range(0, 500).mapToObj(this::grupo).toList();
        var ultima = grupo(500);
        when(grupos.listar(filtro, PageRequest.of(0, 500, orden)))
                .thenReturn(new PaginaResponse<>(primeraPagina, 0, 500, 501, 2));
        when(grupos.listar(filtro, PageRequest.of(1, 500, orden)))
                .thenReturn(new PaginaResponse<>(List.of(ultima), 1, 500, 501, 2));
        try (var libro = WorkbookFactory.create(new ByteArrayInputStream(servicio.exportar(filtro, orden, true)))) {
            assertEquals(2, libro.getNumberOfSheets());
            var resumen = libro.getSheet("Contribuyentes");
            var detalle = libro.getSheet("Cartera");
            assertEquals(501, resumen.getLastRowNum());
            assertEquals(1002, detalle.getLastRowNum());
            var documentos = new java.util.HashSet<String>();
            var cuentas = new java.util.HashSet<String>();
            var importesPorDocumento = new java.util.HashMap<String, BigDecimal>();
            var filasPorDocumento = new java.util.HashMap<String, Integer>();
            for (int i = 1; i <= detalle.getLastRowNum(); i++) {
                var fila = detalle.getRow(i);
                assertTrue(cuentas.add(fila.getCell(0).getStringCellValue()), "CM repetido en el detalle");
                String doc = fila.getCell(3).getStringCellValue();
                importesPorDocumento.merge(doc, BigDecimal.valueOf(fila.getCell(9).getNumericCellValue()), BigDecimal::add);
                filasPorDocumento.merge(doc, 1, Integer::sum);
            }
            for (int i = 1; i <= resumen.getLastRowNum(); i++) {
                var fila = resumen.getRow(i);
                var esperado = grupo(i - 1);
                String doc = fila.getCell(0).getStringCellValue();
                assertEquals(esperado.documento(), doc);
                assertTrue(documentos.add(doc), "Documento repetido en el resumen");
                assertEquals(2, fila.getCell(2).getNumericCellValue());
                assertEquals(Integer.valueOf(2), filasPorDocumento.get(doc));
                assertEquals("Mora Tardía", fila.getCell(3).getStringCellValue());
                assertEquals(0, esperado.importeTotal().compareTo(BigDecimal.valueOf(fila.getCell(5).getNumericCellValue())));
                assertEquals(0, esperado.importeTotal().compareTo(importesPorDocumento.get(doc)));
            }
            assertEquals(documentos, importesPorDocumento.keySet());
            assertEquals(ultima.documento(), resumen.getRow(501).getCell(0).getStringCellValue());
            assertEquals("CM-1002", detalle.getRow(1002).getCell(0).getStringCellValue());
        }
        verifyNoInteractions(deudas);
        verify(grupos).listar(filtro, PageRequest.of(0, 500, orden));
        verify(grupos).listar(filtro, PageRequest.of(1, 500, orden));
        verifyNoMoreInteractions(grupos);
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
