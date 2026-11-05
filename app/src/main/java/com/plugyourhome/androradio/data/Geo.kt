package com.plugyourhome.androradio.data

import androidx.annotation.StringRes
import com.plugyourhome.androradio.R

/**
 * Continents du tuner « Tour du monde ». Radio Browser ne donne pas le continent d'une station :
 * on le déduit du code pays ISO 3166-1 avec cette table.
 */
enum class Continent(@StringRes val label: Int, codes: String) {
    EUROPE(
        R.string.continent_europe,
        "AD AL AT AX BA BE BG BY CH CY CZ DE DK EE ES FI FO FR GB GG GI GR HR HU IE IM IS IT JE LI LT LU LV " +
            "MC MD ME MK MT NL NO PL PT RO RS RU SE SI SJ SK SM UA VA XK",
    ),
    AFRICA(
        R.string.continent_africa,
        "AO BF BI BJ BW CD CF CG CI CM CV DJ DZ EG EH ER ET GA GH GM GN GQ GW KE KM LR LS LY MA MG ML MR MU " +
            "MW MZ NA NE NG RE RW SC SD SH SL SN SO SS ST SZ TD TG TN TZ UG YT ZA ZM ZW",
    ),
    ASIA(
        R.string.continent_asia,
        "AE AF AM AZ BD BH BN BT CN GE HK ID IL IN IQ IR JO JP KG KH KP KR KW KZ LA LB LK MM MN MO MV MY NP " +
            "OM PH PK PS QA SA SG SY TH TJ TL TM TR TW UZ VN YE",
    ),
    NORTH_AMERICA(
        R.string.continent_north_america,
        "AG AI AW BB BL BM BQ BS BZ CA CR CU CW DM DO GD GL GP GT HN HT JM KN KY LC MF MQ MS MX NI PA PM PR " +
            "SV SX TC TT US VC VG VI",
    ),
    SOUTH_AMERICA(R.string.continent_south_america, "AR BO BR CL CO EC FK GF GY PE PY SR UY VE"),
    OCEANIA(
        R.string.continent_oceania,
        "AS AU CK FJ FM GU KI MH MP NC NF NR NU NZ PF PG PN PW SB TK TO TV UM VU WF WS",
    );

    val countryCodes: Set<String> = codes.split(' ').filter { it.isNotBlank() }.toSet()

    companion object {
        /** Continent d'un pays (code à 2 lettres), null si inconnu. */
        fun of(countryCode: String): Continent? {
            val code = countryCode.trim().uppercase()
            return entries.firstOrNull { code in it.countryCodes }
        }
    }
}

/** Un pays du tuner, avec son nombre de stations dans l'annuaire. */
data class GeoCountry(val code: String, val name: String, val stations: Int)

/** Une région / ville du tuner ; key null = « Toutes ». */
data class GeoRegion(val key: String?, val name: String, val stations: Int)

/**
 * Regroupe les stations d'un pays par région (champ « state » de Radio Browser, saisi librement :
 * ville, département ou région selon les contributeurs). Les variantes d'écriture
 * (« nice », « Nice ») sont fusionnées ; l'orthographe gardée est celle de la station la plus écoutée.
 * Les régions sont triées par nombre de stations.
 */
fun List<Station>.regions(): List<GeoRegion> {
    data class Acc(val name: String, var count: Int)
    val groups = LinkedHashMap<String, Acc>()
    forEach { s ->
        val name = s.state.trim().replace(Regex("\\s+"), " ")
        if (name.isEmpty()) return@forEach
        groups.getOrPut(regionKey(name)) { Acc(name, 0) }.count++
    }
    return groups.map { (key, acc) -> GeoRegion(key, acc.name, acc.count) }
        .sortedWith(compareByDescending<GeoRegion> { it.stations }.thenBy { it.name.lowercase() })
}

fun regionKey(state: String): String = state.trim().replace(Regex("\\s+"), " ").lowercase()
