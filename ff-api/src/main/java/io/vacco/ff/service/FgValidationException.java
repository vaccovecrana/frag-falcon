package io.vacco.ff.service;

import io.vacco.ronove.util.RvValidation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Thrown by services when a request fails validation. Carries locale-agnostic
 * {@link RvValidation} hints so the API layer can surface them to browser
 * clients (key + params, rendered by the frontend's i18n templates).
 */
public class FgValidationException extends RuntimeException {

  public final List<RvValidation> validations = new ArrayList<>();

  public FgValidationException(String message, RvValidation... validations) {
    super(message);
    this.validations.addAll(Arrays.asList(validations));
  }
}
