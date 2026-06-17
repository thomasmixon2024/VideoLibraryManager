package com.example.videolibrarymanager.safety

import java.io.File
import java.io.IOException
import java.util.UUID

/**
 * Result of a file operation attempt. This is the whole point of the module:
 * a failed rename/move/delete returns a typed result instead of throwing,
 * so it can never take the app down or fail silently.
 */
sealed class FileOpResult {
    data class Success(val message: String) : FileOpResult()
    data class Failure(val reason: String, val exception: Throwable? = null) : FileOpResult()
    data class Blocked(val reason: String) : FileOpResult() // stopped before touching disk
}

enum class FileOpType { RENAME, MOVE, DELETE, COPY }

data class PendingOperation(
    val id: String = UUID.randomUUID().toString(),
    val type: FileOpType,
    val source: File,
    val destination: File?,
    val timestampMillis: Long = System.currentTimeMillis()
)

/**
 * Append-only rollback log, one JSON line per event. An "attempt" line is
 * written BEFORE the underlying file system call, and an "outcome" line
 * after. If the app crashes mid-operation, this log tells you exactly what
 * was in flight and whether it completed - the Kotlin equivalent of the
 * rollback log scanner_p1.py already writes on the Python side.
 */
class FileOperationLog(logDir: File) {
    private val logFile = File(logDir, "vlm_file_ops_log.jsonl")

    init {
        logDir.mkdirs()
        if (!logFile.exists()) logFile.createNewFile()
    }

    fun recordAttempt(op: PendingOperation) {
        appendLine(
            """{"phase":"attempt","id":"${op.id}","type":"${op.type}","source":"${esc(op.source.absolutePath)}","destination":"${esc(op.destination?.absolutePath ?: "")}","time":${op.timestampMillis}}"""
        )
    }

    fun recordOutcome(op: PendingOperation, result: FileOpResult) {
        val (status, detail) = when (result) {
            is FileOpResult.Success -> "success" to result.message
            is FileOpResult.Failure -> "failure" to result.reason
            is FileOpResult.Blocked -> "blocked" to result.reason
        }
        appendLine(
            """{"phase":"outcome","id":"${op.id}","status":"$status","detail":"${esc(detail)}","time":${System.currentTimeMillis()}}"""
        )
    }

    fun readRecentEntries(limit: Int = 50): List<String> =
        if (logFile.exists()) logFile.readLines().takeLast(limit) else emptyList()

    private fun esc(s: String) = s.replace("\\", "\\\\").replace("\"", "'")

    private fun appendLine(json: String) {
        logFile.appendText(json + "\n")
    }
}

/**
 * Every rename/move/delete in the app must go through this class. No code
 * path should call File.renameTo() / File.delete() directly anywhere else -
 * that is how a "looks like it worked" UI turns into silent data loss.
 *
 * dryRun defaults to true on purpose. Something has to deliberately flip it
 * before any real disk write happens.
 */
class SafeFileExecutor(
    private val libraryRoots: List<File>,   // operations are only allowed inside these
    private val quarantineDir: File,        // "deletes" land here instead of actually deleting
    private val log: FileOperationLog,
    var dryRun: Boolean = true
) {

    fun rename(source: File, newName: String, confirmed: Boolean): FileOpResult {
        if (!confirmed) return FileOpResult.Blocked("Rename requires explicit user confirmation")
        if (newName.isBlank() || newName.contains("/")) return FileOpResult.Blocked("Invalid file name")
        val destination = File(source.parentFile, newName)
        return execute(PendingOperation(type = FileOpType.RENAME, source = source, destination = destination))
    }

    fun move(source: File, targetDir: File, confirmed: Boolean): FileOpResult {
        if (!confirmed) return FileOpResult.Blocked("Move requires explicit user confirmation")
        val destination = File(targetDir, source.name)
        return execute(PendingOperation(type = FileOpType.MOVE, source = source, destination = destination))
    }

    /** Never a true delete on first action - always quarantines first so it's recoverable. */
    fun delete(source: File, confirmed: Boolean): FileOpResult {
        if (!confirmed) return FileOpResult.Blocked("Delete requires explicit user confirmation")
        val destination = File(quarantineDir, "${System.currentTimeMillis()}_${source.name}")
        return execute(PendingOperation(type = FileOpType.DELETE, source = source, destination = destination))
    }

    fun copy(source: File, targetDir: File, confirmed: Boolean): FileOpResult {
        if (!confirmed) return FileOpResult.Blocked("Copy requires explicit user confirmation")
        val destination = File(targetDir, source.name)
        return execute(PendingOperation(type = FileOpType.COPY, source = source, destination = destination))
    }

    /** Permanently empties quarantine. The only place an actual delete() call should exist. */
    fun purgeQuarantine(olderThanMillis: Long): FileOpResult {
        val cutoff = System.currentTimeMillis() - olderThanMillis
        var removed = 0
        quarantineDir.listFiles()?.forEach { f ->
            if (f.lastModified() < cutoff && f.delete()) removed++
        }
        return FileOpResult.Success("Purged $removed quarantined file(s)")
    }

    private fun execute(op: PendingOperation): FileOpResult {
        preCheck(op)?.let { return it }

        log.recordAttempt(op)

        if (dryRun) {
            val result = FileOpResult.Success(
                "[DRY RUN] Would ${op.type} ${op.source.name}" +
                    (op.destination?.let { " -> ${it.name}" } ?: "")
            )
            log.recordOutcome(op, result)
            return result
        }

        val result = try {
            val dest = op.destination ?: return FileOpResult.Failure("Missing destination").also {
                log.recordOutcome(op, it)
            }
            dest.parentFile?.mkdirs()
            when (op.type) {
                FileOpType.RENAME, FileOpType.MOVE, FileOpType.DELETE -> {
                    val ok = op.source.renameTo(dest)
                    if (ok) FileOpResult.Success("${op.type} completed")
                    else FileOpResult.Failure("renameTo() returned false - check storage permissions or cross-volume move")
                }
                FileOpType.COPY -> {
                    op.source.copyTo(dest, overwrite = false)
                    FileOpResult.Success("Copy completed")
                }
            }
        } catch (e: IOException) {
            FileOpResult.Failure("IO error: ${e.message}", e)
        } catch (e: SecurityException) {
            FileOpResult.Failure("Permission denied: ${e.message}", e)
        } catch (e: Exception) {
            FileOpResult.Failure("Unexpected error: ${e.message}", e)
        }

        log.recordOutcome(op, result)
        return result
    }

    private fun preCheck(op: PendingOperation): FileOpResult.Blocked? {
        if (!op.source.exists())
            return FileOpResult.Blocked("Source no longer exists - was it already moved or deleted elsewhere?")
        if (!isInsideLibrary(op.source))
            return FileOpResult.Blocked("Source is outside managed library roots - refusing to touch it")
        op.destination?.let { dest ->
            if (op.type != FileOpType.DELETE && dest.exists())
                return FileOpResult.Blocked("Destination already exists - refusing to silently overwrite")
            if (op.type != FileOpType.DELETE && !isInsideLibrary(dest) && op.type != FileOpType.COPY)
                return FileOpResult.Blocked("Destination is outside managed library roots")
        }
        return null
    }

    private fun isInsideLibrary(file: File): Boolean =
        libraryRoots.any { root ->
            runCatching { file.canonicalPath.startsWith(root.canonicalPath) }.getOrDefault(false)
        }
}
