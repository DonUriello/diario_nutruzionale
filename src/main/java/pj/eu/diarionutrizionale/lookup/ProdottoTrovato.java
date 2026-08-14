package pj.eu.diarionutrizionale.lookup;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Un prodotto letto da una fonte esterna, pronto per riempire la modale
 * "Nuovo ingrediente": stessa forma che il frontend si aspetta in
 * {@code applicaProdotto} (nome, marca, fonte, valori per 100 g).
 *
 * @param fonte  codice della provenienza dei dati ("OFF" per Open Food Facts),
 *               che il frontend rimanda al salvataggio come {@code fonte}.
 * @param per100 valori per 100 g, mappa codice-nutriente -> valore. Contiene
 *               solo i nutrienti che la fonte dichiara davvero: un campo
 *               assente resta assente, perché "non dichiarato" non è "zero".
 */
public record ProdottoTrovato(
    String nome,
    String marca,
    String fonte,
    Map<String, BigDecimal> per100
) {}
