package no.battlefront.balancer

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class BattlefrontBalancerApplication

fun main(args: Array<String>) {
    runApplication<BattlefrontBalancerApplication>(*args)
}
