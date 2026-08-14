package it.elia.nutri.alimento;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.elia.nutri.comune.GestoreErrori.NonTrovato;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
public class AlimentoRepository {

    private final JdbcClient jdbc;
    private final ObjectMapper json;

    public AlimentoRepository(JdbcClient jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    /**
     * Ricerca per similarità trigram: "mozarella" trova comunque la mozzarella.
     * Il filtro su proprietario è nel WHERE, non in un controllo successivo.
     */
    public List<AlimentoSintesi> cerca(long utente, String q, int limite) {
        if (q == null || q.isBlank()) {
            return jdbc.sql("""
                    SELECT a.id, a.nome, a.marca, a.stato, a.verificato,
                           an.valore AS kcal
                    FROM   alimento a
                    LEFT   JOIN alimento_nutriente an
                           ON an.alimento_id = a.id AND an.nutriente = 'kcal'
                    WHERE  (a.proprietario = :u OR a.proprietario IS NULL)
                      AND  NOT a.archiviato
                    ORDER  BY a.nome
                    LIMIT  :lim
                    """)
                .param("u", utente).param("lim", limite)
                .query(AlimentoSintesi.class).list();
        }
        return jdbc.sql("""
                SELECT a.id, a.nome, a.marca, a.stato, a.verificato,
                       an.valore AS kcal
                FROM   alimento a
                LEFT   JOIN alimento_nutriente an
                       ON an.alimento_id = a.id AND an.nutriente = 'kcal'
                WHERE  (a.proprietario = :u OR a.proprietario IS NULL)
                  AND  NOT a.archiviato
                  AND  (a.nome ILIKE '%' || :q || '%' OR a.nome % :q)
                ORDER  BY similarity(a.nome, :q) DESC, a.nome
                LIMIT  :lim
                """)
            .param("u", utente).param("q", q).param("lim", limite)
            .query(AlimentoSintesi.class).list();
    }

    public Alimento perId(long utente, long id) {
        var testa = jdbc.sql("""
                SELECT id, nome, marca, ean, stato, fonte, verificato, versione
                FROM   alimento
                WHERE  id = :id
                  AND  (proprietario = :u OR proprietario IS NULL)
                  AND  NOT archiviato
                """)
            .param("id", id).param("u", utente)
            .query((rs, n) -> new Alimento(
                rs.getLong("id"), rs.getString("nome"), rs.getString("marca"),
                rs.getString("ean"), rs.getString("stato"), rs.getString("fonte"),
                rs.getBoolean("verificato"), rs.getInt("versione"), Map.of()))
            .optional()
            .orElseThrow(() -> new NonTrovato("alimento " + id + " non trovato"));

        return new Alimento(testa.id(), testa.nome(), testa.marca(), testa.ean(),
            testa.stato(), testa.fonte(), testa.verificato(), testa.versione(),
            valoriDi(id));
    }

    public Map<String, BigDecimal> valoriDi(long alimentoId) {
        Map<String, BigDecimal> m = new HashMap<>();
        jdbc.sql("SELECT nutriente, valore FROM alimento_nutriente WHERE alimento_id = :id")
            .param("id", alimentoId)
            .query((rs, n) -> {
                m.put(rs.getString("nutriente"), rs.getBigDecimal("valore"));
                return null;
            }).list();
        return m;
    }

    public long inserisci(long utente, AlimentoRichiesta r) {
        KeyHolder chiavi = new GeneratedKeyHolder();
        jdbc.sql("""
                INSERT INTO alimento (proprietario, nome, marca, ean, stato, fonte, versione)
                VALUES (:u, :nome, :marca, :ean, :stato, :fonte, 1)
                """)
            .param("u", utente)
            .param("nome", r.nome().trim())
            .param("marca", r.marca())
            .param("ean", r.ean())
            .param("stato", r.statoOrDefault())
            .param("fonte", r.fonteOrDefault())
            .update(chiavi, "id");

        long id = ((Number) chiavi.getKeys().get("id")).longValue();
        scriviValori(id, r.per100());
        scriviVersione(id, 1, r.nome().trim(), r.per100());
        return id;
    }

    /** Modifica: nuova versione immutabile, così il diario passato non cambia. */
    public int aggiorna(long utente, long id, AlimentoRichiesta r) {
        int toccate = jdbc.sql("""
                UPDATE alimento
                SET    nome = :nome, marca = :marca, ean = :ean,
                       stato = :stato, fonte = :fonte,
                       versione = versione + 1, aggiornato_il = now()
                WHERE  id = :id AND proprietario = :u AND NOT archiviato
                """)
            .param("id", id).param("u", utente)
            .param("nome", r.nome().trim()).param("marca", r.marca()).param("ean", r.ean())
            .param("stato", r.statoOrDefault()).param("fonte", r.fonteOrDefault())
            .update();

        if (toccate == 0) throw new NonTrovato("alimento " + id + " non modificabile");

        int versione = jdbc.sql("SELECT versione FROM alimento WHERE id = :id")
            .param("id", id).query(Integer.class).single();

        jdbc.sql("DELETE FROM alimento_nutriente WHERE alimento_id = :id")
            .param("id", id).update();
        scriviValori(id, r.per100());
        scriviVersione(id, versione, r.nome().trim(), r.per100());
        return versione;
    }

    /** Archivia invece di cancellare: le voci di diario puntano alle versioni. */
    public void archivia(long utente, long id) {
        int n = jdbc.sql("UPDATE alimento SET archiviato = true WHERE id = :id AND proprietario = :u")
            .param("id", id).param("u", utente).update();
        if (n == 0) throw new NonTrovato("alimento " + id + " non trovato");
    }

    private void scriviValori(long id, Map<String, BigDecimal> valori) {
        if (valori == null) return;
        valori.forEach((codice, valore) -> {
            if (valore == null) return;
            jdbc.sql("""
                    INSERT INTO alimento_nutriente (alimento_id, nutriente, valore)
                    VALUES (:id, :n, :v)
                    ON CONFLICT (alimento_id, nutriente) DO UPDATE SET valore = :v
                    """)
                .param("id", id).param("n", codice).param("v", valore)
                .update();
        });
    }

    private void scriviVersione(long id, int versione, String nome, Map<String, BigDecimal> valori) {
        try {
            String testo = json.writeValueAsString(valori == null ? Map.of() : valori);
            jdbc.sql("""
                    INSERT INTO alimento_versione (alimento_id, versione, nome, valori)
                    VALUES (:id, :ver, :nome, CAST(:valori AS jsonb))
                    """)
                .param("id", id).param("ver", versione).param("nome", nome).param("valori", testo)
                .update();
        } catch (Exception e) {
            throw new IllegalStateException("serializzazione valori fallita", e);
        }
    }
}
