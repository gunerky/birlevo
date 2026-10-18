package com.example.contentstudio.advertising

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal enum class AdAgeTreatment { BLOCKED, PROTECTED, ADULT }

internal enum class AdAudienceGroup(val treatment: AdAgeTreatment) {
    UNSET(AdAgeTreatment.BLOCKED),
    UNDER_13(AdAgeTreatment.BLOCKED),
    AGE_13_15(AdAgeTreatment.PROTECTED),
    AGE_16_17(AdAgeTreatment.PROTECTED),
    ADULT(AdAgeTreatment.ADULT),
    UNDISCLOSED(AdAgeTreatment.BLOCKED);

    companion object {
        fun restore(value: String?) = entries.firstOrNull { it.name == value } ?: UNSET
    }
}

internal data class AdAudienceState(
    val group: AdAudienceGroup,
    val saving: Boolean = false,
    val saveFailed: Boolean = false,
)

/** Serialized durable choices. Pending, failed and superseded writes cannot permit ads. */
internal class AdAudienceChoices(
    initial: String?,
    private val scope: CoroutineScope,
    private val persist: suspend (AdAudienceGroup) -> Boolean,
    private val restrictImmediately: () -> Unit,
) {
    private val mutable = MutableStateFlow(AdAudienceState(AdAudienceGroup.restore(initial)))
    val state = mutable.asStateFlow()
    private val writer = Mutex()
    @Volatile private var revision = 0L
    val version get() = revision

    fun choose(group: AdAudienceGroup) {
        require(group != AdAudienceGroup.UNSET)
        val token = ++revision
        restrictImmediately()
        mutable.value = AdAudienceState(AdAudienceGroup.UNSET, saving = true)
        scope.launch {
            writer.withLock {
                if (token != revision) return@withLock
                val saved = try { persist(group) }
                catch (error: Exception) {
                    if (error is CancellationException) throw error
                    false
                }
                if (token == revision) mutable.value = if (saved) AdAudienceState(group)
                    else AdAudienceState(AdAudienceGroup.UNSET, saveFailed = true)
            }
        }
    }
}
