package app.tasbih.counter.shared.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateRulesTest {
    @Test
    fun `newer versions are found`() {
        assertTrue(isNewerVersion("v0.1.1", "0.1.0"))
        assertTrue(isNewerVersion("0.10.0", "0.9.9"))
        assertTrue(isNewerVersion("1.0", "0.9.9"))
    }

    @Test
    fun `the same or older version is not an update`() {
        assertFalse(isNewerVersion("0.1.0", "0.1.0"))
        assertFalse(isNewerVersion("0.1", "0.1.0"))
        assertFalse(isNewerVersion("0.0.9", "0.1.0"))
    }

    @Test
    fun `unreadable versions are never an update`() {
        assertFalse(isNewerVersion("latest", "0.1.0"))
        assertFalse(isNewerVersion("0.2.0", "dev"))
    }

    @Test
    fun `only this project's https releases can be downloaded`() {
        assertTrue(isTrustedDownload("https://github.com/iamfaraz/tasbih/releases/download/v0.2.0/tasbih-phone-0.2.0.apk"))
        listOf(
            "http://github.com/iamfaraz/tasbih/releases/download/v1/a.apk",
            "https://github.com/someone/tasbih/releases/download/v1/a.apk",
            "https://github.com.evil.example/iamfaraz/tasbih/releases/download/v1/a.apk",
            "https://evil.example/https://github.com/iamfaraz/tasbih/releases/download/v1/a.apk",
            "not a url",
        ).forEach { assertFalse(it, isTrustedDownload(it)) }
    }

    @Test
    fun `an update must have exactly the installed signers`() {
        assertTrue(sameSigners(setOf("a"), setOf("a")))
        assertFalse(sameSigners(setOf("a"), setOf("b")))
        assertFalse(sameSigners(setOf("a"), setOf("a", "b")))
        assertFalse(sameSigners(emptySet(), emptySet()))
    }
}
