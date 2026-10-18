package com.frankstiwrx.norteversostudio.generator;

import com.frankstiwrx.norteversostudio.csv.Csv;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Protecao EXCLUSIVA contra geracao automatica (edicao manual continua liberada).
 * Lista persistida fora dos CSVs do PES; protege o ID, independentemente de clube.
 */
public final class ProtectionRegistry {
    private final Path path;
    private final Map<String, String> protectedNames = new LinkedHashMap<>();

    private ProtectionRegistry(Path path) {
        this.path = path.toAbsolutePath().normalize();
    }

    public static ProtectionRegistry load(Path file) throws IOException {
        ProtectionRegistry result = new ProtectionRegistry(file);
        if (!Files.exists(result.path)) return result;
        List<List<String>> rows = Csv.ler(result.path);
        if (rows.isEmpty() || rows.get(0).size() != 2
                || !rows.get(0).get(0).replace("\uFEFF", "").equals("IdPlayer")
                || !rows.get(0).get(1).equals("NomeReferencia")) {
            throw new IOException("Arquivo de protecao invalido: esperado IdPlayer;NomeReferencia");
        }
        for (int i = 1; i < rows.size(); i++) {
            List<String> row = rows.get(i);
            if (row.size() != 2 || !row.get(0).matches("[0-9]+")
                    || result.protectedNames.putIfAbsent(row.get(0), row.get(1)) != null) {
                throw new IOException("Linha invalida/duplicada em " + file + ": " + (i + 1));
            }
        }
        return result;
    }

    public Path file() { return path; }
    public int size() { return protectedNames.size(); }
    public boolean isProtected(String playerId) { return protectedNames.containsKey(playerId); }
    public Set<String> ids() { return Set.copyOf(protectedNames.keySet()); }
    public String originalName(String id) { return protectedNames.get(id); }

    public void setProtected(String id, String name, boolean value) {
        if (id == null || !id.matches("[0-9]+")) throw new IllegalArgumentException("ID invalido: " + id);
        if (value) protectedNames.putIfAbsent(id, name == null ? "" : name);
        else protectedNames.remove(id);
    }

    /** Escrita atomica onde suportada; nao apaga o arquivo anterior em caso de erro. */
    public void save() throws IOException {
        Path folder = path.getParent();
        if (folder != null) Files.createDirectories(folder);
        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of("IdPlayer", "NomeReferencia"));
        protectedNames.forEach((id, name) -> rows.add(List.of(id, name)));
        Path temporary = Files.createTempFile(folder, "nv_protection_", ".tmp");
        try {
            Csv.gravar(temporary, rows);
            try {
                Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
