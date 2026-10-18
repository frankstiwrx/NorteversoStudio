package com.frankstiwrx.norteversostudio.generator;

import com.frankstiwrx.norteversostudio.data.PesDatabase;
import com.frankstiwrx.norteversostudio.generator.SquadGenerationService.Role;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Limites de pico POR CATEGORIA somente na geracao automatica de elencos.
 * Valores de design, nao o calculo de overall do PES.
 */
public final class SquadBalancePolicy {
    private SquadBalancePolicy() { }

    public static int ceiling(Role role, int target) {
        return switch (role) {
            case STAR -> Math.min(96, target + 9);
            case STARTER -> Math.min(94, target + 7);
            case RESERVE -> Math.min(89, target + 7);
            case PROSPECT -> Math.min(90, target + 8);
            case VETERAN -> Math.min(90, target + 8);
        };
    }

    /**
     * No maximo duas caracteristicas excepcionalmente altas por craque.
     * Zera o excesso de picos sem nivelar todos os atributos ao mesmo numero.
     * Nao toca nas colunas de goleiro de um jogador de linha.
     */
    public static void balance(Map<String, String> edits, Role role, int target, int position) {
        int ceiling = ceiling(role, target);
        int end = position == 0 ? 25 : 20;
        int start = position == 0 ? 20 : 0;
        List<String> byQuality = new ArrayList<>(PesDatabase.ATTRIBUTES.subList(start, end));
        byQuality.sort(Comparator.comparingInt((String field) -> Integer.parseInt(edits.get(field))).reversed());
        int exceptions = role == Role.STAR && target >= 85 ? 2 : 0;
        for (int i = 0; i < byQuality.size(); i++) {
            String field = byQuality.get(i);
            int old = Integer.parseInt(edits.get(field));
            int allowed = ceiling;
            // Dois melhores atributos de um craque podem se destacar, mas sem chegar a 99.
            if (i < exceptions) allowed = Math.min(97, ceiling + (i == 0 ? 2 : 1));
            if (old > allowed) {
                // Pequena variacao deterministica para evitar dezenas de '92' identicos.
                int reduced = Math.min(allowed, Math.max(40, allowed - ((old - allowed) / 5)));
                edits.put(field, Integer.toString(reduced));
            }
        }
    }
}
