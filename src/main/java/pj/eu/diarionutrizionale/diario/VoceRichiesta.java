package pj.eu.diarionutrizionale.diario;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record VoceRichiesta(
    @NotNull(message = "alimentoId obbligatorio") Long alimentoId,
    @NotNull @Positive(message = "i grammi devono essere positivi") BigDecimal grammi
) {}
