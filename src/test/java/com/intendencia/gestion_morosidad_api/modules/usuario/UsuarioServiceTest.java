package com.intendencia.gestion_morosidad_api.modules.usuario;
import com.intendencia.gestion_morosidad_api.modules.usuario.dto.*;
import com.intendencia.gestion_morosidad_api.shared.exception.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
@ExtendWith(MockitoExtension.class)
class UsuarioServiceTest {
 static final Instant AHORA = Instant.parse("2026-11-25T12:00:00Z");
 static final String CLAVE = "clave-de-prueba-larga";
 @Mock UsuarioRepository repo; @Mock PasswordEncoder encoder; UsuarioService service;
 @BeforeEach void crearServicio(){service=new UsuarioService(repo,encoder,Clock.fixed(AHORA,ZoneOffset.UTC));}
 Usuario usuario(){var u=new Usuario();u.setId(1L);u.setUsername("admin");u.setPassword("hash-anterior");u.setRoles(new HashSet<>(Set.of(Rol.ADMINISTRADOR)));return u;}
 @Test void creaConNombreNormalizadoPasswordCodificadaYTresRoles(){
  when(encoder.encode(CLAVE)).thenReturn("hash-nuevo");
  var r=service.saveUser(new UsuarioRequest(" mixto ",CLAVE,Set.of(Rol.values()),null));
  assertThat(r.username()).isEqualTo("mixto");assertThat(r.roles()).containsExactlyInAnyOrder(Rol.values());assertThat(r.activo()).isTrue();
  verify(repo).save(argThat(u->u.getPassword().equals("hash-nuevo") && !u.getPassword().equals(CLAVE)));
 }
 @Test void elAltaDejaLaClaveComoTemporalYExigeCambiarlaAlIngresar(){when(encoder.encode(CLAVE)).thenReturn("hash-nuevo");
  service.saveUser(new UsuarioRequest("nuevo",CLAVE,Set.of(Rol.ANALISTA),null));
  verify(repo).save(argThat(u->u.requiereCambioPassword() && u.getPasswordTemporalHasta().equals(AHORA.plus(Duration.ofHours(24)))));}
 @ParameterizedTest @ValueSource(strings={"clave-segura","catorce-carac","                    "}) void claveQueNoCumpleLaPoliticaNoCreaElUsuario(String clave){
  assertThatThrownBy(()->service.saveUser(new UsuarioRequest("nuevo",clave,Set.of(Rol.ANALISTA),null))).isInstanceOf(ReglaNegocioException.class);verifyNoInteractions(encoder);verify(repo,never()).save(any());}
 @Test void duplicadoNoCodificaNiGuarda(){when(repo.existsByUsernameIgnoreCase("repetido")).thenReturn(true);
  assertThatThrownBy(()->service.saveUser(new UsuarioRequest(" repetido ",CLAVE,Set.of(Rol.ANALISTA),null))).isInstanceOf(RecursoYaExisteException.class);verifyNoInteractions(encoder);verify(repo,never()).save(any());}
 @Test void listadoYConsultaMapeanRolesSinDevolverPassword(){var u=usuario();when(repo.findAll()).thenReturn(List.of(u));when(repo.findById(1L)).thenReturn(Optional.of(u));assertThat(service.getAllUsers()).containsExactly(service.getUsuarioById(1L));}
 @Test void usuarioInexistenteNoActualiza(){assertThatThrownBy(()->service.updateUser(99L,new UsuarioUpdateRequest("x",Set.of(Rol.ANALISTA),true),"otro")).isInstanceOf(RecursoNoEncontradoException.class);verify(repo,never()).save(any());}
 @ParameterizedTest @ValueSource(booleans={false,true}) void noPuedeDesactivarseNiQuitarseAdmin(boolean activo){var u=usuario();when(repo.findById(1L)).thenReturn(Optional.of(u));
  assertThatThrownBy(()->service.updateUser(1L,new UsuarioUpdateRequest("admin",Set.of(Rol.ANALISTA),activo),"admin")).isInstanceOf(ReglaNegocioException.class);verify(repo,never()).save(any());assertThat(u.getRoles()).contains(Rol.ADMINISTRADOR);}
 @Test void noDesactivaAlUltimoAdmin(){var u=usuario();when(repo.findById(1L)).thenReturn(Optional.of(u));when(repo.contarActivosConRol(Rol.ADMINISTRADOR)).thenReturn(1L);
  assertThatThrownBy(()->service.updateUser(1L,new UsuarioUpdateRequest("admin",Set.of(Rol.ADMINISTRADOR),false),"otro")).isInstanceOf(ReglaNegocioException.class);verify(repo,never()).save(any());}
 @Test void actualizaRolesSiQuedaOtroAdminSinCambiarElPassword(){var u=usuario();when(repo.findById(1L)).thenReturn(Optional.of(u));when(repo.contarActivosConRol(Rol.ADMINISTRADOR)).thenReturn(2L);
  var r=service.updateUser(1L,new UsuarioUpdateRequest(" nuevo ",Set.of(Rol.NOTIFICADOR,Rol.ANALISTA),true),"otro");assertThat(r.username()).isEqualTo("nuevo");assertThat(u.getPassword()).isEqualTo("hash-anterior");verifyNoInteractions(encoder);verify(repo).save(u);}
 @Test void cambioANombreOcupadoNoModificaElUsuario(){var u=usuario();when(repo.findById(1L)).thenReturn(Optional.of(u));when(repo.existsByUsernameIgnoreCaseAndIdNot("ocupado",1L)).thenReturn(true);
  assertThatThrownBy(()->service.updateUser(1L,new UsuarioUpdateRequest("ocupado",Set.of(Rol.ANALISTA),true),"otro")).isInstanceOf(RecursoYaExisteException.class);assertThat(u.getUsername()).isEqualTo("admin");verify(repo,never()).save(any());}
}
