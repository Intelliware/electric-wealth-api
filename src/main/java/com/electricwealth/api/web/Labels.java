package com.electricwealth.api.web;

import java.util.Map;

import com.electricwealth.api.dto.ApiEnums.Language;

/** Server-side labels (e.g. dashboard quick links) are localized from Accept-Language. */
final class Labels {

    private static final Map<String, String> FRENCH = Map.ofEntries(
            Map.entry("Open account", "Ouvrir un compte"),
            Map.entry("Documents", "Documents"),
            Map.entry("Research", "Recherche"),
            Map.entry("Book advisor", "Prendre rendez-vous"),
            Map.entry("Call advisor", "Appeler votre conseiller"),
            Map.entry("Tax documents", "Documents fiscaux"),
            Map.entry("RESP contributions", "Cotisations REEE"),
            Map.entry("Start investing", "Commencer à investir"),
            Map.entry("Learn basics", "Apprendre les bases"),
            Map.entry("Advanced analytics", "Analyses avancées"),
            Map.entry("Private markets", "Marchés privés"));

    private Labels() {
    }

    static String localize(String label, Language language) {
        return language == Language.FR_CA ? FRENCH.getOrDefault(label, label) : label;
    }
}
