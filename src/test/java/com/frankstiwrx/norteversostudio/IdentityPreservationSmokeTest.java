package com.frankstiwrx.norteversostudio;

import com.frankstiwrx.norteversostudio.data.PesDatabase;
import com.frankstiwrx.norteversostudio.generator.IdentityPreservationVerifier;
import com.frankstiwrx.norteversostudio.generator.NameCatalog;
import com.frankstiwrx.norteversostudio.generator.PlayerGenerationService;
import com.frankstiwrx.norteversostudio.generator.ProtectionRegistry;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Executar com os 3 CSVs exportados do Ejogc327:
 * java ...IdentityPreservationSmokeTest Jogadores.csv Times.csv Atribuicoes.csv [IdTime]
 * Nunca edita os arquivos usados como entrada.
 */
public class IdentityPreservationSmokeTest {
    public static void main(String[] args) throws Exception {
        if (args.length < 3) throw new IllegalArgumentException(
                "Uso: IdentityPreservationSmokeTest Jogadores.csv Times.csv Atribuicoes.csv [IdTime]");
        var db = PesDatabase.load(Path.of(args[0]), Path.of(args[1]), Path.of(args[2]), System.out::println);
        String teamId = args.length > 3 ? args[3] : "1219";
        var names = NameCatalog.load(Path.of("dados/nomes.csv"), Path.of("dados/sobrenomes.csv"));
        Path protectPath = Files.createTempFile("norteverso_audit_test_", ".csv");
        Files.deleteIfExists(protectPath);
        var protectedPlayers = ProtectionRegistry.load(protectPath);
        var roster = db.roster(teamId);
        if (roster.size() < 2) throw new AssertionError("Elenco de testes precisa ter 2 jogadores.");
        String protectedId = roster.get(0).player().id();
        protectedPlayers.setProtected(protectedId, db.player(protectedId).name(), true);
        var generator = new PlayerGenerationService(names, protectedPlayers);
        var before = IdentityPreservationVerifier.capture(db, teamId);
        var ids = roster.stream().map(e -> e.player().id()).toList();
        var options = new PlayerGenerationService.Options(PlayerGenerationService.Mode.IDENTITY,
                "FI", 20261018L, 60, true, true);
        var drafts = generator.preview(db, ids, options);
        if (drafts.stream().anyMatch(d -> d.playerId().equals(protectedId)))
            throw new AssertionError("Protegido incluido na geracao.");
        var audit = IdentityPreservationVerifier.verifyPreview(db, before, drafts, protectedPlayers);
        if (!audit.approved()) throw new AssertionError(audit.description());
        if (drafts.isEmpty()) throw new AssertionError("Nao gerou jogadores.");
        // Prova negativa: mesmo que alterem manualmente a previa, nao pode mudar atributos.
        var first = drafts.get(0);
        var originalDraft = new LinkedHashMap<>(first.edits());
        first.edits().put("Finishing", "99");
        var forbidden = IdentityPreservationVerifier.verifyPreview(db, before, drafts, protectedPlayers);
        if (forbidden.approved() || forbidden.issues().stream().noneMatch(s -> s.contains("Finishing")))
            throw new AssertionError("Auditor aceitou modificacao tecnica proibida.");
        first.replaceEdits(originalDraft);
        if (!IdentityPreservationVerifier.verifyPreview(db, before, drafts, protectedPlayers).approved())
            throw new AssertionError("Auditoria nao foi recuperada.");
        generator.apply(db, drafts);
        var applied = IdentityPreservationVerifier.verifyApplied(db, before, drafts, protectedPlayers);
        if (!applied.approved()) throw new AssertionError(applied.description());
        if (!db.player(protectedId).name().equals(before.players().get(protectedId).get("Name")))
            throw new AssertionError("Protegido mudou.");
        // Prova negativa apos aplicar: uma alteracao tecnica deve ser detectada.
        String oldFinishing = db.playerField(first.playerId(), "Finishing");
        String newFinishing = oldFinishing.equals("60") ? "61" : "60";
        db.editPlayer(first.playerId(), Map.of("Finishing", newFinishing));
        if (IdentityPreservationVerifier.verifyApplied(db, before, drafts, protectedPlayers).approved())
            throw new AssertionError("Verificador nao identificou adulteracao de atributo apos aplicar.");
        db.editPlayer(first.playerId(), Map.of("Finishing", oldFinishing));
        if (!IdentityPreservationVerifier.verifyApplied(db, before, drafts, protectedPlayers).approved())
            throw new AssertionError("Auditoria nao recuperou estado correto.");
        System.out.println("VERIFICADOR APROVADO: " + applied.inspected()
                + " jogadores gerados; " + applied.protectedCount() + " protegido(s).");
        System.out.println("Sem mudancas em atributos, overal, posicoes, habilidades, IDs ou vinculos.");
    }
}
