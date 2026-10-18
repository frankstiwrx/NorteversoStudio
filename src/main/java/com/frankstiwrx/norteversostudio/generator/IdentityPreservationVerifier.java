package com.frankstiwrx.norteversostudio.generator;

import com.frankstiwrx.norteversostudio.data.PesDatabase;
import com.frankstiwrx.norteversostudio.data.PesDatabase.RosterEntry;
import com.frankstiwrx.norteversostudio.generator.PlayerGenerationService.Draft;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Etapa 13 — audita o modo "So nome e nacionalidade" SEM usar arquivos de competicao.
 * A auditoria cobre todas as colunas do CSV do jogador (nao somente atributos),
 * as atribuicoes do elenco e os campos do time. Nao escreve no disco.
 */
public final class IdentityPreservationVerifier {
    /** Somente estes tres campos podem mudar nesse modo. */
    public static final Set<String> ALLOWED = Set.of("Name", "Shirt", "Country");

    private IdentityPreservationVerifier() { }

    public record Snapshot(String teamId,
                           Map<String, Map<String, String>> players,
                           Map<String, Map<String, String>> assignments,
                           Map<String, String> team,
                           Map<String, Integer> overall) { }

    public record Report(boolean approved, boolean applied, int squadSize,
                         int inspected, int protectedCount, int changedNames,
                         int changedCountries, int changedShirts,
                         List<String> issues) {
        public Report {
            issues = List.copyOf(issues);
        }

        public String description() {
            StringBuilder sb = new StringBuilder();
            sb.append(approved ? "APROVADO: dados tecnicos preservados.\n"
                               : "BLOQUEADO: alteracoes fora do modo de identidade!\n");
            sb.append(applied ? "Auditoria apos aplicar em memoria\n" : "Auditoria da previa (antes de aplicar)\n")
              .append("\nClube: ").append(squadSize).append(" jogadores no elenco\n")
              .append("Jogadores na geracao: ").append(inspected).append("\n")
              .append("Jogadores protegidos: ").append(protectedCount).append("\n")
              .append("Nomes alterados: ").append(changedNames).append("\n")
              .append("Nacionalidades alteradas: ").append(changedCountries).append("\n")
              .append("Nomes na camisa alterados: ").append(changedShirts).append("\n\n")
              .append("Conferidos: TODOS os campos dos jogadores, overall calculado,\n")
              .append("posicoes, habilidades, estilos COM, IDs, vinculos de elenco,\n")
              .append("numeros de camisa, capitao/cobradores e dados do clube.\n")
              .append("Campos permitidos: Name, Country e Shirt.\n")
              .append("Faces/minifaces externas nao sao verificadas (ficam fora dos CSVs).\n");
            if (!issues.isEmpty()) {
                sb.append("\nPROBLEMAS (maximo 30 mostrados):\n");
                for (int i = 0; i < Math.min(30, issues.size()); i++) {
                    sb.append(" - ").append(issues.get(i)).append("\n");
                }
                if (issues.size() > 30) sb.append(" ... e ").append(issues.size() - 30).append(" outros.\n");
            }
            sb.append("\nObservacao: o verificador compara os CSVs em memoria.\n")
              .append("Nao substitui um teste de importacao no Ejogc327/PES.\n");
            return sb.toString();
        }
    }

    /** Foto completa dos dados do clube, tirada antes da geracao da previa. */
    public static Snapshot capture(PesDatabase db, String teamId) {
        if (db == null || teamId == null || db.team(teamId) == null)
            throw new IllegalArgumentException("Importe os CSVs e selecione um clube antes de verificar.");
        Map<String, Map<String, String>> playerRows = new LinkedHashMap<>();
        Map<String, Map<String, String>> assignmentRows = new LinkedHashMap<>();
        Map<String, Integer> overall = new LinkedHashMap<>();
        for (RosterEntry entry : db.roster(teamId)) {
            String id = entry.player().id();
            playerRows.putIfAbsent(id, db.playerFieldsSnapshot(id));
            assignmentRows.put(entry.assignmentId(), db.assignmentFieldsSnapshot(entry.assignmentId()));
            overall.putIfAbsent(id, db.calculatedOverall(id));
        }
        return new Snapshot(teamId, Map.copyOf(playerRows), Map.copyOf(assignmentRows),
                db.teamFieldsSnapshot(teamId), Map.copyOf(overall));
    }

    /** Valida a proposta e rejeita campos tecnicos, inclusive os inseridos manualmente. */
    public static Report verifyPreview(PesDatabase db, Snapshot before,
                                       List<Draft> drafts, ProtectionRegistry registry) {
        requireArguments(db, before, drafts, registry);
        List<String> issues = new ArrayList<>();
        checkUnmodifiedBeforePreview(db, before, issues);
        Set<String> ids = new LinkedHashSet<>();
        int named = 0, countries = 0, shirts = 0;
        for (Draft draft : drafts) {
            String id = draft.playerId();
            if (!ids.add(id)) issues.add("ID duplicado na previa: " + id);
            if (!before.players().containsKey(id)) {
                issues.add("Jogador de fora do elenco: " + id);
                continue;
            }
            if (registry.isProtected(id)) issues.add("Jogador protegido na previa: " + id);
            Map<String, String> old = before.players().get(id);
            for (var edit : draft.edits().entrySet()) {
                if (!old.containsKey(edit.getKey()) || !ALLOWED.contains(edit.getKey())) {
                    issues.add(id + ": campo proibido no modo identidade: " + edit.getKey());
                }
            }
            named += isDifferent(old, draft.edits(), "Name") ? 1 : 0;
            countries += isDifferent(old, draft.edits(), "Country") ? 1 : 0;
            shirts += isDifferent(old, draft.edits(), "Shirt") ? 1 : 0;
            try {
                int expectedOvr = before.overall().get(id);
                int proposedOvr = db.calculatedOverall(id, draft.edits());
                if (expectedOvr != proposedOvr) {
                    issues.add(id + ": overall mudou de " + expectedOvr + " para " + proposedOvr);
                }
            } catch (RuntimeException ex) {
                issues.add(id + ": falha ao verificar overall: " + ex.getMessage());
            }
        }
        return new Report(issues.isEmpty(), false, before.players().size(), drafts.size(),
                countProtected(before, registry), named, countries, shirts, issues);
    }

    /** Confere TODO o elenco depois de aplicar: so as 3 colunas liberadas podem mudar. */
    public static Report verifyApplied(PesDatabase db, Snapshot before,
                                       List<Draft> drafts, ProtectionRegistry registry) {
        requireArguments(db, before, drafts, registry);
        List<String> issues = new ArrayList<>();
        Map<String, Draft> proposed = new LinkedHashMap<>();
        int named = 0, countries = 0, shirts = 0;
        for (Draft draft : drafts) {
            String id = draft.playerId();
            if (proposed.putIfAbsent(id, draft) != null) issues.add("ID duplicado: " + id);
            if (!before.players().containsKey(id)) issues.add("ID de outro clube: " + id);
            if (registry.isProtected(id)) issues.add("Jogador protegido modificado: " + id);
        }
        if (!before.team().equals(db.teamFieldsSnapshot(before.teamId()))) {
            issues.add("Dados do clube mudaram durante a geracao.");
        }
        compareAssignments(db, before, issues);
        for (var original : before.players().entrySet()) {
            String id = original.getKey();
            Map<String, String> current;
            try { current = db.playerFieldsSnapshot(id); }
            catch (RuntimeException ex) { issues.add("Jogador desapareceu: " + id); continue; }
            Draft draft = proposed.get(id);
            for (var field : original.getValue().entrySet()) {
                String key = field.getKey();
                String expected = field.getValue();
                if (draft != null && ALLOWED.contains(key) && draft.edits().containsKey(key)) {
                    expected = draft.edits().get(key);
                }
                if (!Objects.equals(expected, current.get(key))) {
                    issues.add(id + ": campo " + key + " diferente do esperado.");
                }
            }
            if (draft != null) {
                named += !Objects.equals(original.getValue().get("Name"), current.get("Name")) ? 1 : 0;
                countries += !Objects.equals(original.getValue().get("Country"), current.get("Country")) ? 1 : 0;
                shirts += !Objects.equals(original.getValue().get("Shirt"), current.get("Shirt")) ? 1 : 0;
            }
            try {
                if (db.calculatedOverall(id) != before.overall().get(id)) {
                    issues.add(id + ": overall individual alterado.");
                }
            } catch (RuntimeException ex) {
                issues.add(id + ": nao foi possivel recalcular overall: " + ex.getMessage());
            }
        }
        return new Report(issues.isEmpty(), true, before.players().size(), drafts.size(),
                countProtected(before, registry), named, countries, shirts, issues);
    }

    private static void checkUnmodifiedBeforePreview(PesDatabase db, Snapshot before, List<String> issues) {
        if (!before.team().equals(db.teamFieldsSnapshot(before.teamId()))) {
            issues.add("Dados do clube mudaram desde a geracao da previa.");
        }
        compareAssignments(db, before, issues);
        for (var entry : before.players().entrySet()) {
            try {
                if (!entry.getValue().equals(db.playerFieldsSnapshot(entry.getKey()))) {
                    issues.add(entry.getKey() + ": jogador foi editado desde a geracao da previa. Regere.");
                }
            } catch (RuntimeException ex) {
                issues.add("Jogador nao encontrado: " + entry.getKey());
            }
        }
    }

    private static void compareAssignments(PesDatabase db, Snapshot before, List<String> issues) {
        Map<String, String> currentEntries = new LinkedHashMap<>();
        for (RosterEntry entry : db.roster(before.teamId())) {
            currentEntries.put(entry.assignmentId(), entry.player().id());
        }
        Map<String, String> originallyAssigned = new LinkedHashMap<>();
        before.assignments().forEach((id, row) -> originallyAssigned.put(id, row.get("IdPlayer")));
        if (!currentEntries.equals(originallyAssigned)) issues.add("Vinculos do elenco (IDs/atribuicoes) foram alterados.");
        for (var entry : before.assignments().entrySet()) {
            try {
                if (!entry.getValue().equals(db.assignmentFieldsSnapshot(entry.getKey()))) {
                    issues.add("Atribuicao " + entry.getKey() + " alterada (camisa, capitao ou transferencia).");
                }
            } catch (RuntimeException ex) {
                issues.add("Atribuicao " + entry.getKey() + " ausente.");
            }
        }
    }

    private static void requireArguments(PesDatabase db, Snapshot before,
                                         List<Draft> drafts, ProtectionRegistry registry) {
        if (db == null || before == null || drafts == null || registry == null)
            throw new IllegalArgumentException("Verificador sem base, previa ou protecoes.");
    }

    private static int countProtected(Snapshot before, ProtectionRegistry registry) {
        return (int) before.players().keySet().stream().filter(registry::isProtected).count();
    }

    private static boolean isDifferent(Map<String, String> old, Map<String, String> changes, String key) {
        return changes.containsKey(key) && !Objects.equals(old.get(key), changes.get(key));
    }
}
