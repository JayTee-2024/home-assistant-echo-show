package net.badacid.homeassist.echoshow.devices.sensor

import net.badacid.homeassist.echoshow.core.EntityState
import net.badacid.homeassist.echoshow.core.readText
import net.badacid.homeassist.echoshow.devices.UNAVAILABLE
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeParseException

data class SensorView(
    val reading: String,
    /** null when the sensor has no unit, or has no reading to put one on. */
    val unit: String?,
)

private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

/** A date-and-time reading is shown in `zone`, the device's own time zone unless a test gives one. */
fun readSensor(entity: EntityState, zone: ZoneId = ZoneId.systemDefault()): SensorView {
    if (entity.state == "unavailable") return SensorView(UNAVAILABLE, null)
    if (entity.state == "unknown") return SensorView("Unknown", null)
    if (readText(entity, "device_class") == "timestamp") {
        val moment = try {
            OffsetDateTime.parse(entity.state)
        } catch (_: DateTimeParseException) {
            null
        }
        if (moment != null) {
            return SensorView(formatDateTime(moment.atZoneSameInstant(zone).toLocalDateTime()), null)
        }
    }
    return SensorView(entity.state, readText(entity, "unit_of_measurement"))
}

/** The reading with its unit. A percent sign sits against its number; every other unit gets a space. */
fun sensorText(view: SensorView): String = when (view.unit) {
    null -> view.reading
    "%" -> view.reading + "%"
    else -> view.reading + " " + view.unit
}

/** '20 Nov, 08:05': the day, the month in three letters, and the time on a 24-hour clock. */
fun formatDateTime(dateTime: LocalDateTime): String =
    "${dateTime.dayOfMonth} ${MONTHS[dateTime.monthValue - 1]}, " +
        String.format(java.util.Locale.ROOT, "%02d:%02d", dateTime.hour, dateTime.minute)
