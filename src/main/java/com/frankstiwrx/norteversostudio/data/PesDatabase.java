package com.frankstiwrx.norteversostudio.data;

import com.frankstiwrx.norteversostudio.csv.Csv;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Base PES em memória, exclusivamente para consulta nesta etapa.
 * Nenhum CSV original é modificado, nem se supõe que a base esteja atualizada.
 */
public final class PesDatabase {

    public record Team(String id, String name, String country, boolean national) {
        @Override public String toString() { return name + "  (#" + id + ")"; }
    }

    public record Player(String id, String name, String country,
                         String age, String height, String weight, String foot,
                         String position, String overall, String playingStyle) { }

    public record RosterEntry(Player player, String shirtNumber, String assignmentId) { }

    private final List<Team> teams;
    private final Map<String, List<RosterEntry>> rosters;
    private final int totalPlayers;
    private final int totalAssignments;
    private final int unmatchedPlayers;
    private final int unmatchedTeams;

    private PesDatabase(List<Team> teams, Map<String, List<RosterEntry>> rosters,
                        int totalPlayers, int totalAssignments,
                        int unmatchedPlayers, int unmatchedTeams) {
        this.teams = List.copyOf(teams);
        Map<String, List<RosterEntry>> sealed = new HashMap<>();
        rosters.forEach((id, rows) -> sealed.put(id, List.copyOf(rows)));
        this.rosters = Collections.unmodifiableMap(sealed);
        this.totalPlayers = totalPlayers;
        this.totalAssignments = totalAssignments;
        this.unmatchedPlayers = unmatchedPlayers;
        this.unmatchedTeams = unmatchedTeams;
    }

    public List<Team> teams() { return teams; }
    public List<RosterEntry> roster(String teamId) {
        return rosters.getOrDefault(teamId, List.of());
    }
    public int totalPlayers() { return totalPlayers; }
    public int totalAssignments() { return totalAssignments; }
    public int unmatchedPlayers() { return unmatchedPlayers; }
    public int unmatchedTeams() { return unmatchedTeams; }

    /** CSV do Ejogc327, separado por ponto e vírgula e com cabeçalho original. */
    public static PesDatabase load(Path playersFile, Path teamsFile, Path assignmentsFile,
                                   Consumer<String> progress) throws IOException {
        progress.accept("Lendo jogadores...");
        CsvRows playersCsv = read(playersFile, "Id", "Name", "Country", "Age", "Height",
                "Weight", "Foot", "POS", "OverallStats", "PlayingStyle");
        Map<String, Player> players = new LinkedHashMap<>();
        for (List<String> row : playersCsv.rows) {
            String id = playersCsv.value(row, "Id");
            if (id.isBlank()) throw new IOException("Há jogador sem ID em " + playersFile.getFileName());
            Player p = new Player(id, playersCsv.value(row, "Name"), playersCsv.value(row, "Country"),
                    playersCsv.value(row, "Age"), playersCsv.value(row, "Height"),
                    playersCsv.value(row, "Weight"), playersCsv.value(row, "Foot"),
                    playersCsv.value(row, "POS"), playersCsv.value(row, "OverallStats"),
                    playersCsv.value(row, "PlayingStyle"));
            if (players.putIfAbsent(id, p) != null) {
                throw new IOException("ID de jogador duplicado: " + id);
            }
        }

        progress.accept("Lendo times...");
        CsvRows teamsCsv = read(teamsFile, "Id", "Name", "Country", "National");
        List<Team> teams = new ArrayList<>();
        Set<String> teamIds = new HashSet<>();
        for (List<String> row : teamsCsv.rows) {
            String id = teamsCsv.value(row, "Id");
            if (id.isBlank() || !teamIds.add(id)) {
                throw new IOException("ID de time vazio ou repetido: " + id);
            }
            teams.add(new Team(id, teamsCsv.value(row, "Name"),
                    teamsCsv.value(row, "Country"),
                    "True".equalsIgnoreCase(teamsCsv.value(row, "National"))));
        }

        progress.accept("Associando jogadores aos clubes...");
        CsvRows assignmentsCsv = read(assignmentsFile, "Id", "IdPlayer", "IdTeam", "ShirtNumber");
        Map<String, List<RosterEntry>> rosters = new HashMap<>();
        int unmatchedPlayers = 0, unmatchedTeams = 0;
        for (List<String> row : assignmentsCsv.rows) {
            String teamId = assignmentsCsv.value(row, "IdTeam");
            if (!teamIds.contains(teamId)) { unmatchedTeams++; continue; }
            Player player = players.get(assignmentsCsv.value(row, "IdPlayer"));
            if (player == null) { unmatchedPlayers++; continue; }
            rosters.computeIfAbsent(teamId, ignored -> new ArrayList<>())
                    .add(new RosterEntry(player,
                            assignmentsCsv.value(row, "ShirtNumber"),
                            assignmentsCsv.value(row, "Id")));
        }
        teams.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
        progress.accept("Base carregada com sucesso.");
        return new PesDatabase(teams, rosters, players.size(), assignmentsCsv.rows.size(),
                unmatchedPlayers, unmatchedTeams);
    }

    private static CsvRows read(Path file, String... required) throws IOException {
        List<List<String>> content = Csv.ler(file);
        if (content.isEmpty()) throw new IOException("Arquivo CSV vazio: " + file.getFileName());
        List<String> header = content.get(0);
        Map<String, Integer> indices = new HashMap<>();
        for (int i = 0; i < header.size(); i++) {
            String label = header.get(i).replace("\uFEFF", "").trim();
            if (indices.putIfAbsent(label, i) != null) {
                throw new IOException("Cabeçalho duplicado '" + label + "' em " + file.getFileName());
            }
        }
        for (String field : required) {
            if (!indices.containsKey(field)) {
                throw new IOException("Falta coluna '" + field + "' em " + file.getFileName()
                        + ". Confira se selecionou o CSV correto.");
            }
        }
        for (int i = 1; i < content.size(); i++) {
            if (content.get(i).size() != header.size()) {
                throw new IOException("Colunas incompatíveis na linha " + (i + 1)
                        + " de " + file.getFileName());
            }
        }
        return new CsvRows(indices, content.subList(1, content.size()));
    }

    private record CsvRows(Map<String, Integer> columns, List<List<String>> rows) {
        String value(List<String> row, String column) {
            return row.get(columns.get(column)).trim();
        }
    }
}
