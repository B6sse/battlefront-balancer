package no.battlefront.balancer.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("MapNames")
class MapNamesTest {
    @Test
    fun `level paths map to the site's map names`() {
        assertEquals("Dune Sea", MapNames.displayName("Levels/Desert/Desert_04/Desert_04"))
        assertEquals("Goazon Badlands", MapNames.displayName("XP0/Levels/Valley/Valley_02/Valley_02"))
        assertEquals("Raider Camp", MapNames.displayName("Levels/Desert/Desert_13_MP/Desert_13_MP"))
        assertEquals("Twilight on Hoth", MapNames.displayName("Levels/Arctic/Arctic_04/Arctic_04"))
    }

    @Test
    fun `lookup ignores case, surrounding spaces and a leading slash`() {
        assertEquals("Jawa Refuge", MapNames.displayName(" /levels/desert/desert_02/DESERT_02 "))
    }

    @Test
    fun `known map names are normalised to the site's spelling`() {
        assertEquals("Swamp Crash Site", MapNames.displayName("SWAMP CRASH SITE"))
    }

    @Test
    fun `unknown paths are kept as they are`() {
        assertEquals(
            "XP_Offline/Levels/Arctic/Arctic_01_Offline/Arctic_01_Offline",
            MapNames.displayName("XP_Offline/Levels/Arctic/Arctic_01_Offline/Arctic_01_Offline"),
        )
    }
}
