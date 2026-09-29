package mz.bank.system.tests

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.io.File
import java.io.IOException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/**
 * One parsed JSON log line from a bank service's stdout.
 *
 * The Logback JSON config shipped in `connector-observability` emits one JSON
 * object per line: MDC fields `traceId`/`spanId` at the top level plus
 * `message`, `level`, `logger_name` (LogstashEncoder's default logger field
 * name), `thread_name`, `level_value`, and `timestamp`. [logger] is read as a
 * convenience for diagnostics only.
 */
data class JsonLogLine(
    val message: String,
    val traceId: String?,
    val spanId: String?,
    val logger: String?,
)

/**
 * Reads a compose service's stdout from the host side via
 * `docker compose logs --no-log-prefix <service>` and parses each line as JSON.
 *
 * The parse never depends on Alloy or Loki shipping: it runs host-side, so the
 * acceptance test stays transport- and shipper-agnostic.
 */
object ContainerLogs {
    private val objectMapper = ObjectMapper()

    private const val PROCESS_TIMEOUT_SECONDS = 60L

    /**
     * Collects every JSON-parsable log line currently in the service's output.
     * Lines that are blank or not parseable as a JSON object (container log
     * prefixes, multi-line stack traces, non-JSON noise) are skipped.
     *
     * Line reading runs concurrently with [Process.waitFor] so a hung
     * `docker compose logs` is reaped after [PROCESS_TIMEOUT_SECONDS] instead
     * of blocking the read forever. The process is always destroyed on exit.
     */
    fun serviceLogLines(serviceName: String): List<JsonLogLine> {
        val process =
            try {
                ProcessBuilder("docker", "compose", "logs", "--no-log-prefix", serviceName)
                    .directory(repoRoot())
                    .redirectErrorStream(true)
                    .start()
            } catch (e: IOException) {
                throw IllegalStateException(
                    "Failed to start `docker compose logs` for service $serviceName — " +
                        "is the docker CLI installed and on PATH?",
                    e,
                )
            }
        try {
            val output =
                CompletableFuture.supplyAsync {
                    process.inputStream.bufferedReader().readLines()
                }
            val finishedInTime = process.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            if (!finishedInTime) {
                process.destroyForcibly()
                throw IllegalStateException(
                    "`docker compose logs` for service $serviceName timed out after " +
                        "$PROCESS_TIMEOUT_SECONDS seconds",
                )
            }
            val lines = output.get()
            check(process.exitValue() == 0) {
                "docker compose logs failed for service $serviceName with exit code ${process.exitValue()}"
            }
            return lines
                .asSequence()
                .filter { it.isNotBlank() }
                .mapNotNull { line -> parseJsonLine(line) }
                .toList()
        } finally {
            process.destroyForcibly()
        }
    }

    /**
     * Resolves the repo root (the directory holding compose.yaml) by walking up
     * from the current working directory. Gradle runs test JVMs with
     * user.dir set to the module dir, so the root is its ancestor.
     */
    private fun repoRoot(): File {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null && !dir.resolve("compose.yaml").exists()) {
            dir = dir.parentFile
        }
        checkNotNull(dir) {
            "compose.yaml not found walking up from ${System.getProperty("user.dir")}"
        }
        return dir
    }

    /**
     * Parses one stdout line as a JSON log object. Returns null when the line
     * is not a JSON object or has no `message` (so it would be useless for
     * assertions anyway).
     */
    private fun parseJsonLine(line: String): JsonLogLine? =
        try {
            val node = objectMapper.readTree(line)
            if (node.isObject && node.get("message")?.isNull == false) {
                JsonLogLine(
                    message = node.get("message").stringValue(),
                    traceId = optionalString(node, "traceId"),
                    spanId = optionalString(node, "spanId"),
                    logger = optionalString(node, "logger_name") ?: optionalString(node, "logger"),
                )
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }

    private fun optionalString(
        node: JsonNode,
        field: String,
    ): String? {
        val value = node.get(field) ?: return null
        return if (value.isNull) null else value.stringValue()
    }
}

/**
 * Polls a service's log lines until [predicate] holds, or returns the last
 * collected lines when [maxDelayMillis] elapses. Logs flush asynchronously
 * after a saga completes, so the caller must await before asserting.
 */
suspend fun awaitLogLines(
    serviceName: String,
    maxDelayMillis: Long = 60000,
    pollIntervalMillis: Long = 2000,
    predicate: (List<JsonLogLine>) -> Boolean,
): List<JsonLogLine> {
    val startTime = System.currentTimeMillis()
    while (true) {
        val lines =
            withContext(Dispatchers.IO) {
                ContainerLogs.serviceLogLines(serviceName)
            }
        if (predicate(lines)) {
            return lines
        }
        val elapsed = System.currentTimeMillis() - startTime
        if (elapsed >= maxDelayMillis) {
            return lines
        }
        delay(pollIntervalMillis)
    }
}

/**
 * Renders saga log lines for failure diagnostics: one line per log line, with
 * traceId, logger, and message, so a missing hop or mismatched traceId is
 * diagnosable from the test report alone.
 */
fun List<JsonLogLine>.renderDiagnostic(): String = joinToString("\n") { "traceId=${it.traceId} logger=${it.logger} message=${it.message}" }
