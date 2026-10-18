package com.frankstiwrx.norteversostudio.data;

import com.frankstiwrx.norteversostudio.csv.Csv;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Base PES em memoria. Preserva todas as colunas do CSV original.
 * Alteracoes sao locais ate Exportar; nunca escreve nos arquivos de entrada.
 */
public final class PesDatabase {

    public record Team(String id, String name, String country, boolean national) {
        @Override public String toString() { return name + "  (#" + id + ")"; }
    }

    public record Player(String id, String name, String country,
                         String age, String height, String weight, String foot,
                         String position, String overall, String playingStyle) { }

    public record RosterEntry(Player player, String shirtNumber, String assignmentId) { }

    public static final List<String> ATTRIBUTES = List.of(
            "OffensiveAwareness", "BallControl", "Dribbling", "TightPossession", "LowPass",
            "LoftedPass", "Finishing", "Heading", "PlaceKicking", "Curl", "Speed",
            "Acceleration", "KickingPower", "Jump", "PhysicalContact", "Balance",
            "Stamina", "DefensiveAwareness", "BallWinning", "Aggression",
            "GKAwareness", "GKCatching", "GKClearing", "GKReflexes", "GKReach"
    );
    public static final List<String> POSITIONS = List.of(
            "GK", "CB", "LB", "RB", "DMF", "CMF", "LMF", "RMF", "AMF", "LWF", "RWF", "SS", "CF"
    );
    public static final List<String> COM_STYLES = List.of(
            "Trickster", "MazingRun", "SpeedingBullet", "IncisiveRun", "LongBallExpert", "EarlyCross", "LongRanger"
    );
    public static final List<String> SKILLS = List.of(
            "ScissorsFeint", "DoubleTouch", "FlipFlap", "MarseilleTurn", "Sombrero",
            "CrossOverTurn", "CutBehindAndTurn", "ScotchMove", "StepOnSkillcontrol",
            "HeadingSpecial", "LongRangeDrive", "Chipshotcontrol", "LongRangeShot",
            "KnuckleShot", "DippingShots", "RisingShots", "AcrobaticFinishing",
            "HeelTrick", "FirstTimeShot", "OneTouchPass", "ThroughPassing", "WeightedPass",
            "PinpointCrossing", "OutsideCurler", "Rabona", "NoLookPass", "LowLoftedPass",
            "GKLowPunt", "GKHighPunt", "LongThrow", "GKLongThrow", "PenaltySpecialist",
            "GKPenaltySaver", "Gamesmanship", "ManMarking", "TrackBack", "Interception",
            "AcrobaticClear", "Captaincy", "SuperSub", "FightingSpirit"
    );
    public static final List<String> PLAYER_BASICS = List.of(
            "Name", "JapName", "Shirt", "ShirtNational", "Country", "Country2",
            "Age", "Height", "Weight", "Foot", "POS", "PlayingStyle", "WeakFootUsage",
            "WeakFootAcc", "Form", "InjuryResistance", "Reputation", "PlayingAttitude", "Legend"
    );
    public static final List<String> TEAM_FIELDS = List.of(
            "Name", "Country", "Stadium", "Coach", "NameEnglish", "NameUSEnglish",
            "NamePortuguese", "NameBrazilian", "ShortNameLicensed", "ShortNameFake", "Abbreviation"
    );
    private static final Set<String> PLAYER_EDITABLE;
    private static final Set<String> TEAM_EDITABLE = Set.copyOf(TEAM_FIELDS);
    static {
        Set<String> allowed = new HashSet<>();
        allowed.addAll(PLAYER_BASICS);
        allowed.addAll(ATTRIBUTES);
        allowed.addAll(POSITIONS);
        allowed.addAll(SKILLS);
        allowed.addAll(COM_STYLES);
        PLAYER_EDITABLE = Collections.unmodifiableSet(allowed);
    }

    private final CsvRows playersCsv, teamsCsv, assignmentsCsv;
    private final Map<String, List<String>> playerRows, teamRows, assignmentRows;
    private final Map<String, Player> players;
    private final List<Team> teams;
    private final Map<String, List<RosterEntry>> rosters;
    private final Map<String, List<String>> initialPlayerRows = new HashMap<>();
    private final Map<String, List<String>> initialTeamRows = new HashMap<>();
    private final Map<String, List<String>> initialAssignmentRows = new HashMap<>();
    public static final List<String> ASSIGNMENT_FLAGS = List.of(
            "Captain", "Short Free Kick", "Long Free Kick", "Right Corner", "Left Corner", "Penalty");
    private static final Set<String> ASSIGNMENT_EDITABLE = Set.of(
            "ShirtNumber", "Captain", "Short Free Kick", "Long Free Kick",
            "Right Corner", "Left Corner", "Penalty");
    private final int totalAssignments, unmatchedPlayers, unmatchedTeams;
    private final Path playersSource, teamsSource, assignmentsSource;
    private long revision = 0;
    private long exportedRevision = 0;

    private PesDatabase(CsvRows playersCsv, CsvRows teamsCsv, CsvRows assignmentsCsv,
                        Map<String, List<String>> playerRows, Map<String, List<String>> teamRows,
                        Map<String, List<String>> assignmentRows,
                        Map<String, Player> players, List<Team> teams,
                        Map<String, List<RosterEntry>> rosters, int totalAssignments,
                        int unmatchedPlayers, int unmatchedTeams, Path playersSource, Path teamsSource, Path assignmentsSource) {
        this.playersCsv = playersCsv;
        this.teamsCsv = teamsCsv;
        this.assignmentsCsv = assignmentsCsv;
        this.playerRows = playerRows;
        this.teamRows = teamRows;
        this.assignmentRows = assignmentRows;
        this.players = players;
        this.teams = teams;
        this.rosters = rosters;
        this.totalAssignments = totalAssignments;
        this.unmatchedPlayers = unmatchedPlayers;
        this.unmatchedTeams = unmatchedTeams;
        this.playersSource = playersSource.toAbsolutePath().normalize();
        this.teamsSource = teamsSource.toAbsolutePath().normalize();
        this.assignmentsSource = assignmentsSource.toAbsolutePath().normalize();
    }

    public List<Team> teams() { return Collections.unmodifiableList(teams); }
    public List<RosterEntry> roster(String teamId) {
        return Collections.unmodifiableList(rosters.getOrDefault(teamId, List.of()));
    }
    public int totalPlayers() { return players.size(); }
    public int totalAssignments() { return totalAssignments; }
    public int unmatchedPlayers() { return unmatchedPlayers; }
    public int unmatchedTeams() { return unmatchedTeams; }
    public int dirtyPlayers() { return initialPlayerRows.size(); }
    public int dirtyTeams() { return initialTeamRows.size(); }
    public int dirtyAssignments() { return initialAssignmentRows.size(); }
    public boolean hasUnexportedChanges() {
        return revision != exportedRevision && (dirtyPlayers() > 0 || dirtyTeams() > 0 || dirtyAssignments() > 0);
    }
    public boolean isPlayerDirty(String id) { return initialPlayerRows.containsKey(id); }
    public boolean isTeamDirty(String id) { return initialTeamRows.containsKey(id); }
    public Player player(String id) { return players.get(id); }
    /** Consulta utilizada pelo gerador para evitar duplicar nomes em toda a base PES. */
    public List<String> allPlayerNames() {
        return players.values().stream().map(Player::name).toList();
    }
    /** Nomes em uso, ignorando somente os registros que serao renomeados. */
    public List<String> allPlayerNamesExceptIds(Set<String> excluded) {
        return players.values().stream().filter(p -> !excluded.contains(p.id()))
                .map(Player::name).toList();
    }
    /** Valida todos os campos propostos ANTES da geracao em massa escrever dados. */
    public void validatePlayerChanges(Map<String, String> changes) {
        validateKeys(changes, PLAYER_EDITABLE, playersCsv, "jogador");
        validatePlayer(changes);
    }
    public boolean isAssignmentDirty(String id) { return initialAssignmentRows.containsKey(id); }
    public String assignmentField(String id, String field) {
        List<String> row = assignmentRows.get(id);
        if (row == null) throw new IllegalArgumentException("Atribuicao inexistente: " + id);
        return assignmentsCsv.value(row, field);
    }

    /** Edição de camisa e funções táticas sem afetar outros registros/seleções. */
    public void editAssignment(String id, Map<String, String> edits) {
        List<String> row = assignmentRows.get(id);
        if (row == null) throw new IllegalArgumentException("Atribuicao nao encontrada: " + id);
        validateKeys(edits, ASSIGNMENT_EDITABLE, assignmentsCsv, "atribuicao");
        String teamId = assignmentsCsv.value(row, "IdTeam");
        Team team = team(teamId);
        if (team == null || team.national()) {
            throw new IllegalArgumentException("Edicao de convocacoes/selecoes nao suportada nesta etapa.");
        }
        if (edits.containsKey("ShirtNumber")) {
            int shirt = requireShirt(edits.get("ShirtNumber"));
            checkShirt(teamId, id, shirt);
        }
        for (String field : ASSIGNMENT_FLAGS) booleanField(edits, field);
        if (!changed(row, edits, assignmentsCsv)) return;
        // Mantém um único responsável para cada função, tal como o editor original.
        for (String flag : ASSIGNMENT_FLAGS) {
            if ("True".equalsIgnoreCase(edits.get(flag))) {
                for (RosterEntry other : roster(teamId)) {
                    if (!other.assignmentId().equals(id)
                            && "True".equalsIgnoreCase(assignmentField(other.assignmentId(), flag))) {
                        updateAssignmentRow(other.assignmentId(), Map.of(flag, "False"));
                    }
                }
            }
        }
        updateAssignmentRow(id, edits);
        updateRosterEntries();
    }

    /** Transfere um vínculo de clube já existente. Não cria IDs nem mexe em seleções. */
    public void transferPlayer(String assignmentId, String targetId, String newShirt) {
        List<String> row = assignmentRows.get(assignmentId);
        if (row == null) throw new IllegalArgumentException("Atribuicao inexistente: " + assignmentId);
        String sourceId = assignmentsCsv.value(row, "IdTeam");
        Team source = team(sourceId), target = team(targetId);
        if (source == null || source.national() || target == null || target.national()) {
            throw new IllegalArgumentException("Transferencia permitida somente entre clubes existentes.");
        }
        if (sourceId.equals(targetId)) throw new IllegalArgumentException("Escolha outro clube de destino.");
        String playerId = assignmentsCsv.value(row, "IdPlayer");
        if (roster(targetId).stream().anyMatch(e -> e.player().id().equals(playerId))) {
            throw new IllegalArgumentException("O jogador ja possui vinculo com o clube de destino.");
        }
        int shirt = requireShirt(newShirt);
        checkShirt(targetId, assignmentId, shirt);
        Map<String, String> change = new LinkedHashMap<>();
        change.put("IdTeam", targetId);
        change.put("ShirtNumber", Integer.toString(shirt));
        // Não traz as responsabilidades de bolas paradas do antigo clube.
        for (String flag : ASSIGNMENT_FLAGS) change.put(flag, "False");
        updateAssignmentRow(assignmentId, change);
        updateRosterEntries();
    }

    /** Restaura exatamente a linha do arquivo de entrada, inclusive transferência. */
    public void restoreAssignment(String id) {
        List<String> original = initialAssignmentRows.remove(id);
        if (original == null) return;
        List<String> row = assignmentRows.get(id);
        for (int i = 0; i < row.size(); i++) row.set(i, original.get(i));
        revision++;
        updateRosterEntries();
    }

    private void checkShirt(String teamId, String assignmentId, int shirt) {
        for (RosterEntry other : roster(teamId)) {
            if (!other.assignmentId().equals(assignmentId)
                    && Integer.toString(shirt).equals(other.shirtNumber())) {
                throw new IllegalArgumentException("Camisa " + shirt + " ja utilizada por " + other.player().name()
                        + " neste clube.");
            }
        }
    }
    private static int requireShirt(String raw) {
        try {
            int shirt = Integer.parseInt(raw.trim());
            if (shirt >= 1 && shirt <= 99) return shirt;
        } catch (NumberFormatException ignored) { }
        throw new IllegalArgumentException("O numero de camisa deve estar entre 1 e 99.");
    }
    private void updateAssignmentRow(String id, Map<String, String> updates) {
        List<String> row = assignmentRows.get(id);
        if (!changed(row, updates, assignmentsCsv)) return;
        initialAssignmentRows.putIfAbsent(id, List.copyOf(row));
        apply(row, updates, assignmentsCsv);
        if (row.equals(initialAssignmentRows.get(id))) initialAssignmentRows.remove(id);
        revision++;
    }
    private void updateRosterEntries() {
        rosters.clear();
        for (List<String> row : assignmentsCsv.rows) {
            String teamId = assignmentsCsv.value(row, "IdTeam");
            Player player = players.get(assignmentsCsv.value(row, "IdPlayer"));
            if (player != null && teamRows.containsKey(teamId)) {
                rosters.computeIfAbsent(teamId, ignored -> new ArrayList<>())
                        .add(new RosterEntry(player, assignmentsCsv.value(row, "ShirtNumber"),
                                assignmentsCsv.value(row, "Id")));
            }
        }
    }
    public Team team(String id) {
        return teams.stream().filter(t -> t.id().equals(id)).findFirst().orElse(null);
    }

    public String playerField(String id, String field) {
        if (!playerRows.containsKey(id)) throw new IllegalArgumentException("Jogador inexistente: " + id);
        return playerRows.get(id).get(playersCsv.columns.get(field));
    }
    public String teamField(String id, String field) {
        if (!teamRows.containsKey(id)) throw new IllegalArgumentException("Time inexistente: " + id);
        return teamRows.get(id).get(teamsCsv.columns.get(field));
    }

    /** So permite colunas explicitamente autorizadas: nao altera IDs nem aparencias. */
    public void editPlayer(String id, Map<String, String> edits) {
        List<String> row = playerRows.get(id);
        if (row == null) throw new IllegalArgumentException("Jogador nao encontrado: " + id);
        validateKeys(edits, PLAYER_EDITABLE, playersCsv, "jogador");
        validatePlayer(edits);
        if (!changed(row, edits, playersCsv)) return;
        initialPlayerRows.putIfAbsent(id, List.copyOf(row));
        apply(row, edits, playersCsv);
        refreshPlayer(id);
        if (row.equals(initialPlayerRows.get(id))) initialPlayerRows.remove(id);
        revision++;
    }

    public void editTeam(String id, Map<String, String> edits) {
        List<String> row = teamRows.get(id);
        if (row == null) throw new IllegalArgumentException("Time nao encontrado: " + id);
        validateKeys(edits, TEAM_EDITABLE, teamsCsv, "time");
        validateTeam(edits);
        if (!changed(row, edits, teamsCsv)) return;
        initialTeamRows.putIfAbsent(id, List.copyOf(row));
        apply(row, edits, teamsCsv);
        refreshTeam(id);
        if (row.equals(initialTeamRows.get(id))) initialTeamRows.remove(id);
        revision++;
    }

    public void restorePlayer(String id) {
        List<String> previous = initialPlayerRows.remove(id);
        if (previous == null) return;
        List<String> current = playerRows.get(id);
        for (int i = 0; i < current.size(); i++) current.set(i, previous.get(i));
        refreshPlayer(id);
        revision++;
    }
    public void restoreTeam(String id) {
        List<String> previous = initialTeamRows.remove(id);
        if (previous == null) return;
        List<String> current = teamRows.get(id);
        for (int i = 0; i < current.size(); i++) current.set(i, previous.get(i));
        refreshTeam(id);
        revision++;
    }

    /** Salva duas copias, nunca sobrescreve o original. */
    public void export(Path playersOutput, Path teamsOutput) throws IOException {
        if (dirtyAssignments() > 0) {
            throw new IOException("Ha alteracoes nos elencos. Use a exportacao de tres CSVs.");
        }
        Path p = playersOutput.toAbsolutePath().normalize();
        Path t = teamsOutput.toAbsolutePath().normalize();
        if (p.equals(t) || p.equals(playersSource) || p.equals(teamsSource)
                || t.equals(playersSource) || t.equals(teamsSource)) {
            throw new IOException("Os destinos devem ser diferentes entre si e dos CSVs originais.");
        }
        if (Files.exists(p) || Files.exists(t)) {
            throw new IOException("Um arquivo de destino ja existe; selecione uma pasta sem exportacoes anteriores.");
        }
        writeNewFile(p, playersCsv.content());
        try {
            writeNewFile(t, teamsCsv.content());
        } catch (IOException ex) {
            try { Files.deleteIfExists(p); } catch (IOException ignored) { /* arquivo de seguranca */ }
            throw ex;
        }
        exportedRevision = revision;
    }

    /** Exporta os três CSVs completos preservando ordem, cabeçalhos e demais colunas. */
    public void exportAll(Path playersOutput, Path teamsOutput, Path assignmentOutput) throws IOException {
        Path p = playersOutput.toAbsolutePath().normalize();
        Path t = teamsOutput.toAbsolutePath().normalize();
        Path a = assignmentOutput.toAbsolutePath().normalize();
        Set<Path> all = new HashSet<>(List.of(p, t, a));
        if (all.size() != 3 || all.contains(playersSource) || all.contains(teamsSource)
                || all.contains(assignmentsSource)) {
            throw new IOException("Os arquivos de destino devem ser distintos dos tres CSVs originais.");
        }
        if (Files.exists(p) || Files.exists(t) || Files.exists(a)) {
            throw new IOException("Pelo menos um destino ja existe; use uma pasta livre ou novo horario.");
        }
        List<Path> done = new ArrayList<>();
        try {
            writeNewFile(p, playersCsv.content()); done.add(p);
            writeNewFile(t, teamsCsv.content()); done.add(t);
            writeNewFile(a, assignmentsCsv.content()); done.add(a);
        } catch (IOException ex) {
            for (Path f : done) try { Files.deleteIfExists(f); } catch (IOException ignored) { }
            throw ex;
        }
        exportedRevision = revision;
    }

    /**
     * Exportacao REDUZIDA para ANALISE, nao para importar BIN/EDIT.
     * Preserva todas as colunas e a ordem das linhas originais.
     * Nao confirma nem limpa o estado de exportacao pendente.
     */
    public void exportClubReview(Path folder, String teamId) throws IOException {
        Team selected = team(teamId);
        if (selected == null || selected.national()) throw new IOException("Selecione um clube valido.");
        if (Files.exists(folder)) throw new IOException("A pasta de analise ja existe: " + folder);
        Files.createDirectories(folder);
        try {
            Set<String> ids = new HashSet<>();
            for (RosterEntry entry : roster(teamId)) ids.add(entry.player().id());
            List<List<String>> playerRowsOut = new ArrayList<>();
            playerRowsOut.add(playersCsv.header);
            for (List<String> row : playersCsv.rows) {
                if (ids.contains(playersCsv.value(row, "Id"))) playerRowsOut.add(row);
            }
            List<List<String>> teamRowsOut = new ArrayList<>();
            teamRowsOut.add(teamsCsv.header);
            for (List<String> row : teamsCsv.rows) {
                if (teamId.equals(teamsCsv.value(row, "Id"))) teamRowsOut.add(row);
            }
            List<List<String>> assignRowsOut = new ArrayList<>();
            assignRowsOut.add(assignmentsCsv.header);
            for (List<String> row : assignmentsCsv.rows) {
                if (teamId.equals(assignmentsCsv.value(row, "IdTeam"))) assignRowsOut.add(row);
            }
            // 3 arquivos pequenos evitam misturar jogadores de outras equipes na auditoria.
            Csv.gravar(folder.resolve("Jogadores_APENAS_CLUBE.csv"), playerRowsOut);
            Csv.gravar(folder.resolve("Time_APENAS_CLUBE.csv"), teamRowsOut);
            Csv.gravar(folder.resolve("Atribuicoes_APENAS_CLUBE.csv"), assignRowsOut);
            Files.writeString(folder.resolve("LEIA-ME.txt"),
                    "NORTEVERSO STUDIO - EXPORTACAO PARA CONFERENCIA\n"
                  + "Clube: " + selected.name() + " (ID " + teamId + ")\n"
                  + "Jogadores: " + (playerRowsOut.size() - 1) + "\n"
                  + "Vinculos: " + (assignRowsOut.size() - 1) + "\n\n"
                  + "ATENCAO: estes arquivos contem apenas um clube. NAO importe diretamente "
                  + "no Ejogc327, BIN ou EDIT. Para atualizar o patch, use 'Exportar base COMPLETA'.\n"
                  + "Este relatorio NAO substitui a exportacao completa e NAO limpa pendencias.\n",
                    java.nio.charset.StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException ex) {
            // Evita deixar uma pasta de analise parcial com dados inconsistentes.
            for (String name : List.of("Jogadores_APENAS_CLUBE.csv", "Time_APENAS_CLUBE.csv",
                    "Atribuicoes_APENAS_CLUBE.csv", "LEIA-ME.txt")) {
                try { Files.deleteIfExists(folder.resolve(name)); } catch (IOException ignored) { }
            }
            try { Files.deleteIfExists(folder); } catch (IOException ignored) { }
            throw ex;
        }
    }

    private static void writeNewFile(Path destination, List<List<String>> rows) throws IOException {
        Path folder = destination.getParent();
        if (folder != null) Files.createDirectories(folder);
        Path temp = Files.createTempFile(folder, "norteverso_", ".tmp");
        try {
            Csv.gravar(temp, rows);
            Files.move(temp, destination);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private static void validateKeys(Map<String, String> fields, Set<String> allow,
                                     CsvRows data, String type) {
        for (var entry : fields.entrySet()) {
            if (!allow.contains(entry.getKey()) || !data.columns.containsKey(entry.getKey())) {
                throw new IllegalArgumentException("Campo nao editavel de " + type + ": " + entry.getKey());
            }
            if (entry.getValue() == null) throw new IllegalArgumentException("Valor nulo: " + entry.getKey());
        }
    }
    private static void number(Map<String, String> edits, String field, int min, int max) {
        if (!edits.containsKey(field)) return;
        String raw = edits.get(field).strip();
        try {
            int n = Integer.parseInt(raw);
            if (n < min || n > max) throw new NumberFormatException();
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(field + " deve ser um inteiro entre " + min + " e " + max + ".");
        }
    }
    private static void validatePlayer(Map<String, String> edits) {
        if (edits.containsKey("Name") && edits.get("Name").isBlank()) {
            throw new IllegalArgumentException("O nome do jogador nao pode ficar vazio.");
        }
        number(edits, "Age", 15, 80);
        number(edits, "Height", 130, 230);
        number(edits, "Weight", 35, 180);
        number(edits, "Country", 0, 9999);
        number(edits, "Country2", 0, 9999);
        number(edits, "POS", 0, 12);
        number(edits, "PlayingStyle", 0, 21);
        number(edits, "WeakFootUsage", 1, 4);
        number(edits, "WeakFootAcc", 1, 4);
        number(edits, "Form", 1, 8);
        number(edits, "InjuryResistance", 1, 3);
        number(edits, "Reputation", 1, 8);
        number(edits, "PlayingAttitude", 1, 3);
        booleanField(edits, "Legend");
        if (edits.containsKey("Foot") && !isBoolean(edits.get("Foot"))) {
            throw new IllegalArgumentException("Foot deve ser True ou False.");
        }
        for (String f : ATTRIBUTES) number(edits, f, 1, 127);
        for (String f : POSITIONS) number(edits, f, 0, 2);
        for (String f : SKILLS) booleanField(edits, f);
        for (String f : COM_STYLES) booleanField(edits, f);
    }
    private static boolean isBoolean(String s) { return s.equalsIgnoreCase("True") || s.equalsIgnoreCase("False"); }
    private static void booleanField(Map<String, String> edits, String field) {
        if (edits.containsKey(field) && !isBoolean(edits.get(field))) {
            throw new IllegalArgumentException(field + " deve ser True ou False.");
        }
    }
    private static void validateTeam(Map<String, String> edits) {
        if (edits.containsKey("Name") && edits.get("Name").isBlank()) {
            throw new IllegalArgumentException("O nome do time nao pode ficar vazio.");
        }
        for (String f : List.of("Country", "Stadium", "Coach")) number(edits, f, -1, 99999999);
    }
    private static boolean changed(List<String> row, Map<String, String> edits, CsvRows csv) {
        return edits.entrySet().stream().anyMatch(e -> !row.get(csv.columns.get(e.getKey())).equals(e.getValue()));
    }
    private static void apply(List<String> row, Map<String, String> edits, CsvRows csv) {
        for (var e : edits.entrySet()) row.set(csv.columns.get(e.getKey()), e.getValue());
    }
    private void refreshPlayer(String id) {
        List<String> row = playerRows.get(id);
        Player p = new Player(id, playersCsv.value(row, "Name"), playersCsv.value(row, "Country"),
                playersCsv.value(row, "Age"), playersCsv.value(row, "Height"),
                playersCsv.value(row, "Weight"), playersCsv.value(row, "Foot"),
                playersCsv.value(row, "POS"), playersCsv.value(row, "OverallStats"),
                playersCsv.value(row, "PlayingStyle"));
        players.put(id, p);
        for (List<RosterEntry> roster : rosters.values()) {
            for (int i = 0; i < roster.size(); i++) {
                RosterEntry old = roster.get(i);
                if (old.player().id().equals(id)) roster.set(i, new RosterEntry(p, old.shirtNumber(), old.assignmentId()));
            }
        }
    }
    private void refreshTeam(String id) {
        List<String> row = teamRows.get(id);
        Team newTeam = new Team(id, teamsCsv.value(row, "Name"), teamsCsv.value(row, "Country"),
                "True".equalsIgnoreCase(teamsCsv.value(row, "National")));
        for (int i = 0; i < teams.size(); i++) if (teams.get(i).id().equals(id)) {
            teams.set(i, newTeam);
            break;
        }
        teams.sort((a,b) -> a.name().compareToIgnoreCase(b.name()));
    }

    public static PesDatabase load(Path playersFile, Path teamsFile, Path assignmentsFile,
                                   Consumer<String> progress) throws IOException {
        progress.accept("Lendo jogadores...");
        CsvRows playersCsv = read(playersFile, "Id", "Name", "Country", "Age", "Height",
                "Weight", "Foot", "POS", "OverallStats", "PlayingStyle");
        if (playersCsv.columns.containsKey("SkinColour")) {
            throw new IOException("Foi selecionada uma exportacao combinada Jogador + Aparencia "
                    + "(281 colunas). Para esta etapa, abra Jogadores - PES 2021 - Bin.csv "
                    + "(151 colunas) para preservar integralmente as aparencias.");
        }
        Map<String, Player> players = new LinkedHashMap<>();
        Map<String, List<String>> playerRows = new LinkedHashMap<>();
        for (List<String> row : playersCsv.rows) {
            String id = playersCsv.value(row, "Id");
            if (id.isBlank()) throw new IOException("Ha jogador sem ID em " + playersFile.getFileName());
            Player p = new Player(id, playersCsv.value(row, "Name"), playersCsv.value(row, "Country"),
                    playersCsv.value(row, "Age"), playersCsv.value(row, "Height"),
                    playersCsv.value(row, "Weight"), playersCsv.value(row, "Foot"),
                    playersCsv.value(row, "POS"), playersCsv.value(row, "OverallStats"),
                    playersCsv.value(row, "PlayingStyle"));
            if (players.putIfAbsent(id, p) != null) throw new IOException("ID de jogador duplicado: " + id);
            playerRows.put(id, row);
        }
        progress.accept("Lendo times...");
        CsvRows teamsCsv = read(teamsFile, "Id", "Name", "Country", "National");
        List<Team> teams = new ArrayList<>();
        Map<String, List<String>> teamRows = new LinkedHashMap<>();
        Set<String> teamIds = new HashSet<>();
        for (List<String> row : teamsCsv.rows) {
            String id = teamsCsv.value(row, "Id");
            if (id.isBlank() || !teamIds.add(id)) throw new IOException("ID de time vazio ou repetido: " + id);
            teams.add(new Team(id, teamsCsv.value(row, "Name"), teamsCsv.value(row, "Country"),
                    "True".equalsIgnoreCase(teamsCsv.value(row, "National"))));
            teamRows.put(id, row);
        }
        progress.accept("Associando jogadores aos clubes...");
        CsvRows assignmentsCsv = read(assignmentsFile, "Id", "IdPlayer", "IdTeam", "ShirtNumber",
                "Captain", "Short Free Kick", "Long Free Kick", "Right Corner", "Left Corner", "Penalty");
        Map<String, List<RosterEntry>> rosters = new HashMap<>();
        Map<String, List<String>> assignmentRows = new LinkedHashMap<>();
        int unmatchedPlayers = 0, unmatchedTeams = 0;
        for (List<String> row : assignmentsCsv.rows) {
            String assignmentId = assignmentsCsv.value(row, "Id");
            if (assignmentId.isBlank() || assignmentRows.putIfAbsent(assignmentId, row) != null) {
                throw new IOException("Id de atribuicao vazio ou duplicado: " + assignmentId);
            }
            String teamId = assignmentsCsv.value(row, "IdTeam");
            if (!teamIds.contains(teamId)) { unmatchedTeams++; continue; }
            Player player = players.get(assignmentsCsv.value(row, "IdPlayer"));
            if (player == null) { unmatchedPlayers++; continue; }
            rosters.computeIfAbsent(teamId, ignored -> new ArrayList<>())
                    .add(new RosterEntry(player, assignmentsCsv.value(row, "ShirtNumber"),
                            assignmentsCsv.value(row, "Id")));
        }
        teams.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
        progress.accept("Base carregada com sucesso.");
        return new PesDatabase(playersCsv, teamsCsv, assignmentsCsv, playerRows, teamRows,
                assignmentRows, players, teams, rosters, assignmentsCsv.rows.size(), unmatchedPlayers,
                unmatchedTeams, playersFile, teamsFile, assignmentsFile);
    }

    private static CsvRows read(Path file, String... required) throws IOException {
        List<List<String>> content = Csv.ler(file);
        if (content.isEmpty()) throw new IOException("Arquivo CSV vazio: " + file.getFileName());
        List<String> header = content.get(0);
        Map<String, Integer> indices = new HashMap<>();
        for (int i = 0; i < header.size(); i++) {
            String label = header.get(i).replace("\uFEFF", "").trim();
            if (indices.putIfAbsent(label, i) != null) {
                throw new IOException("Cabecalho duplicado '" + label + "' em " + file.getFileName());
            }
        }
        for (String field : required) if (!indices.containsKey(field)) {
            throw new IOException("Falta coluna '" + field + "' em " + file.getFileName()
                    + ". Confira se selecionou o CSV correto.");
        }
        for (int i = 1; i < content.size(); i++) if (content.get(i).size() != header.size()) {
            throw new IOException("Colunas incompatíveis na linha " + (i+1) + " de " + file.getFileName());
        }
        return new CsvRows(indices, content.get(0), content.subList(1, content.size()));
    }
    private record CsvRows(Map<String, Integer> columns, List<String> header, List<List<String>> rows) {
        String value(List<String> row, String column) {
            Integer i = columns.get(column);
            if (i == null) throw new IllegalArgumentException("Coluna nao encontrada: " + column);
            return row.get(i).trim();
        }
        List<List<String>> content() {
            List<List<String>> all = new ArrayList<>(rows.size() + 1);
            all.add(header);
            all.addAll(rows);
            return all;
        }
    }
}
