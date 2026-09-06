package com.lanraragi.reader.ui

import kotlinx.coroutines.flow.MutableStateFlow

object FilterBus {
    val filter = MutableStateFlow<String?>(null)
}