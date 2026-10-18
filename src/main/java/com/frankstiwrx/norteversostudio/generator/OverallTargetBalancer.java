package com.frankstiwrx.norteversostudio.generator;

import com.frankstiwrx.norteversostudio.data.OverallCalculator;
import com.frankstiwrx.norteversostudio.data.PesDatabase;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Ajusta APENAS os atributos técnicos já gerados para aproximar o OVR alvo.
 * Mantém posição, identidade, aparência, idade, vínculos, habilidades e IDs.
 * Não modifica OverallStats: o PES calcula a nota usando os atributos importados.
 * O cálculo usa a fórmula de referência de OverallCalculator (PES 2021).
 */
public final class OverallTargetBalancer {
    private OverallTargetBalancer() { }

    public record Result(int target, int achieved, int initial) {
        public int delta() { return achieved - target; }
        public boolean closeEnough() { return Math.abs(delta()) <= 1; }
    }

    /**
     * @param edits mapa editável da prévia; altera somente atributos de linha OU GK
     * @param baseline acesso aos valores originais caso estejam ausentes em edits
     * @param position posição principal de 0 (GK) a 12 (CF)
     * @param target nota desejada (40-99)
     * @param ceiling limite máximo de cada atributo gerado (40-99)
     */
    public static Result tune(Map<String, String> edits, Function<String, String> baseline,
                              int position, int target, int ceiling) {
        if (target < 40 || target > 99) throw new IllegalArgumentException("OVR alvo deve estar entre 40 e 99.");
        if (ceiling < 40 || ceiling > 99) throw new IllegalArgumentException("Limite de atributo fora de 40–99.");
        if (position < 0 || position >= PesDatabase.POSITIONS.size()) throw new IllegalArgumentException("Posição inválida.");

        List<String> fields = position == 0 ? List.of(OverallCalculator.GOALKEEPER_FIELDS)
                : List.of(OverallCalculator.OUTFIELD_FIELDS);
        LinkedHashMap<String, String> original = new LinkedHashMap<>();
        for (String field : fields) {
            String raw = edits.getOrDefault(field, baseline.apply(field));
            original.put(field, raw);
        }
        Function<String, String> access = key -> edits.getOrDefault(key, baseline.apply(key));
        int initial = OverallCalculator.calculate(access);

        // Procura um ajuste uniforme mínimo: mantém os contrastes entre pontos fortes
        // e fracos (por exemplo, ponta rápido segue mais veloz que um zagueiro).
        int bestScore = Integer.MAX_VALUE;
        int bestOverall = initial;
        Map<String, String> best = new LinkedHashMap<>(original);
        for (int offset = -55; offset <= 55; offset++) {
            for (String field : fields) {
                int v = Integer.parseInt(original.get(field));
                edits.put(field, Integer.toString(clamp(v + offset, 40, ceiling)));
            }
            int ovr = OverallCalculator.calculate(access);
            // Prioridade absoluta: nota mais próxima; desempate: menor alteração.
            int score = Math.abs(ovr - target) * 1000 + Math.abs(offset);
            if (score < bestScore) {
                bestScore = score;
                bestOverall = ovr;
                for (String field : fields) best.put(field, edits.get(field));
            }
        }
        edits.putAll(best);

        // Ajuste fino: alguns arredondamentos exigem alterar atributos individualmente.
        // A aceitação estrita evita ciclos e preserva a previsibilidade da geração.
        for (int round = 0; round < 5 && bestOverall != target; round++) {
            int direction = bestOverall < target ? 1 : -1;
            String winningField = null;
            int winningValue = 0;
            int winningOverall = bestOverall;
            int winningCost = Integer.MAX_VALUE;
            for (String field : fields) {
                int previous = Integer.parseInt(edits.get(field));
                for (int steps = 1; steps <= 8; steps++) {
                    int proposal = clamp(previous + direction * steps, 40, ceiling);
                    if (proposal == previous) break;
                    edits.put(field, Integer.toString(proposal));
                    int measured = OverallCalculator.calculate(access);
                    edits.put(field, Integer.toString(previous));
                    int gain = Math.abs(bestOverall - target) - Math.abs(measured - target);
                    if (gain > 0 && (Math.abs(measured - target) < Math.abs(winningOverall - target)
                            || (Math.abs(measured - target) == Math.abs(winningOverall - target) && steps < winningCost))) {
                        winningField = field;
                        winningValue = proposal;
                        winningOverall = measured;
                        winningCost = steps;
                    }
                }
            }
            if (winningField == null) break;
            edits.put(winningField, Integer.toString(winningValue));
            bestOverall = winningOverall;
        }
        return new Result(target, bestOverall, initial);
    }

    private static int clamp(int v, int min, int max) { return Math.max(min, Math.min(max, v)); }
}
