package com.example.data.ai

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.util.ArrayDeque

/**
 * Control técnico anti-abuso (gratuito para todos los usuarios).
 * Protege la cuota de Gemini frente a envíos repetidos involuntarios o ráfagas excesivas.
 */
class RateLimiter(
    private val minIntervalMs: Long = 1_200L,
    private val maxPerMinute: Int = 20,
    private val maxPerHour: Int = 120
) {
    private val timestamps = ArrayDeque<Long>()
    private var lastRequestAt: Long = 0L

    @Synchronized
    fun checkAndRecord(nowMs: Long = System.currentTimeMillis()): Result<Unit> {
        if (nowMs - lastRequestAt < minIntervalMs) {
            return Result.failure(
                IllegalStateException("Espera un momento antes de enviar otra consulta.")
            )
        }

        val oneHourAgo = nowMs - 3_600_000L
        while (timestamps.isNotEmpty() && timestamps.first() < oneHourAgo) {
            timestamps.removeFirst()
        }

        if (timestamps.size >= maxPerHour) {
            return Result.failure(
                IllegalStateException(
                    "Alcanzaste el límite técnico de $maxPerHour consultas por hora para proteger el servicio."
                )
            )
        }

        val oneMinuteAgo = nowMs - 60_000L
        val lastMinuteCount = timestamps.count { it >= oneMinuteAgo }
        if (lastMinuteCount >= maxPerMinute) {
            return Result.failure(
                IllegalStateException(
                    "Has enviado muchas consultas seguidas. Espera unos segundos e inténtalo nuevamente."
                )
            )
        }

        lastRequestAt = nowMs
        timestamps.addLast(nowMs)
        return Result.success(Unit)
    }
}

object NetworkChecker {
    fun isOnline(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return true
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
