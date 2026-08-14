package pj.eu.diarionutrizionale.alimento;

import java.math.BigDecimal;
import java.util.Map;

/** Alimento con i suoi valori correnti per 100 g. */
public record Alimento(
    Long id,
    String nome,
    String marca,
    String ean,
    String stato,
    String fonte,
    boolean verificato,
    int versione,
    Map<String, BigDecimal> per100
) {}
