package com.intendencia.gestion_morosidad_api.modules.contacto.service;
import com.intendencia.gestion_morosidad_api.modules.contacto.*;
import com.intendencia.gestion_morosidad_api.modules.contacto.dto.*;
import com.intendencia.gestion_morosidad_api.modules.contacto.entity.Contacto;
import com.intendencia.gestion_morosidad_api.modules.contacto.repository.ContactoRepository;
import com.intendencia.gestion_morosidad_api.modules.contribuyente.entity.Contribuyente;
import com.intendencia.gestion_morosidad_api.modules.padron.entity.Padron;
import com.intendencia.gestion_morosidad_api.modules.padron.repository.PadronRepository;
import com.intendencia.gestion_morosidad_api.shared.enums.OrigenDatos;
import com.intendencia.gestion_morosidad_api.shared.exception.RecursoNoEncontradoException;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
@ExtendWith(MockitoExtension.class)
class ContactoServiceTest {
 @Mock ContactoRepository contactos;@Mock PadronRepository padrones;@InjectMocks ContactoService service;
 Contribuyente persona;Padron padron;
 @BeforeEach void datos(){persona=new Contribuyente();persona.setId(7L);padron=new Padron();padron.setContribuyente(persona);}
 void buscar(OrigenDatos origen){if(origen==OrigenDatos.INTENDENCIA)when(padrones.findByCm("123")).thenReturn(Optional.of(padron));else when(padrones.findByCmAndOrigen("123",origen)).thenReturn(Optional.of(padron));}
 @ParameterizedTest @EnumSource(OrigenDatos.class) void creaSoloParaLaPersonaDelOrigenSeleccionado(OrigenDatos origen){buscar(origen);when(contactos.save(any())).thenAnswer(i->{Contacto c=i.getArgument(0);c.setId(9L);return c;});var r=service.guardarOActualizar("123",new ContactoRequest(null,TipoContacto.TELEFONO," 099123456 ",false," Entrevista "),origen);assertThat(r.valor()).isEqualTo("099123456");assertThat(r.origen()).isEqualTo("Entrevista");verify(contactos).save(argThat(c->c.getContribuyente()==persona&&!c.getEsDeContribuyente()));}
 @Test void editaSinCambiarElTitular(){buscar(OrigenDatos.INTENDENCIA);var c=new Contacto();c.setId(9L);c.setContribuyente(persona);when(contactos.findByIdAndContribuyente(9L,persona)).thenReturn(Optional.of(c));when(contactos.save(c)).thenReturn(c);var r=service.guardarOActualizar("123",new ContactoRequest(9L,TipoContacto.EMAIL,"a@example.test",true,"Manual"));assertThat(r.id()).isEqualTo(9);assertThat(c.getContribuyente()).isSameAs(persona);}
 @Test void noPermiteEditarUnIdAjeno(){buscar(OrigenDatos.INTENDENCIA);assertThatThrownBy(()->service.guardarOActualizar("123",new ContactoRequest(9L,TipoContacto.EMAIL,"a@example.test",true,"Manual"))).isInstanceOf(RecursoNoEncontradoException.class);verify(contactos,never()).save(any());}
 @Test void cmInexistenteNoConsultaContactos(){assertThatThrownBy(()->service.listarPorCm("no-existe")).isInstanceOf(RecursoNoEncontradoException.class);verifyNoInteractions(contactos);}
 @ParameterizedTest @EnumSource(OrigenDatos.class) void listaVaciaEsUnaConsultaValida(OrigenDatos origen){buscar(origen);assertThat(service.listarPorCm("123",origen)).isEmpty();verify(contactos).findByContribuyente(persona);}
}
