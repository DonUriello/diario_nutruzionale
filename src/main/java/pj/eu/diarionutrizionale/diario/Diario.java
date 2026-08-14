package pj.eu.diarionutrizionale.diario;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Vista completa di una giornata: pasti, voci e totali. */
public record Diario(
    LocalDate giorno,
    List<Pasto> pasti,
    Map<String, BigDecimal> totale
) {

    public record Pasto(
        long id,
        String tipo,
        String nome,
        short ordine,
        List<Voce> voci,
        Map<String, BigDecimal> totale
    ) {}

    public record Voce(
        long id,
        long alimentoId,
        int versione,
        String nome,
        BigDecimal grammi,
        Map<String, BigDecimal> valori
    ) {}
}
