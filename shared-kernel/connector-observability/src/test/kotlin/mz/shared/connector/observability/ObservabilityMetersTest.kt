package mz.shared.connector.observability

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import mz.shared.domain.ObservabilityMeterNames
import mz.shared.domain.OperationOutcome
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyVararg
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class ObservabilityMetersTest {
    private val meterRegistry = SimpleMeterRegistry()
    private val observabilityMeters = ObservabilityMetersImpl(meterRegistry)

    @Test
    fun `should increment transfer completed counter with outcome tag`() {
        observabilityMeters.transferCompleted(OperationOutcome.OK)
        observabilityMeters.transferCompleted(OperationOutcome.OK)
        observabilityMeters.transferCompleted(OperationOutcome.FAIL)

        val ok = meterRegistry.find(ObservabilityMeterNames.TRANSFER_COMPLETED).tag("outcome", "ok").counter()
        val fail = meterRegistry.find(ObservabilityMeterNames.TRANSFER_COMPLETED).tag("outcome", "fail").counter()

        assertThat(ok!!.count()).isEqualTo(2.0)
        assertThat(fail!!.count()).isEqualTo(1.0)
    }

    @Test
    fun `should increment transfer rolled back counter with reason tag`() {
        observabilityMeters.transferRolledBack("timeout")

        val counter = meterRegistry.find(ObservabilityMeterNames.TRANSFER_ROLLED_BACK).tag("reason", "timeout").counter()

        assertThat(counter!!.count()).isEqualTo(1.0)
    }

    @Test
    fun `should increment account operation counter with op and outcome tags`() {
        observabilityMeters.accountOperation("deposit", OperationOutcome.OK)

        val counter =
            meterRegistry
                .find(ObservabilityMeterNames.ACCOUNT_OPERATION)
                .tag("op", "deposit")
                .tag("outcome", "ok")
                .counter()

        assertThat(counter!!.count()).isEqualTo(1.0)
    }

    @Test
    fun `should never propagate exception when meter registry throws`() {
        val throwingRegistry = mock<MeterRegistry>()
        whenever(throwingRegistry.counter(any<String>(), anyVararg<String>()))
            .thenThrow(IllegalStateException("registry broken"))
        val meters = ObservabilityMetersImpl(throwingRegistry)

        assertThatCode { meters.transferCompleted(OperationOutcome.OK) }
            .doesNotThrowAnyException()
    }
}
