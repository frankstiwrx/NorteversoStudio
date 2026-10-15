package com.frankstiwrx.norteversostudio;

import com.frankstiwrx.norteversostudio.model.Clube;
import com.frankstiwrx.norteversostudio.csv.Csv;
import java.nio.file.Path;
import java.io.IOException;

public class NorteversoStudio {

    public static void main(String[] args) {

        System.out.println("=================================");
        System.out.println("       NORTEVERSO STUDIO         ");
        System.out.println("=================================");

        Clube norte44 = new Clube(
                1208,
                "Norte44",
                "Norte44",
                "N44",
                1,
                "Potência Nacional",
                "4-3-3",
                30,
                94,
                95
        );

        System.out.println();
        System.out.println(norte44);
        
        System.out.println();
System.out.println("=== TESTE DE LEITURA CSV ===");

try {

    var registros = Csv.ler(Path.of("dados/teste.csv"));

    for (var registro : registros) {
        System.out.println(registro);
    }

    System.out.println();
System.out.println("=== TESTE DE ESCRITA CSV ===");

Path destino = Path.of("saida/teste_gerado.csv");

Csv.gravar(destino, registros);

var registrosGerados = Csv.ler(destino);

if (registros.equals(registrosGerados)) {

    System.out.println("VALIDAÇÃO: CSV preservado corretamente!");

} else {

    System.err.println("ERRO: O CSV gerado possui diferenças!");

}

System.out.println("Arquivo gerado com sucesso!");
System.out.println("Destino: " + destino.toAbsolutePath());
    
} catch (IOException e) {

    System.err.println(
            "Erro ao ler CSV: " + e.getMessage()
    );
}
    }
}