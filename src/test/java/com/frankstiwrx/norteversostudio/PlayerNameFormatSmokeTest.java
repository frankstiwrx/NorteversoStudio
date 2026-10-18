package com.frankstiwrx.norteversostudio;

import com.frankstiwrx.norteversostudio.generator.PlayerNameFormat;

public final class PlayerNameFormatSmokeTest {
    public static void main(String[] args) {
        assertEqual("MARTIN SILVA", PlayerNameFormat.upper("Martin Silva"));
        assertEqual("RÚRIK FREYSSON", PlayerNameFormat.upper("Rúrik Freysson"));
        assertEqual("KIM WIKSTRÖM", PlayerNameFormat.upper(" Kim Wikström "));
        assertEqual("DOVYDAS ČERNIAUSKAS", PlayerNameFormat.upper("Dovydas Černiauskas"));
        assertEqual("PALLE MØLLER", PlayerNameFormat.upper("Palle Møller"));
        System.out.println("Nomes em caixa alta: OK");
    }
    private static void assertEqual(String expected, String value) {
        if (!expected.equals(value)) throw new AssertionError(expected + " != " + value);
    }
}
