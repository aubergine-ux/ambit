package io.aubergine.ambit.ui.common

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.aubergine.ambit.R
import io.aubergine.ambit.analysis.TrackerCorrelator
import io.aubergine.ambit.model.DeviceCategory

@StringRes
fun DeviceCategory.labelRes(): Int = when (this) {
    DeviceCategory.PRIORITY -> R.string.category_priority
    DeviceCategory.FLEET -> R.string.category_fleet
    DeviceCategory.INFRASTRUCTURE -> R.string.category_infrastructure
    DeviceCategory.COMMERCIAL -> R.string.category_commercial
    DeviceCategory.RESIDENTIAL -> R.string.category_residential
    DeviceCategory.RANDOMIZED -> R.string.category_randomized
    DeviceCategory.UNKNOWN -> R.string.category_unknown
}

@Composable
fun DeviceCategory.label(): String = stringResource(labelRes())

@Composable
fun DeviceCategory.legendLabel(): String = stringResource(
    when (this) {
        DeviceCategory.PRIORITY -> R.string.legend_priority
        DeviceCategory.FLEET -> R.string.legend_fleet
        DeviceCategory.INFRASTRUCTURE -> R.string.legend_infrastructure
        DeviceCategory.COMMERCIAL -> R.string.legend_commercial
        DeviceCategory.RESIDENTIAL -> R.string.legend_residential
        DeviceCategory.RANDOMIZED -> R.string.legend_randomized
        DeviceCategory.UNKNOWN -> R.string.legend_unknown
    },
)

@Composable
fun TrackerCorrelator.Severity.label(): String = stringResource(
    when (this) {
        TrackerCorrelator.Severity.LOW -> R.string.severity_low
        TrackerCorrelator.Severity.ELEVATED -> R.string.severity_elevated
        TrackerCorrelator.Severity.HIGH -> R.string.severity_high
    },
)
