package no.battlefront.balancer.service

/**
 * Translates the game's level paths (as reported by Auric) to the map names the site uses.
 *
 * Names follow the site's existing style (title case, and "Dune Sea" for Dune Sea Exchange) so uploaded matches
 * line up with older matches and the randomizer weights.
 */
object MapNames {
    private val byPath =
        mapOf(
            "Levels/Arctic/Arctic_01/Arctic_01" to "Outpost Beta",
            "Levels/Arctic/Arctic_02/Arctic_02" to "Ice Caves",
            "Levels/Arctic/Arctic_03/Arctic_03" to "Rebel Base",
            "Levels/Arctic/Arctic_04/Arctic_04" to "Twilight on Hoth",
            "Levels/Desert/Desert_01/Desert_01" to "Jundland Wastes",
            "Levels/Desert/Desert_02/Desert_02" to "Jawa Refuge",
            "Levels/Desert/Desert_03/Desert_03" to "Rebel Depot",
            "Levels/Desert/Desert_04/Desert_04" to "Dune Sea",
            "Levels/Desert/Desert_13_MP/Desert_13_MP" to "Raider Camp",
            "Levels/Forest/Forest_01/Forest_01" to "Forest Moon of Endor",
            "Levels/Forest/Forest_02/Forest_02" to "Swamp Crash Site",
            "Levels/Forest/Forest_03/Forest_03" to "Imperial Station",
            "Levels/Forest/Forest_06/Forest_06" to "Survivors of Endor",
            "Levels/Volcanic/Volcanic_01/Volcanic_01" to "SoroSuub Centroplex",
            "Levels/Volcanic/Volcanic_02/Volcanic_02" to "Sulfur Fields",
            "Levels/Volcanic/Volcanic_03/Volcanic_03" to "Imperial Hangar",
            "XP0/Levels/Valley/Valley_01/Valley_01" to "Graveyard of Giants",
            "XP0/Levels/Valley/Valley_02/Valley_02" to "Goazon Badlands",
            "XP1/Levels/Industrial_01/Industrial_01" to "SoroSuub Refinery",
            "XP1/Levels/Industrial_02/Industrial_02" to "SoroSuub Pipelines",
            "XP1/Levels/Palace_01/Palace_01" to "Jabba's Palace",
            "XP1/Levels/Palace_02/Palace_02" to "Palace Garage",
            "XP2/Levels/Clouds/Clouds_01/Clouds_01" to "Cloud City",
            "XP2/Levels/Clouds/Clouds_02/Clouds_02" to "Administrator's Palace",
            "XP2/Levels/Clouds/Clouds_03/Clouds_03" to "BioNiip Laboratories",
            "XP2/Levels/Clouds/Clouds_04/Clouds_04" to "Carbonite-Freezing Chambers",
            "XP2/Levels/Clouds/Clouds_05/Clouds_05" to "Bespin Airspace",
            "XP3/Levels/Space/Space_01/Space_01" to "Defense Sector",
            "XP3/Levels/Space/Space_02/Space_02" to "Power Sector",
            "XP3/Levels/Space/Space_03/Space_03" to "Command Sector",
            "XP4/Levels/Beach/Beach_01/Beach_01" to "Scarif Beach",
            "XP4/Levels/Beach/Beach_02/Beach_02" to "Landing Pad 13",
            "XP4/Levels/Beach/Beach_03/Beach_03" to "Scarif Jungle",
        ).mapKeys { it.key.lowercase() }

    private val byName = byPath.values.associateBy { it.lowercase() }

    /**
     * Returns the site's name for a level path (case-insensitive). A known map name is returned in the site's
     * spelling; anything else is returned trimmed and unchanged.
     */
    fun displayName(pathOrName: String): String {
        val key = pathOrName.trim().trimStart('/').lowercase()
        return byPath[key] ?: byName[key] ?: pathOrName.trim()
    }
}
