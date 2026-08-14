package pj.eu.diarionutrizionale.comune;

import pj.eu.diarionutrizionale.comune.GestoreErrori.DatoNonValido;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AutenticazioneController {

    private static final Logger log = LoggerFactory.getLogger(AutenticazioneController.class);

    private final UtenteRepository utenti;
    private final UtenteDettagliService dettagli;
    private final PasswordEncoder cifratore;
    private final SecurityContextRepository contesti = new HttpSessionSecurityContextRepository();

    public AutenticazioneController(UtenteRepository utenti,
                                    UtenteDettagliService dettagli,
                                    PasswordEncoder cifratore) {
        this.utenti = utenti;
        this.dettagli = dettagli;
        this.cifratore = cifratore;
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
     * Verifica la password e salva il contesto in sessione.
     *
     * Il confronto è fatto qui con il PasswordEncoder invece di passare da
     * AuthenticationManager e DaoAuthenticationProvider: meno pezzi in mezzo,
     * e il messaggio di log dice esattamente quale dei due controlli fallisce.
     */
    private void entra(String email, String password,
                       HttpServletRequest req, HttpServletResponse res) {
        UserDetails utente;
        try {
            utente = dettagli.loadUserByUsername(email);
        } catch (UsernameNotFoundException e) {
            log.debug("accesso fallito: nessun utente con email {}", email);
            throw new DatoNonValido("email o password non corretti");
        }

        if (!cifratore.matches(password, utente.getPassword())) {
            log.debug("accesso fallito: password errata per {}", email);
            throw new DatoNonValido("email o password non corretti");
        }

        var auth = UsernamePasswordAuthenticationToken.authenticated(
            utente, null, utente.getAuthorities());

        var contesto = SecurityContextHolder.createEmptyContext();
        contesto.setAuthentication(auth);
        SecurityContextHolder.setContext(contesto);
        contesti.saveContext(contesto, req, res);

        log.debug("accesso riuscito per {}", email);
    }
}
