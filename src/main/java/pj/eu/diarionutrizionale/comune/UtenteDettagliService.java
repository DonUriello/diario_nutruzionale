package pj.eu.diarionutrizionale.comune;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class UtenteDettagliService implements UserDetailsService {

    private final JdbcClient jdbc;

    public UtenteDettagliService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public UserDetails loadUserByUsername(String email) {
        // Mappatura esplicita invece di query(Riga.class): la conversione
        // automatica da password_hash a passwordHash è una cosa in meno
        // che può andare storta.
        return jdbc.sql("""
                SELECT email, password_hash
                FROM   utente
                WHERE  lower(email) = lower(:e)
                """)
            .param("e", email == null ? "" : email.trim())
            .query((rs, n) -> (UserDetails) User
                .withUsername(rs.getString("email"))
                .password(rs.getString("password_hash"))
                .roles("UTENTE")
                .build())
            .optional()
            .orElseThrow(() -> new UsernameNotFoundException("nessun utente con email " + email));
    }

    /** Id dell'utente autenticato. Serve a filtrare ogni query nel WHERE. */
    public long idDi(String email) {
        return jdbc.sql("SELECT id FROM utente WHERE lower(email) = lower(:e)")
            .param("e", email == null ? "" : email.trim())
            .query(Long.class)
            .single();
    }
}
