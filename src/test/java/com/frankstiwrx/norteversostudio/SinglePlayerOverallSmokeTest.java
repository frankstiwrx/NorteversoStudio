package com.frankstiwrx.norteversostudio;

import com.frankstiwrx.norteversostudio.data.PesDatabase;
import com.frankstiwrx.norteversostudio.generator.NameCatalog;
import com.frankstiwrx.norteversostudio.generator.OverallTargetBalancer;
import com.frankstiwrx.norteversostudio.generator.ProtectionRegistry;
import com.frankstiwrx.norteversostudio.generator.PlayerGenerationService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class SinglePlayerOverallSmokeTest {
    public static void main(String[] args) throws Exception {
        if (args.length<3) throw new IllegalArgumentException("Jogadores Times Atribuicoes");
        var db = PesDatabase.load(Path.of(args[0]),Path.of(args[1]),Path.of(args[2]),x->{});
        var names = NameCatalog.load(Path.of("dados/nomes.csv"),Path.of("dados/sobrenomes.csv"));
        var protect = ProtectionRegistry.load(Files.createTempDirectory("norte_ind_").resolve("p.csv"));
        var gen = new PlayerGenerationService(names,protect);
        int succeeded=0;
        for (var entry : db.roster("1219")) {
            var id = entry.player().id();
            var draft = gen.preview(db,List.of(id),new PlayerGenerationService.Options(
                    PlayerGenerationService.Mode.KEEP_POSITION,"MIX",201221L,77,true,true)).get(0);
            var before = draft.result(db,"POS");
            var originalName = draft.originalName();
            OverallTargetBalancer.Result result = OverallTargetBalancer.tune(draft.edits(),
                    key -> db.playerField(id,key), Integer.parseInt(before),77,99);
            if (!result.closeEnough()) throw new AssertionError("OVR não atingido: " + id + " => " + result);
            if (!draft.result(db,"POS").equals(before)) throw new AssertionError("Posição mudou");
            if (!originalName.equals(db.player(id).name())) throw new AssertionError("Gerador alterou base original");
            if (!draft.result(db,"Name").equals(draft.result(db,"Name").toUpperCase(java.util.Locale.ROOT)))
                throw new AssertionError("Nome gerado fora do padrão MAIÚSCULO");
            succeeded++;
        }
        System.out.println("OK modo individual | OVR 77 alvo | " + succeeded + " jogadores conferidos");
    }
}
