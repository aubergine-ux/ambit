package io.aubergine.ambit.ui

import io.aubergine.ambit.model.TrackerFamily
import io.aubergine.ambit.ui.guide.TRACKER_GUIDES
import io.aubergine.ambit.ui.guide.guideFor
import org.junit.Assert.assertEquals
import org.junit.Test

class GuideContentTest {

    @Test
    fun `every tracker family has its own guide`() {
        for (family in TrackerFamily.entries) {
            assertEquals(family, guideFor(family).family)
        }
        assertEquals(TrackerFamily.entries.size, TRACKER_GUIDES.map { it.family }.distinct().size)
    }
}
