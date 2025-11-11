package mz.shared.domain

const val AGGREGATE_ID = "aggregateId"

data class AggregateId(
    val value: String,
) {
    init {
        assert(value.isNotEmpty())
    }
}
