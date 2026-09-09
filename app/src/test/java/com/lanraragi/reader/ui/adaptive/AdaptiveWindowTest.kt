package com.lanraragi.reader.ui.adaptive

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class AdaptiveWindowTest {
    @Test fun breakpointUsesSinglePaneBelow840() {
        assertEquals(AdaptiveLayout.PHONE_SINGLE_PANE, adaptiveLayout(839.dp))
        assertEquals(AdaptiveLayout.TABLET_MASTER_DETAIL, adaptiveLayout(840.dp))
    }
}
