package com.frankstiwrx.norteversostudio.generator;

import com.frankstiwrx.norteversostudio.data.PesDatabase;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Geracao de nomes por pre-visualizacao. Nao modifica a base ate apply().
 * A unica escrita do gerador e Name e, opcionalmente, Shirt.
 */
public final class IdentityGenerator {
    public record Proposal(String playerId, String before, String after,
                           String shirt, String country) { }
    private final NameCatalog catalog;
    private final ProtectionRegistry protectedPlayers;

    public IdentityGenerator(NameCatalog catalog, ProtectionRegistry protectedPlayers) {
        this.catalog = catalog;
        this.protectedPlayers = protectedPlayers;
    }

    public List<Proposal> preview(PesDatabase db, List<String> playerIds,
                                  String profile, long seed) {
        if (db == null) throw new IllegalArgumentException("Carregue a base antes de gerar nomes.");
        Random random = new Random(seed);
        Set<String> occupied = new HashSet<>();
        for (String name : db.allPlayerNames()) occupied.add(normalize(name));
        List<Proposal> proposals = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        for (String id : playerIds) {
            if (!visited.add(id)) continue;
            PesDatabase.Player player = db.player(id);
            if (player == null) throw new IllegalArgumentException("Jogador inexistente: " + id);
            if (protectedPlayers.isProtected(id)) continue;
            String currentProfile = profile;
            if ("AUTO".equals(profile)) {
                currentProfile = PesNationalityCatalog.nameOriginForCountryCode(player.country());
                if (currentProfile == null || !catalog.countries().contains(currentProfile)) continue;
            }
            NameCatalog.Identity candidate = null;
            for (int attempt = 0; attempt < 1000; attempt++) {
                NameCatalog.Identity next = catalog.generate(currentProfile, random);
                String fullname = next.fullName();
                if (fullname.length() <= 32 && occupied.add(normalize(fullname))) {
                    candidate = next;
                    break;
                }
            }
            if (candidate == null) throw new IllegalArgumentException(
                    "Nao foi possivel gerar nome unico para " + id + " nesse perfil. Tente outra origem.");
            proposals.add(new Proposal(id, player.name(), candidate.fullName(),
                    candidate.shirtName(), candidate.country()));
        }
        return List.copyOf(proposals);
    }

    public int apply(PesDatabase db, List<Proposal> proposals, boolean updateShirt) {
        if (db == null) throw new IllegalArgumentException("Base nao carregada.");
        Set<String> used = new HashSet<>();
        Set<String> ids = new HashSet<>();
        for (Proposal p : proposals) {
            if (!ids.add(p.playerId())) throw new IllegalArgumentException("Jogador duplicado na previa.");
            PesDatabase.Player player = db.player(p.playerId());
            if (player == null || protectedPlayers.isProtected(p.playerId())) {
                throw new IllegalArgumentException("Jogador protegido/inexistente: " + p.playerId());
            }
            if (!player.name().equals(p.before())) {
                throw new IllegalArgumentException("Jogador foi alterado apos a previa: " + p.playerId());
            }
            if (p.after().isBlank() || p.after().length() > 32 || !used.add(normalize(p.after()))) {
                throw new IllegalArgumentException("Nome invalido ou duplicado na previa: " + p.after());
            }
        }
        // Revalida contra outros jogadores; nao toca neles.
        Set<String> globalNames = new HashSet<>();
        for (String n : db.allPlayerNames()) globalNames.add(normalize(n));
        for (Proposal p : proposals) {
            if (globalNames.contains(normalize(p.after()))) {
                throw new IllegalArgumentException("Nome ja pertence a outro jogador: " + p.after());
            }
        }
        for (Proposal p : proposals) {
            Map<String, String> edits = new LinkedHashMap<>();
            edits.put("Name", p.after());
            if (updateShirt) edits.put("Shirt", p.shirt());
            db.editPlayer(p.playerId(), edits);
        }
        return proposals.size();
    }

    private static String normalize(String s) {
        // Garante comparacao case-insensitive e ignora espacos extras; preserva acentos no CSV.
        return Normalizer.normalize(s.strip(), Normalizer.Form.NFC)
                .replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}
