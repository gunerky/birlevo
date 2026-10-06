package com.example.contentstudio.media

import androidx.core.content.FileProvider

/** A dedicated subclass avoids the device compatibility issues of registering the base class. */
class OutputFileProvider : FileProvider()
