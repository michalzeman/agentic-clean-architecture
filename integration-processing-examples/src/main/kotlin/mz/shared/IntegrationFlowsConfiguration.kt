package mz.shared

import mz.shared.domain.JobDocument
import mz.shared.domain.JobDocumentConverter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.integration.channel.QueueChannel
import org.springframework.integration.dsl.IntegrationFlow

@Configuration
class IntegrationFlowsConfiguration(
    val jobDocumentConverter: JobDocumentConverter,
) {
    @Bean
    fun jobDocumentInboundFlow(jobDocumentInboundChannel: QueueChannel) =
        IntegrationFlow
            .from(jobDocumentInboundChannel)
            .handle<JobDocument> { message, _ ->
                println("Inbound JobDocument -> $message")
            }.get()

    @Bean
    fun runJobReplayFlow(requestReplayChannel: QueueChannel): IntegrationFlow =
        IntegrationFlow
            .from(requestReplayChannel)
            .transform<JobDocument, JobResult> {
                JobResult(it.name)
            }.get()
}
