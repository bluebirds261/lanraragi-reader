package com.lanraragi.reader.data.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SensitiveFieldClassifierTest {
    @Test
    fun classifiesCommonCredentialNamesAndKeepsDisplaySettingsPublic() {
        assertEquals(ConfigFieldSensitivity.SECRET, SensitiveFieldClassifier.classify("api_key"))
        assertEquals(ConfigFieldSensitivity.SECRET, SensitiveFieldClassifier.classify("session-cookie"))
        assertEquals(ConfigFieldSensitivity.SECRET, SensitiveFieldClassifier.classify("refreshToken"))
        assertEquals(ConfigFieldSensitivity.NON_SENSITIVE, SensitiveFieldClassifier.classify("readerMode"))
        assertFalse(SensitiveFieldClassifier.isSecret("theme"))
        assertTrue(SensitiveFieldClassifier.isSecret("passwordHash"))
    }
}

