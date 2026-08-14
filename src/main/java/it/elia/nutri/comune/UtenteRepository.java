package it.elia.nutri.comune;

import it.elia.nutri.comune.GestoreErrori.DatoNonValido;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class UtenteRepository {

    private final JdbcClient jdbc;
    private final PasswordEncoder cifratore;

    public UtenteRepository(JdbcClient jdbc, PasswordEncoder cifratore) {
        this.jdbc = jdbc;
        this.cifratore = cifratore;
    }

    /**
     * Registrazione su invito: un diario alimentare non ha motivo
     * di accettare iscrizioni aperte.
     */
    @Transactional
    public long registra(String email, String password, String invito) {
        int valido = jdbc.sql("SELECT count(*) FROM invito WHERE codice = :c AND usato_da IS NULL")
            .param("c", invito).query(Integer.class).single();
        if (valido == 0) throw new DatoNonValido("codice di invito non valido o già usato");

        int esiste = jdbc.sql("SELECT count(*) FROM utente WHERE lower(email) = lower(:e)")
            .param("e", email).query(Integer.class).single();
        if (esiste > 0) throw new DatoNonValido("esiste già un account con questa email");

        KeyHolder chiavi = new GeneratedKeyHolder();
        jdbc.sql("INSERT INTO utente (email, password_hash) VALUES (:e, :p)")
            .param("e", email.trim().toLowerCase())
            .param("p", cifratore.encode(password))
            .update(chiavi, "id");
        long id = ((Number) chiavi.getKeys().get("id")).longValue();

        jdbc.sql("UPDATE invito SET usato_da = :u, usato_il = now() WHERE codice = :c")
            .param("u", id).param("c", invito).update();
        return id;
    }
}
