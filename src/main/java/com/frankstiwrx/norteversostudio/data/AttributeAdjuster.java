package com.frankstiwrx.norteversostudio.data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Ajustes direcionados: calculos preliminares, sem gravar no CSV ou no banco.
 * O peso de cada atributo e uma escolha de design do Norteverso, nao uma regra do PES.
 */
public final class AttributeAdjuster {
    public enum Group {
        ATTACK("Ataque"), DEFENSE("Defesa"), SPEED("Velocidade"),
        FINISHING("Finalizacao"), PASSING("Passe"), PHYSICAL("Fisico"),
        GOALKEEPER("Goleiro (somente GO)");
        private final String label;
        Group(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }

    /** Proporcoes relativas usadas no ajuste de cada grupo. */
    private static final Map<Group, Map<String, Double>> WEIGHTS = new LinkedHashMap<>();
    static {
        WEIGHTS.put(Group.ATTACK, weights(
                "OffensiveAwareness", 1, "BallControl", .75, "Dribbling", .7,
                "TightPossession", .65, "LowPass", .45, "LoftedPass", .3, "Heading", .35));
        WEIGHTS.put(Group.DEFENSE, weights(
                "DefensiveAwareness", 1, "BallWinning", 1, "Aggression", .8,
                "PhysicalContact", .6, "Jump", .4, "Stamina", .35));
        WEIGHTS.put(Group.SPEED, weights(
                "Speed", 1, "Acceleration", 1, "Stamina", .45, "Balance", .3));
        WEIGHTS.put(Group.FINISHING, weights(
                "Finishing", 1, "KickingPower", .75, "Heading", .6,
                "Curl", .5, "PlaceKicking", .35));
        WEIGHTS.put(Group.PASSING, weights(
                "LowPass", 1, "LoftedPass", 1, "BallControl", .55,
                "TightPossession", .4, "Curl", .35));
        WEIGHTS.put(Group.PHYSICAL, weights(
                "PhysicalContact", 1, "Stamina", .9, "Balance", .8,
                "Jump", .8, "KickingPower", .5, "Aggression", .3));
        WEIGHTS.put(Group.GOALKEEPER, weights(
                "GKAwareness", 1, "GKCatching", 1, "GKClearing", 1,
                "GKReflexes", 1, "GKReach", 1, "Jump", .4));
    }
    private AttributeAdjuster() {}

    private static Map<String, Double> weights(Object... alternating) {
        Map<String, Double> result = new LinkedHashMap<>();
        for (int i = 0; i < alternating.length; i += 2) {
            result.put((String) alternating[i], ((Number) alternating[i + 1]).doubleValue());
        }
        return Map.copyOf(result);
    }

    public static Map<String, Integer> adjust(Map<String, Integer> current, Group group,
                                               int value, boolean percent, boolean increase,
                                               boolean goalkeeper) {
        if (value < 1 || value > 30) throw new IllegalArgumentException("Ajuste entre 1 e 30.");
        if (group == Group.GOALKEEPER && !goalkeeper) {
            throw new IllegalArgumentException("O grupo Goleiro so pode ser usado com posicao GO (0).");
        }
        Map<String, Integer> result = new LinkedHashMap<>();
        for (var e : WEIGHTS.get(group).entrySet()) {
            Integer oldValue = current.get(e.getKey());
            if (oldValue == null) continue;
            int delta = percent
                    ? (int) Math.round(oldValue * value * e.getValue() / 100.0)
                    : (int) Math.round(value * e.getValue());
            int updated = Math.max(1, Math.min(127, oldValue + (increase ? delta : -delta)));
            if (updated != oldValue) result.put(e.getKey(), updated);
        }
        return result;
    }
}
