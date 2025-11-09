package mz.integration.messaging.data.domain

import org.apache.commons.logging.LogFactory
import org.springframework.integration.store.MessageGroup

private val logger = LogFactory.getLog(JobDocumentAggregate::class.java)

data class JobInfo(
    val status: String,
    val active: Boolean,
)

data class JobDocument(
    val id: String,
    val name: String,
    val jobInfos: List<JobInfo>,
)

data class JobDocumentAggregate(
    val jobs: List<JobDocument>,
)

fun MessageGroup.toJobDocumentAggregate(): JobDocumentAggregate {
    val jobs = this.messages.map { it.payload as JobDocument }
    return JobDocumentAggregate(jobs)
}

fun JobDocumentAggregate.finished(): Boolean {
    val result =
        jobs.any { doc ->
            doc.jobInfos.any { docInfo -> docInfo.status == "STOPPED" }
        }
    logger.info("Aggregator finished -> $result")
    return result
}
