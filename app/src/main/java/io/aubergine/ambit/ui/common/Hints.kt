package io.aubergine.ambit.ui.common

import androidx.annotation.StringRes
import io.aubergine.ambit.R
import io.aubergine.ambit.model.DeviceHint

@StringRes
fun DeviceHint.descriptionRes(): Int = when (this) {
    DeviceHint.CAMERA_GLASSES -> R.string.hint_camera_glasses
    DeviceHint.CAMERA_SETUP_NETWORK -> R.string.hint_camera_setup
    DeviceHint.IOT_SETUP_NETWORK -> R.string.hint_iot_setup
    DeviceHint.CAMERA_VENDOR -> R.string.hint_camera_vendor
}
