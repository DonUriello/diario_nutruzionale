package it.elia.nutri.diario;

import it.elia.nutri.comune.UtenteDettagliService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class DiarioController {

    private final DiarioRepository repo;
    private final UtenteDettagliService utenti;

    public DiarioController(DiarioRepository repo, UtenteDettagliService utenti) {
        this.repo = repo;
        this.utenti = utenti;
    }

    @GetMapping("/giorni/{data}")
    @Transactional
    public Diario giorno(@AuthenticationPrincipal UserDetails u,
                         @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data) {
        long id = id(u);
        repo.assicuraPastiFissi(id, data);
        return repo.giorno(id, data);
    }

    @PostMapping("/giorni/{data}/pasti")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> aggiungiPasto(
            @AuthenticationPrincipal UserDetails u,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data,
            @Valid @RequestBody PastoRichiesta r) {
        long id = repo.creaPasto(id(u), data, r.tipoOrDefault(), r.nome());
        return Map.of("id", id);
    }

    @PostMapping("/pasti/{pastoId}/voci")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> aggiungiVoce(@AuthenticationPrincipal UserDetails u,
                                            @PathVariable long pastoId,
                                            @Valid @RequestBody VoceRichiesta r) {
        long id = repo.aggiungiVoce(id(u), pastoId, r.alimentoId(), r.grammi());
        return Map.of("id", id);
    }

    @PatchMapping("/voci/{voceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void modificaVoce(@AuthenticationPrincipal UserDetails u,
                             @PathVariable long voceId,
                             @Valid @RequestBody GrammiRichiesta r) {
        repo.aggiornaGrammi(id(u), voceId, r.grammi());
    }

    @DeleteMapping("/voci/{voceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void rimuoviVoce(@AuthenticationPrincipal UserDetails u, @PathVariable long voceId) {
        repo.rimuoviVoce(id(u), voceId);
    }

    private long id(UserDetails u) {
        return utenti.idDi(u.getUsername());
    }
}
