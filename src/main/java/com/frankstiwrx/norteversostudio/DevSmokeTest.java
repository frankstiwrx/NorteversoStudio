package com.frankstiwrx.norteversostudio;

import com.frankstiwrx.norteversostudio.model.Clube;
import com.frankstiwrx.norteversostudio.csv.Csv;
import com.frankstiwrx.norteversostudio.validation.DatabaseValidator;
import java.io.IOException;
import java.nio.file.Path;

/** Preserva os testes manuais da versão console, sem executá-los ao abrir o editor. */
public class DevSmokeTest {
    public static void main(String[] args) {
        System.out.println("=== TESTES DE DESENVOLVIMENTO ===");
        Clube norte44 = new Clube(1208, "Norte44", "Norte44", "N44", 1,
                "Potência Nacional", "4-3-3", 30, 94, 95);
        System.out.println(norte44);
        try {
            var registros = Csv.ler(Path.of("dados/teste.csv"));
            Path destino = Path.of("saida/teste_gerado.csv");
            Csv.gravar(destino, registros);
            if (!registros.equals(Csv.ler(destino))) {
                throw new IllegalStateException("Diferença após leitura/escrita do CSV");
            }
            System.out.println("CSV de teste preservado corretamente.");
            DatabaseValidator.inspecionar(Path.of("dados/testeinvalido.csv"));
        } catch (IOException e) {
            System.err.println("Erro no teste: " + e.getMessage());
        }
    }
}
