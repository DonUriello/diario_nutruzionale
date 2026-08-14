package it.elia.nutri.alimento;

import it.elia.nutri.comune.GestoreErrori.DatoNonValido;
import it.elia.nutri.nutriente.NutrienteRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/**
 * Controlli di coerenza sui valori per 100 g.
 * Riferimento: piano, sezione 5.2.
 *
 * Il controllo che vale davvero è quello energetico: le kcal dichiarate
 * devono coincidere con la somma pesata dei macronutrienti secondo i
 * fattori dell'allegato XIV del reg. UE 1169/2011.
 *
 * Attenzione: nella dichiarazione europea i carboidrati sono già al netto
 * delle fibre. Con fonti americane, dove le comprendono, questo controllo
 * produce falsi positivi sugli alimenti ricchi di fibre.
 */
@Component
public class ValidatoreNutrienti {

    private static final BigDecimal CENTO = new BigDecimal("100");
    private static final BigDecimal SOSPETTO = new BigDecimal("0.10");
    private static final BigDecimal RIFIUTO  = new BigDecimal("0.25");

    private final NutrienteRepository nutrienti;

    public ValidatoreNutrienti(NutrienteRepository nutrienti) {
        this.nutrienti = nutrienti;
    }

    public record Esito(boolean sospetto, String messaggio) {
        static Esito ok() { return new Esito(false, null); }
    }

    public Esito verifica(Map<String, BigDecimal> v) {
        if (v == null || v.isEmpty()) return Esito.ok();

        var codiciNoti = nutrienti.codici();
        for (String codice : v.keySet()) {
            if (!codiciNoti.contains(codice)) {
                throw new DatoNonValido("nutriente sconosciuto: " + codice);
            }
        }
        v.forEach((k, val) -> {
            if (val != null && val.signum() < 0) {
                throw new DatoNonValido("valore negativo per " + k);
            }
        });

        maggiore(v, "saturi", "grassi", "i saturi non possono superare i grassi");
        maggiore(v, "zuccheri", "carbo", "gli zuccheri non possono superare i carboidrati");

        BigDecimal massa = somma(v, "grassi").add(somma(v, "carbo"))
            .add(somma(v, "fibre")).add(somma(v, "proteine"));
        if (massa.compareTo(CENTO) > 0) {
            throw new DatoNonValido(
                "grassi, carboidrati, fibre e proteine sommano a " + massa + " g su 100 g");
        }

        BigDecimal dichiarate = v.get("kcal");
        if (dichiarate == null || dichiarate.signum() == 0) return Esito.ok();

        BigDecimal attese = somma(v, "proteine").multiply(new BigDecimal("4"))
            .add(somma(v, "carbo").multiply(new BigDecimal("4")))
            .add(somma(v, "grassi").multiply(new BigDecimal("9")))
            .add(somma(v, "fibre").multiply(new BigDecimal("2")));

        if (attese.signum() == 0) return Esito.ok();

        BigDecimal scarto = dichiarate.subtract(attese).abs()
            .divide(attese, 4, RoundingMode.HALF_UP);

        if (scarto.compareTo(RIFIUTO) > 0) {
            throw new DatoNonValido("le kcal dichiarate (" + dichiarate
                + ") non tornano con i macronutrienti (attese circa "
                + attese.setScale(0, RoundingMode.HALF_UP) + ")");
        }
        if (scarto.compareTo(SOSPETTO) > 0) {
            return new Esito(true, "le kcal si discostano di circa "
                + scarto.multiply(CENTO).setScale(0, RoundingMode.HALF_UP)
                + "% dal calcolo sui macronutrienti");
        }
        return Esito.ok();
    }

    private void maggiore(Map<String, BigDecimal> v, String figlio, String padre, String errore) {
        BigDecimal f = v.get(figlio), p = v.get(padre);
        if (f != null && p != null && f.compareTo(p) > 0) throw new DatoNonValido(errore);
    }

    /** Valore del nutriente, zero se assente o nullo. */
    private BigDecimal somma(Map<String, BigDecimal> v, String k) {
        BigDecimal x = v.get(k);
        return x == null ? BigDecimal.ZERO : x;
    }
}
