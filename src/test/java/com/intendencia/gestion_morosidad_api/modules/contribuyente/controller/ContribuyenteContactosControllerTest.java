package com.intendencia.gestion_morosidad_api.modules.contribuyente.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.intendencia.gestion_morosidad_api.modules.contacto.TipoContacto;
import com.intendencia.gestion_morosidad_api.modules.contacto.entity.Contacto;
import com.intendencia.gestion_morosidad_api.modules.contacto.repository.ContactoRepository;
import com.intendencia.gestion_morosidad_api.modules.contacto.service.ContactoService;
import com.intendencia.gestion_morosidad_api.modules.contribuyente.entity.Contribuyente;
import com.intendencia.gestion_morosidad_api.modules.contribuyente.service.ContribuyenteService;
import com.intendencia.gestion_morosidad_api.modules.padron.entity.Padron;
import com.intendencia.gestion_morosidad_api.modules.padron.repository.PadronRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ContribuyenteController.class)
@Import(ContactoService.class)
@WithMockUser
class ContribuyenteContactosControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private ContribuyenteService contribuyenteService;
    @MockitoBean private PadronRepository padronRepository;
    @MockitoBean private ContactoRepository contactoRepository;

    private Contribuyente contribuyente;

    @BeforeEach
    void setUp() {
        contribuyente = new Contribuyente();
        contribuyente.setId(5L);
        contribuyente.setNombre("Ana Pérez");
        Padron padron = new Padron();
        padron.setCm("123");
        padron.setContribuyente(contribuyente);
        when(padronRepository.findByCm("123")).thenReturn(Optional.of(padron));
    }

    @Test
    void getDevuelveLosContactosDelContribuyenteVinculadoAlCm() throws Exception {
        Contacto contacto = contacto(7L, TipoContacto.TELEFONO, "099123456");
        when(contactoRepository.findByContribuyente(contribuyente)).thenReturn(List.of(contacto));

        mockMvc.perform(get("/api/contribuyentes/123/contactos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(7))
                .andExpect(jsonPath("$[0].tipoContacto").value("TELEFONO"))
                .andExpect(jsonPath("$[0].valor").value("099123456"))
                .andExpect(jsonPath("$[0].esDeContribuyente").value(true))
                .andExpect(jsonPath("$[0].origen").value("MANUAL"));

        verify(contactoRepository).findByContribuyente(contribuyente);
    }

    @Test
    void getSinContactosDevuelveListaVacia() throws Exception {
        when(contactoRepository.findByContribuyente(contribuyente)).thenReturn(List.of());

        mockMvc.perform(get("/api/contribuyentes/123/contactos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void getConCmInexistenteDevuelve404() throws Exception {
        mockMvc.perform(get("/api/contribuyentes/999/contactos"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));

        verify(contactoRepository, never()).findByContribuyente(any());
    }

    @Test
    void putSinIdCreaContactoVinculadoAlContribuyente() throws Exception {
        when(contactoRepository.save(any(Contacto.class))).thenAnswer(invocation -> {
            Contacto contacto = invocation.getArgument(0);
            assertThat(contacto.getContribuyente()).isSameAs(contribuyente);
            contacto.setId(7L);
            return contacto;
        });

        mockMvc.perform(put("/api/contribuyentes/123/contactos").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tipoContacto":"EMAIL","valor":"ana@example.test",
                                 "esDeContribuyente":true,"origen":"MANUAL"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.tipoContacto").value("EMAIL"))
                .andExpect(jsonPath("$.valor").value("ana@example.test"))
                .andExpect(jsonPath("$.esDeContribuyente").value(true))
                .andExpect(jsonPath("$.origen").value("MANUAL"));
    }

    @Test
    void putConIdActualizaSoloContactoDelMismoContribuyente() throws Exception {
        Contacto existente = contacto(7L, TipoContacto.TELEFONO, "099123456");
        when(contactoRepository.findByIdAndContribuyente(7L, contribuyente))
                .thenReturn(Optional.of(existente));
        when(contactoRepository.save(existente)).thenReturn(existente);

        mockMvc.perform(put("/api/contribuyentes/123/contactos").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"id":7,"tipoContacto":"TELEFONO","valor":"099999999",
                                 "esDeContribuyente":false,"origen":"GEOPAGOS"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.valor").value("099999999"))
                .andExpect(jsonPath("$.esDeContribuyente").value(false))
                .andExpect(jsonPath("$.origen").value("GEOPAGOS"));

        assertThat(existente.getContribuyente()).isSameAs(contribuyente);
        verify(contactoRepository).findByIdAndContribuyente(7L, contribuyente);
        verify(contactoRepository).save(existente);
    }

    @Test
    void putConIdAjenoDevuelve404SinGuardar() throws Exception {
        when(contactoRepository.findByIdAndContribuyente(8L, contribuyente))
                .thenReturn(Optional.empty());

        mockMvc.perform(put("/api/contribuyentes/123/contactos").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"id":8,"tipoContacto":"TELEFONO","valor":"099999999",
                                 "esDeContribuyente":true,"origen":"MANUAL"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));

        verify(contactoRepository, never()).save(any());
    }

    @Test
    void putInvalidoDevuelve400SinGuardar() throws Exception {
        mockMvc.perform(put("/api/contribuyentes/123/contactos").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"valor":" ","origen":" "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.tipoContacto").exists())
                .andExpect(jsonPath("$.errores.valor").exists())
                .andExpect(jsonPath("$.errores.esDeContribuyente").exists())
                .andExpect(jsonPath("$.errores.origen").exists());

        verify(contactoRepository, never()).save(any());
    }

    private Contacto contacto(Long id, TipoContacto tipo, String valor) {
        Contacto contacto = new Contacto();
        contacto.setId(id);
        contacto.setTipoContacto(tipo);
        contacto.setValor(valor);
        contacto.setEsDeContribuyente(true);
        contacto.setOrigen("MANUAL");
        contacto.setContribuyente(contribuyente);
        return contacto;
    }
}
