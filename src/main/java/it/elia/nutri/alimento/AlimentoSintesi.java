package it.elia.nutri.alimento;

import java.math.BigDecimal;

/** Riga di risultato della ricerca: quel che serve a una lista. */
public record AlimentoSintesi(
    long id,
    String nome,
    String marca,
    String stato,
    boolean verificato,
    BigDecimal kcal
) {}
