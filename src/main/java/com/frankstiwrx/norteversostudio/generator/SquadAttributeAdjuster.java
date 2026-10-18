package com.frankstiwrx.norteversostudio.generator;

import com.frankstiwrx.norteversostudio.data.AttributeAdjuster;
import com.frankstiwrx.norteversostudio.data.PesDatabase;
import com.frankstiwrx.norteversostudio.data.PesDatabase.RosterEntry;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Ajuste em massa, com preview, protecao por ID e conferencia de dados obsoletos. */
public final class SquadAttributeAdjuster {
    public enum Area {
        ALL("Todos relevantes (linha / GO)"),
        ATTACK("Ataque"), DEFENSE("Defesa"), SPEED("Velocidade"),
        FINISHING("Finalização"), PASSING("Passe"), PHYSICAL("Físico"), GOALKEEPER("Goleiro (GO)");
        private final String label;
        Area(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }

    public record Change(String playerId, String name, String position,
                         Map<String, String> before, Map<String, String> after) {
        public Change {
            before = Map.copyOf(before);
            after = Map.copyOf(after);
        }
    }
    public record Preview(String teamId, int protectedCount, int skippedCount, List<Change> changes) {
        public Preview { changes = List.copyOf(changes); }
    }
    private final ProtectionRegistry registry;
    public SquadAttributeAdjuster(ProtectionRegistry registry) { this.registry = registry; }

    /** Entre -30 e +30%, com piso 40, teto 99 e limite recomendado de 94 no upgrade. */
    public Preview preview(PesDatabase db, String teamId, Area area, int percentage, boolean cap94) {
        if (db == null || db.team(teamId) == null || db.team(teamId).national())
            throw new IllegalArgumentException("Selecione um clube valido.");
        if (percentage == 0 || percentage < -30 || percentage > 30)
            throw new IllegalArgumentException("Escolha um percentual entre -30% e +30% (exceto zero).");
        int protectedCount = 0, skippedCount = 0;
        List<Change> changes = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (RosterEntry entry : db.roster(teamId)) {
            String id = entry.player().id();
            if (!seen.add(id)) continue;
            if (registry.isProtected(id)) { protectedCount++; continue; }
            boolean goalkeeper = "0".equals(db.playerField(id, "POS"));
            if ((area == Area.GOALKEEPER && !goalkeeper) || (goalkeeper && area != Area.GOALKEEPER && area != Area.ALL)) {
                skippedCount++; continue;
            }
            List<String> fields = area == Area.ALL ?
                    (goalkeeper ? PesDatabase.ATTRIBUTES.subList(20, 25) : PesDatabase.ATTRIBUTES.subList(0, 20))
                    : PesDatabase.ATTRIBUTES;
            Map<String, Integer> current = new LinkedHashMap<>();
            for (String field : fields) current.put(field, Integer.parseInt(db.playerField(id, field)));
            Map<String, Integer> intended;
            if (area == Area.ALL) {
                intended = new LinkedHashMap<>();
                for (var item : current.entrySet()) {
                    intended.put(item.getKey(), (int)Math.round(item.getValue() * (1.0 + percentage / 100.0)));
                }
            } else {
                AttributeAdjuster.Group group = switch (area) {
                    case ATTACK -> AttributeAdjuster.Group.ATTACK;
                    case DEFENSE -> AttributeAdjuster.Group.DEFENSE;
                    case SPEED -> AttributeAdjuster.Group.SPEED;
                    case FINISHING -> AttributeAdjuster.Group.FINISHING;
                    case PASSING -> AttributeAdjuster.Group.PASSING;
                    case PHYSICAL -> AttributeAdjuster.Group.PHYSICAL;
                    case GOALKEEPER -> AttributeAdjuster.Group.GOALKEEPER;
                    default -> throw new IllegalArgumentException("Categoria desconhecida");
                };
                intended = AttributeAdjuster.adjust(current, group, Math.abs(percentage), true,
                        percentage > 0, goalkeeper);
            }
            Map<String, String> before = new LinkedHashMap<>();
            Map<String, String> after = new LinkedHashMap<>();
            for (var item : intended.entrySet()) {
                String field = item.getKey();
                int oldValue = current.get(field);
                int newValue = Math.max(40, Math.min(99, item.getValue()));
                if (cap94 && percentage > 0) newValue = Math.min(newValue, Math.max(oldValue, 94));
                if (newValue != oldValue) {
                    before.put(field, Integer.toString(oldValue));
                    after.put(field, Integer.toString(newValue));
                }
            }
            if (!after.isEmpty()) changes.add(new Change(id, db.player(id).name(),
                    goalkeeper ? "GO" : PesDatabase.POSITIONS.get(Integer.parseInt(db.playerField(id,"POS"))), before, after));
            else skippedCount++;
        }
        return new Preview(teamId, protectedCount, skippedCount, changes);
    }

    /** Validacao integral antes de gravar; nunca altera jogadores protegidos. */
    public int apply(PesDatabase db, Preview preview) {
        if (db.team(preview.teamId()) == null) throw new IllegalArgumentException("Clube removido.");
        Set<String> inTeam = new HashSet<>();
        for (RosterEntry entry : db.roster(preview.teamId())) inTeam.add(entry.player().id());
        for (Change change : preview.changes()) {
            if (registry.isProtected(change.playerId())) throw new IllegalArgumentException("Protegido desde a prévia: " + change.name());
            if (!inTeam.contains(change.playerId())) throw new IllegalArgumentException("Transferido desde a prévia: " + change.name());
            for (var field : change.before().entrySet()) {
                if (!field.getValue().equals(db.playerField(change.playerId(), field.getKey())))
                    throw new IllegalArgumentException("Atributo alterado desde a prévia: " + change.name());
            }
            db.validatePlayerChanges(change.after());
        }
        for (Change change : preview.changes()) db.editPlayer(change.playerId(), change.after());
        return preview.changes().size();
    }
}
