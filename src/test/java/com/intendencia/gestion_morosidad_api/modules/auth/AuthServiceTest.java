package com.intendencia.gestion_morosidad_api.modules.auth;
import com.intendencia.gestion_morosidad_api.modules.auth.dto.LoginRequest;
import com.intendencia.gestion_morosidad_api.modules.usuario.*;
import java.util.*;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
 @Mock AuthenticationManager manager;@Mock UsuarioRepository usuarios;@Mock TokenService tokens;@Mock LimitadorLogin limite;@InjectMocks AuthService service;
 @BeforeEach void permiteEjecutarElFlujoDeCredenciales(){when(limite.autenticar(anyString(),anyString(),any())).thenAnswer(a->a.<Supplier<?>>getArgument(2).get());}
 @Test void autenticaAntesDeEmitirTokenYDevuelveTodosLosRoles(){var u=new Usuario();u.setId(4L);u.setUsername("mixto");u.setRoles(Set.of(Rol.values()));when(usuarios.findByUsername("mixto")).thenReturn(Optional.of(u));when(tokens.generateToken(u)).thenReturn("token-prueba");
  var r=service.login(new LoginRequest("mixto","clave-prueba"),"10.0.0.1");assertThat(r.token()).isEqualTo("token-prueba");assertThat(r.roles()).containsExactlyInAnyOrder(Rol.values());assertThat(r.id()).isEqualTo(4);
  var orden=inOrder(manager,usuarios,tokens);orden.verify(usuarios).findByUsername("mixto");orden.verify(manager).authenticate(argThat(a->a.getName().equals("mixto")&&a.getCredentials().equals("clave-prueba")));orden.verify(tokens).generateToken(u);}
 @Test void credencialesInvalidasNoEmitenToken(){when(manager.authenticate(any())).thenThrow(new BadCredentialsException("inválidas"));assertThatThrownBy(()->service.login(new LoginRequest("x","y"),"10.0.0.1")).isInstanceOf(BadCredentialsException.class);verifyNoInteractions(tokens);}
 @Test void cuentaInactivaRechazadaPorAutenticacionNoEmiteToken(){when(manager.authenticate(any())).thenThrow(new DisabledException("inactiva"));assertThatThrownBy(()->service.login(new LoginRequest("x","y"),"10.0.0.1")).isInstanceOf(DisabledException.class);verifyNoInteractions(tokens);}
 @Test void usuarioAusenteDespuesDeAutenticarNoGeneraToken(){assertThatThrownBy(()->service.login(new LoginRequest("x","y"),"10.0.0.1")).isInstanceOf(BadCredentialsException.class);verifyNoInteractions(tokens);}
}
