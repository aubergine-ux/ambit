package io.aubergine.ambit.scan

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import android.hardware.TriggerEvent
import android.hardware.TriggerEventListener
import android.location.Location
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Turns location fixes, plus the significant-motion sensor where the phone has one, into
 * a [Motion] state. The sensor matters because a still phone gets few location updates;
 * without it, the switch back to continuous scanning would wait for a fix.
 */
@Singleton
class MotionMonitor @Inject constructor(private val context: Context) {

    private val classifier = MotionClassifier()
    private val _motion = MutableStateFlow(Motion.UNKNOWN)
    val motion: StateFlow<Motion> = _motion.asStateFlow()

    private val sensors: SensorManager? get() = ContextCompat.getSystemService(context, SensorManager::class.java)
    private var armed = false

    private val trigger = object : TriggerEventListener() {
        override fun onTrigger(event: TriggerEvent?) {
            armed = false
            _motion.value = Motion.MOVING
            // One-shot by design; re-arm for the next time the phone settles.
            arm()
        }
    }

    fun onLocation(location: Location) {
        val speed = if (location.hasSpeed()) location.speed else null
        _motion.value = classifier.onFix(location.latitude, location.longitude, location.time, speed)
    }

    fun start() = arm()

    fun stop() {
        val sensor = sensors?.getDefaultSensor(Sensor.TYPE_SIGNIFICANT_MOTION) ?: return
        runCatching { sensors?.cancelTriggerSensor(trigger, sensor) }
        armed = false
    }

    private fun arm() {
        if (armed) return
        val sensor = sensors?.getDefaultSensor(Sensor.TYPE_SIGNIFICANT_MOTION) ?: return
        armed = runCatching { sensors?.requestTriggerSensor(trigger, sensor) == true }.getOrDefault(false)
    }
}
