package com.frankstiwrx.norteversostudio.generator;

import com.frankstiwrx.norteversostudio.csv.Csv;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/** Le a biblioteca de nomes consolidada sem alterar os arquivos. */
public final class NameCatalog {
    private record Part(String text, int weight) { }
    private final Map<String, List<Part>> given;
    private final Map<String, List<Part>> surnames;
    private static final Set<String> NORDIC = Set.of("SE", "NO", "DK", "IS");
    private static final Set<String> BALTIC = Set.of("EE", "LV", "LT");
    private static final Set<String> ANGLO = Set.of("GB", "IE", "US", "CA");
    private static final Set<String> EAST = Set.of("RU", "PL", "UA", "CZ", "SK", "DE", "NL", "BE");

    private NameCatalog(Map<String, List<Part>> given, Map<String, List<Part>> surnames) {
        this.given = given;
        this.surnames = surnames;
    }

    public static NameCatalog load(Path names, Path family) throws IOException {
        if (!Files.isRegularFile(names) || !Files.isRegularFile(family)) {
            throw new IOException("Biblioteca nao encontrada. Coloque nomes.csv e sobrenomes.csv em dados/.");
        }
        Map<String, List<Part>> first = read(names, "nome");
        Map<String, List<Part>> last = read(family, "sobrenome");
        first.keySet().retainAll(last.keySet());
        last.keySet().retainAll(first.keySet());
        if (first.isEmpty()) throw new IOException("Biblioteca sem paises com nomes e sobrenomes.");
        return new NameCatalog(first, last);
    }

    private static Map<String, List<Part>> read(Path path, String valueColumn) throws IOException {
        List<List<String>> rows = Csv.ler(path);
        if (rows.isEmpty()) throw new IOException("Biblioteca vazia: " + path);
        Map<String, Integer> columns = new LinkedHashMap<>();
        for (int i = 0; i < rows.get(0).size(); i++) {
            columns.put(rows.get(0).get(i).replace("\uFEFF", "").trim(), i);
        }
        for (String required : List.of(valueColumn, "pais", "peso")) {
            if (!columns.containsKey(required)) throw new IOException("Coluna ausente '" + required + "' em " + path);
        }
        Map<String, List<Part>> byCountry = new LinkedHashMap<>();
        for (int index = 1; index < rows.size(); index++) {
            List<String> row = rows.get(index);
            if (row.size() != rows.get(0).size()) {
                throw new IOException("Colunas incorretas em " + path.getFileName() + ", linha " + (index + 1));
            }
            String value = row.get(columns.get(valueColumn)).trim();
            String country = row.get(columns.get("pais")).trim().toUpperCase(Locale.ROOT);
            int weight;
            try { weight = Integer.parseInt(row.get(columns.get("peso")).trim()); }
            catch (NumberFormatException e) { throw new IOException("Peso invalido na linha " + (index + 1), e); }
            if (value.isBlank() || !country.matches("[A-Z]{2}") || weight < 1 || weight > 10) {
                throw new IOException("Nome/pais/peso invalido em " + path.getFileName() + ":" + (index + 1));
            }
            byCountry.computeIfAbsent(country, ignored -> new ArrayList<>()).add(new Part(value, weight));
        }
        return byCountry;
    }

    public int namesCount() { return given.values().stream().mapToInt(List::size).sum(); }
    public int surnamesCount() { return surnames.values().stream().mapToInt(List::size).sum(); }
    public List<String> countries() {
        List<String> countries = new ArrayList<>(given.keySet());
        Collections.sort(countries);
        return countries;
    }

    /** Origem do nome; nao e nacionalidade no PES. */
    public record Identity(String name, String surname, String country) {
        public String fullName() { return name + " " + surname; }
        public String shirtName() {
            String upper = surname.toUpperCase(Locale.ROOT);
            // Limite conservador para nao criar nomes de camisa enormes.
            return upper.length() > 16 ? upper.substring(0, 16).strip() : upper;
        }
    }

    public Identity generate(String profile, Random random) {
        String country = chooseCountry(profile, random);
        return new Identity(chooseWeighted(given.get(country), random),
                chooseWeighted(surnames.get(country), random), country);
    }

    private String chooseCountry(String profile, Random random) {
        if (given.containsKey(profile)) return profile;
        if (!Set.of("MIX", "NORDIC", "BALTIC", "ANGLO", "EAST", "ALL").contains(profile)) {
            throw new IllegalArgumentException("Perfil de nomes desconhecido: " + profile);
        }
        if (profile.equals("MIX")) {
            // Preferencia finlandesa definida para o Norteverso; probabilidades configuraveis depois.
            int dice = random.nextInt(100);
            if (dice < 65 && given.containsKey("FI")) return "FI";
            if (dice < 80) return chooseFrom(NORDIC, random);
            if (dice < 90) return chooseFrom(BALTIC, random);
            if (dice < 98) return chooseFrom(ANGLO, random);
            return chooseFrom(EAST, random);
        }
        return switch (profile) {
            case "NORDIC" -> chooseFrom(NORDIC, random);
            case "BALTIC" -> chooseFrom(BALTIC, random);
            case "ANGLO" -> chooseFrom(ANGLO, random);
            case "EAST" -> chooseFrom(EAST, random);
            default -> chooseFrom(given.keySet(), random);
        };
    }

    private String chooseFrom(Set<String> codes, Random random) {
        List<String> countries = codes.stream().filter(given::containsKey).sorted().toList();
        if (countries.isEmpty()) throw new IllegalArgumentException("Sem dados de nomes nessa origem.");
        return countries.get(random.nextInt(countries.size()));
    }

    private static String chooseWeighted(List<Part> list, Random random) {
        if (list == null || list.isEmpty()) throw new IllegalArgumentException("Lista de nomes vazia.");
        int total = list.stream().mapToInt(Part::weight).sum();
        int ticket = random.nextInt(total);
        for (Part part : list) {
            ticket -= part.weight;
            if (ticket < 0) return part.text;
        }
        throw new IllegalStateException("Falha no sorteio de nomes.");
    }
}
