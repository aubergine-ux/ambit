package io.aubergine.civops.model

/**
 * Coarse bucket derived from the OUI vendor name.
 *
 * This is a heuristic over a public vendor registry, not an identification. A vendor sells
 * to everyone, so "Priority" means "this MAC prefix is registered to a company whose main
 * business is public-safety equipment" and nothing stronger than that.
 */
enum class DeviceCategory(val label: String, val rank: Int) {
    PRIORITY("Priority / LE / Public Safety", 0),
    FLEET("Fleet / Cellular Router", 1),
    INFRASTRUCTURE("Infrastructure / Network", 2),
    COMMERCIAL("Commercial", 3),
    RESIDENTIAL("Residential", 4),
    RANDOMIZED("Randomized address", 5),
    UNKNOWN("Unclassified", 6);

    companion object {
        /** Categories drawn in the radar legend, in display order. */
        val LEGEND = listOf(PRIORITY, FLEET, COMMERCIAL, RESIDENTIAL)
    }
}
