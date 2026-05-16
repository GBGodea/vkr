package ru.vkr.verification;

import java.util.ArrayList;
import java.util.List;

public final class ProofObligation {

    private final List<String> hypotheses;
    private final String conclusion;

    private ProofObligation(List<String> hypotheses, String conclusion) {
        this.hypotheses = List.copyOf(hypotheses);
        this.conclusion = normalize(conclusion, "conclusion");
    }

    public static ProofObligation from(List<String> hypotheses, String conclusion) {
        Contract.requires(hypotheses != null, "hypotheses must be defined");
        List<String> normalizedHypotheses = new ArrayList<>(hypotheses.size());
        for (String hypothesis : hypotheses) {
            normalizedHypotheses.add(normalize(hypothesis, "hypothesis"));
        }
        return new ProofObligation(normalizedHypotheses, conclusion);
    }

    public static ProofObligation of(String conclusion, String... hypotheses) {
        Contract.requires(hypotheses != null, "hypotheses must be defined");
        return from(List.of(hypotheses), conclusion);
    }

    public List<String> hypotheses() {
        return hypotheses;
    }

    public String conclusion() {
        return conclusion;
    }

    public String asDirectFormula() {
        if (hypotheses.isEmpty()) {
            return conclusion;
        }
        return "(%s) -> (%s)".formatted(String.join(" & ", wrapAll(hypotheses)), conclusion);
    }

    public String asRefutationFormula() {
        if (hypotheses.isEmpty()) {
            return "!(%s)".formatted(conclusion);
        }
        return "%s & !(%s)".formatted(String.join(" & ", wrapAll(hypotheses)), conclusion);
    }

    public String asSequent() {
        if (hypotheses.isEmpty()) {
            return "|- %s".formatted(conclusion);
        }
        return "%s |- %s".formatted(String.join(", ", hypotheses), conclusion);
    }

    private static List<String> wrapAll(List<String> formulas) {
        return formulas.stream()
                .map(formula -> "(%s)".formatted(formula))
                .toList();
    }

    private static String normalize(String formula, String name) {
        Contract.requires(formula != null && !formula.isBlank(), "%s must be defined".formatted(name));
        return formula.trim();
    }
}
