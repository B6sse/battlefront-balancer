package no.battlefront.balancer.dto

data class RandomizerWeightDto(
    val id: Int,
    val type: String,
    val name: String,
    val weight: Int,
)

data class RandomizerWeightsResponse(
    val maps: List<RandomizerWeightDto>,
    val rules: List<RandomizerWeightDto>,
)

data class RandomizerWeightUpdateRequest(
    val id: Int,
    val weight: Int,
)

data class RandomizerWeightsUpdateRequest(
    val weights: List<RandomizerWeightUpdateRequest>,
)
