package com.lanraragi.reader.ui

import kotlinx.coroutines.flow.MutableStateFlow

object LibraryRefreshBus {
    val tick = MutableStateFlow(0)
}