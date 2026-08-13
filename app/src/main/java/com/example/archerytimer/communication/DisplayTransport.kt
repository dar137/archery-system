package com.example.archerytimer.communication

import kotlinx.coroutines.flow.Flow

interface DisplayTransport {
    fun messages(): Flow<DisplayMessage>
}
