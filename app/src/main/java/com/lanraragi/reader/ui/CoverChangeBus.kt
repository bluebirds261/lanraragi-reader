package com.lanraragi.reader.ui

import kotlinx.coroutines.flow.MutableStateFlow

object CoverChangeBus {
    val version = MutableStateFlow(0)
}