package pj.eu.diarionutrizionale.diario;

import pj.eu.diarionutrizionale.comune.GestoreErrori.NonTrovato;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
public class DiarioRepository {

    private final JdbcClient jdbc;

    public DiarioRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    private record RigaPasto(long id, String tipo, String nome, short ordine) {}

    /**
     * Una riga per ogni coppia (voce, nutriente): il jsonb viene esploso da
     * Postgres con jsonb_each_text e la proporzione sui grammi è già fatta
     * nella query. Il raggruppamento avviene qui, sull'ordine del catalogo.
     */
    private record RigaValore(long voceId, long pastoId, long alimentoId, int versione,
                              String nome, BigDecimal grammi,
                              String chiave, BigDecimal valore) {}

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

        // LEFT JOIN LATERAL: una voce con valori vuoti resta comunque nella lista.
        List<RigaValore> righe = pasti.isEmpty() ? List.of() : jdbc.sql("""
                SELECT vp.id            AS voce_id,
                       vp.pasto_id      AS pasto_id,
                       vp.alimento_id   AS alimento_id,
                       vp.versione      AS versione,
                       av.nome          AS nome,
                       vp.grammi        AS grammi,
                       v.chiave         AS chiave,
                       round(nullif(v.valore, 'null')::numeric * vp.grammi / 100, 3) AS valore
                FROM   voce_pasto vp
                JOIN   pasto p ON p.id = vp.pasto_id
                JOIN   alimento_versione av
                       ON av.alimento_id = vp.alimento_id
                      AND av.versione    = vp.versione
                LEFT   JOIN LATERAL jsonb_each_text(av.valori) AS v(chiave, valore) ON true
                WHERE  p.utente_id = :u AND p.giorno = :g
                ORDER  BY vp.aggiunto_il, vp.id
                """)
            .param("u", utente).param("g", data)
            .query(RigaValore.class).list();

        // Ricompone le voci mantenendo l'ordine di inserimento.
        Map<Long, Diario.Voce> voci = new LinkedHashMap<>();
        Map<Long, Map<String, BigDecimal>> valoriDi = new LinkedHashMap<>();
        Map<Long, Long> pastoDi = new LinkedHashMap<>();

        for (RigaValore r : righe) {
            valoriDi.computeIfAbsent(r.voceId(), k -> new LinkedHashMap<>());
            pastoDi.putIfAbsent(r.voceId(), r.pastoId());
            if (r.chiave() != null && r.valore() != null) {
                valoriDi.get(r.voceId()).put(r.chiave(), r.valore());
            }
            voci.putIfAbsent(r.voceId(), new Diario.Voce(
                r.voceId(), r.alimentoId(), r.versione(), r.nome(), r.grammi(),
                valoriDi.get(r.voceId())));
        }

        Map<String, BigDecimal> totaleGiorno = new LinkedHashMap<>();
        List<Diario.Pasto> risultato = new ArrayList<>();

        for (RigaPasto p : pasti) {
            List<Diario.Voce> sue = new ArrayList<>();
            Map<String, BigDecimal> totalePasto = new LinkedHashMap<>();

            voci.forEach((voceId, voce) -> {
                if (!Long.valueOf(p.id()).equals(pastoDi.get(voceId))) return;
                sue.add(voce);
                accumula(totalePasto, voce.valori());
            });

            accumula(totaleGiorno, totalePasto);
            risultato.add(new Diario.Pasto(p.id(), p.tipo(), p.nome(), p.ordine(), sue, totalePasto));
        }
        return new Diario(data, risultato, totaleGiorno);
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
