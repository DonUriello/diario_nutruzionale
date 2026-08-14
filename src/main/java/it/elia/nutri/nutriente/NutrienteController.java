package it.elia.nutri.nutriente;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/nutrienti")
public class NutrienteController {

    private final NutrienteRepository repo;

    public NutrienteController(NutrienteRepository repo) {
        this.repo = repo;
    }

    /** Il frontend legge da qui ordine, unità e gerarchia: non li ricodifica. */
    @GetMapping
    public List<Nutriente> catalogo() {
        return repo.tutti();
    }
}
