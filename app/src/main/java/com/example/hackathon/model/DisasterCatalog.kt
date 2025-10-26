package com.example.hackathon.model

/**
 * Shared catalog of disaster categories with stable integer codes.
 * Codes are grouped by domain:
 * - 100s: Health/Medical
 * - 200s: Supplies
 * - 300s: Technical/Rescue
 */

data class DisasterItem(val code: Int, val label: String)

object DisasterCatalog {
    val items: List<DisasterItem> = listOf(
        // Health (100s)
        DisasterItem(101, "Medizinische Hilfe – Arzt benötigt"),
        DisasterItem(102, "Medizinische Hilfe – Erste Hilfe"),
        DisasterItem(103, "Schwerverletzte/r"),
        DisasterItem(104, "Bewusstlosigkeit"),
        DisasterItem(105, "Atemprobleme"),
        DisasterItem(106, "Herzbeschwerden"),
        DisasterItem(107, "Starke Blutung"),
        DisasterItem(108, "Verbrennung"),
        DisasterItem(109, "Knochenbruch"),
        DisasterItem(110, "Unterkühlung"),
        DisasterItem(111, "Dehydrierung"),
        DisasterItem(112, "Schwangerschaft/Entbindung"),
        DisasterItem(113, "Kind – medizinische Hilfe"),
        DisasterItem(114, "Psychologische Hilfe"),
        DisasterItem(115, "Medikamente – Insulin"),
        DisasterItem(116, "Medikamente – Antibiotika"),
        DisasterItem(117, "Medikamente – Schmerzmittel"),
        DisasterItem(118, "Allergische Reaktion"),
        DisasterItem(119, "Wundversorgung"),
        // Supplies (200s)
        DisasterItem(201, "Nahrungsmittel"),
        DisasterItem(202, "Trinkwasser"),
        DisasterItem(203, "Babynahrung"),
        DisasterItem(204, "Hygieneartikel"),
        DisasterItem(205, "Decken/Schlafsäcke"),
        DisasterItem(206, "Kleidung"),
        DisasterItem(207, "Strom/Powerbank"),
        DisasterItem(208, "Kommunikationshilfe (Funk/Telefon)"),
        DisasterItem(209, "Unterkunft/Schutz"),
        // Technical/Rescue (300s)
        DisasterItem(301, "Technische Hilfsgüter"),
        DisasterItem(302, "Bergeausrüstung"),
        DisasterItem(303, "Räumung – Wege freimachen"),
        DisasterItem(304, "Transport/Fahrzeug erforderlich"),
        DisasterItem(305, "Feuer/Brandmeldung"),
        DisasterItem(306, "Gasleck/Gefahrstoff"),
        DisasterItem(307, "Such- und Rettungstrupp"),
        DisasterItem(308, "Evakuierung nötig"),
        DisasterItem(309, "Tierhilfe")
    )

    private val codeToLabel: Map<Int, String> = items.associate { it.code to it.label }

    fun hasCode(code: Int): Boolean = codeToLabel.containsKey(code)

    /**
     * If [text] is an integer code present in the catalog, returns the corresponding label.
     * Otherwise returns [text] unchanged.
     */
    fun labelForText(text: String): String {
        val code = text.toIntOrNull() ?: return text
        return codeToLabel[code] ?: text
    }
}
