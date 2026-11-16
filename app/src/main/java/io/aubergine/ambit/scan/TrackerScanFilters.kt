package io.aubergine.ambit.scan

import android.bluetooth.le.ScanFilter
import android.os.ParcelUuid

/**
 * Hardware-offloaded filters for the tracker families the correlator understands.
 *
 * These exist for one reason: Android keeps delivering filtered scans with the screen off
 * and stops delivering unfiltered ones. Keep the list short — some controllers only hold
 * a handful of filters and fall back to software matching beyond that.
 */
internal object TrackerScanFilters {

    private fun uuid16(short: String) = ParcelUuid.fromString("0000$short-0000-1000-8000-00805f9b34fb")

    fun all(): List<ScanFilter> = listOf(
        // Apple offline finding: type byte 0x12.
        ScanFilter.Builder()
            .setManufacturerData(0x004C, byteArrayOf(0x12), byteArrayOf(0xFF.toByte()))
            .build(),
        ScanFilter.Builder().setServiceData(uuid16("fd5a"), byteArrayOf()).build(),
        ScanFilter.Builder().setServiceData(uuid16("feaa"), byteArrayOf()).build(),
        // Tile and Chipolo have shipped both service-data-only and UUID-list forms.
        ScanFilter.Builder().setServiceData(uuid16("feed"), byteArrayOf()).build(),
        ScanFilter.Builder().setServiceUuid(uuid16("feed")).build(),
        ScanFilter.Builder().setServiceData(uuid16("fe33"), byteArrayOf()).build(),
        ScanFilter.Builder().setServiceData(uuid16("fe65"), byteArrayOf()).build(),
        ScanFilter.Builder().setManufacturerData(0x08C3, byteArrayOf()).build(),
    )
}
