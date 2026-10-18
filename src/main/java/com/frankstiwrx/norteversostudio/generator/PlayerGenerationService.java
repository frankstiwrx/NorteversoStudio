package com.frankstiwrx.norteversostudio.generator;

import com.frankstiwrx.norteversostudio.data.PesDatabase;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Gera propostas para REGISTROS EXISTENTES no PES. Nunca aloca Id, modifica
 * aparencia, foto ou atribuições de clube/seleção.
 */
public final class PlayerGenerationService {
    public enum Mode {
        IDENTITY("Só nome e nacionalidade", true, false),
        ATTRIBUTES("Só novos atributos (preserva o resto)", false, true),
        KEEP_POSITION("Novo perfil mantendo posição", true, true),
        FULL("Novo perfil completo (mesmo ID/face)", true, true);
        private final String label;
        private final boolean identity, technical;
        Mode(String label, boolean identity, boolean technical) {
            this.label = label;
            this.identity = identity;
            this.technical = technical;
        }
        public boolean generatesIdentity() { return identity; }
        public boolean generatesTechnical() { return technical; }
        @Override public String toString() { return label; }
    }

    /** 'level' e o CENTRO dos atributos, nao um overall calculado pelo PES. */
    public record Options(Mode mode, String origin, long seed, int level,
                          boolean changeNationality, boolean updateShirt) {
        public Options {
            if (mode == null || origin == null) throw new IllegalArgumentException("Modo e origem obrigatorios.");
            if (level < 50 || level > 99) throw new IllegalArgumentException("Nivel base entre 50 e 99.");
        }
    }

    /** Alterações editáveis: antes de Aplicar, a UI pode substituir qualquer campo liberado. */
    public static final class Draft {
        private final String playerId;
        private final Map<String, String> baseline;
        private final LinkedHashMap<String, String> edits;
        private final String nameOrigin;
        private Draft(String playerId, Map<String, String> baseline,
                      Map<String, String> edits, String nameOrigin) {
            this.playerId = playerId;
            this.baseline = Map.copyOf(baseline);
            this.edits = new LinkedHashMap<>(edits);
            this.nameOrigin = nameOrigin;
        }
        public String playerId() { return playerId; }
        public String originalName() { return baseline.get("Name"); }
        public String nameOrigin() { return nameOrigin; }
        public Map<String, String> edits() { return edits; }
        public String result(PesDatabase db, String key) {
            return edits.getOrDefault(key, db.playerField(playerId, key));
        }
        /** Reverte somente os ajustes manuais da prévia, sem aplicar ao CSV. */
        public void replaceEdits(Map<String, String> replacement) {
            edits.clear();
            edits.putAll(replacement);
        }
    }

    private final NameCatalog names;
    private final ProtectionRegistry protectedPlayers;

    public PlayerGenerationService(NameCatalog names, ProtectionRegistry protectedPlayers) {
        this.names = names;
        this.protectedPlayers = protectedPlayers;
    }

    public List<Draft> preview(PesDatabase db, List<String> playerIds, Options options) {
        if (db == null) throw new IllegalArgumentException("Carregue os CSVs antes de gerar.");
        var unique = new ArrayList<String>();
        Set<String> ids = new HashSet<>();
        for (String id : playerIds) {
            if (!ids.add(id)) continue;
            if (db.player(id) == null) throw new IllegalArgumentException("Jogador nao encontrado: " + id);
            if (!protectedPlayers.isProtected(id)) unique.add(id);
        }
        Map<String, IdentityGenerator.Proposal> identity = new HashMap<>();
        if (options.mode().generatesIdentity()) {
            IdentityGenerator idgen = new IdentityGenerator(names, protectedPlayers);
            for (var p : idgen.preview(db, unique, options.origin(), options.seed())) identity.put(p.playerId(), p);
        }
        List<Draft> drafts = new ArrayList<>();
        for (String id : unique) {
            if (options.mode().generatesIdentity() && !identity.containsKey(id)) continue;
            var current = db.player(id);
            LinkedHashMap<String, String> changes = new LinkedHashMap<>();
            String origin = "—";
            if (options.mode().generatesIdentity()) {
                var proposed = identity.get(id);
                changes.put("Name", proposed.after());
                origin = proposed.country();
                if (options.updateShirt()) changes.put("Shirt", proposed.shirt());
                if (options.changeNationality()) {
                    String newCode = PesNationalityCatalog.pesCountryForOrigin(origin);
                    if (newCode != null) changes.put("Country", newCode);
                }
            }
            if (options.mode().generatesTechnical()) {
                // A seed gera o mesmo perfil para o mesmo ID sem depender da ordem da lista.
                Random random = new Random(options.seed() ^ (id.hashCode() * 0x9E3779B97F4A7C15L));
                int pos = Integer.parseInt(current.position());
                if (options.mode() == Mode.FULL) {
                    pos = choosePosition(random);
                    changes.put("POS", Integer.toString(pos));
                    generatePositions(changes, pos, random);
                    changes.put("Foot", random.nextInt(100) < 18 ? "True" : "False");
                }
                if (options.mode() != Mode.ATTRIBUTES) {
                    changes.put("Age", Integer.toString(17 + random.nextInt(19)));
                    changes.put("PlayingStyle", Integer.toString(chooseStyle(pos, random)));
                    changes.put("WeakFootUsage", Integer.toString(1 + random.nextInt(4)));
                    changes.put("WeakFootAcc", Integer.toString(1 + random.nextInt(4)));
                    changes.put("Form", Integer.toString(4 + random.nextInt(5)));
                    changes.put("InjuryResistance", Integer.toString(1 + random.nextInt(3)));
                    generateSkills(changes, pos, options.level(), random);
                }
                attributes(changes, pos, options.level(), random);
            }
            Map<String, String> baseline = new LinkedHashMap<>();
            baseline.put("Name", current.name());
            // Guarda valores ORIGINAIS de todos os campos gerados, impedindo aplicar prévia obsoleta.
            for (String key : changes.keySet()) baseline.put(key, db.playerField(id, key));
            drafts.add(new Draft(id, baseline, changes, origin));
        }
        return drafts;
    }

    public int apply(PesDatabase db, List<Draft> drafts) {
        if (db == null) throw new IllegalArgumentException("Nenhuma base carregada.");
        Set<String> ids = new HashSet<>();
        Set<String> occupiedNames = new HashSet<>();
        Set<String> changedIds = new HashSet<>();
        for (Draft draft : drafts) {
            if (draft.edits.containsKey("Name")
                    && !draft.edits.get("Name").equals(db.player(draft.playerId).name())) {
                changedIds.add(draft.playerId);
            }
        }
        // Toda identidade não modificada (mesmo em outro clube) continua ocupada.
        // Usa todos os IDs, e nao apenas nomes, para permitir detectar duplicatas reais.
        for (String name : db.allPlayerNamesExceptIds(changedIds)) occupiedNames.add(normalize(name));
        for (Draft draft : drafts) {
            if (!ids.add(draft.playerId)) throw new IllegalArgumentException("Mesmo ID repetido na previa: " + draft.playerId);
            if (protectedPlayers.isProtected(draft.playerId)) {
                throw new IllegalArgumentException("Jogador protegido nao pode ser gerado: " + draft.playerId);
            }
            var player = db.player(draft.playerId);
            if (player == null) throw new IllegalArgumentException("Jogador nao encontrado: " + draft.playerId);
            if (!draft.originalName().equals(player.name())) {
                throw new IllegalArgumentException("Nome alterado desde a previa: " + draft.playerId);
            }
            for (var original : draft.baseline.entrySet()) {
                if (!db.playerField(draft.playerId, original.getKey()).equals(original.getValue())) {
                    throw new IllegalArgumentException("Previa desatualizada para o jogador " + draft.playerId
                            + " (campo " + original.getKey() + "). Gere novamente.");
                }
            }
            // Checagem completa ANTES de gravar o primeiro registro.
            db.validatePlayerChanges(draft.edits);
            if (draft.edits.containsKey("Name") && changedIds.contains(draft.playerId)) {
                String next = normalize(draft.edits.get("Name"));
                if (!occupiedNames.add(next)) {
                    throw new IllegalArgumentException("Nome completo repetido: " + draft.edits.get("Name"));
                }
            }
        }
        for (Draft draft : drafts) db.editPlayer(draft.playerId, draft.edits);
        return drafts.size();
    }

    private static String normalize(String s) {
        return Normalizer.normalize(s.strip(), Normalizer.Form.NFC)
                .replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private static int choosePosition(Random r) {
        // Distinção entre defensores, meias e atacantes, sem superpopulação de goleiros.
        int[] slots = {0,1,1,1,2,3,4,4,4,5,5,5,5,6,7,8,8,8,9,10,11,12,12,12};
        return slots[r.nextInt(slots.length)];
    }

    private static void generatePositions(Map<String, String> changes, int pos, Random r) {
        for (String key : PesDatabase.POSITIONS) changes.put(key, "0");
        changes.put(PesDatabase.POSITIONS.get(pos), "2");
        if (pos == 0) return;
        int[][] neighbors = {
            {}, {2,3,4}, {1,3,6}, {1,2,7}, {1,5}, {4,6,7,8},
            {2,5,8,9}, {3,5,8,10}, {5,6,7,11}, {6,10,11}, {7,9,11}, {8,9,10,12}, {11}
        };
        for (int next : neighbors[pos]) if (r.nextBoolean()) changes.put(PesDatabase.POSITIONS.get(next), "1");
    }

    private static int chooseStyle(int pos, Random r) {
        int[][] byPosition = {
            {16,17},             // GK
            {9,10,15},           // CB
            {11,12,21},          // LB
            {11,12,21},          // RB
            {8,9,15,20,7},       // DMF
            {7,20,6,14},         // CMF
            {7,14,19,6},         // LMF
            {7,14,19,6},         // RMF
            {6,14,5,20},         // AMF
            {4,18,14,19},        // LWF
            {4,18,14,19},        // RWF
            {1,6,14,2},          // SS
            {1,3,13,2}          // CF
        };
        int[] styles = byPosition[pos];
        return styles[r.nextInt(styles.length)];
    }

    private static void attributes(Map<String, String> edits, int position, int level, Random r) {
        // Desvios sobre o nivel de referência: NAO equivalem à fórmula de OVR do PES.
        int[][] biases = {
            // GA=fora de campo; cinco atributos GK são tratados separadamente.
            {-34,-25,-34,-32,-19,-15,-38,-12,-18,-27,-18,-22,-12,4,0,-18,1,-7,-13,-11},
            {-27,-14,-26,-21,-8,0,-28,9,-21,-18,-12,-15,0,7,10,-10,6,14,13,12},
            {-15,-4,0,-5,0,1,-17,0,-10,-10,3,5,-2,3,1,2,7,7,7,6},
            {-15,-4,0,-5,0,1,-17,0,-10,-10,3,5,-2,3,1,2,7,7,7,6},
            {-10,3,-7,0,7,8,-20,0,-9,-8,-6,-7,-3,2,7,-2,9,10,12,8},
            {2,7,3,6,11,9,-5,-8,-2,1,0,0,0,-5,-1,6,7,1,2,1},
            {4,5,7,4,8,8,-3,-12,0,5,8,9,-1,-7,-8,6,3,-12,-10,-9},
            {4,5,7,4,8,8,-3,-12,0,5,8,9,-1,-7,-8,6,3,-12,-10,-9},
            {10,12,10,11,10,6,5,-10,3,9,3,5,4,-8,-11,10,-3,-16,-18,-15},
            {9,9,13,12,2,0,8,-11,1,10,13,15,4,-6,-14,12,2,-24,-25,-19},
            {9,9,13,12,2,0,8,-11,1,10,13,15,4,-6,-14,12,2,-24,-25,-19},
            {12,10,13,8,4,1,13,-5,2,6,9,9,8,-1,-9,9,0,-20,-21,-18},
            {17,3,-8,-9,-6,-11,19,11,-3,-10,2,-1,6,5,13,-8,0,-24,-22,-17}
        };
        for (int i = 0; i < PesDatabase.ATTRIBUTES.size(); i++) {
            int raw;
            if (i >= 20) {
                raw = position == 0 ? level + 2 + r.nextInt(15) - 7 : 40 + r.nextInt(7) - 3;
            } else {
                raw = level + biases[position][i] + r.nextInt(15) - 7;
            }
            edits.put(PesDatabase.ATTRIBUTES.get(i), Integer.toString(Math.max(40, Math.min(99, raw))));
        }
    }

    private static void generateSkills(Map<String, String> edits, int pos, int level, Random r) {
        for (String key : PesDatabase.SKILLS) edits.put(key, "False");
        for (String key : PesDatabase.COM_STYLES) edits.put(key, "False");
        String[] related = switch (pos) {
            case 0 -> new String[]{"GKLowPunt","GKHighPunt","GKLongThrow","GKPenaltySaver","Captaincy","FightingSpirit"};
            case 1,2,3 -> new String[]{"Interception","ManMarking","AcrobaticClear","HeadingSpecial","TrackBack","Captaincy","FightingSpirit","PinpointCrossing"};
            case 4,5 -> new String[]{"OneTouchPass","ThroughPassing","WeightedPass","Interception","TrackBack","LongRangeShot","Captaincy","FightingSpirit"};
            case 6,7,8 -> new String[]{"OneTouchPass","ThroughPassing","WeightedPass","DoubleTouch","LongRangeShot","OutsideCurler","PinpointCrossing","FirstTimeShot"};
            case 9,10 -> new String[]{"DoubleTouch","ScissorsFeint","FirstTimeShot","LongRangeShot","OutsideCurler","PinpointCrossing","Gamesmanship"};
            default -> new String[]{"FirstTimeShot","HeadingSpecial","AcrobaticFinishing","LongRangeShot","SuperSub","FightingSpirit","OneTouchPass"};
        };
        List<String> available = new ArrayList<>(Arrays.asList(related));
        int max = Math.min(available.size(), 1 + (level - 50) / 12 + r.nextInt(3));
        for (int i = 0; i < max; i++) {
            int pick = r.nextInt(available.size());
            edits.put(available.remove(pick), "True");
        }
        if (pos != 0) {
            String[] play = pos <= 5
                    ? new String[]{"LongBallExpert", "LongRanger"}
                    : new String[]{"MazingRun", "SpeedingBullet", "IncisiveRun", "LongRanger", "Trickster", "EarlyCross"};
            int maxCom = level >= 84 ? 2 : 1;
            for (int i = 0; i < maxCom; i++) if (r.nextInt(100) < 55) edits.put(play[r.nextInt(play.length)], "True");
        }
    }
}
