package com.intendencia.gestion_morosidad_api.modules.segmento.service;
import com.intendencia.gestion_morosidad_api.modules.segmento.entity.SegmentoMora;
import com.intendencia.gestion_morosidad_api.modules.segmento.dto.SegmentoMoraRequest;
import com.intendencia.gestion_morosidad_api.modules.segmento.repository.SegmentoMoraRepository;
import com.intendencia.gestion_morosidad_api.shared.exception.RecursoYaExisteException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
@ExtendWith(MockitoExtension.class)
class SegmentoMoraServiceTest {
 @Mock SegmentoMoraRepository repo;@InjectMocks SegmentoMoraService service;
 @Test void creaConNombreNormalizadoYConservaDescripcionYOrden(){when(repo.save(any())).thenAnswer(i->{SegmentoMora s=i.getArgument(0);s.setId(4L);return s;});var r=service.crear(new SegmentoMoraRequest(" Prueba ","Descripción",4));assertThat(r.nombre()).isEqualTo("Prueba");assertThat(r.descripcion()).isEqualTo("Descripción");assertThat(r.orden()).isEqualTo(4);}
 @Test void rechazaDuplicadoSinInsertar(){when(repo.existsByNombreIgnoreCase("Repetido")).thenReturn(true);assertThatThrownBy(()->service.crear(new SegmentoMoraRequest(" Repetido ",null,4))).isInstanceOf(RecursoYaExisteException.class);verify(repo,never()).save(any());}
 @Test void listaEnElOrdenDelRepositorio(){var s=new SegmentoMora();s.setId(1L);s.setNombre("Primero");s.setOrden(1);when(repo.findAllByOrderByOrdenAsc()).thenReturn(List.of(s));assertThat(service.listar()).singleElement().satisfies(r->assertThat(r.nombre()).isEqualTo("Primero"));}
 @Test void catalogoVacioDevuelveListaVacia(){assertThat(service.listar()).isEmpty();}
}
