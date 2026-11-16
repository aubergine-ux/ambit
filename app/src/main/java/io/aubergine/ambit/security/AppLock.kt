package io.aubergine.ambit.security

import io.aubergine.ambit.data.repo.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Whether the UI is currently hidden behind the PIN screen.
 *
 * Starts locked and relocks every time the app leaves the screen. Whether the lock is
 * actually shown is decided by the settings; this only tracks the state.
 */
@Singleton
class AppLock @Inject constructor(private val settings: SettingsRepository) {

    private val _locked = MutableStateFlow(true)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    private var failures = 0

    fun lock() {
        _locked.value = true
    }

    fun unlock() {
        failures = 0
        _locked.value = false
    }

    /** Checks a PIN. After five wrong tries each further try waits a little longer. */
    suspend fun tryPin(pin: String): Boolean {
        val current = settings.settings.first()
        val hash = current.pinHash ?: return false
        val salt = current.pinSalt ?: return false
        if (failures >= FREE_ATTEMPTS) kotlinx.coroutines.delay((failures - FREE_ATTEMPTS + 1) * BACKOFF_MILLIS)
        val ok = withContext(Dispatchers.Default) { PinHasher.verify(pin, PinHasher.Stored(hash, salt)) }
        if (ok) unlock() else failures += 1
        return ok
    }

    private companion object {
        const val FREE_ATTEMPTS = 5
        const val BACKOFF_MILLIS = 2_000L
    }
}
