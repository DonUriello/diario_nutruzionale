package pj.eu.diarionutrizionale.lookup;

import pj.eu.diarionutrizionale.comune.GestoreErrori.FonteNonRaggiungibile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Legge i dati nutrizionali dei produttori da Open Food Facts.
 *
 * L'API è pubblica e in sola lettura, senza chiave. Chiedono un User-Agent
 * che identifichi l'applicazione: lo prendiamo dalla configurazione.
 *
 * Il compito qui è tradurre la risposta di OFF nella forma che la modale
 * "Nuovo ingrediente" si aspetta ({@link ProdottoTrovato}), riportando i
 * valori ai codici e alle unità del nostro catalogo nutrienti. Navighiamo la
 * risposta come mappe generiche, così non dipendiamo dalla versione di Jackson.
 */
@Component
public class ClientOpenFoodFacts {

    private static final String FONTE = "OFF";

    /** Solo i campi che ci servono: meno banda, risposta più leggera. */
    private static final String CAMPI = "product_name,brands,nutriments";

    private static final ParameterizedTypeReference<Map<String, Object>> MAPPA =
        new ParameterizedTypeReference<>() { };

    private final RestClient client;

    public ClientOpenFoodFacts(RestClient.Builder builder,
                               @Value("${openfoodfacts.base-url}") String baseUrl,
                               @Value("${openfoodfacts.user-agent}") String userAgent) {
        // Timeout espliciti: una fonte esterna lenta non deve tenere in ostaggio
        // la richiesta dell'utente. Con i virtual thread il blocco costa poco,
        // ma un limite netto lo vogliamo comunque.
        var http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
        var factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(8));

        this.client = builder
            .baseUrl(baseUrl)
            .defaultHeader(HttpHeaders.USER_AGENT, userAgent)
            .requestFactory(factory)
            .build();
    }

    /**
     * Cerca per codice a barre (EAN/UPC). Un codice individua un prodotto solo:
     * o c'è, o non c'è.
     */
    public Optional<ProdottoTrovato> perEan(String ean) {
        Map<String, Object> radice;
        try {
            radice = client.get()
                .uri("/api/v2/product/{ean}?fields=" + CAMPI, ean)
                .accept(MediaType.APPLICATION_JSON)
                // Prodotto assente: OFF risponde 404 (v2) oppure 200 con status 0.
                // In entrambi i casi non è un errore, è "non trovato".
                .retrieve()
                .onStatus(s -> s.value() == 404, (req, res) -> { })
                .body(MAPPA);
        } catch (RestClientException e) {
            throw new FonteNonRaggiungibile("Open Food Facts non ha risposto.", e);
        }

        if (radice == null) return Optional.empty();
        BigDecimal stato = numero(radice.get("status"));
        if (stato == null || stato.intValue() != 1) return Optional.empty();
        Map<String, Object> prodotto = mappa(radice.get("product"));
        if (prodotto == null) return Optional.empty();
        return Optional.of(daNodo(prodotto));
    }

    /** Cerca per testo libero: restituisce i primi risultati, i più pertinenti prima. */
    public List<ProdottoTrovato> perNome(String q) {
        Map<String, Object> radice;
        try {
            radice = client.get()
                .uri(b -> b.path("/cgi/search.pl")
                    .queryParam("search_terms", "{q}")
                    .queryParam("search_simple", "1")
                    .queryParam("action", "process")
                    .queryParam("json", "1")
                    .queryParam("page_size", "20")
                    .queryParam("fields", CAMPI)
                    .build(Map.of("q", q)))
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(MAPPA);
        } catch (RestClientException e) {
            throw new FonteNonRaggiungibile("Open Food Facts non ha risposto.", e);
        }

        if (radice == null || !(radice.get("products") instanceof List<?> prodotti)) {
            return List.of();
        }
        List<ProdottoTrovato> trovati = new ArrayList<>();
        for (Object o : prodotti) {
            Map<String, Object> p = mappa(o);
            if (p == null) continue;
            ProdottoTrovato t = daNodo(p);
            // Senza nome non è mostrabile né salvabile: lo saltiamo.
            if (t.nome() != null) trovati.add(t);
        }
        return trovati;
    }

    /* ---------------------------------------------------------------
       Traduzione OFF -> nostro modello
       --------------------------------------------------------------- */

    private static ProdottoTrovato daNodo(Map<String, Object> p) {
        return new ProdottoTrovato(
            testo(p, "product_name"),
            primaMarca(testo(p, "brands")),
            FONTE,
            valori(mappa(p.get("nutriments"))));
    }

    /**
     * Riporta i nutrimenti di OFF ai codici del nostro catalogo, sempre per 100 g.
     * OFF dà i valori in grammi; sodio, calcio e ferro nel nostro catalogo sono
     * in milligrammi, quindi vanno moltiplicati per 1000.
     */
    private static Map<String, BigDecimal> valori(Map<String, Object> n) {
        Map<String, BigDecimal> m = new LinkedHashMap<>();
        if (n == null) return m;

        metti(m, "kcal",     numero(n.get("energy-kcal_100g")));
        metti(m, "grassi",   numero(n.get("fat_100g")));
        metti(m, "saturi",   numero(n.get("saturated-fat_100g")));
        metti(m, "carbo",    numero(n.get("carbohydrates_100g")));
        metti(m, "zuccheri", numero(n.get("sugars_100g")));
        metti(m, "fibre",    numero(n.get("fiber_100g")));
        metti(m, "proteine", numero(n.get("proteins_100g")));

        // Il sodio spesso non è dichiarato ma il sale sì: 1 g di sale = 400 mg di sodio.
        BigDecimal sodio = numero(n.get("sodium_100g"));
        if (sodio == null) {
            BigDecimal sale = numero(n.get("salt_100g"));
            if (sale != null) sodio = sale.multiply(new BigDecimal("0.4"));
        }
        metti(m, "sodio",  inMg(sodio));
        metti(m, "calcio", inMg(numero(n.get("calcium_100g"))));
        metti(m, "ferro",  inMg(numero(n.get("iron_100g"))));
        return m;
    }

    private static void metti(Map<String, BigDecimal> m, String codice, BigDecimal v) {
        if (v != null) m.put(codice, v);
    }

    private static BigDecimal inMg(BigDecimal grammi) {
        return grammi == null ? null : grammi.multiply(BigDecimal.valueOf(1000));
    }

    /** Un valore di OFF può arrivare come numero o come stringa: normalizziamo. */
    private static BigDecimal numero(Object v) {
        if (v == null) return null;
        if (v instanceof BigDecimal b) return b;
        if (v instanceof Number n) {
            try {
                return new BigDecimal(n.toString());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        if (v instanceof String s) {
            String t = s.trim().replace(',', '.');
            if (t.isEmpty()) return null;
            try {
                return new BigDecimal(t);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private static String testo(Map<String, Object> m, String chiave) {
        Object v = m.get(chiave);
        if (!(v instanceof String s)) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mappa(Object v) {
        return v instanceof Map ? (Map<String, Object>) v : null;
    }

    /** Il campo "brands" di OFF è un elenco separato da virgole: teniamo il primo. */
    private static String primaMarca(String marche) {
        if (marche == null) return null;
        int virgola = marche.indexOf(',');
        String prima = (virgola >= 0 ? marche.substring(0, virgola) : marche).trim();
        return prima.isEmpty() ? null : prima;
    }
}
