package com.intendencia.gestion_morosidad_api.shared.exception;

import com.intendencia.gestion_morosidad_api.shared.dto.ErrorResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Traduce excepciones a respuestas JSON uniformes.
 * Nunca devuelve stacktraces ni mensajes internos al cliente (Anexo 5).
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler({org.springframework.web.servlet.resource.NoResourceFoundException.class,
            org.springframework.web.servlet.NoHandlerFoundException.class})
    public ResponseEntity<ErrorResponse> rutaNoEncontrada(Exception ex) {
        return respuesta(HttpStatus.NOT_FOUND, "No se encontró el recurso solicitado");
    }

    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ResponseEntity<ErrorResponse> noEncontrado(RecursoNoEncontradoException ex) {
        return respuesta(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(ReglaNegocioException.class)
    public ResponseEntity<ErrorResponse> reglaNegocio(ReglaNegocioException ex) {
        return respuesta(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(OperacionEnCursoException.class)
    public ResponseEntity<ErrorResponse> operacionEnCurso(OperacionEnCursoException ex) {
        return respuesta(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(IntegracionExternaException.class)
    public ResponseEntity<ErrorResponse> integracionExterna(IntegracionExternaException ex) {
        return respuesta(HttpStatus.BAD_GATEWAY, ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> validacion(MethodArgumentNotValidException ex) {
        Map<String, String> errores = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> errores.putIfAbsent(error.getField(), error.getDefaultMessage()));
        return ResponseEntity.badRequest()
                .body(ErrorResponse.de(400, "Datos inválidos", errores));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> tipoInvalido(MethodArgumentTypeMismatchException ex) {
        return respuesta(HttpStatus.BAD_REQUEST, "Valor inválido para el parámetro '" + ex.getName() + "'");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> cuerpoInvalido(HttpMessageNotReadableException ex) {
        return respuesta(HttpStatus.BAD_REQUEST, "El cuerpo de la solicitud no es válido");
    }

    /** Ordenamiento por un campo inexistente, ej. ?sort=noExiste */
    @ExceptionHandler(PropertyReferenceException.class)
    public ResponseEntity<ErrorResponse> propiedadInvalida(PropertyReferenceException ex) {
        return respuesta(HttpStatus.BAD_REQUEST, "Campo de ordenamiento inválido: " + ex.getPropertyName());
    }

    /** Se relanza para que Spring Security responda 403 en lugar de caer en el handler genérico. */
    @ExceptionHandler(AccessDeniedException.class)
    public void accesoDenegado(AccessDeniedException ex) {
        throw ex;
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> inesperado(Exception ex) {
        // Errores de protocolo de Spring MVC (ruta inexistente, método o content-type no soportado):
        // conservan su código en lugar de convertirse en 500.
        if (ex instanceof org.springframework.web.ErrorResponse error) {
            HttpStatus status = HttpStatus.valueOf(error.getStatusCode().value());
            String mensaje = switch (status) {
                case NOT_FOUND -> "Recurso no encontrado";
                case METHOD_NOT_ALLOWED -> "Método no permitido para esta ruta";
                case UNSUPPORTED_MEDIA_TYPE -> "Tipo de contenido no soportado";
                default -> "Solicitud inválida";
            };
            return ResponseEntity.status(status).headers(error.getHeaders())
                    .body(ErrorResponse.de(status.value(), mensaje));
        }
        log.error("Error no controlado", ex);
        return respuesta(HttpStatus.INTERNAL_SERVER_ERROR, "Ocurrió un error inesperado");
    }

    private ResponseEntity<ErrorResponse> respuesta(HttpStatus status, String mensaje) {
        return ResponseEntity.status(status).body(ErrorResponse.de(status.value(), mensaje));
    }

    @ExceptionHandler(RecursoYaExisteException.class)
    public ResponseEntity<ErrorResponse> yaExiste(RecursoYaExisteException ex) {
        return respuesta(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(LoginBloqueadoException.class)
    public ResponseEntity<ErrorResponse> loginBloqueado(LoginBloqueadoException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, Long.toString(ex.reintentarEnSegundos()))
                .body(ErrorResponse.de(429, ex.getMessage()));
    }

    /** Login fallido: BadCredentialsException, DisabledException, LockedException, etc. */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> credencialesInvalidas(AuthenticationException ex) {
        return respuesta(HttpStatus.UNAUTHORIZED, "Usuario o contraseña incorrectos");
    }

}
