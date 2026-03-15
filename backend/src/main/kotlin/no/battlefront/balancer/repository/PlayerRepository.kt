package no.battlefront.balancer.repository

import no.battlefront.balancer.model.Player
import org.springframework.data.jpa.repository.JpaRepository

interface PlayerRepository : JpaRepository<Player, Long>
