package pj.eu.diarionutrizionale.alimento;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Corpo di POST e PUT /api/alimenti.
 * I valori arrivano come mappa codice -> valore per 100 g.
 */
public record AlimentoRichiesta(
    @NotBlank(message = "il nome è obbligatorio")
    String nome,

    String marca,
    String ean,

    @Pattern(regexp = "CRUDO|COTTO|SECCO", message = "stato non ammesso")
    String stato,

    @Pattern(regexp = "MANUALE|ETICHETTA|OFF|CREA|MODELLO", message = "fonte non ammessa")
    String fonte,

    Map<String, BigDecimal> per100
) {
    public String statoOrDefault() { return stato == null ? "CRUDO" : stato; }
    public String fonteOrDefault() { return fonte == null ? "MANUALE" : fonte; }
}
