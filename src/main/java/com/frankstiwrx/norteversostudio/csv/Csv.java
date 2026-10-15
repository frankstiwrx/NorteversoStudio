package com.frankstiwrx.norteversostudio.csv;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class Csv {

    private Csv() {
        // Classe utilitária: não precisa ser instanciada.
    }

    public static List<List<String>> ler(Path caminho) throws IOException {

        String conteudo = Files.readString(
                caminho,
                StandardCharsets.UTF_8
        );

        List<List<String>> registros = new ArrayList<>();
        List<String> registroAtual = new ArrayList<>();

        StringBuilder campo = new StringBuilder();

        boolean dentroDeAspas = false;

        for (int i = 0; i < conteudo.length(); i++) {

            char caractere = conteudo.charAt(i);

            if (caractere == '"') {

                if (dentroDeAspas
                        && i + 1 < conteudo.length()
                        && conteudo.charAt(i + 1) == '"') {

                    campo.append('"');
                    i++;

                } else {
                    dentroDeAspas = !dentroDeAspas;
                }

            } else if (caractere == ';' && !dentroDeAspas) {

                registroAtual.add(campo.toString());
                campo.setLength(0);

            } else if ((caractere == '\n' || caractere == '\r')
                    && !dentroDeAspas) {

                registroAtual.add(campo.toString());
                campo.setLength(0);

                registros.add(registroAtual);
                registroAtual = new ArrayList<>();

                if (caractere == '\r'
                        && i + 1 < conteudo.length()
                        && conteudo.charAt(i + 1) == '\n') {
                    i++;
                }

            } else {
                campo.append(caractere);
            }
        }

        if (dentroDeAspas) {
            throw new IOException("CSV inválido: aspas não fechadas.");
        }

        if (!registroAtual.isEmpty() || campo.length() > 0) {

            registroAtual.add(campo.toString());
            registros.add(registroAtual);
        }

        return registros;
    }
}