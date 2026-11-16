package io.aubergine.ambit.data

import androidx.test.core.app.ApplicationProvider
import io.aubergine.ambit.analysis.OuiClassifier
import io.aubergine.ambit.data.oui.OuiLookup
import io.aubergine.ambit.model.DeviceCategory
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Guards the bundled registry asset end to end.
 *
 * This exists because the asset pipeline can rename or re-encode a file underneath you:
 * a gzipped asset gets its extension stripped and is stored expanded, so an `open()` by
 * name throws and every vendor lookup quietly returns null. Nothing else in the app fails
 * loudly when that happens — the UI just shows an environment with no recognisable
 * hardware in it — so the asset is asserted here directly.
 */
@RunWith(RobolectricTestRunner::class)
// Pinned rather than inheriting targetSdk: Robolectric ships a fixed set of SDK images and
// has none for the compile target, which fails the whole class at initialisation. The
// asset pipeline this test guards does not vary by SDK level.
@Config(sdk = [34])
class OuiLookupTest {

    private val lookup = OuiLookup(ApplicationProvider.getApplicationContext())

    @Test
    fun `the registry asset is present under the name the loader opens`() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val names = context.assets.list("")?.toList().orEmpty()
        assertTrue("assets were $names", names.contains(OuiLookup.ASSET))
    }

    @Test
    fun `the registry loads and holds the full table`() = runTest {
        val table = lookup.ensureLoaded()
        // The IEEE MA-L registry runs to tens of thousands of assignments; a partial or
        // mis-decoded read would land far below this.
        assertTrue("only ${table.size} entries loaded", table.size > 30_000)
    }

    @Test
    fun `a known hardware prefix resolves to its vendor`() = runTest {
        // 00:00:0C is Cisco, one of the oldest assignments in the registry.
        val vendor = lookup.vendorFor("00:00:0C:11:22:33")
        assertNotNull(vendor)
        assertTrue(vendor.orEmpty(), vendor.orEmpty().contains("Cisco", ignoreCase = true))
    }

    @Test
    fun `an unassigned prefix resolves to nothing rather than to a guess`() = runTest {
        lookup.ensureLoaded()
        assertNull(lookup.vendorForOrNull("FF:FF:FF:00:00:01"))
    }

    @Test
    fun `classification runs end to end over the real registry`() = runTest {
        val classifier = OuiClassifier(lookup)
        classifier.warmUp()

        val cisco = classifier.classifyBlocking("00:00:0C:11:22:33")
        assertEquals(DeviceCategory.INFRASTRUCTURE, cisco.category)
        assertTrue(cisco.vendor.orEmpty().contains("Cisco", ignoreCase = true))
    }

    @Test
    fun `a randomized address reports as randomized and never as an unknown vendor`() = runTest {
        val classifier = OuiClassifier(lookup)
        classifier.warmUp()

        val result = classifier.classifyBlocking("4A:BB:CC:DD:EE:FF")
        assertEquals(DeviceCategory.RANDOMIZED, result.category)
        assertTrue(result.randomized)
        assertNull(result.vendor)
    }
}
