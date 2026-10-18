package com.frankstiwrx.norteversostudio;

import com.frankstiwrx.norteversostudio.data.PesDatabase;
import com.frankstiwrx.norteversostudio.generator.NameCatalog;
import com.frankstiwrx.norteversostudio.generator.PlayerGenerationService;
import com.frankstiwrx.norteversostudio.generator.PlayerGenerationService.Mode;
import com.frankstiwrx.norteversostudio.generator.PlayerGenerationService.Options;
import com.frankstiwrx.norteversostudio.generator.ProtectionRegistry;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;

/**
 * Smoke test real: gera quatro modos, conserva ID/face/clubes, testa
 * previas customizadas, proteção e roundtrip CSV.
 * Executar via java -cp target/classes:target/test-classes ...PlayerGenerationModesSmokeTest J T A
 */
public final class PlayerGenerationModesSmokeTest {
    private PlayerGenerationModesSmokeTest() { }
    private static void check(boolean ok, String msg) { if (!ok) throw new AssertionError(msg); }

    public static void main(String[] args) throws Exception {
        if (args.length != 3) throw new IllegalArgumentException("Use: ...SmokeTest jogadores.csv times.csv atribuicoes.csv");
        Path out = Files.createTempDirectory("nv_stage08_test_");
        PesDatabase db = PesDatabase.load(Path.of(args[0]), Path.of(args[1]), Path.of(args[2]), s -> {});
        NameCatalog names = NameCatalog.load(Path.of("dados/nomes.csv"), Path.of("dados/sobrenomes.csv"));
        ProtectionRegistry registry = ProtectionRegistry.load(out.resolve("protecao.csv"));
        var svc = new PlayerGenerationService(names, registry);
        var team = db.teams().stream().filter(t -> !t.national() && db.roster(t.id()).size() >= 5).findFirst().orElseThrow();
        List<String> ids = db.roster(team.id()).stream().limit(5).map(e -> e.player().id()).toList();
        var attrs = new HashMap<String, String>();
        for (String id : ids) attrs.put(id, db.playerField(id, "OffensiveAwareness"));
        String height = db.playerField(ids.get(1), "Height"), weight = db.playerField(ids.get(1), "Weight");
        String position = db.playerField(ids.get(1), "POS");
        String protectedName = db.player(ids.get(0)).name();
        registry.setProtected(ids.get(0), protectedName, true);
        var identity = svc.preview(db, ids, new Options(Mode.IDENTITY, "FI", 51L, 82, true, true));
        check(identity.size() == 4, "Jogador protegido deveria ser ignorado");
        check(identity.get(0).edits().containsKey("Country"), "Nao modificou nacionalidade");
        check(identity.get(0).edits().get("Country").equals("207"), "Finlandia nao mapeada");
        check(!identity.get(0).edits().containsKey("OffensiveAwareness"), "Modo identidade gerou atributos");
        identity.get(0).edits().put("Name", "MARTIN SILVA");
        identity.get(0).edits().put("Country", "207");
        svc.apply(db, identity);
        check(db.player(ids.get(0)).name().equals(protectedName), "Protegido alterado");
        check(db.player(ids.get(1)).name().equals("MARTIN SILVA"), "Personalizacao manual ignorada");
        check(db.playerField(ids.get(1), "Country").equals("207"), "Naturalizacao manual nao aplicada");
        check(db.playerField(ids.get(1), "Height").equals(height), "Altura alterada");
        check(db.playerField(ids.get(1), "Weight").equals(weight), "Peso alterado");
        check(db.playerField(ids.get(1), "POS").equals(position), "Posicao alterada no modo identidade");
        check(db.playerField(ids.get(1), "OffensiveAwareness").equals(attrs.get(ids.get(1))), "Atributo alterado no modo identidade");

        var samePos = svc.preview(db, List.of(ids.get(1), ids.get(2)), new Options(Mode.KEEP_POSITION, "MIX", 23L, 85, false, true));
        check(samePos.size() == 2, "Quantidade incorreta");
        check(samePos.stream().noneMatch(d -> d.edits().containsKey("POS")), "Modo preservar posicao alterou POS");
        String oldPosition = db.player(ids.get(1)).position();
        svc.apply(db, samePos);
        check(db.player(ids.get(1)).position().equals(oldPosition), "Posicao foi alterada");
        check(!db.playerField(ids.get(1), "OffensiveAwareness").equals(attrs.get(ids.get(1))), "Atributos nao foram recalculados");
        var onlyAttrs = svc.preview(db, List.of(ids.get(3)), new Options(Mode.ATTRIBUTES,"FI",90L,70,true,true));
        check(!onlyAttrs.get(0).edits().containsKey("Name"), "Modo atributos modificou identidade");
        check(!onlyAttrs.get(0).edits().containsKey("POS"), "Modo atributos modificou posicao");
        check(!onlyAttrs.get(0).edits().containsKey("Country"), "Modo atributos modificou nacionalidade");
        check(!onlyAttrs.get(0).edits().containsKey("PlayingStyle"), "Modo atributos modificou estilo");
        check(!onlyAttrs.get(0).edits().containsKey("OneTouchPass"), "Modo atributos modificou habilidade");
        var full = svc.preview(db, List.of(ids.get(4)), new Options(Mode.FULL,"MIX", 9L,76,true,true));
        check(full.get(0).edits().containsKey("POS"), "Modo full nao mudou posicao");
        check(full.get(0).edits().containsKey("GK"), "Modo full nao definiu proficiencias");
        check(full.get(0).edits().containsKey("OneTouchPass"), "Modo full nao definiu habilidades");
        check(full.get(0).edits().containsKey("WeakFootUsage"), "Modo full nao definiu pe ruim");
        check(svc.preview(db, List.of(ids.get(4)), new Options(Mode.FULL,"MIX",9L,76,true,true))
                .get(0).edits().equals(full.get(0).edits()), "Seed nao deterministica");
        svc.apply(db, full);
        check(db.player(ids.get(0)).name().equals(protectedName), "Jogador protegido modificado");
        check(db.dirtyTeams() == 0 && db.dirtyAssignments() == 0, "Clube ou atribuicao foi alterado");
        Path p = out.resolve("j.csv"), t = out.resolve("t.csv"), a = out.resolve("a.csv");
        db.exportAll(p, t, a);
        var reloaded = PesDatabase.load(p, t, a, s -> {});
        check(reloaded.player(ids.get(1)).name().equals(db.player(ids.get(1)).name()), "Nome nao persistiu");
        check(reloaded.player(ids.get(4)).position().equals(db.player(ids.get(4)).position()), "Posicao nao persistiu");
        check(reloaded.playerField(ids.get(1), "Height").equals(height), "Altura mudou no roundtrip");
        System.out.println("OK: 4 modos, protecao, naturalizacao, atributos, estilo, habilidades e roundtrip (" + reloaded.totalPlayers() + " jogadores).");
    }
}
