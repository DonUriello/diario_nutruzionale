package pj.eu.diarionutrizionale.comune;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Errori in application/problem+json (RFC 9457). */
@RestControllerAdvice
public class GestoreErrori {

    public static class DatoNonValido extends RuntimeException {
        public DatoNonValido(String m) { super(m); }
    }

    public static class NonTrovato extends RuntimeException {
        public NonTrovato(String m) { super(m); }
    }

    /** Una fonte esterna non ha risposto o ha risposto in modo illeggibile. */
    public static class FonteNonRaggiungibile extends RuntimeException {
        public FonteNonRaggiungibile(String m, Throwable causa) { super(m, causa); }
    }

    @ExceptionHandler(DatoNonValido.class)
    ProblemDetail nonValido(DatoNonValido e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(NonTrovato.class)
    ProblemDetail nonTrovato(NonTrovato e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(FonteNonRaggiungibile.class)
    ProblemDetail fonteGiu(FonteNonRaggiungibile e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, e.getMessage());
    }
}
