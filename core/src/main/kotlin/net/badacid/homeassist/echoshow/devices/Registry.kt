/*
 * The list of device types the app supports.
 *
 * To add a type: make its folder (copy an existing one), then add it to the list below. The picker, the
 * card sizes, what a tap does and "Not supported yet" all follow from this list.
 *
 * Keep the list in `mainOrder`, lowest first. That is the order in which parts lead a device's card: a
 * doorbell's camera before its battery sensor.
 */
package net.badacid.homeassist.echoshow.devices

import net.badacid.homeassist.echoshow.devices.binarysensor.BinarySensorLogic
import net.badacid.homeassist.echoshow.devices.button.ButtonLogic
import net.badacid.homeassist.echoshow.devices.camera.CameraLogic
import net.badacid.homeassist.echoshow.devices.climate.ClimateLogic
import net.badacid.homeassist.echoshow.devices.cover.CoverLogic
import net.badacid.homeassist.echoshow.devices.fan.FanLogic
import net.badacid.homeassist.echoshow.devices.light.LightLogic
import net.badacid.homeassist.echoshow.devices.mediaplayer.MediaPlayerLogic
import net.badacid.homeassist.echoshow.devices.sensor.SensorLogic
import net.badacid.homeassist.echoshow.devices.switch.SwitchLogic
import net.badacid.homeassist.echoshow.devices.vacuum.VacuumLogic

val DEVICE_LOGIC: List<DeviceLogic> = listOf(
    CameraLogic,
    ClimateLogic,
    MediaPlayerLogic,
    VacuumLogic,
    LightLogic,
    CoverLogic,
    FanLogic,
    SwitchLogic,
    ButtonLogic,
    SensorLogic,
    BinarySensorLogic,
)

/** The type with the given id, or null. Saved data and parts refer to types by id. */
fun deviceLogicFor(typeId: String): DeviceLogic? = DEVICE_LOGIC.firstOrNull { it.id == typeId }
