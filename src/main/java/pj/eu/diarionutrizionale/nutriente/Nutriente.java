package pj.eu.diarionutrizionale.nutriente;

import java.math.BigDecimal;

/**
 * Voce del catalogo nutrienti.
 *
 * @param padre      codice del nutriente che lo contiene ("saturi" sta in "grassi"),
 *                   null se è di primo livello. È la gerarchia dei "di cui".
 * @param kcalPerG   fattore di conversione energetica (allegato XIV del reg. 1169/2011),
 *                   null se non contribuisce all'energia.
 */
public record Nutriente(
    String codice,
    String nome,
    String unita,
    String padre,
    short ordine,
    boolean obbligatorio,
    BigDecimal kcalPerG
) {}
