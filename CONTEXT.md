# Bank demo

Two Spring Boot services that move money between bank accounts using clean and hexagonal architecture. A transfer runs as a saga that spans both services, so the codebase is a working example of cross-service coordination, not an isolated CRUD app.

## Language

**Saga**:
A multi-step business process coordinated by the bank-transaction service, one step at a time. In this repo the only saga is the transfer: it drives withdraw, deposit, and finish, and compensates when a step fails.
_Avoid_: workflow, orchestration, process manager

**Transfer**:
Moving money between two accounts as one business flow: withdraw from the source, deposit to the target, finish. One transfer is one saga run across both services.
_Avoid_: transaction, payment

**Transaction**:
A single operation on one account, recorded by the bank transaction aggregate. A transaction touches one account. A transfer touches two and spans services. Never call a whole transfer a transaction.
_Avoid_: transfer, movement

**Compensation**:
Undoing steps a saga already completed when a later step fails. A failed deposit reverses the earlier withdrawal, so both accounts end where they started.
_Avoid_: rollback, undo, reversal

**Trace**:
The correlated record of one transfer as it crosses both services and the brokers between them. One trace follows one saga from the first withdraw command to the finished or compensated end state.
_Avoid_: request trace, run

**Span**:
One hop inside a trace. A span covers one unit of work: a service handling one saga step, a broker publish or consume, or a message-channel send or receive. Spans link into the trace they belong to.
_Avoid_: step, operation

**Correlation**:
Connecting log lines and spans that belong to the same trace across service boundaries. The test is concrete: both services log the same traceId for one transfer, and clicking that id in Grafana opens the saga trace.
_Avoid_: linking, joining
