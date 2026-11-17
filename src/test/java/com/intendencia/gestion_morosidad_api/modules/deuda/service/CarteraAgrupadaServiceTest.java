package com.intendencia.gestion_morosidad_api.modules.deuda.service;
import com.intendencia.gestion_morosidad_api.modules.deuda.dto.*;
import com.intendencia.gestion_morosidad_api.modules.deuda.entity.Deuda;
import com.intendencia.gestion_morosidad_api.modules.deuda.repository.*;
import com.intendencia.gestion_morosidad_api.modules.contribuyente.dto.ContribuyenteResponse;
import com.intendencia.gestion_morosidad_api.modules.segmento.dto.SegmentoMoraResponse;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
@ExtendWith(MockitoExtension.class)
class CarteraAgrupadaServiceTest {
 @Mock CarteraAgrupadaRepository grupos;@Mock DeudaRepository deudas;@Mock DeudaService mapper;@InjectMocks CarteraAgrupadaService service;
 final DeudaFiltro filtro=new DeudaFiltro(null,null,null,null,null,null,null,null,null);
 DeudaResponse fila(long id,long persona,String doc,String nombre,String importe,Integer orden,Long dias){return DeudaResponse.builder().id(id).contribuyenteId(persona).contribuyente(new ContribuyenteResponse("CM"+id,nombre,doc)).importe(new BigDecimal(importe)).diasAtraso(dias).segmentoMora(orden==null?null:new SegmentoMoraResponse(orden.longValue(),"Mora "+orden,null,orden)).build();}
 @Test void paginaVaciaConservaMetadatosSinConsultarDeudas(){var p=PageRequest.of(9,2);when(grupos.buscarGrupos(filtro,p)).thenReturn(new PageImpl<>(List.of(),p,5));var r=service.listar(filtro,p);assertThat(r.pagina()).isEqualTo(9);assertThat(r.totalElementos()).isEqualTo(5);assertThat(r.contenido()).isEmpty();verifyNoInteractions(deudas,mapper);}
 @Test void sumaGrupoCompletoYUsaMoraYDiasMaximosSinOrdenarPorNombre(){var p=PageRequest.of(0,1);when(grupos.buscarGrupos(filtro,p)).thenReturn(new PageImpl<>(List.of("DOC:001"),p,2));var entidades=List.of(new Deuda(),new Deuda());when(deudas.findAll(any(Specification.class),any(Pageable.class))).thenReturn(new PageImpl<>(entidades));when(mapper.mapearDeudas(entidades)).thenReturn(List.of(fila(1,1,"001","Zeta","10.25",1,30L),fila(2,2," 001 ","Alfa","20.50",3,400L)));var r=service.listar(filtro,p);assertThat(r.totalElementos()).isEqualTo(2);assertThat(r.contenido()).singleElement().satisfies(g->{assertThat(g.importeTotal()).isEqualByComparingTo("30.75");assertThat(g.nombres()).containsExactly("Alfa","Zeta");assertThat(g.segmentoMora().orden()).isEqualTo(3);assertThat(g.diasAtrasoMaximos()).isEqualTo(400);assertThat(g.deudas()).hasSize(2);});verify(deudas).findAll(any(Specification.class),argThat((Pageable q)->q.isUnpaged()&&q.getSort().getOrderFor("id")!=null));}
 @Test void conservaOrdenDePaginaYNoUneIdentidadesSinDocumento(){var p=PageRequest.of(0,3);when(grupos.buscarGrupos(filtro,p)).thenReturn(new PageImpl<>(List.of("ID:2","DOC:001","ID:3"),p,3));var entidades=List.of(new Deuda());when(deudas.findAll(any(Specification.class),any(Pageable.class))).thenReturn(new PageImpl<>(entidades));when(mapper.mapearDeudas(entidades)).thenReturn(List.of(fila(1,1,"001","Mismo","10",null,null),fila(2,2,null,"Mismo","20",null,null),fila(3,3,"  ","Mismo","30",null,null)));var r=service.listar(filtro,p);assertThat(r.contenido()).extracting(DeudasAgrupadasResponse::clave).containsExactly("ID:2","DOC:001","ID:3");assertThat(r.contenido().getFirst().segmentoMora()).isNull();assertThat(r.contenido().getFirst().diasAtrasoMaximos()).isNull();}
}
