package io.aubergine.ambit.reliability

import androidx.annotation.StringRes
import io.aubergine.ambit.R

/**
 * What to change on each phone brand so background scanning survives. Several makers add
 * their own battery managers on top of Android's, and those kill foreground services that
 * stock Android would leave alone.
 */
object OemGuidance {

    @StringRes
    fun forManufacturer(manufacturer: String): Int = when (manufacturer.lowercase().trim()) {
        "samsung" -> R.string.oem_samsung
        "xiaomi", "redmi", "poco" -> R.string.oem_xiaomi
        "oneplus", "oppo", "realme" -> R.string.oem_oneplus
        "huawei", "honor" -> R.string.oem_huawei
        "vivo", "iqoo" -> R.string.oem_vivo
        "motorola" -> R.string.oem_motorola
        "google" -> R.string.oem_google
        else -> R.string.oem_generic
    }
}
