package no.battlefront.balancer.dto

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("Dto")
class PlayerMatchStatDtoTest {
    @Test
    fun `PlayerMatchStatDto constructor and JSON uses NewBR`() {
        val dto =
            PlayerMatchStatDto(
                id = 10L,
                faction = "Rebel",
                outcome = "Won",
                score = 150,
                perf = 1.25,
                change = 25,
                newBR = 900,
            )

        // Plain Kotlin usage coverage: constructor + property access
        assertEquals(10L, dto.id)
        assertEquals("Rebel", dto.faction)
        assertEquals("Won", dto.outcome)
        assertEquals(150, dto.score)
        assertEquals(1.25, dto.perf)
        assertEquals(25, dto.change)
        assertEquals(900, dto.newBR)

        // Jackson usage coverage: ensure @JsonProperty("NewBR") is honoured
        val mapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
        val json = mapper.writeValueAsString(dto)

        assertTrue(json.contains("\"NewBR\""))
        assertFalse(json.contains("\"newBR\""))

        val roundTripped = mapper.readValue(json, PlayerMatchStatDto::class.java)
        assertEquals(900, roundTripped.newBR)
    }
}
