package no.battlefront.balancer.dto

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule

@Tag("Dto")
class BalanceDtosTest {
    private val mapper = JsonMapper.builder().addModule(KotlinModule.Builder().build()).build()

    @Test
    fun `BalanceRequest defaults ratingKey to br when omitted`() {
        val request = mapper.readValue("""{"personaIds":[1,2]}""", BalanceRequest::class.java)

        assertEquals("br", request.ratingKey)
        assertEquals(listOf(1L, 2L), request.personaIds)
    }
}
