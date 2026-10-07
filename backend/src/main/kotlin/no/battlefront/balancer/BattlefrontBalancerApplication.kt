package no.battlefront.balancer

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import java.util.TimeZone

@SpringBootApplication
class BattlefrontBalancerApplication

/** Match dates are stored as local time without a zone; keep them in Norwegian time wherever the server runs. */
const val APP_TIME_ZONE = "Europe/Oslo"

fun main(args: Array<String>) {
    TimeZone.setDefault(TimeZone.getTimeZone(APP_TIME_ZONE))
    runApplication<BattlefrontBalancerApplication>(*args)
}
