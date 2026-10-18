package com.frankstiwrx.norteversostudio.generator;

import java.util.Locale;

/** Mantem nomes exibidos no PES em caixa alta, sem perder diacriticos. */
public final class PlayerNameFormat {
    private PlayerNameFormat() { }

    public static String upper(String name) {
        return name == null ? "" : name.strip().toUpperCase(Locale.ROOT);
    }
}
