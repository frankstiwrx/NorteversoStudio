package com.frankstiwrx.norteversostudio;

import com.frankstiwrx.norteversostudio.csv.Csv;
import com.frankstiwrx.norteversostudio.data.PesDatabase;
import com.frankstiwrx.norteversostudio.generator.NameCatalog;
import com.frankstiwrx.norteversostudio.generator.ProtectionRegistry;
import com.frankstiwrx.norteversostudio.generator.SquadGenerationService;
import com.frankstiwrx.norteversostudio.generator.SquadGenerationService.Config;
import com.frankstiwrx.norteversostudio.generator.SquadGenerationService.Role;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Execute com os caminhos antigos de Jogadores, Times e Atribuicoes. */
public final class SquadGenerationSmokeTest {
    private SquadGenerationSmokeTest() { }
    private static void check(boolean condition, String text) {
        if (!condition) throw new AssertionError(text);
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 3) throw new IllegalArgumentException("Use 3 CSVs: Jogadores Times Atribuicoes");
        Path temp = Files.createTempDirectory("norte_stage09_");
        PesDatabase db = PesDatabase.load(Path.of(args[0]), Path.of(args[1]), Path.of(args[2]), msg -> {});
        NameCatalog catalog = NameCatalog.load(Path.of("dados/nomes.csv"), Path.of("dados/sobrenomes.csv"));
        ProtectionRegistry registry = ProtectionRegistry.load(temp.resolve("protected.csv"));
        var team = db.teams().stream().filter(t -> !t.national() && db.roster(t.id()).size() >= 23)
                .findFirst().orElseThrow();
        var source = db.roster(team.id());
        String protectedId = source.get(0).player().id();
        String protectedName = db.player(protectedId).name();
        registry.setProtected(protectedId, protectedName, true);
        registry.save();
        SquadGenerationService gen = new SquadGenerationService(catalog, registry);
        for (int n = 0; n <= 120; n++) {
            var suggestion = SquadGenerationService.suggestedDistribution(n);
            check(suggestion.values().stream().mapToInt(Integer::intValue).sum() == n,
                    "Distribuicao de categoria incorreta: " + n);
        }
        int eligible = gen.eligibleCount(db, team.id());
        Map<Role,Integer> count = SquadGenerationService.suggestedDistribution(eligible);
        check(count.values().stream().mapToInt(Integer::intValue).sum() == eligible, "Categoria nao fecha elenco");
        Config options = new Config(77, "MIX", 20261017L, true, true, count);
        try {
            gen.preview(db, team.id(), new Config(77, "MIX", 123, true, true,
                    Map.of(Role.STAR, 1)));
            throw new AssertionError("Plano com contagem incorreta deveria falhar");
        } catch (IllegalArgumentException expected) { /* correto */ }
        var plan = gen.preview(db, team.id(), options);
        var plan2 = gen.preview(db, team.id(), options);
        check(plan.items().size() == eligible, "Quantidade de propostas nao bate");
        check(plan.protectedIds().contains(protectedId), "Protegido nao aparece");
        Map<String,String> oldIds = new HashMap<>();
        Map<String,String> oldAge = new HashMap<>();
        Map<String,String> oldH = new HashMap<>();
        Map<String,String> oldPOS = new HashMap<>();
        Map<String,String> oldOverall = new HashMap<>();
        Map<String,String> before = new HashMap<>();
        for (var item : plan.items()) {
            String id = item.playerId();
            oldIds.put(id, db.playerField(id, "Id"));
            oldAge.put(id, db.playerField(id, "Age"));
            oldH.put(id, db.playerField(id, "Height"));
            oldPOS.put(id, db.playerField(id, "POS"));
            oldOverall.put(id, db.playerField(id, "OverallStats"));
            before.put(id, db.playerField(id, "Name"));
            int age = Integer.parseInt(item.draft().result(db, "Age"));
            check(age >= item.role().minAge() && age <= item.role().maxAge(), "Idade fora da categoria");
            check(item.targetLevel() >= 50 && item.targetLevel() <= 99, "Nivel fora do intervalo");
            check(db.playerField(id, "POS").equals(item.draft().result(db, "POS")), "Posicao mudou");
        }
        for (int i = 0; i < plan.items().size(); i++) {
            var a = plan.items().get(i);
            var b = plan2.items().get(i);
            check(a.playerId().equals(b.playerId()) && a.role() == b.role()
                    && a.targetLevel() == b.targetLevel(), "Seed gerou categoria diferente");
            check(a.draft().edits().equals(b.draft().edits()), "Seed gerou valores diferentes");
        }
        List<List<String>> audit = SquadGenerationService.auditRows(db, plan);
        check(audit.size() == eligible + 2, "Relatorio deve incluir protegido");
        check(audit.stream().skip(1).allMatch(r -> r.size() == audit.get(0).size()), "CSV com colunas distintas");
        Path report = temp.resolve("relatorio.csv");
        Csv.gravar(report, audit);
        check(Csv.ler(report).equals(audit), "Roundtrip do relatorio falhou");
        // A previa e livre para personalizacao.
        var first = plan.items().get(0);
        first.draft().edits().put("Country", "207");
        first.draft().edits().put("Name", "Martin Silva");
        check(db.player(first.playerId()).name().equals(before.get(first.playerId())), "Previa gravou antes do apply");
        check(gen.apply(db, plan) == eligible, "Aplicacao incompleta");
        check(db.player(protectedId).name().equals(protectedName), "Protegido modificado");
        check(db.playerField(first.playerId(), "Country").equals("207"), "Nacionalidade editavel falhou");
        check(db.playerField(first.playerId(), "Name").equals("MARTIN SILVA"), "Nome editavel falhou");
        for (var item : plan.items()) {
            String id = item.playerId();
            check(db.playerField(id,"Id").equals(oldIds.get(id)), "ID alterado");
            check(db.playerField(id,"Height").equals(oldH.get(id)), "Altura alterada");
            check(db.playerField(id,"POS").equals(oldPOS.get(id)), "Posicao alterada");
            check(db.playerField(id,"OverallStats").equals(oldOverall.get(id)), "OverallStats alterado");
        }
        check(db.dirtyTeams() == 0 && db.dirtyAssignments() == 0, "Times/transferencias indevidamente alterados");
        Path players = temp.resolve("Jogadores_Norteverso.csv");
        Path teams = temp.resolve("Times_Norteverso.csv");
        Path assigned = temp.resolve("Atribuicao_Norteverso.csv");
        db.exportAll(players, teams, assigned);
        PesDatabase roundtrip = PesDatabase.load(players, teams, assigned, msg -> {});
        check(roundtrip.player(first.playerId()).name().equals("MARTIN SILVA"), "Roundtrip falhou");
        check(roundtrip.roster(team.id()).size() == source.size(), "Elenco foi reatribuido");
        check(roundtrip.player(protectedId).name().equals(protectedName), "Protegido alterado no CSV");
        System.out.println("OK Etapa09 | Clube: " + team.name() + " | Elenco: " + source.size()
                + " | Livres: " + eligible + " | Protegidos: 1 | CSV: " + players);
    }
}
