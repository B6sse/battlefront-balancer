package no.battlefront.balancer.service

import no.battlefront.balancer.dto.RandomizerDto
import no.battlefront.balancer.model.Randomizer
import no.battlefront.balancer.repository.RandomizerRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock

class RandomizerServiceTest {

    private val randomizerRepository: RandomizerRepository = mock(RandomizerRepository::class.java)
    private val service = RandomizerService(randomizerRepository)

    @Test
    fun `getLatest returns default when repository is empty`() {
        // arrange
        `when`(randomizerRepository.findTop1ByOrderByIdDesc()).thenReturn(null)

        // act
        val result: RandomizerDto = service.getLatest()

        // assert
        assertEquals("Dune Sea", result.map)
        assertEquals("DSE", result.rule)
    }

    @Test
    fun `getLatest returns latest entry from repository`() {
        // arrange
        val entity = Randomizer(id = 42L, map = "Endor", rule = "E1")
        `when`(randomizerRepository.findTop1ByOrderByIdDesc()).thenReturn(entity)

        // act
        val result: RandomizerDto = service.getLatest()

        // assert
        assertEquals("Endor", result.map)
        assertEquals("E1", result.rule)
    }
}