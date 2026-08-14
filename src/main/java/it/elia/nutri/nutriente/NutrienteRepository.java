package it.elia.nutri.nutriente;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class NutrienteRepository {

    private final JdbcClient jdbc;

    public NutrienteRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<Nutriente> tutti() {
        return jdbc.sql("""
                SELECT codice, nome, unita, padre, ordine, obbligatorio, kcal_per_g
                FROM   nutriente
                ORDER  BY ordine
                """)
            .query(Nutriente.class)
            .list();
    }

    public List<String> codici() {
        return jdbc.sql("SELECT codice FROM nutriente ORDER BY ordine")
            .query(String.class)
            .list();
    }
}
