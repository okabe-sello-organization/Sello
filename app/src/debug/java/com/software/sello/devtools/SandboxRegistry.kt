package com.software.sello.devtools

/**
 * What the debug composition built for this process, for the tools screen and the
 * badge, which Android creates without constructor parameters. It is set once at
 * application start, before any activity exists. [time] is absent outside the sandbox
 * and when the data did not open; [scenarios] too, and until the graph is built.
 */
class SandboxTools(
    val profiles: SandboxProfiles,
    val time: SandboxTime?,
    val scenarios: ScenarioRunner? = null,
    val receipts: ReceiptOutcomes? = null
)

object SandboxRegistry {
    @Volatile
    var tools: SandboxTools? = null
}
