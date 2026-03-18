package no.battlefront.balancer.service

import no.battlefront.balancer.dto.RandomizerDto
import no.battlefront.balancer.model.Randomizer
import no.battlefront.balancer.repository.RandomizerRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

/**
 * JUnit test class for [RandomizerService].
 */
@Tag("RandomizerService")
class RandomizerServiceTest {
    private val randomizerRepository: RandomizerRepository = mock(RandomizerRepository::class.java)
    private val service = RandomizerService(randomizerRepository)

    /**
     * Test that getLatest returns the default map and rule when the repository is empty.
     *
     * When no randomizer entry exists, the service should return "Dune Sea" and "DSE".
     *
     * 1. Arrange: Mock repository to return null.
     * 2. Act: Call getLatest().
     * 3. Assert: Verify map is "Dune Sea" and rule is "DSE".
     */
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

    /**
     * Test that getLatest returns the most recent randomizer entry from the repository.
     *
     * When the repository has at least one entry, the service should return its map and rule.
     *
     * 1. Arrange: Mock repository to return a [Randomizer] entity.
     * 2. Act: Call getLatest().
     * 3. Assert: Verify returned DTO matches the entity map and rule.
     */
    @Test
    fun `getLatest returns latest entry from repository`() {
        // arrange
        val entity = Randomizer(id = 42L, map = "Imperial Hangar", rule = "DSE")
        `when`(randomizerRepository.findTop1ByOrderByIdDesc()).thenReturn(entity)

        // act
        val result: RandomizerDto = service.getLatest()

        // assert
        assertEquals("Imperial Hangar", result.map)
        assertEquals("DSE", result.rule)
    }

    /**
     * Test that save persists a new randomizer and returns the saved entity.
     *
     * Saving a map and rule should delegate to the repository and return the persisted entity.
     *
     * 1. Arrange: Mock repository save to return an entity.
     * 2. Act: Call save(map, rule).
     * 3. Assert: Verify returned entity has correct id, map and rule.
     */
    @Test
    fun `save persists entity and returns saved randomizer`() {
        val entity = Randomizer(id = 1L, map = "Dune Sea", rule = "DSE")
        `when`(randomizerRepository.save(any(Randomizer::class.java))).thenReturn(entity)

        val result = service.save("Dune Sea", "DSE")

        assertEquals(1L, result.id)
        assertEquals("Dune Sea", result.map)
        assertEquals("DSE", result.rule)
    }
}
