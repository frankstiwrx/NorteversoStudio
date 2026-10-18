package com.frankstiwrx.norteversostudio;

import com.frankstiwrx.norteversostudio.csv.Csv;
import com.frankstiwrx.norteversostudio.data.PesDatabase;
import com.frankstiwrx.norteversostudio.generator.IdentityGenerator;
import com.frankstiwrx.norteversostudio.generator.NameCatalog;
import com.frankstiwrx.norteversostudio.generator.ProtectionRegistry;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Executar com tres caminhos de CSV: Jogadores Times Atribuicoes. */
public final class GeneratorSmokeTest {
    private GeneratorSmokeTest() { }
    private static void check(boolean valid, String message) {
        if (!valid) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 3) throw new IllegalArgumentException("Use: GeneratorSmokeTest JOGADORES TIMES ATRIBUICOES");
        Path folder = Files.createTempDirectory("norteverso_test_gerador_");
        NameCatalog names = NameCatalog.load(Path.of("dados/nomes.csv"), Path.of("dados/sobrenomes.csv"));
        check(names.namesCount() == 2132, "Contagem de nomes incorreta");
        check(names.surnamesCount() == 2831, "Contagem de sobrenomes incorreta");
        PesDatabase db = PesDatabase.load(Path.of(args[0]), Path.of(args[1]), Path.of(args[2]), s -> { });
        String teamId = db.teams().stream().filter(t -> !t.national() && db.roster(t.id()).size() > 3)
                .findFirst().orElseThrow().id();
        List<String> playerIds = db.roster(teamId).stream().limit(4).map(e -> e.player().id()).toList();
        String protectedId = playerIds.get(0);
        String oldName = db.player(protectedId).name();
        var registry = ProtectionRegistry.load(folder.resolve("protecao.csv"));
        registry.setProtected(protectedId, oldName, true);
        registry.save();
        registry = ProtectionRegistry.load(folder.resolve("protecao.csv"));
        check(registry.isProtected(protectedId), "Protecao nao foi persistida");
        IdentityGenerator gen = new IdentityGenerator(names, registry);
        List<IdentityGenerator.Proposal> preview1 = gen.preview(db, playerIds, "MIX", 20261017L);
        List<IdentityGenerator.Proposal> preview2 = gen.preview(db, playerIds, "MIX", 20261017L);
        check(preview1.equals(preview2), "Seed nao e deterministica");
        check(preview1.size() == 3, "Jogador protegido deveria ser ignorado");
        check(preview1.stream().noneMatch(p -> p.playerId().equals(protectedId)), "Protecao ignorada");
        check(new HashSet<>(preview1.stream().map(IdentityGenerator.Proposal::after).toList()).size() == 3,
                "Previa gerou nomes repetidos");
        String editedId = preview1.get(0).playerId();
        String country = db.playerField(editedId, "Country");
        String age = db.playerField(editedId, "Age");
        String skinIndicator = db.playerField(editedId, "Height");
        String pos = db.playerField(editedId, "POS");
        String style = db.playerField(editedId, "PlayingStyle");
        check(!db.isPlayerDirty(editedId), "Previa modificou base");
        check(gen.apply(db, preview1, true) == 3, "Aplicou contagem errada");
        check(db.player(protectedId).name().equals(oldName), "Jogador protegido foi modificado");
        check(db.playerField(editedId, "Country").equals(country), "Nacionalidade alterada");
        check(db.playerField(editedId, "Age").equals(age), "Idade alterada");
        check(db.playerField(editedId, "Height").equals(skinIndicator), "Altura alterada");
        check(db.playerField(editedId, "POS").equals(pos), "Posicao alterada");
        check(db.playerField(editedId, "PlayingStyle").equals(style), "Estilo alterado");
        check(db.playerField(editedId, "Name").equals(preview1.get(0).after()), "Nome nao salvo em memoria");
        check(db.playerField(editedId, "Shirt").equals(preview1.get(0).shirt()), "Camisa nao salva em memoria");
        check(db.dirtyPlayers() == 3 && db.dirtyTeams() == 0 && db.dirtyAssignments() == 0,
                "Alteracoes de times/vinculos inesperadas");
        try {
            gen.apply(db, preview1, true);
            throw new AssertionError("Reaplicar previa antiga deveria falhar");
        } catch (IllegalArgumentException expected) { /* esperado */ }
        Path p = folder.resolve("players.csv"), t = folder.resolve("teams.csv"), a = folder.resolve("assignments.csv");
        db.exportAll(p, t, a);
        check(Files.size(p) > 10000 && Files.size(t) > 1000 && Files.size(a) > 1000,
                "Exportacao de tres CSVs falhou");
        PesDatabase imported = PesDatabase.load(p, t, a, s -> { });
        check(imported.player(editedId).name().equals(db.player(editedId).name()), "Roundtrip alterou nome");
        check(imported.playerField(editedId, "Shirt").equals(db.playerField(editedId, "Shirt")),
                "Roundtrip alterou camisa");
        System.out.println("OK: " + names.namesCount() + " nomes, " + names.surnamesCount() + " sobrenomes, "
                + db.totalPlayers() + " jogadores, " + db.teams().size() + " times, "
                + preview1.size() + " alterados com protecao e exportacao íntegra.");
    }
}
