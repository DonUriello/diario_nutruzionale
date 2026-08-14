package it.elia.nutri.diario;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.elia.nutri.comune.GestoreErrori.NonTrovato;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

@Repository
public class DiarioRepository {

    private static final BigDecimal CENTO = new BigDecimal("100");

    private final JdbcClient jdbc;
    private final ObjectMapper json;

    public DiarioRepository(JdbcClient jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    private record RigaPasto(long id, String tipo, String nome, short ordine) {}

    private record RigaVoce(long id, long pastoId, long alimentoId, int versione,
                            String nome, BigDecimal grammi, String valori) {}

    public Diario giorno(long utente, LocalDate data) {
        List<RigaPasto> pasti = jdbc.sql("""
                SELECT id, tipo, nome, ordine
                FROM   pasto
                WHERE  utente_id = :u AND giorno = :g
                ORDER  BY CASE tipo
                            WHEN 'COLAZIONE' THEN 1
                            WHEN 'PRANZO'    THEN 2
                            WHEN 'CENA'      THEN 3
                            ELSE 4 END, ordine
                """)
            .param("u", utente).param("g", data)
            .query(RigaPasto.class).list();

        List<RigaVoce> voci = pasti.isEmpty() ? List.of() : jdbc.sql("""
                SELECT vp.id, vp.pasto_id, vp.alimento_id, vp.versione,
                       av.nome, vp.grammi, av.valori::text AS valori
                FROM   voce_pasto vp
                JOIN   pasto p ON p.id = vp.pasto_id
                JOIN   alimento_versione av
                       ON av.alimento_id = vp.alimento_id
                      AND av.versione    = vp.versione
                WHERE  p.utente_id = :u AND p.giorno = :g
                ORDER  BY vp.aggiunto_il
                """)
            .param("u", utente).param("g", data)
            .query(RigaVoce.class).list();

        Map<String, BigDecimal> totaleGiorno = new LinkedHashMap<>();
        List<Diario.Pasto> risultato = new ArrayList<>();

        for (RigaPasto p : pasti) {
            List<Diario.Voce> sue = new ArrayList<>();
            Map<String, BigDecimal> totalePasto = new LinkedHashMap<>();

            for (RigaVoce v : voci) {
                if (v.pastoId() != p.id()) continue;
                Map<String, BigDecimal> proporzionati = proporziona(v.valori(), v.grammi());
                sue.add(new Diario.Voce(v.id(), v.alimentoId(), v.versione(),
                    v.nome(), v.grammi(), proporzionati));
                accumula(totalePasto, proporzionati);
            }
            accumula(totaleGiorno, totalePasto);
            risultato.add(new Diario.Pasto(p.id(), p.tipo(), p.nome(), p.ordine(), sue, totalePasto));
        }
        return new Diario(data, risultato, totaleGiorno);
    }

    /** Proporzione al volo: valori per 100 g scalati sui grammi effettivi. */
    private Map<String, BigDecimal> proporziona(String valoriJson, BigDecimal grammi) {
        Map<String, BigDecimal> per100 = leggi(valoriJson);
        BigDecimal fattore = grammi.divide(CENTO, 6, RoundingMode.HALF_UP);
        Map<String, BigDecimal> out = new LinkedHashMap<>();
        per100.forEach((k, v) -> {
            if (v != null) out.put(k, v.multiply(fattore).setScale(3, RoundingMode.HALF_UP));
        });
        return out;
    }

    private Map<String, BigDecimal> leggi(String testo) {
        try {
            return json.readValue(testo, new TypeReference<Map<String, BigDecimal>>() {});
        } catch (Exception e) {
            throw new IllegalStateException("valori non leggibili", e);
        }
    }

    private void accumula(Map<String, BigDecimal> dove, Map<String, BigDecimal> cosa) {
        cosa.forEach((k, v) -> dove.merge(k, v, BigDecimal::add));
    }

    public long creaPasto(long utente, LocalDate giorno, String tipo, String nome) {
        short ordine = jdbc.sql("""
                SELECT coalesce(max(ordine) + 1, 0)
                FROM   pasto
                WHERE  utente_id = :u AND giorno = :g AND tipo = :t
                """)
            .param("u", utente).param("g", giorno).param("t", tipo)
            .query(Short.class).single();

        KeyHolder chiavi = new GeneratedKeyHolder();
        jdbc.sql("""
                INSERT INTO pasto (utente_id, giorno, tipo, nome, ordine)
                VALUES (:u, :g, :t, :n, :o)
                """)
            .param("u", utente).param("g", giorno).param("t", tipo)
            .param("n", nome).param("o", ordine)
            .update(chiavi, "id");
        return ((Number) chiavi.getKeys().get("id")).longValue();
    }

    /** Crea i tre pasti fissi se il giorno è vuoto. */
    public void assicuraPastiFissi(long utente, LocalDate giorno) {
        for (String tipo : List.of("COLAZIONE", "PRANZO", "CENA")) {
            jdbc.sql("""
                    INSERT INTO pasto (utente_id, giorno, tipo, ordine)
                    VALUES (:u, :g, :t, 0)
                    ON CONFLICT (utente_id, giorno, tipo, ordine) DO NOTHING
                    """)
                .param("u", utente).param("g", giorno).param("t", tipo)
                .update();
        }
    }

    /** La voce punta alla versione corrente dell'alimento, non all'alimento. */
    public long aggiungiVoce(long utente, long pastoId, long alimentoId, BigDecimal grammi) {
        Integer versione = jdbc.sql("""
                SELECT a.versione
                FROM   alimento a
                WHERE  a.id = :a AND (a.proprietario = :u OR a.proprietario IS NULL)
                """)
            .param("a", alimentoId).param("u", utente)
            .query(Integer.class).optional()
            .orElseThrow(() -> new NonTrovato("alimento " + alimentoId + " non trovato"));

        int miei = jdbc.sql("SELECT count(*) FROM pasto WHERE id = :p AND utente_id = :u")
            .param("p", pastoId).param("u", utente).query(Integer.class).single();
        if (miei == 0) throw new NonTrovato("pasto " + pastoId + " non trovato");

        KeyHolder chiavi = new GeneratedKeyHolder();
        jdbc.sql("""
                INSERT INTO voce_pasto (pasto_id, alimento_id, versione, grammi)
                VALUES (:p, :a, :v, :g)
                """)
            .param("p", pastoId).param("a", alimentoId)
            .param("v", versione).param("g", grammi)
            .update(chiavi, "id");
        return ((Number) chiavi.getKeys().get("id")).longValue();
    }

    public void aggiornaGrammi(long utente, long voceId, BigDecimal grammi) {
        int n = jdbc.sql("""
                UPDATE voce_pasto vp
                SET    grammi = :g
                FROM   pasto p
                WHERE  p.id = vp.pasto_id AND vp.id = :v AND p.utente_id = :u
                """)
            .param("v", voceId).param("u", utente).param("g", grammi).update();
        if (n == 0) throw new NonTrovato("voce " + voceId + " non trovata");
    }

    public void rimuoviVoce(long utente, long voceId) {
        int n = jdbc.sql("""
                DELETE FROM voce_pasto vp
                USING  pasto p
                WHERE  p.id = vp.pasto_id AND vp.id = :v AND p.utente_id = :u
                """)
            .param("v", voceId).param("u", utente).update();
        if (n == 0) throw new NonTrovato("voce " + voceId + " non trovata");
    }
}
