package no.battlefront.balancer.service

import no.battlefront.balancer.dto.RandomizerDto
import no.battlefront.balancer.dto.RandomizerWeightDto
import no.battlefront.balancer.dto.RandomizerWeightsResponse
import no.battlefront.balancer.dto.RandomizerWeightsUpdateRequest
import no.battlefront.balancer.model.Randomizer
import no.battlefront.balancer.model.RandomizerWeight
import no.battlefront.balancer.repository.RandomizerRepository
import no.battlefront.balancer.repository.RandomizerWeightRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import kotlin.random.Random

@Service
class RandomizerService(
    private val randomizerRepository: RandomizerRepository,
    private val weightRepository: RandomizerWeightRepository,
) {
    fun save(
        map: String,
        rule: String,
    ): Randomizer {
        val entity = Randomizer(map = map, rule = rule)
        return randomizerRepository.save(entity)
    }

    fun getLatest(): RandomizerDto {
        val r = randomizerRepository.findTop1ByOrderByIdDesc()
        return if (r != null) {
            RandomizerDto(map = r.map, rule = r.rule)
        } else {
            RandomizerDto(map = "Dune Sea", rule = "DSE")
        }
    }

    fun getWeights(): RandomizerWeightsResponse {
        val maps = weightRepository.findByTypeOrderByIdAsc("map").map { it.toDto() }
        val rules = weightRepository.findByTypeOrderByIdAsc("rule").map { it.toDto() }
        return RandomizerWeightsResponse(maps = maps, rules = rules)
    }

    @Transactional
    fun updateWeights(request: RandomizerWeightsUpdateRequest) {
        for (update in request.weights) {
            require(update.weight >= 0) { "Weight must be >= 0" }
            val entity =
                weightRepository.findById(update.id).orElseThrow {
                    IllegalArgumentException("Weight id=${update.id} not found")
                }
            entity.weight = update.weight
            weightRepository.save(entity)
        }
    }

    /**
     * Picks a random map and rule based on DB weights, saves the result to the randomizer table,
     * and returns the chosen pair.
     * Falls back to hardcoded defaults if the weights table is empty.
     */
    @Transactional
    fun pickAndSave(): RandomizerDto {
        val maps = weightRepository.findByTypeOrderByIdAsc("map")
        val rules = weightRepository.findByTypeOrderByIdAsc("rule")
        val map = if (maps.any { it.weight > 0 }) selectRandom(maps) else "Dune Sea"
        val rule = if (rules.any { it.weight > 0 }) selectRandom(rules) else "DSE"
        save(map, rule)
        return RandomizerDto(map = map, rule = rule)
    }

    private fun selectRandom(items: List<RandomizerWeight>): String {
        val total = items.sumOf { it.weight }
        var r = Random.nextInt(total)
        for (item in items) {
            r -= item.weight
            if (r < 0) return item.name
        }
        return items.last().name
    }

    private fun RandomizerWeight.toDto() = RandomizerWeightDto(id = id, type = type, name = name, weight = weight)
}
