package com.frankstiwrx.norteversostudio.validation;

import com.frankstiwrx.norteversostudio.csv.Csv;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DatabaseValidator {

    public static void inspecionar(Path caminho) throws IOException {

        System.out.println();
        System.out.println("=================================");
        System.out.println("       INSPECAO DE DATABASE      ");
        System.out.println("=================================");

        List<List<String>> registros = Csv.ler(caminho);

        if (registros.isEmpty()) {
            System.out.println("Arquivo CSV vazio.");
            return;
        }

        // Primeira linha: cabecalho
        List<String> cabecalho = registros.get(0);

        int totalColunas = cabecalho.size();
        int totalRegistros = registros.size() - 1;

        System.out.println("Arquivo: " + caminho.getFileName());
        System.out.println("Registros: " + totalRegistros);
        System.out.println("Colunas: " + totalColunas);

        // Exibir nomes das colunas
        System.out.println();
        System.out.println("=== COLUNAS ENCONTRADAS ===");

        for (int i = 0; i < cabecalho.size(); i++) {
            System.out.println((i + 1) + " - " + cabecalho.get(i));
        }

        // Verificar quantidade de campos
        int linhasInvalidas = 0;

        for (int i = 1; i < registros.size(); i++) {

            List<String> registro = registros.get(i);

            if (registro.size() != totalColunas) {

                linhasInvalidas++;

                System.out.println(
                        "Linha " + (i + 1)
                        + ": esperado " + totalColunas
                        + " colunas, encontrado "
                        + registro.size()
                );
            }
        }

        // Procurar uma coluna de identificacao
        int colunaId = -1;

        for (int i = 0; i < cabecalho.size(); i++) {

            String nome = cabecalho.get(i).trim();

            if (nome.equalsIgnoreCase("Id")
                    || nome.equalsIgnoreCase("PlayerID")
                    || nome.equalsIgnoreCase("PlayerId")) {

                colunaId = i;
                break;
            }
        }

        int idsDuplicados = 0;
        int idsVazios = 0;

        if (colunaId >= 0) {

            Set<String> idsEncontrados = new HashSet<>();

            for (int i = 1; i < registros.size(); i++) {

                List<String> registro = registros.get(i);

                if (registro.size() != totalColunas) {
                    continue;
                }

                String id = registro.get(colunaId).trim();

                if (id.isEmpty()) {
                    idsVazios++;
                } else if (!idsEncontrados.add(id)) {
                    idsDuplicados++;
                }
            }

        } else {

            System.out.println();
            System.out.println(
                    "Aviso: coluna de ID nao identificada."
            );
        }

        // Relatorio final
        System.out.println();
        System.out.println("=== RESULTADO DA VALIDACAO ===");

        System.out.println("Linhas invalidas: " + linhasInvalidas);

        if (colunaId >= 0) {
            System.out.println("IDs duplicados: " + idsDuplicados);
            System.out.println("IDs vazios: " + idsVazios);
        }

        if (linhasInvalidas == 0
                && idsDuplicados == 0
                && idsVazios == 0
                && colunaId >= 0) {

            System.out.println();
            System.out.println("VALIDACAO CONCLUIDA COM SUCESSO!");

        } else {

            System.out.println();
            System.out.println("VALIDACAO CONCLUIDA COM AVISOS.");
        }
    }
}