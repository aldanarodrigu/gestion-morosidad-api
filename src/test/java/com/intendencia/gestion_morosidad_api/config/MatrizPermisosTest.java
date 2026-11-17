package com.intendencia.gestion_morosidad_api.config;

import com.intendencia.gestion_morosidad_api.modules.accion.AccionService;
import com.intendencia.gestion_morosidad_api.modules.auth.AuthService;
import com.intendencia.gestion_morosidad_api.modules.captura.CapturaService;
import com.intendencia.gestion_morosidad_api.modules.contacto.service.ContactoService;
import com.intendencia.gestion_morosidad_api.modules.contribuyente.service.ContribuyenteService;
import com.intendencia.gestion_morosidad_api.modules.convenio.ConvenioService;
import com.intendencia.gestion_morosidad_api.modules.deuda.service.CarteraAgrupadaService;
import com.intendencia.gestion_morosidad_api.modules.deuda.service.CarteraExportacionService;
import com.intendencia.gestion_morosidad_api.modules.deuda.service.DeudaService;
import com.intendencia.gestion_morosidad_api.modules.deuda.service.FacturasPadronService;
import com.intendencia.gestion_morosidad_api.modules.deuda.service.HistorialPadronService;
import com.intendencia.gestion_morosidad_api.modules.gestion.GestionService;
import com.intendencia.gestion_morosidad_api.modules.padron.service.PadronService;
import com.intendencia.gestion_morosidad_api.modules.reporte.ReporteService;
import com.intendencia.gestion_morosidad_api.modules.segmento.service.ConfiguracionSegmentoService;
import com.intendencia.gestion_morosidad_api.modules.segmento.service.SegmentoMoraService;
import com.intendencia.gestion_morosidad_api.modules.sincronizacion.service.PeriodoSincronizacionService;
import com.intendencia.gestion_morosidad_api.modules.sincronizacion.service.SincronizacionDeudasService;
import com.intendencia.gestion_morosidad_api.modules.sincronizacion.service.SincronizacionManualService;
import com.intendencia.gestion_morosidad_api.modules.tributo.service.TributoService;
import com.intendencia.gestion_morosidad_api.modules.usuario.Rol;
import com.intendencia.gestion_morosidad_api.modules.usuario.UsuarioRepository;
import com.intendencia.gestion_morosidad_api.modules.usuario.UsuarioService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

/**
 * Matriz de permisos completa: cada endpoint de la API contra cada combinación de roles y sin sesión.
 * Es la versión ejecutable de README "Permisos actuales". Un endpoint nuevo sin fila en {@link #MATRIZ}
 * hace fallar {@link #todosLosEndpointsEstanEnLaMatriz()}.
 *
 * Los servicios están mockeados: un acceso permitido solo verifica que la seguridad no lo corte
 * (ni 401 ni 403); lo que responda el controlador después se prueba en sus propios tests.
 */
@WebMvcTest
@Import(SecurityConfig.class)
class MatrizPermisosTest {
    @Autowired MockMvc mvc;
    @Autowired @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping mapeos;

    @MockitoBean AuthService auth;
    @MockitoBean UsuarioRepository usuarioRepository;
    @MockitoBean UsuarioService usuarios;
    @MockitoBean DeudaService deudas;
    @MockitoBean CarteraAgrupadaService agrupadas;
    @MockitoBean CarteraExportacionService exportacion;
    @MockitoBean FacturasPadronService facturas;
    @MockitoBean HistorialPadronService historial;
    @MockitoBean ContribuyenteService contribuyentes;
    @MockitoBean ContactoService contactos;
    @MockitoBean PadronService padrones;
    @MockitoBean GestionService gestiones;
    @MockitoBean AccionService acciones;
    @MockitoBean TributoService tributos;
    @MockitoBean SegmentoMoraService segmentos;
    @MockitoBean ConfiguracionSegmentoService periodos;
    @MockitoBean SincronizacionDeudasService sincronizacion;
    @MockitoBean SincronizacionManualService sincronizacionManual;
    @MockitoBean PeriodoSincronizacionService periodoSincronizacion;
    @MockitoBean CapturaService capturas;
    @MockitoBean ReporteService reportes;
    @MockitoBean ConvenioService convenios;
    @MockitoBean JwtDecoder decoder;
    @MockitoBean JwtAuthenticationConverter converter;

    static final Set<Rol> TODOS = EnumSet.allOf(Rol.class);
    static final Set<Rol> ADMIN = EnumSet.of(Rol.ADMINISTRADOR);
    static final Set<Rol> NOTIFICADOR = EnumSet.of(Rol.NOTIFICADOR);
    /** Endpoint abierto sin sesión (login). */
    static final Set<Rol> PUBLICO = null;

    record Endpoint(String metodo, String ruta, Set<Rol> permitidos, String cuerpo) {
        Endpoint(String metodo, String ruta, Set<Rol> permitidos) { this(metodo, ruta, permitidos, null); }

        String clave() { return metodo + " " + ruta; }

        MockHttpServletRequestBuilder peticion() {
            var builder = request(HttpMethod.valueOf(metodo), ruta.replaceAll("\\{[^}]+}", "1"));
            return cuerpo == null ? builder : builder.contentType(MediaType.APPLICATION_JSON).content(cuerpo);
        }

        @Override public String toString() { return clave(); }
    }

    static final String CONTACTO = """
            {"tipoContacto":"EMAIL","valor":"correo@example.test","origen":"Llamada","esDeContribuyente":true}""";
    static final String RANGO = """
            {"segmentoId":1,"diasDesde":0,"diasHasta":90}""";

    static final List<Endpoint> MATRIZ = List.of(
            // Autenticación
            new Endpoint("POST", "/api/auth/login", PUBLICO, """
                    {"username":"usuario","password":"clave-segura"}"""),
            new Endpoint("GET", "/api/auth/me", TODOS),
            // Cartera
            new Endpoint("GET", "/api/deudas", TODOS),
            new Endpoint("GET", "/api/deudas/tipos-padron", TODOS),
            new Endpoint("GET", "/api/deudas/{id}", TODOS),
            new Endpoint("GET", "/api/deudas/{id}/tributos", TODOS),
            new Endpoint("GET", "/api/deudas/{id}/facturas", TODOS),
            new Endpoint("GET", "/api/deudas/{id}/historial-contribuyentes", TODOS),
            new Endpoint("GET", "/api/deudas/agrupadas", TODOS),
            new Endpoint("GET", "/api/deudas/exportar", TODOS),
            new Endpoint("PUT", "/api/deudas/{id}/estado", ADMIN, """
                    {"estado":"EN_GESTION"}"""),
            new Endpoint("GET", "/api/padrones/{numPadron}/deudas", TODOS),
            new Endpoint("GET", "/api/contribuyentes/{cm}/deudas", TODOS),
            new Endpoint("GET", "/api/convenios", TODOS),
            new Endpoint("GET", "/api/reportes/cartera", TODOS),
            new Endpoint("GET", "/api/reportes/cartera/exportar", TODOS),
            new Endpoint("GET", "/api/capturas-cartera", TODOS),
            new Endpoint("GET", "/api/capturas-cartera/{id}", TODOS),
            new Endpoint("POST", "/api/capturas-cartera", TODOS, """
                    {"nombre":"Captura","cuentas":[{"cm":"123","origen":"INTENDENCIA"}]}"""),
            // Contribuyentes, padrones y contactos
            new Endpoint("GET", "/api/contribuyentes", TODOS),
            new Endpoint("GET", "/api/contribuyentes/{cm}", TODOS),
            new Endpoint("GET", "/api/contribuyentes/{cm}/padrones", TODOS),
            new Endpoint("GET", "/api/contribuyentes/{cm}/contactos", TODOS),
            new Endpoint("PUT", "/api/contribuyentes/{cm}/contactos", TODOS, CONTACTO),
            new Endpoint("GET", "/api/contribuyentes/id/{contribuyenteId}/deudas", TODOS),
            new Endpoint("GET", "/api/padrones/{numeroPadron}", TODOS),
            new Endpoint("GET", "/api/padrones/{numeroPadron}/contribuyente", TODOS),
            // Gestiones: solo NOTIFICADOR registra, aunque ADMINISTRADOR consulta
            new Endpoint("GET", "/api/gestiones", TODOS),
            new Endpoint("GET", "/api/gestiones/{id}", TODOS),
            new Endpoint("GET", "/api/gestiones/deuda/{deudaId}", TODOS),
            new Endpoint("GET", "/api/gestiones/deuda/{deudaId}/acciones-disponibles", TODOS),
            new Endpoint("POST", "/api/gestiones", NOTIFICADOR, """
                    {"deudaId":1,"accionId":1,"resultado":"EXITOSA"}"""),
            // Configuración: lectura para todos, cambios solo ADMINISTRADOR
            new Endpoint("GET", "/api/acciones", TODOS),
            new Endpoint("GET", "/api/acciones/{id}", TODOS),
            new Endpoint("POST", "/api/acciones", ADMIN, """
                    {"nombre":"Nueva accion","segmentoId":1}"""),
            new Endpoint("PUT", "/api/acciones/{id}", ADMIN, """
                    {"activo":false}"""),
            new Endpoint("DELETE", "/api/acciones/{id}", ADMIN),
            new Endpoint("GET", "/api/tributos", TODOS),
            new Endpoint("GET", "/api/tributos/paginados", TODOS),
            new Endpoint("POST", "/api/tributos", ADMIN, """
                    {"codigo":"T1","descripcion":"Tributo"}"""),
            new Endpoint("PUT", "/api/tributos/{id}", ADMIN, """
                    {"descripcion":"Tributo"}"""),
            new Endpoint("DELETE", "/api/tributos/{id}", ADMIN),
            new Endpoint("GET", "/api/segmentos-mora", TODOS),
            new Endpoint("POST", "/api/segmentos-mora", ADMIN, """
                    {"nombre":"Temprana","orden":1}"""),
            new Endpoint("GET", "/api/configuraciones-segmento", TODOS),
            new Endpoint("GET", "/api/configuraciones-segmento/tipos-padron", TODOS),
            new Endpoint("POST", "/api/configuraciones-segmento", ADMIN, RANGO),
            new Endpoint("PUT", "/api/configuraciones-segmento", ADMIN, "{\"rangos\":[" + RANGO + "]}"),
            new Endpoint("DELETE", "/api/configuraciones-segmento/tipo-padron/{tipoPadron}", ADMIN),
            // Usuarios
            new Endpoint("GET", "/api/usuarios", ADMIN),
            new Endpoint("GET", "/api/usuarios/{id}", ADMIN),
            new Endpoint("POST", "/api/usuarios", ADMIN, """
                    {"username":"nuevo","password":"clave-segura","roles":["ANALISTA"]}"""),
            new Endpoint("PUT", "/api/usuarios/{id}", ADMIN, """
                    {"username":"nuevo","roles":["ANALISTA"],"activo":true}"""),
            // Sincronización
            new Endpoint("POST", "/api/sincronizaciones/deudas", ADMIN),
            new Endpoint("GET", "/api/sincronizaciones/configuracion", ADMIN),
            new Endpoint("PUT", "/api/sincronizaciones/configuracion", ADMIN, """
                    {"anioMinimo":2020}"""),
            new Endpoint("GET", "/api/sincronizaciones/ejecucion", ADMIN),
            new Endpoint("POST", "/api/sincronizaciones/ejecucion", ADMIN),
            new Endpoint("POST", "/api/sincronizaciones/ejecucion/cancelar", ADMIN)
    );

    /** Las 7 combinaciones no vacías de roles: los permisos se suman. */
    static List<Set<Rol>> combinacionesDeRoles() {
        Rol[] roles = Rol.values();
        List<Set<Rol>> combinaciones = new ArrayList<>();
        for (int mascara = 1; mascara < (1 << roles.length); mascara++) {
            Set<Rol> combinacion = EnumSet.noneOf(Rol.class);
            for (int i = 0; i < roles.length; i++) if ((mascara & (1 << i)) != 0) combinacion.add(roles[i]);
            combinaciones.add(combinacion);
        }
        return combinaciones;
    }

    static Stream<Arguments> casos() {
        return MATRIZ.stream().flatMap(e -> combinacionesDeRoles().stream().map(roles -> Arguments.of(e, roles)));
    }

    static Stream<Endpoint> endpoints() { return MATRIZ.stream(); }

    @ParameterizedTest(name = "{0} con {1}")
    @MethodSource("casos")
    void cadaCombinacionDeRolesTieneElAccesoEsperado(Endpoint endpoint, Set<Rol> roles) throws Exception {
        String[] nombres = roles.stream().map(Enum::name).toArray(String[]::new);
        int estado = mvc.perform(endpoint.peticion().with(user("usuario").roles(nombres)))
                .andReturn().getResponse().getStatus();

        boolean permitido = endpoint.permitidos() == PUBLICO
                || roles.stream().anyMatch(endpoint.permitidos()::contains);
        if (permitido) assertThat(estado).as("%s con %s debe pasar la seguridad", endpoint, roles).isNotIn(401, 403);
        else assertThat(estado).as("%s con %s debe estar prohibido", endpoint, roles).isEqualTo(403);
    }

    @ParameterizedTest(name = "{0} sin sesión")
    @MethodSource("endpoints")
    void sinSesionSoloElLoginEstaAbierto(Endpoint endpoint) throws Exception {
        int estado = mvc.perform(endpoint.peticion()).andReturn().getResponse().getStatus();

        if (endpoint.permitidos() == PUBLICO) assertThat(estado).isNotIn(401, 403);
        else assertThat(estado).as("%s sin sesión", endpoint).isEqualTo(401);
    }

    @Test
    void todosLosEndpointsEstanEnLaMatriz() {
        Set<String> registrados = new TreeSet<>();
        mapeos.getHandlerMethods().forEach((info, metodo) -> {
            if (!metodo.getBeanType().getPackageName().startsWith("com.intendencia")) return;
            for (String ruta : info.getPatternValues())
                for (var verbo : info.getMethodsCondition().getMethods())
                    registrados.add(verbo.name() + " " + ruta);
        });
        Set<String> enMatriz = MATRIZ.stream().map(Endpoint::clave).collect(Collectors.toCollection(TreeSet::new));

        assertThat(enMatriz).as("Endpoints sin fila en la matriz (agregarlos con sus roles)")
                .containsAll(registrados);
        assertThat(registrados).as("Filas de la matriz que ya no existen en la API")
                .containsAll(enMatriz);
    }
}
