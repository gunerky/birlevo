package com.example.contentstudio.storage

import android.app.ActivityManager
import android.content.Context

fun deviceMemory(context: Context? = null): WorkingMemory = WorkingMemory {
    val runtime = Runtime.getRuntime()
    val system = context?.let {
        runCatching { ActivityManager.MemoryInfo().also { info ->
            checkNotNull(context.getSystemService(ActivityManager::class.java)).getMemoryInfo(info)
        } }.getOrNull()
    }
    MemorySnapshot(runtime.maxMemory(),(runtime.totalMemory()-runtime.freeMemory()).coerceAtLeast(0),
        system?.availMem,system?.threshold ?: 0)
}
