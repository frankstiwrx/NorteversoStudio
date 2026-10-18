package com.frankstiwrx.norteversostudio.data;

import java.util.List;
import java.util.Map;

/** Campos taticos verificaveis na exportacao Táticas - PES 2021 - Bin.csv.
 *  Mantemos os códigos 0/1/2 exatamente como vêm do CSV; nenhuma tradução
 *  não verificada altera os valores gravados. */
public final class TacticsFields {
    private TacticsFields() { }

    public record Field(String key, String label, int min, int max, String group) { }
    public static final List<Field> FIELDS = List.of(
        new Field("AttackingStyles", "Estilo ofensivo", 0, 1, "Ataque"),
        new Field("BuildUp", "Construção das jogadas", 0, 1, "Ataque"),
        new Field("AttackingArea", "Área de ataque", 0, 1, "Ataque"),
        new Field("Positioning", "Posicionamento", 0, 1, "Ataque"),
        new Field("SupportRange", "Distância de apoio", 1, 10, "Ataque"),
        new Field("NumbersInAttack", "Jogadores no ataque", 0, 2, "Ataque"),
        new Field("DefensiveStyles", "Estilo defensivo", 0, 1, "Defesa"),
        new Field("ContainmentArea", "Área de contenção", 0, 1, "Defesa"),
        new Field("Pressuring", "Tipo de pressão", 0, 1, "Defesa"),
        new Field("DefensiveLine", "Linha defensiva", 1, 10, "Defesa"),
        new Field("Compactness", "Compactação", 1, 10, "Defesa"),
        new Field("NumbersInDefense", "Jogadores na defesa", 0, 2, "Defesa")
    );

    /** Edita apenas campos autorizados, sem tocar em Id, IdTeam ou demais colunas. */
    public static boolean apply(Map<String, Integer> columns, List<String> row,
                                Map<String, Integer> updates) {
        for (var entry : updates.entrySet()) {
            Field def = FIELDS.stream().filter(f -> f.key().equals(entry.getKey()))
                    .findFirst().orElseThrow(() -> new IllegalArgumentException(
                            "Campo tático não editável: " + entry.getKey()));
            if (!columns.containsKey(def.key())) {
                throw new IllegalArgumentException("Coluna tática não encontrada: " + def.key());
            }
            int value = entry.getValue();
            if (value < def.min() || value > def.max()) {
                throw new IllegalArgumentException(def.label() + " deve ficar entre "
                        + def.min() + " e " + def.max());
            }
        }
        boolean changed = false;
        for (var entry : updates.entrySet()) {
            int index = columns.get(entry.getKey());
            String value = Integer.toString(entry.getValue());
            if (!row.get(index).equals(value)) {
                row.set(index, value);
                changed = true;
            }
        }
        return changed;
    }
}
