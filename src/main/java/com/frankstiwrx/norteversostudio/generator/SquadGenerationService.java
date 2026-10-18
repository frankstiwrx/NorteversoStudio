package com.frankstiwrx.norteversostudio.generator;

import com.frankstiwrx.norteversostudio.data.PesDatabase;
import com.frankstiwrx.norteversostudio.data.PesDatabase.RosterEntry;
import com.frankstiwrx.norteversostudio.generator.PlayerGenerationService.Draft;
import com.frankstiwrx.norteversostudio.generator.PlayerGenerationService.Mode;
import com.frankstiwrx.norteversostudio.generator.PlayerGenerationService.Options;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Etapa 09: monta um plano de elenco para jogadores EXISTENTES.
 * Nenhum metodo de preview grava CSV, altera time, ID, aparencia ou vinculo.
 * 'nivel de referencia' NAO e o OverallStats oficial do PES.
 */
public final class SquadGenerationService {
    public enum Division {
        SUPERLIIGA("Suomen Superliiga", 84),
        LIIGA1("Suomen Liiga 1", 76),
        LIIGA2("Suomen Liiga 2", 68);
        private final String label;
        private final int defaultLevel;
        Division(String label, int defaultLevel) {
            this.label = label;
            this.defaultLevel = defaultLevel;
        }
        public int defaultLevel() { return defaultLevel; }
        @Override public String toString() { return label + " (referencia " + defaultLevel + ")"; }
    }

    public enum Role {
        STAR("Craque", 8, 23, 32, 8),
        STARTER("Titular", 3, 21, 32, 40),
        RESERVE("Reserva", -3, 19, 33, 30),
        PROSPECT("Promessa", -8, 16, 21, 14),
        VETERAN("Veterano", -4, 32, 39, 8);
        private final String label;
        private final int modifier, minAge, maxAge, share;
        Role(String label, int modifier, int minAge, int maxAge, int share) {
            this.label = label;
            this.modifier = modifier;
            this.minAge = minAge;
            this.maxAge = maxAge;
            this.share = share;
        }
        public int modifier() { return modifier; }
        public int minAge() { return minAge; }
        public int maxAge() { return maxAge; }
        public int share() { return share; }
        @Override public String toString() { return label; }
    }

    /** Categorias devem totalizar o numero de jogadores NAO protegidos do clube. */
    public record Config(int baseLevel, String origin, long seed,
                         boolean changeNationality, boolean updateShirt,
                         Map<Role, Integer> roles) {
        public Config {
            if (baseLevel < 58 || baseLevel > 94) throw new IllegalArgumentException("Nível base deve ficar entre 58 e 94.");
            if (origin == null || origin.isBlank()) throw new IllegalArgumentException("Escolha a origem dos nomes.");
            if (roles == null) throw new IllegalArgumentException("Defina as categorias do elenco.");
            EnumMap<Role, Integer> copy = new EnumMap<>(Role.class);
            for (Role role : Role.values()) {
                int amount = roles.getOrDefault(role, 0);
                if (amount < 0) throw new IllegalArgumentException("Quantidade negativa em " + role + ".");
                copy.put(role, amount);
            }
            roles = Collections.unmodifiableMap(copy);
        }
    }

    public record Item(String playerId, Role role, int targetLevel, Draft draft) { }
    public record Preview(String teamId, Config config, List<Item> items, List<String> protectedIds) {
        public Preview {
            items = List.copyOf(items);
            protectedIds = List.copyOf(protectedIds);
        }
    }

    private final PlayerGenerationService players;
    private final ProtectionRegistry protection;

    public SquadGenerationService(NameCatalog catalog, ProtectionRegistry protection) {
        this.protection = protection;
        this.players = new PlayerGenerationService(catalog, protection);
    }

    /** Aplica percentual arredondado por maiores restos; soma e SEMPRE igual ao tamanho elegivel. */
    public static Map<Role, Integer> suggestedDistribution(int eligible) {
        if (eligible < 0) throw new IllegalArgumentException("Quantidade de jogadores invalida.");
        EnumMap<Role, Integer> result = new EnumMap<>(Role.class);
        List<Role> order = new ArrayList<>(List.of(Role.values()));
        int distributed = 0;
        for (Role role : Role.values()) {
            int amount = eligible * role.share() / 100;
            result.put(role, amount);
            distributed += amount;
        }
        order.sort(Comparator.<Role>comparingInt(r -> eligible * r.share() % 100).reversed()
                .thenComparingInt(Enum::ordinal));
        for (int i = 0; i < eligible - distributed; i++) {
            Role role = order.get(i % order.size());
            result.put(role, result.get(role) + 1);
        }
        return Collections.unmodifiableMap(result);
    }

    public int eligibleCount(PesDatabase db, String teamId) {
        return (int) db.roster(teamId).stream().map(e -> e.player().id()).distinct()
                .filter(id -> !protection.isProtected(id)).count();
    }

    public Preview preview(PesDatabase db, String teamId, Config config) {
        if (db == null || db.team(teamId) == null) throw new IllegalArgumentException("Clube nao encontrado.");
        if (db.team(teamId).national()) throw new IllegalArgumentException("Use um clube, nao uma selecao nacional.");
        LinkedHashMap<String, RosterEntry> available = new LinkedHashMap<>();
        List<String> protectedIds = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (RosterEntry entry : db.roster(teamId)) {
            String id = entry.player().id();
            if (!seen.add(id)) continue;
            if (protection.isProtected(id)) protectedIds.add(id);
            else available.put(id, entry);
        }
        if (available.isEmpty()) throw new IllegalArgumentException("Todos os jogadores estao protegidos, ou o clube nao tem elenco.");
        int requested = config.roles().values().stream().mapToInt(Integer::intValue).sum();
        if (requested != available.size()) {
            throw new IllegalArgumentException("Categorias somam " + requested + ", mas existem "
                    + available.size() + " jogadores livres. Clique em 'Sugerir distribuicao'.");
        }

        // Um unico lote garante unicidade de nomes entre craques, titulares, reservas etc.
        List<String> allIds = new ArrayList<>(available.keySet());
        Options identityOptions = new Options(Mode.KEEP_POSITION, config.origin(), config.seed(),
                config.baseLevel(), config.changeNationality(), config.updateShirt());
        List<Draft> drafts = players.preview(db, allIds, identityOptions);
        if (drafts.size() != allIds.size()) {
            throw new IllegalArgumentException("Algum jogador nao recebeu nome valido. Verifique origem, protecoes e banco de nomes.");
        }
        Map<String, Draft> byId = new HashMap<>();
        for (Draft draft : drafts) byId.put(draft.playerId(), draft);

        // Distribui as categorias entre setores do time, sem mudar a posicao de ninguem.
        // O mesmo conjunto de IDs e seed sempre produz os mesmos papeis e niveis.
        EnumMap<Role, Integer> remainingRoles = new EnumMap<>(Role.class);
        remainingRoles.putAll(config.roles());
        Random orderRandom = new Random(config.seed() ^ 0x52E3A119L);
        Map<Integer, List<String>> sectorIds = new LinkedHashMap<>();
        for (String id : allIds) {
            int pos = Integer.parseInt(db.player(id).position());
            int sector = sector(pos);
            sectorIds.computeIfAbsent(sector, ignored -> new ArrayList<>()).add(id);
        }
        sectorIds.values().forEach(ids -> Collections.shuffle(ids, orderRandom));
        Map<String, Role> assigned = new HashMap<>();
        List<Integer> sectors = new ArrayList<>(sectorIds.keySet());
        // Uma fila intercalada pelos quatro setores para nao concentrar craques em um so setor.
        List<String> woven = new ArrayList<>();
        while (woven.size() < allIds.size()) {
            Collections.shuffle(sectors, orderRandom);
            for (int s : sectors) {
                List<String> queue = sectorIds.get(s);
                if (!queue.isEmpty()) woven.add(queue.remove(0));
            }
        }
        // Primeiro garantimos estrelas e titulares pelo elenco inteiro; depois distribuimos o resto.
        Role[] priority = {Role.STAR, Role.STARTER, Role.PROSPECT, Role.VETERAN, Role.RESERVE};
        int index = 0;
        for (Role role : priority) {
            for (int count = 0; count < remainingRoles.get(role); count++) {
                assigned.put(woven.get(index++), role);
            }
        }

        List<Item> items = new ArrayList<>();
        for (String id : allIds) {
            Role role = assigned.get(id);
            Random rng = new Random(config.seed() ^ (id.hashCode() * 0xD1B54A32D192ED03L));
            int jitter = rng.nextInt(5) - 2;
            int target = clamp(config.baseLevel() + role.modifier() + jitter, 50, 99);
            Draft proposal = byId.get(id);
            // Usa o mesmo algoritmo validado na Etapa 08, agora com nivel por funcao.
            List<Draft> attrs = players.preview(db, List.of(id), new Options(Mode.ATTRIBUTES, "MIX",
                    config.seed() ^ 0x179A41L, target, false, false));
            for (String field : PesDatabase.ATTRIBUTES) {
                proposal.edits().put(field, attrs.get(0).edits().get(field));
            }
            // Idades coerentes com a categoria: promessa e veterano se diferenciam.
            int age = role.minAge() + rng.nextInt(role.maxAge() - role.minAge() + 1);
            proposal.edits().put("Age", Integer.toString(age));
            items.add(new Item(id, role, target, proposal));
        }
        return new Preview(teamId, config, items, protectedIds);
    }

    public int apply(PesDatabase db, Preview preview) {
        if (db.team(preview.teamId()) == null) throw new IllegalArgumentException("Clube nao encontrado.");
        // Validacao extra: o jogador nao pode ter sido transferido para outra equipe apos a previa.
        Set<String> inClub = new HashSet<>();
        for (RosterEntry entry : db.roster(preview.teamId())) inClub.add(entry.player().id());
        for (Item item : preview.items()) {
            if (!inClub.contains(item.playerId()))
                throw new IllegalArgumentException("Jogador saiu do clube desde a previa: " + item.playerId());
        }
        return players.apply(db, preview.items().stream().map(Item::draft).toList());
    }

    /** Relatorio ANTES do apply: inclui protegidos e atributos antes/depois para auditoria. */
    public static List<List<String>> auditRows(PesDatabase db, Preview preview) {
        List<String> header = new ArrayList<>(List.of("IdTeam", "NomeTime", "IdPlayer", "NumeroCamisa", "Status", "Categoria", "NivelReferenciaNaoOVR",
                "NomeAntes", "NomeProposto", "PaisAntes", "PaisProposto", "PosicaoAntes", "PosicaoProposta",
                "IdadeAntes", "IdadeProposta", "EstiloAntes", "EstiloProposto", "OverallStatsOriginalNaoRecalculado", "MediaTecnicaAntes",
                "MediaTecnicaProposta", "HabilidadesAntes", "HabilidadesPropostas"));
        for (String field : PesDatabase.ATTRIBUTES) {
            header.add(field + "_Antes");
            header.add(field + "_Proposto");
        }
        List<List<String>> result = new ArrayList<>();
        result.add(header);
        Map<String, Item> itemById = new HashMap<>();
        for (Item item : preview.items()) itemById.put(item.playerId(), item);
        Set<String> seen = new HashSet<>();
        for (RosterEntry roster : db.roster(preview.teamId())) {
            String id = roster.player().id();
            if (!seen.add(id)) continue;
            Item item = itemById.get(id);
            Draft draft = item == null ? null : item.draft();
            String pos = db.playerField(id, "POS");
            String newPos = draft == null ? pos : draft.result(db, "POS");
            List<String> row = new ArrayList<>(List.of(preview.teamId(), db.team(preview.teamId()).name(), id,
                    roster.shirtNumber(), item == null ? "PROTEGIDO/NAO_GERADO" : "PROPOSTO",
                    item == null ? "-" : item.role().toString(),
                    item == null ? "" : String.valueOf(item.targetLevel()),
                    db.playerField(id, "Name"), draft == null ? db.playerField(id, "Name") : draft.result(db, "Name"),
                    db.playerField(id, "Country"), draft == null ? db.playerField(id, "Country") : draft.result(db, "Country"),
                    pos, newPos, db.playerField(id, "Age"), draft == null ? db.playerField(id, "Age") : draft.result(db, "Age"),
                    db.playerField(id, "PlayingStyle"), draft == null ? db.playerField(id, "PlayingStyle") : draft.result(db, "PlayingStyle"),
                    db.playerField(id, "OverallStats"),
                    averageTechnical(db, id, null, pos), averageTechnical(db, id, draft, newPos),
                    Integer.toString(skillCount(db, id, null)), Integer.toString(skillCount(db, id, draft))));
            for (String field : PesDatabase.ATTRIBUTES) {
                row.add(db.playerField(id, field));
                row.add(draft == null ? db.playerField(id, field) : draft.result(db, field));
            }
            result.add(row);
        }
        return result;
    }

    public static String averageTechnical(PesDatabase db, String id, Draft draft, String pos) {
        int position = Integer.parseInt(pos);
        int from = position == 0 ? 20 : 0;
        int to = position == 0 ? 25 : 20;
        int sum = 0;
        for (int i = from; i < to; i++) {
            String field = PesDatabase.ATTRIBUTES.get(i);
            sum += Integer.parseInt(draft == null ? db.playerField(id, field) : draft.result(db, field));
        }
        return String.format(java.util.Locale.ROOT, "%.1f", (double) sum / (to - from));
    }

    private static int skillCount(PesDatabase db, String id, Draft draft) {
        int sum = 0;
        for (String field : PesDatabase.SKILLS) {
            String value = draft == null ? db.playerField(id, field) : draft.result(db, field);
            if ("True".equalsIgnoreCase(value)) sum++;
        }
        return sum;
    }

    private static int sector(int pos) {
        if (pos == 0) return 0;
        if (pos <= 3) return 1;
        if (pos <= 8) return 2;
        return 3;
    }

    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
}
