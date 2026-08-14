package it.elia.nutri.comune;

import it.elia.nutri.comune.GestoreErrori.DatoNonValido;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AutenticazioneController {

    private final UtenteRepository utenti;
    private final AuthenticationManager autenticatore;
    private final SecurityContextRepository contesti = new HttpSessionSecurityContextRepository();

    public AutenticazioneController(UtenteRepository utenti, AuthenticationManager autenticatore) {
        this.utenti = utenti;
        this.autenticatore = autenticatore;
    }

    public record Registrazione(
        @Email(message = "email non valida") @NotBlank String email,
        @Size(min = 8, message = "la password deve avere almeno 8 caratteri") String password,
        @NotBlank(message = "serve un codice di invito") String invito
    ) {}

    public record Accesso(@NotBlank String email, @NotBlank String password) {}

    @PostMapping("/registrazione")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> registrati(@Valid @RequestBody Registrazione r,
                                          HttpServletRequest req, HttpServletResponse res) {
        long id = utenti.registra(r.email(), r.password(), r.invito());
        entra(r.email(), r.password(), req, res);
        return Map.of("id", id, "email", r.email());
    }

    @PostMapping("/accesso")
    public Map<String, Object> accedi(@Valid @RequestBody Accesso a,
                                      HttpServletRequest req, HttpServletResponse res) {
        entra(a.email(), a.password(), req, res);
        return Map.of("email", a.email());
    }

    @GetMapping("/io")
    public Map<String, Object> io(@AuthenticationPrincipal UserDetails u) {
        return Map.of("email", u.getUsername());
    }

    @PostMapping("/uscita")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void esci(HttpSession sessione) {
        sessione.invalidate();
        SecurityContextHolder.clearContext();
    }

    /**
     * Autentica e salva il contesto in sessione. Senza il salvataggio
     * esplicito l'autenticazione varrebbe solo per questa richiesta.
     */
    private void entra(String email, String password,
                       HttpServletRequest req, HttpServletResponse res) {
        try {
            var token = UsernamePasswordAuthenticationToken.unauthenticated(email, password);
            var auth = autenticatore.authenticate(token);
            var contesto = SecurityContextHolder.createEmptyContext();
            contesto.setAuthentication(auth);
            SecurityContextHolder.setContext(contesto);
            contesti.saveContext(contesto, req, res);
        } catch (BadCredentialsException e) {
            throw new DatoNonValido("email o password non corretti");
        }
    }
}
