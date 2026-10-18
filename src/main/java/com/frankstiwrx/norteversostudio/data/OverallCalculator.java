package com.frankstiwrx.norteversostudio.data;

import java.util.Map;
import java.util.function.Function;

/**
 * Calcula o overall do PES 2021 a partir da posição registrada, 25 atributos
 * e precisão do pé fraco. NÃO lê nem altera arquivos BIN/EDIT.
 *
 * Implementação independente em Java a partir da fórmula documentada no projeto
 * MIT pes21-player-rating de gvoze32 (docs/FORMULA.md e rating.py):
 * https://github.com/gvoze32/pes21-player-rating
 * O método de referência foi recuperado do Ejogc327 PES 2020 Editor v0.12.10.
 * A classificação pode superar 99 em bancos personalizados. Para valores de
 * atributos acima de 99, a fidelidade ainda não foi verificada com o jogo.
 */
public final class OverallCalculator {
    private OverallCalculator() { }

    public static final String[] OUTFIELD_FIELDS = { "OffensiveAwareness", "BallControl", "Dribbling", "TightPossession", "LowPass", "LoftedPass", "Finishing", "Heading", "PlaceKicking", "Curl", "Speed", "Acceleration", "KickingPower", "Jump", "PhysicalContact", "Balance", "Stamina", "DefensiveAwareness", "BallWinning", "Aggression" };
    public static final String[] GOALKEEPER_FIELDS = { "GKAwareness", "GKCatching", "GKClearing", "GKReflexes", "GKReach" };

    // Índices PES: 0 GO, 1 ZC, 2 LE, 3 LD, 4 VOL, 5 MC,
    // 6 MLE, 7 MLD, 8 MAT, 9 PTE, 10 PTD, 11 SA, 12 CA.
    private static final double[][] WEIGHTS = {
        {0.00, 0.00, 0.00, 0.00, 0.00, 0.00, 0.00, 0.00, 0.00, 0.00, 0.00, 0.00, 0.00, 0.11, 0.11, 0.00, 0.00, 0.00, 0.00, 0.00},
        {0.00, 0.00, 0.00, 0.00, 0.00, 0.00, 0.00, 0.22, 0.00, 0.00, 0.10, 0.00, 0.00, 0.19, 0.19, 0.00, 0.09, 0.25, 0.17, 0.08},
        {0.06, 0.09, 0.09, 0.05, 0.00, 0.14, 0.00, 0.00, 0.00, 0.00, 0.16, 0.14, 0.00, 0.11, 0.08, 0.04, 0.14, 0.14, 0.08, 0.05},
        {0.06, 0.09, 0.09, 0.05, 0.00, 0.14, 0.00, 0.00, 0.00, 0.00, 0.16, 0.14, 0.00, 0.11, 0.08, 0.04, 0.14, 0.14, 0.08, 0.05},
        {0.07, 0.18, 0.10, 0.05, 0.18, 0.19, 0.00, 0.00, 0.00, 0.12, 0.03, 0.03, 0.00, 0.05, 0.09, 0.04, 0.14, 0.07, 0.03, 0.01},
        {0.05, 0.23, 0.16, 0.08, 0.23, 0.21, 0.00, 0.00, 0.00, 0.00, 0.04, 0.06, 0.00, 0.00, 0.03, 0.02, 0.17, 0.03, 0.00, 0.00},
        {0.07, 0.15, 0.17, 0.08, 0.07, 0.12, 0.00, 0.00, 0.00, 0.04, 0.24, 0.21, 0.00, 0.00, 0.00, 0.00, 0.13, 0.00, 0.00, 0.00},
        {0.07, 0.15, 0.17, 0.08, 0.07, 0.12, 0.00, 0.00, 0.00, 0.04, 0.24, 0.21, 0.00, 0.00, 0.00, 0.00, 0.13, 0.00, 0.00, 0.00},
        {0.14, 0.23, 0.17, 0.07, 0.21, 0.14, 0.17, 0.00, 0.00, 0.00, 0.05, 0.07, 0.00, 0.00, 0.02, 0.03, 0.03, 0.00, 0.00, 0.00},
        {0.17, 0.19, 0.15, 0.07, 0.05, 0.09, 0.11, 0.00, 0.00, 0.00, 0.15, 0.15, 0.05, 0.00, 0.02, 0.04, 0.06, 0.00, 0.00, 0.00},
        {0.17, 0.19, 0.15, 0.07, 0.05, 0.09, 0.11, 0.00, 0.00, 0.00, 0.15, 0.15, 0.05, 0.00, 0.02, 0.04, 0.06, 0.00, 0.00, 0.00},
        {0.15, 0.19, 0.13, 0.06, 0.09, 0.09, 0.14, 0.00, 0.00, 0.00, 0.09, 0.21, 0.06, 0.00, 0.02, 0.05, 0.04, 0.00, 0.00, 0.00},
        {0.31, 0.23, 0.09, 0.05, 0.00, 0.00, 0.36, 0.03, 0.00, 0.00, 0.05, 0.05, 0.03, 0.03, 0.08, 0.02, 0.00, 0.00, 0.00, 0.00},
    };
    private static final double[] WEAK_FOOT_WEIGHT = { 0.00, 0.23, 0.56, 0.56, 0.23, 0.23, 0.56, 0.56, 0.37, 0.47, 0.47, 0.37, 0.38 };
    private static final int[] REGISTERED_BONUS = { 8, 8, 9, 9, 9, 8, 8, 8, 8, 10, 10, 9, 8 };
    private static final int[] AVERAGE_CURVE = { 31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51, 52, 56, 59, 62, 65, 67, 70, 73, 75, 77, 79, 82, 83, 85, 87, 89, 90, 92, 93, 94, 95, 96, 97, 98, 99, 100, 101, 102, 103, 104, 105, 106, 107, 108, 109, 110, 111, 112, 113 };

    /** Passar os campos de uma linha CSV, sem modificar o mapa. */
    public static int calculate(Map<String, String> csvRow) {
        return calculate(csvRow::get);
    }

    /** Aceita acesso aos campos, inclusive de prévias ainda não aplicadas. */
    public static int calculate(Function<String, String> field) {
        int pos = read(field, "POS");
        if (pos < 0 || pos >= WEIGHTS.length) {
            throw new IllegalArgumentException("POS inválida: " + pos);
        }
        int[] abilities = new int[OUTFIELD_FIELDS.length];
        int sum = 0;
        for (int i = 0; i < abilities.length; i++) {
            abilities[i] = read(field, OUTFIELD_FIELDS[i]);
            sum += abilities[i];
        }
        int[] gk = new int[GOALKEEPER_FIELDS.length];
        for (int i = 0; i < gk.length; i++) gk[i] = read(field, GOALKEEPER_FIELDS[i]);
        int weakFootAcc = read(field, "WeakFootAcc");

        int awarenessReach = (gk[0] + gk[4]) / 2;
        int handling = (gk[1] + gk[2] + gk[3]) / 3;
        double positional = 0;
        if (pos == 0) {
            positional += (awarenessReach - 25) * 0.5 + (handling - 25) * 0.48;
        }
        for (int i = 0; i < abilities.length; i++) {
            positional += (abilities[i] - 25) * WEIGHTS[pos][i];
        }
        positional += (weakFootAcc - 1) * WEAK_FOOT_WEIGHT[pos];
        positional += 0.5;
        int result = (int) positional + REGISTERED_BONUS[pos];
        if (pos != 0) {
            int avg = Math.max((int) ((awarenessReach + handling + sum) / 22.0 + 0.5), 40);
            // Curva publicada até média 99; limitar o índice evita exceção
            // em jogadores personalizados com atributos além da escala usual.
            avg = Math.min(avg, 99);
            result = (int) (result * 0.6 + AVERAGE_CURVE[avg - 40] * 0.4 + 0.5);
        }
        return Math.max(result, 40);
    }

    private static int read(Function<String, String> field, String key) {
        String raw = field.apply(key);
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Coluna ausente/vazia: " + key);
        }
        try { return Integer.parseInt(raw.trim()); }
        catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Valor numérico inválido em " + key + ": " + raw, ex);
        }
    }
}
