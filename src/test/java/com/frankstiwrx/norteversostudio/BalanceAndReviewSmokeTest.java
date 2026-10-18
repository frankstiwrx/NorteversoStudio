package com.frankstiwrx.norteversostudio;

import com.frankstiwrx.norteversostudio.csv.Csv;
import com.frankstiwrx.norteversostudio.data.PesDatabase;
import com.frankstiwrx.norteversostudio.generator.NameCatalog;
import com.frankstiwrx.norteversostudio.generator.ProtectionRegistry;
import com.frankstiwrx.norteversostudio.generator.SquadAttributeAdjuster;
import com.frankstiwrx.norteversostudio.generator.SquadBalancePolicy;
import com.frankstiwrx.norteversostudio.generator.SquadGenerationService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Teste da Etapa 10 (real CSV, sem escrever na base original). */
public final class BalanceAndReviewSmokeTest {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    public static void main(String[] args) throws Exception {
        if (args.length != 3) throw new IllegalArgumentException("Use Jogadores.csv Times.csv Atribuicoes.csv");
        Path dir = Files.createTempDirectory("norte10_");
        var db = PesDatabase.load(Path.of(args[0]), Path.of(args[1]), Path.of(args[2]), System.out::println);
        // Escolhemos um clube da base com pelo menos 20 jogadores e ignoramos nome e ID codificados.
        var team = db.teams().stream().filter(t -> !t.national() && db.roster(t.id()).size() > 24)
                .findFirst().orElseThrow();
        var players = db.roster(team.id());
        String protectedId = players.get(0).player().id();
        String protectedName = db.player(protectedId).name();
        var registry = ProtectionRegistry.load(dir.resolve("protected.csv"));
        registry.setProtected(protectedId, protectedName, true);
        var catalog = NameCatalog.load(Path.of("dados/nomes.csv"), Path.of("dados/sobrenomes.csv"));
        var generator = new SquadGenerationService(catalog, registry);
        var options = new SquadGenerationService.Config(76, "MIX", 10111L, true, true,
                SquadGenerationService.suggestedDistribution(generator.eligibleCount(db,team.id())));
        var plan = generator.preview(db, team.id(), options);
        int outOfCap = 0;
        int above90 = 0;
        for (var item : plan.items()) {
            int allowed = SquadBalancePolicy.ceiling(item.role(), item.targetLevel());
            int pos = Integer.parseInt(db.playerField(item.playerId(), "POS"));
            int from = pos == 0 ? 20 : 0;
            int to = pos == 0 ? 25 : 20;
            for (int i = from; i < to; i++) {
                int value = Integer.parseInt(item.draft().result(db,PesDatabase.ATTRIBUTES.get(i)));
                if (value > 90) above90++;
                if (value > allowed + (item.role() == SquadGenerationService.Role.STAR ? 2 : 0)) outOfCap++;
            }
        }
        check(outOfCap == 0, "Balanceamento permitiu pico fora da regra");
        check(db.player(protectedId).name().equals(protectedName), "Prévia alterou protegido");
        check(generator.apply(db,plan) == plan.items().size(), "Gerador não aplicou tudo");
        var tool = new SquadAttributeAdjuster(registry);
        var preview = tool.preview(db, team.id(), SquadAttributeAdjuster.Area.ALL, -10, true);
        check(preview.protectedCount() == 1, "Protegido deveria ser excluido");
        check(!preview.changes().isEmpty(), "Nenhum upgrade/downgrade calculado");
        for (var change : preview.changes()) {
            for (String value : change.after().values()) {
                int n = Integer.parseInt(value);
                check(n >= 40 && n <= 99, "Atributo ilegal");
            }
        }
        // Uma mudanca manual invalida a prévia toda.
        var first = preview.changes().get(0);
        String key = first.before().keySet().iterator().next();
        db.editPlayer(first.playerId(), Map.of(key, "52"));
        boolean rejected = false;
        try { tool.apply(db, preview); } catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "Prévia obsoleta deveria ser rejeitada");
        var fresh = tool.preview(db, team.id(), SquadAttributeAdjuster.Area.ALL, -10, true);
        tool.apply(db, fresh);
        check(db.player(protectedId).name().equals(protectedName), "Ajuste alterou jogador protegido");
        String goalkeeper = db.roster(team.id()).stream().map(e->e.player().id())
                .filter(id->"0".equals(db.playerField(id,"POS")))
                .filter(id->!registry.isProtected(id)).findFirst().orElse(null);
        if (goalkeeper != null) {
            var gkPreview = tool.preview(db,team.id(), SquadAttributeAdjuster.Area.GOALKEEPER, 10, true);
            check(gkPreview.changes().stream().allMatch(c->"GO".equals(c.position())), "Modo GK editou atleta de linha");
        }
        Path review = dir.resolve("ANALISE");
        db.exportClubReview(review, team.id());
        var rPlayers = Csv.ler(review.resolve("Jogadores_APENAS_CLUBE.csv"));
        var rTeams = Csv.ler(review.resolve("Time_APENAS_CLUBE.csv"));
        var rAssignments = Csv.ler(review.resolve("Atribuicoes_APENAS_CLUBE.csv"));
        check(rTeams.size() == 2, "Revisão deve ter exatamente 1 time");
        check(rPlayers.size() > 20 && rPlayers.size() < 40, "Revisão tem jogadores demais ou de menos");
        check(rAssignments.size() == players.size() + 1, "Revisão tem vínculos incorretos");
        check(db.hasUnexportedChanges(), "Revisão NÃO pode limpar status de exportação pendente");
        System.out.println("OK Etapa10 | " + team.name() + " | " + plan.items().size()
                + " gerados | atributos >90: " + above90 + " | ajuste: " + fresh.changes().size()
                + " | revisão: " + (rPlayers.size()-1) + " jogadores | pasta " + review);
    }
}
