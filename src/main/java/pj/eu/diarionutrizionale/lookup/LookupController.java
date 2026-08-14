package pj.eu.diarionutrizionale.lookup;

import pj.eu.diarionutrizionale.comune.GestoreErrori.DatoNonValido;
import pj.eu.diarionutrizionale.comune.GestoreErrori.NonTrovato;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Ricerca degli alimenti su una fonte esterna, così l'utente non deve copiare
 * l'etichetta a mano. Alimenta la modale "Nuovo ingrediente": i valori tornano
 * al frontend nella forma per 100 g e vengono solo proposti — l'utente li
 * controlla prima di salvare.
 */
@RestController
@RequestMapping("/api/lookup")
public class LookupController {

    private final ClientOpenFoodFacts off;

    public LookupController(ClientOpenFoodFacts off) {
        this.off = off;
    }

    /** Codice a barre: un solo prodotto, applicato subito lato frontend. */
    @GetMapping("/ean/{ean}")
    public ProdottoTrovato perEan(@PathVariable String ean) {
        return off.perEan(ean).orElseThrow(() ->
            new NonTrovato("Nessun prodotto con questo codice a barre."));
    }

    /** Testo libero: una rosa di risultati fra cui scegliere. */
    @GetMapping("/nome")
    public List<ProdottoTrovato> perNome(@RequestParam String q) {
        String testo = q == null ? "" : q.trim();
        if (testo.length() < 2) {
            throw new DatoNonValido("Servono almeno due caratteri per cercare.");
        }
        return off.perNome(testo);
    }
}
