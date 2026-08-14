package pj.eu.diarionutrizionale.alimento;

import pj.eu.diarionutrizionale.comune.UtenteDettagliService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/alimenti")
public class AlimentoController {

    private final AlimentoRepository repo;
    private final ValidatoreNutrienti validatore;
    private final UtenteDettagliService utenti;

    public AlimentoController(AlimentoRepository repo,
                              ValidatoreNutrienti validatore,
                              UtenteDettagliService utenti) {
        this.repo = repo;
        this.validatore = validatore;
        this.utenti = utenti;
    }

    @GetMapping
    public List<AlimentoSintesi> cerca(@AuthenticationPrincipal UserDetails u,
                                       @RequestParam(required = false) String q,
                                       @RequestParam(defaultValue = "20") int limite) {
        return repo.cerca(id(u), q, Math.min(limite, 100));
    }

    @GetMapping("/{id}")
    public Alimento uno(@AuthenticationPrincipal UserDetails u, @PathVariable long id) {
        return repo.perId(id(u), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public Map<String, Object> crea(@AuthenticationPrincipal UserDetails u,
                                    @Valid @RequestBody AlimentoRichiesta r) {
        var esito = validatore.verifica(r.per100());
        long id = repo.inserisci(id(u), r);
        return risposta(id, 1, esito);
    }

    @PutMapping("/{id}")
    @Transactional
    public Map<String, Object> modifica(@AuthenticationPrincipal UserDetails u,
                                        @PathVariable long id,
                                        @Valid @RequestBody AlimentoRichiesta r) {
        var esito = validatore.verifica(r.per100());
        int versione = repo.aggiorna(id(u), id, r);
        return risposta(id, versione, esito);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void archivia(@AuthenticationPrincipal UserDetails u, @PathVariable long id) {
        repo.archivia(id(u), id);
    }

    private Map<String, Object> risposta(long id, int versione, ValidatoreNutrienti.Esito e) {
        return e.sospetto()
            ? Map.of("id", id, "versione", versione, "avviso", e.messaggio())
            : Map.of("id", id, "versione", versione);
    }

    private long id(UserDetails u) {
        return utenti.idDi(u.getUsername());
    }
}
