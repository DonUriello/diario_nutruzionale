package it.elia.nutri.comune;

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

    public record Riga(String email, String passwordHash) {}

    @Override
    public UserDetails loadUserByUsername(String email) {
        return jdbc.sql("SELECT email, password_hash FROM utente WHERE lower(email) = lower(:e)")
            .param("e", email)
            .query(Riga.class)
            .optional()
            .map(r -> (UserDetails) User.withUsername(r.email()).password(r.passwordHash()).build())
            .orElseThrow(() -> new UsernameNotFoundException(email));
    }

    /** Id dell'utente autenticato. Serve a filtrare ogni query nel WHERE. */
    public long idDi(String email) {
        return jdbc.sql("SELECT id FROM utente WHERE lower(email) = lower(:e)")
            .param("e", email)
            .query(Long.class)
            .single();
    }
}
