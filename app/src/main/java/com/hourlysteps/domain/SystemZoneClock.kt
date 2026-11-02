package com.hourlysteps.domain

import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/**
 * Follows the device's current time zone. Clock.systemDefaultZone() captures the zone once, so a
 * long-running process would keep using the old zone after the wearer travels.
 */
class SystemZoneClock : Clock() {
    override fun getZone(): ZoneId = ZoneId.systemDefault()
    override fun withZone(zone: ZoneId): Clock = system(zone)
    override fun instant(): Instant = Instant.now()
}
