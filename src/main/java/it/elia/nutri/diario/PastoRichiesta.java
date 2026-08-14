package it.elia.nutri.diario;

import jakarta.validation.constraints.Pattern;

public record PastoRichiesta(
    @Pattern(regexp = "COLAZIONE|PRANZO|CENA|SPUNTINO", message = "tipo non ammesso")
    String tipo,
    String nome
) {
    public String tipoOrDefault() { return tipo == null ? "SPUNTINO" : tipo; }
}
