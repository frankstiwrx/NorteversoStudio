package com.frankstiwrx.norteversostudio.generator;

import java.util.Map;

/**
 * Codigos Country conferidos no arquivo de selecoes de Times - PES 2021 - Bin.csv
 * fornecido pelo usuario. Codigos fora da tabela nao sao adivinhados.
 */
public final class PesNationalityCatalog {
    private static final Map<String, String> PES_TO_ISO = Map.ofEntries(
            Map.entry("207", "FI"),   // Finland
            Map.entry("237", "SE"),   // Sweden
            Map.entry("226", "NO"),   // Norway
            Map.entry("203", "DK"),   // Denmark
            Map.entry("213", "IS"),   // Iceland
            Map.entry("205", "EE"),   // Estonia
            Map.entry("217", "LV"),   // Latvia
            Map.entry("219", "LT"),   // Lithuania
            Map.entry("204", "GB"),   // England (origem linguistica)
            Map.entry("232", "GB"),   // Scotland
            Map.entry("241", "GB"),   // Wales
            Map.entry("225", "GB"),   // Northern Ireland
            Map.entry("214", "IE"),   // Ireland
            Map.entry("135", "US"),   // USA
            Map.entry("210", "DE"),   // Germany
            Map.entry("227", "PL"),   // Poland
            Map.entry("230", "RU"),   // Russia
            Map.entry("239", "UA"),   // Ukraine
            Map.entry("202", "CZ"),   // Czech Republic
            Map.entry("234", "SK"),   // Slovakia
            Map.entry("197", "BE"),   // Belgium
            Map.entry("224", "NL"),   // Netherlands
            Map.entry("208", "FR"),   // France
            Map.entry("215", "IT"),   // Italy
            Map.entry("236", "ES")    // Spain
    );
    private PesNationalityCatalog() { }
    public static String nameOriginForCountryCode(String country) {
        return PES_TO_ISO.get(country);
    }
}
