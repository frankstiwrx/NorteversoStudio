package com.frankstiwrx.norteversostudio.data;

import java.util.ArrayList;
import java.util.List;

/**
 * Codigos verificados com a base CSV do usuario e a tela do Ejogc327.
 * Atenção: a ordem deste exportador nao coincide com a ordem do Edit File PES21.
 * Ver README_ETAPA04.md para evidencias e ressalvas.
 */
public final class PlayingStyleCatalog {
    public record Choice(int code, String portuguese, String english) {
        @Override public String toString() {
            return code + " - " + portuguese + (english.isBlank() ? "" : " (" + english + ")");
        }
    }
    private static final List<Choice> STYLES = List.of(
            new Choice(0, "Nenhum", "None"),
            new Choice(1, "Artilheiro", "Goal Poacher"),
            new Choice(2, "Isca", "Dummy Runner"),
            new Choice(3, "Raposa da area", "Fox in the Box"),
            new Choice(4, "Ponta prolifico", "Prolific Winger"),
            new Choice(5, "Camisa 10 classico", "Classic No. 10"),
            new Choice(6, "Jogador de infiltracao", "Hole Player"),
            new Choice(7, "Meia versatil", "Box-to-Box"),
            new Choice(8, "Primeiro volante", "Anchor Man"),
            new Choice(9, "Destruidor", "The Destroyer"),
            new Choice(10, "Zagueiro que sobe", "Extra Frontman"),
            new Choice(11, "Lateral ofensivo", "Offensive Full-back"),
            new Choice(12, "Lateral defensivo", "Defensive Full-back"),
            new Choice(13, "Homem de referencia", "Target Man"),
            new Choice(14, "Armador criativo", "Creative Playmaker"),
            new Choice(15, "Construtor de jogadas", "Build Up"),
            new Choice(16, "Goleiro ofensivo", "Offensive Goalkeeper"),
            new Choice(17, "Goleiro defensivo", "Defensive Goalkeeper"),
            new Choice(18, "Ponta que corta para dentro", "Roaming Flank"),
            new Choice(19, "Especialista em cruzamentos", "Cross Specialist"),
            new Choice(20, "Orquestrador", "Orchestrator"),
            new Choice(21, "Lateral finalizador", "Full-back Finisher")
    );
    private PlayingStyleCatalog() {}
    public static List<Choice> all() { return STYLES; }
    public static Choice byCode(int code) {
        return STYLES.stream().filter(s -> s.code() == code).findFirst()
                .orElse(new Choice(code, "Codigo desconhecido", ""));
    }
}
