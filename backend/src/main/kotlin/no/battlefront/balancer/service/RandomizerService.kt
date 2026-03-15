package no.battlefront.balancer.service

import no.battlefront.balancer.dto.RandomizerDto
import no.battlefront.balancer.repository.RandomizerRepository
import org.springframework.stereotype.Service

@Service
class RandomizerService(private val randomizerRepository: RandomizerRepository) {

    /**
     * Siste randomizer (map/rule). Default som i gammel api_randomizer.php ved tom tabell.
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
