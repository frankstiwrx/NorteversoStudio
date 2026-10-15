package com.frankstiwrx.norteversostudio;

import com.frankstiwrx.norteversostudio.model.Clube;

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
    }
}