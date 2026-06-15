package com.example.videolibrarymanager

import com.example.videolibrarymanager.util.BugLogger
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.lang.reflect.Field

/**
 * Unit tests for BugLogger.
 *
 * Because BugLogger is an object singleton we use reflection to reset its
 * state between tests. This avoids needing Robolectric for pure-JVM tests.
 */
class BugLoggerTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private lateinit var logFile: File

    @Before
    fun setUp() {
        // Reset the singleton so each test gets a fresh logger
        resetBugLogger()
        logFile = tmpFolder.newFile("vlm_runlog.txt")
        // Directly inject the logFile via reflection (avoids needing Context)
        val fileField = BugLogger::class.java.getDeclaredField("logFile")
        fileField.isAccessible = true
        fileField.set(BugLogger, logFile)
        val initField = BugLogger::class.java.getDeclaredField("initialized")
        initField.isAccessible = true
        initField.set(BugLogger, true)
        // Write the session header directly so tests start with known state
        logFile.writeText("")
    }

    @Test
    fun `info writes INFO level entry to file`() {
        BugLogger.info("TestTag", "hello world")
        val log = logFile.readText()
        assertTrue("Expected INFO in log", log.contains("[INFO ]"))
        assertTrue("Expected tag in log", log.contains("[TestTag]"))
        assertTrue("Expected message in log", log.contains("hello world"))
    }

    @Test
    fun `warn writes WARN level entry to file`() {
        BugLogger.warn("WarnTag", "something fishy")
        val log = logFile.readText()
        assertTrue(log.contains("[WARN ]"))
        assertTrue(log.contains("something fishy"))
    }

    @Test
    fun `error writes ERROR level and exception stack trace`() {
        val ex = RuntimeException("boom")
        BugLogger.error("ErrTag", "crashed", ex)
        val log = logFile.readText()
        assertTrue(log.contains("[ERROR]"))
        assertTrue(log.contains("crashed"))
        assertTrue(log.contains("RuntimeException"))
        assertTrue(log.contains("boom"))
    }

    @Test
    fun `debug writes DEBUG level entry`() {
        BugLogger.debug("DbgTag", "debug msg")
        val log = logFile.readText()
        assertTrue(log.contains("[DEBUG]"))
        assertTrue(log.contains("debug msg"))
    }

    @Test
    fun `multiple entries accumulate in order`() {
        BugLogger.info("T", "first")
        BugLogger.warn("T", "second")
        BugLogger.debug("T", "third")
        val log = logFile.readText()
        val firstIdx  = log.indexOf("first")
        val secondIdx = log.indexOf("second")
        val thirdIdx  = log.indexOf("third")
        assertTrue(firstIdx < secondIdx)
        assertTrue(secondIdx < thirdIdx)
    }

    @Test
    fun `clear erases content`() {
        BugLogger.info("T", "will be erased")
        BugLogger.clear()
        val log = logFile.readText()
        assertFalse("Log should not contain cleared content", log.contains("will be erased"))
    }

    @Test
    fun `readLog returns file contents`() {
        logFile.writeText("manual content")
        assertEquals("manual content", BugLogger.readLog())
    }

    @Test
    fun `logFile returns non-null file`() {
        assertNotNull(BugLogger.getLogFile())
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun resetBugLogger() {
        setField("logFile", null)
        setField("initialized", false)
    }

    private fun setField(name: String, value: Any?) {
        val f: Field = BugLogger::class.java.getDeclaredField(name)
        f.isAccessible = true
        f.set(BugLogger, value)
    }
}
