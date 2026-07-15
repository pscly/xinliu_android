package cc.pscly.onememos.diagnostics

interface DiagnosticsExporter {
    suspend fun export(snapshot: DiagnosticsSnapshot): DiagnosticsExportResult
}

sealed interface DiagnosticsExportResult {
    data class Success(val fileUri: String) : DiagnosticsExportResult

    data class Failure(val error: DiagnosticsError) : DiagnosticsExportResult
}

enum class DiagnosticsError {
    WRITE_FAILED,
    PROVIDER_FAILED,
}
