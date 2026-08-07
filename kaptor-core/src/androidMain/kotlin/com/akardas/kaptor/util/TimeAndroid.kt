package com.akardas.kaptor.util

actual fun currentEpochMillis(): Long = System.currentTimeMillis()

actual fun formatEpochMillis(millis: Long): String = java.util.Date(millis).toString()
