package com.frankstiwrx.norteversostudio;

import com.frankstiwrx.norteversostudio.csv.Csv;
import com.frankstiwrx.norteversostudio.data.TacticsFields;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Teste autonomo: java ...TacticsFieldsSmokeTest caminho_para_Taticas.csv */
public final class TacticsFieldsSmokeTest {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Informe o caminho para Táticas - PES 2021 - Bin.csv");
        }
        List<List<String>> csv = Csv.ler(Path.of(args[0]));
        List<String> header = csv.get(0);
        Map<String, Integer> index = new HashMap<>();
        for (int i = 0; i < header.size(); i++) index.put(header.get(i).replace("\uFEFF", ""), i);
        List<String> row = csv.get(1);
        String originalId = row.get(index.get("Id"));
        String originalTeam = row.get(index.get("IdTeam"));
        String originalA = row.get(index.get("A"));
        int before = Integer.parseInt(row.get(index.get("SupportRange")));
        int next = before == 10 ? 9 : before + 1;
        if (!TacticsFields.apply(index, row, Map.of("SupportRange", next))) {
            throw new AssertionError("Esperada alteracao de suporte");
        }
        if (!row.get(index.get("SupportRange")).equals(Integer.toString(next))) {
            throw new AssertionError("Ajuste nao aplicado");
        }
        if (!row.get(index.get("Id")).equals(originalId)
                || !row.get(index.get("IdTeam")).equals(originalTeam)
                || !row.get(index.get("A")).equals(originalA)) {
            throw new AssertionError("Colunas protegidas alteradas");
        }
        boolean invalidRejected = false;
        try { TacticsFields.apply(index, row, Map.of("SupportRange", 99)); }
        catch (IllegalArgumentException ex) { invalidRejected = true; }
        if (!invalidRejected) throw new AssertionError("Valor fora do intervalo aceito");
        Path temp = Files.createTempFile("nv-taticas-", ".csv");
        try {
            Csv.gravar(temp, csv);
            var imported = Csv.ler(temp);
            if (imported.size() != csv.size() || !imported.get(1).equals(row)) {
                throw new AssertionError("Round-trip dos CSVs falhou");
            }
        } finally { Files.deleteIfExists(temp); }
        System.out.println("OK: " + (csv.size()-1) + " táticas lidas, valores e IDs verificados, CSV salvo e relido.");
    }
}
