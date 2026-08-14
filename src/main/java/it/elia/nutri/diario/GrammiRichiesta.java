package it.elia.nutri.diario;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/** Corpo di PATCH /api/voci/{id}: cambia solo il peso. */
public record GrammiRichiesta(
    @NotNull @Positive(message = "i grammi devono essere positivi") BigDecimal grammi
) {}
