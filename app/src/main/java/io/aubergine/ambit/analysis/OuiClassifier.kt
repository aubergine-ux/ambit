package io.aubergine.ambit.analysis

import io.aubergine.ambit.data.oui.OuiLookup
import io.aubergine.ambit.model.DeviceCategory
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Turns a MAC address into a vendor name and a coarse category.
 *
 * Two stages: the bundled IEEE registry gives the vendor, then keyword rules over the
 * vendor name give the category. The rules are ordered — the first matching bucket wins —
 * because plenty of vendors sell into more than one market and we would rather surface the
 * more operationally interesting label.
 *
 * This is classification of a *manufacturer*, not of a device or its owner. A Cradlepoint
 * router in a category labelled "Fleet" might be in a food truck. The UI must not imply
 * otherwise.
 */
@Singleton
class OuiClassifier @Inject constructor(
    private val lookup: OuiLookup,
) {

    data class Result(
        val vendor: String?,
        val category: DeviceCategory,
        val randomized: Boolean,
    )

    suspend fun warmUp() {
        lookup.ensureLoaded()
    }

    suspend fun classify(address: String): Result {
        lookup.ensureLoaded()
        return classifyBlocking(address)
    }

    /** Requires [OuiLookup.ensureLoaded] to have completed; used on the ingest hot path. */
    fun classifyBlocking(address: String): Result {
        val randomized = OuiLookup.isLocallyAdministered(address)
        val vendor = lookup.vendorForOrNull(address)

        // A locally-administered address has no manufacturer behind it. Any registry hit
        // is a coincidence of the random draw, so we drop it rather than report a vendor
        // the user might act on.
        if (randomized) {
            return Result(null, DeviceCategory.RANDOMIZED, true)
        }
        if (vendor == null || vendor == "Private") {
            return Result(vendor?.takeIf { it != "Private" }, DeviceCategory.UNKNOWN, false)
        }
        return Result(vendor, VendorRules.categoryFor(vendor), false)
    }

    /** Category for a vendor name. See [VendorRules] for the rule table itself. */
    fun categoryFor(vendor: String): DeviceCategory = VendorRules.categoryFor(vendor)
}
