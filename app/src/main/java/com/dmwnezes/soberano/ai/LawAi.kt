package com.dmwnezes.soberano.ai

import com.dmwnezes.soberano.sim.Interpretation
import com.dmwnezes.soberano.sim.LawInterpreter
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.GenerativeModel
import com.google.mlkit.genai.prompt.Generation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * IA do próprio aparelho (Gemini Nano pelo ML Kit). Funciona offline e de graça nos celulares compatíveis.
 * Se não estiver disponível, a análise cai no interpretador local.
 */
object LawAi {
    enum class Mode(val label: String) {
        CHECKING("Verificando IA do aparelho…"),
        READY("IA do aparelho pronta"),
        DOWNLOAD("IA do aparelho disponível para baixar"),
        DOWNLOADING("Baixando a IA do aparelho…"),
        LOCAL("Análise local (este aparelho não tem IA compatível)"),
    }

    private var model: GenerativeModel? = null

    private fun client(): GenerativeModel? = model ?: try { Generation.getClient().also { model = it } } catch (_: Throwable) { null }

    suspend fun mode(): Mode = withContext(Dispatchers.IO) {
        val m = client() ?: return@withContext Mode.LOCAL
        val st = try { withTimeoutOrNull(6000) { m.checkStatus() } } catch (_: Throwable) { null } ?: return@withContext Mode.LOCAL
        when (st) {
            FeatureStatus.AVAILABLE -> Mode.READY
            FeatureStatus.DOWNLOADABLE -> Mode.DOWNLOAD
            FeatureStatus.DOWNLOADING -> Mode.DOWNLOADING
            else -> Mode.LOCAL
        }
    }

    /** Baixa o modelo (uma vez). Devolve true se ficou pronto. */
    suspend fun download(onProgress: (String) -> Unit): Boolean = withContext(Dispatchers.IO) {
        val m = client() ?: return@withContext false
        var ok = false
        try {
            m.download().takeWhile { s ->
                when (s) {
                    is DownloadStatus.DownloadCompleted -> { ok = true; false }
                    is DownloadStatus.DownloadFailed -> false
                    is DownloadStatus.DownloadProgress -> { onProgress("Baixando… ${s.totalBytesDownloaded / 1_000_000} MB"); true }
                    else -> true
                }
            }.collect { }
        } catch (_: Throwable) { ok = false }
        ok || mode() == Mode.READY
    }

    /**
     * Analisa a lei. Tenta a IA do aparelho; se falhar ou não existir, usa o interpretador local.
     * Nunca lança exceção.
     */
    suspend fun interpret(text: String, country: String): Interpretation = withContext(Dispatchers.IO) {
        val local = LawInterpreter.local(text)
        val m = client() ?: return@withContext local
        val status = try { withTimeoutOrNull(6000) { m.checkStatus() } } catch (_: Throwable) { null }
        if (status != FeatureStatus.AVAILABLE) return@withContext local
        val raw = try {
            withTimeoutOrNull(45_000) { m.generateContent(LawInterpreter.prompt(text, country)).candidates.firstOrNull()?.text }
        } catch (_: Throwable) { null } ?: return@withContext local
        LawInterpreter.parseAi(raw, local.name) ?: local
    }
}
