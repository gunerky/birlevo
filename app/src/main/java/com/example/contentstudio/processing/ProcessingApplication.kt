package com.example.contentstudio.processing

import android.app.Application

/** Process lifetime owner; isolated PDF processes never initialize jobs unless requested. */
class ProcessingApplication : Application() {
    val processingJobs by lazy { ProcessingJobs(this) }
}
