package no.battlefront.balancer.service

import no.battlefront.balancer.dto.RandomizerDto
import no.battlefront.balancer.model.Randomizer
import no.battlefront.balancer.repository.RandomizerRepository
import org.springframework.stereotype.Service

@Service
class RandomizerService(
    private val randomizerRepository: RandomizerRepository,
) {
    /**
     * Saves a new randomizer entry.
     *
     * @param map the map name.
     * @param rule the rule code.
     * @return the persisted [Randomizer] entity.
     */
    fun save(
        map: String,
        rule: String,
    ): Randomizer {
        val entity = Randomizer(map = map, rule = rule)
        return randomizerRepository.save(entity)
    }

    /**
     * Returns the latest randomizer (map and rule). Defaults to "Dune Sea" / "DSE" when the table is empty.
     *
     * @return [RandomizerDto] with map and rule; never null.
     */
    fun getLatest(): RandomizerDto {
        val r = randomizerRepository.findTop1ByOrderByIdDesc()
        return if (r != null) {
            RandomizerDto(map = r.map, rule = r.rule)
        } else {
            RandomizerDto(map = "Dune Sea", rule = "DSE")
        }
    }
}
