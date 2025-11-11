package mz.shared

import mz.shared.domain.Job.JobModel
import mz.shared.domain.JobDocument
import mz.shared.domain.JobInfo
import org.springframework.integration.channel.PublishSubscribeChannel
import org.springframework.integration.channel.QueueChannel
import org.springframework.messaging.MessageHeaders
import org.springframework.messaging.support.GenericMessage
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

data class RunJobRequest(
    val text: String,
)

data class JobResult(
    val text: String,
)

@RestController
class JobExampleController(
    val outboundRedisStreamChannel: PublishSubscribeChannel,
    val requestReplayChannel: QueueChannel,
) {
    @PostMapping("/run-job")
    suspend fun runJob(
        @RequestBody request: RunJobRequest,
    ) {
        val jobDocument = JobDocument(UUID.randomUUID().toString(), request.text, listOf(JobInfo("Failed", false)))
        outboundRedisStreamChannel.send(GenericMessage(jobDocument), 2000)
    }

    @PostMapping("/run-job-replay")
    suspend fun runJobReplay(
        @RequestBody request: RunJobRequest,
    ): JobResult {
        val jobDocument = JobDocument(UUID.randomUUID().toString(), request.text, listOf(JobInfo("Failed", false)))
        val replayChannel = QueueChannel()
        val headers = MessageHeaders(mapOf(MessageHeaders.REPLY_CHANNEL to replayChannel))
        val message = GenericMessage(jobDocument, headers)
        requestReplayChannel.send(message)
        return replayChannel.receive(2000)!!.payload as JobResult
    }

    @PostMapping("/job", produces = ["application/json"])
    suspend fun job(
        @RequestBody job: JobModel,
    ): JobModel =
        JobModel
            .newBuilder(job)
            .apply { name = "Response Job" }
            .build()
}
