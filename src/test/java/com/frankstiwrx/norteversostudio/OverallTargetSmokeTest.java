package com.frankstiwrx.norteversostudio;

import com.frankstiwrx.norteversostudio.data.PesDatabase;
import com.frankstiwrx.norteversostudio.data.OverallCalculator;
import com.frankstiwrx.norteversostudio.generator.NameCatalog;
import com.frankstiwrx.norteversostudio.generator.OverallTargetBalancer;
import com.frankstiwrx.norteversostudio.generator.ProtectionRegistry;
import com.frankstiwrx.norteversostudio.generator.SquadGenerationService;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

/** Teste real, sem alterar arquivos do PES. Argumentos: Jogadores.csv Times.csv Atribuicoes.csv [IdTime]. */
public final class OverallTargetSmokeTest {
    public static void main(String[] args) throws Exception {
        if (args.length < 3) throw new IllegalArgumentException("Uso: Jogadores Times Atribuicoes [IdTime]");
        var db = PesDatabase.load(Path.of(args[0]), Path.of(args[1]), Path.of(args[2]), System.out::println);
        String idTeam = args.length > 3 ? args[3] : "1219";
        if (db.team(idTeam) == null) throw new IllegalArgumentException("Clube não encontrado: " + idTeam);
        var catalog = NameCatalog.load(Path.of("dados/nomes.csv"), Path.of("dados/sobrenomes.csv"));
        var registry = ProtectionRegistry.load(Files.createTempDirectory("norte_ovr_test").resolve("protected.csv"));
        var service = new SquadGenerationService(catalog, registry);
        int eligible = service.eligibleCount(db, idTeam);
        var distribution = SquadGenerationService.suggestedDistribution(eligible);
        var oldConfig = new SquadGenerationService.Config(76, "MIX", 20261018L, true, true, distribution, false);
        var config = new SquadGenerationService.Config(76, "MIX", 20261018L, true, true, distribution, true);
        var preview = service.preview(db, idTeam, config);
        var again = service.preview(db, idTeam, config);
        var oldPreview = service.preview(db, idTeam, oldConfig);
        if (preview.items().size() != eligible) throw new AssertionError("Elenco gerado incompleto");
        int close = 0; int exact = 0; int beforeClose = 0;
        for (int i=0;i<preview.items().size();i++) {
            var item = preview.items().get(i);
            var againItem = again.items().get(i);
            var old = oldPreview.items().get(i);
            int actual = db.calculatedOverall(item.playerId(), item.draft().edits());
            int oldActual = db.calculatedOverall(old.playerId(), old.draft().edits());
            int diff = actual-item.targetLevel();
            if (Math.abs(diff) <= 1) close++;
            if (diff==0) exact++;
            if (Math.abs(oldActual-old.targetLevel())<=1) beforeClose++;
            if (!item.draft().edits().equals(againItem.draft().edits()))
                throw new AssertionError("Seed deixou de ser reproduzível: "+item.playerId());
            if (!item.draft().result(db,"POS").equals(db.playerField(item.playerId(),"POS")))
                throw new AssertionError("Posição modificada indevidamente: "+item.playerId());
            if (!item.draft().result(db,"OverallStats").equals(db.playerField(item.playerId(),"OverallStats")))
                throw new AssertionError("OverallStats modificado indevidamente: "+item.playerId());
            System.out.println(item.playerId()+"  "+item.draft().result(db,"Name")
                    +"  "+item.role()+"   objetivo="+item.targetLevel()+"   obtido="+actual+"   diff="+diff);
        }
        var report = SquadGenerationService.auditRows(db,preview);
        if (report.stream().anyMatch(row -> row.size()!=report.get(0).size())) throw new AssertionError("CSV auditoria com tamanho divergente");
        System.out.println("RESUMO "+db.team(idTeam).name()+" | dentro ±1: "+close+"/"+eligible
                +" | exatos: "+exact+"/"+eligible+" | sem ajuste: "+beforeClose+"/"+eligible);
        if (close < eligible*0.9) throw new AssertionError("Calibração insuficiente, <90% dos alvos ±1");
    }
}
