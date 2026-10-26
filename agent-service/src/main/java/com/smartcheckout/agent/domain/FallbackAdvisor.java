package com.smartcheckout.agent.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * Rule-based report used when the AI cannot be used (no key, error, budget reached, invalid answer).
 * Deliberately conservative: in degraded mode the agent never recommends to approve, because an
 * approval needs context (customer history, explanations) that these rules do not have.
 */
public final class FallbackAdvisor {

    public InvestigationReport advise(int riskScore, List<String> reasonCodes) {
        List<String> codes = reasonCodes == null ? List.of() : reasonCodes;
        List<String> evidence = new ArrayList<>();
        evidence.add("Score de risque " + riskScore + "/100 calculé par le moteur de règles.");
        codes.stream().map(FallbackAdvisor::describe).forEach(evidence::add);

        if (codes.contains("CARD_TESTING")) {
            return new InvestigationReport(Recommendation.REJECT, 0.8,
                    "Plusieurs paiements rapprochés avec la même carte : schéma typique de test d'une carte volée. "
                            + "Refus recommandé (analyse automatique par règles, sans IA).",
                    evidence, List.of());
        }
        if (codes.contains("GEO_MISMATCH")) {
            return new InvestigationReport(Recommendation.ESCALATE, 0.6,
                    "La carte et la connexion viennent de pays différents. Vérifier l'identité du client avant de "
                            + "décider (analyse automatique par règles, sans IA).",
                    evidence,
                    List.of("Pouvez-vous confirmer cet achat et le pays d'émission de votre carte ?",
                            "Êtes-vous actuellement à l'étranger ou utilisez-vous un VPN ?"));
        }
        return new InvestigationReport(Recommendation.ESCALATE, 0.5,
                "Signaux de risque modérés. Contacter le client pour confirmer l'achat avant de décider "
                        + "(analyse automatique par règles, sans IA).",
                evidence,
                List.of("Pouvez-vous confirmer cet achat ?"));
    }

    private static String describe(String code) {
        return switch (code) {
            case "HIGH_AMOUNT" -> "Montant élevé (au-dessus de 5 000 MAD).";
            case "VERY_HIGH_AMOUNT" -> "Montant très élevé (au-dessus de 20 000 MAD).";
            case "CARD_VELOCITY" -> "Plusieurs paiements récents avec la même carte.";
            case "CARD_TESTING" -> "Au moins 4 autres paiements avec la même carte en 10 minutes.";
            case "GEO_MISMATCH" -> "Pays de la carte différent du pays de connexion.";
            case "NEW_DEVICE" -> "Client connu payant depuis un nouvel appareil.";
            default -> "Signal de risque : " + code + ".";
        };
    }
}
