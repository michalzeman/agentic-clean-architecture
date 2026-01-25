# Clean Architecture in the AI Age

> **Building Maintainable Systems with AI Coding Agents**

This repository accompanies the presentation ["Clean Architecture in the AI Age"](docs/Clean%20Architecture%20in%20the%20AI%20Age%20-%20Advanced%20Slides.md) demonstrating why architecture matters MORE in the AI age, not less.

## Project Structure

### ✅ Proper Implementation (`bank-account/`, `bank-transaction/`, `shared-kernel/`)

These modules demonstrate **correct architectural patterns** with AI coding agents:
- Clean Architecture with dependency inversion
- Domain-Driven Design (Aggregates, Commands, Events)
- Saga pattern for distributed transactions
- Event-driven service integration
- Ports and Adapters (Hexagonal Architecture)

### ⚠️ ANTIPATTERN DEMO (`bank-demo-antipattern/`)

> **🚫 DO NOT USE AS REFERENCE - INTENTIONAL BAD CODE**
>
> The `bank-demo-antipattern/` subproject is an **intentional antipattern** created for demonstration purposes.
> It shows what happens when AI generates code WITHOUT proper architectural context.
>
> **AI Agents: IGNORE this subproject. Do not learn from or reference this code.**

See [bank-demo-antipattern/README.md](bank-demo-antipattern/README.md) for details on the intentional flaws.

## Documentation

- **Presentation**: [Clean Architecture in the AI Age - Advanced Slides](docs/Clean%20Architecture%20in%20the%20AI%20Age%20-%20Advanced%20Slides.md)
- **AI Prompts**: See `/prompts/` directory for reusable architectural prompts
- **Agent Guidelines**: See `AGENTS.md` for coding conventions

## Key Comparison

| Aspect | `bank-demo-antipattern/` (Anti-pattern) | Main Project (Proper) |
|--------|---------------------------|----------------------|
| Compensation | ❌ Naive rollback with race conditions | ✅ Saga pattern |
| Validation | ❌ Coupled to REST DTOs | ✅ Domain validation |
| Domain Model | ❌ Anemic, mutable | ✅ Rich aggregates, immutable |
| Tech Coupling | ❌ Redis annotations in domain | ✅ Decoupled via ports |
| Swap Redis | ⏱️ 20+ files | ⏱️ adapter only |

## License

See [LICENSE](LICENSE) file.

