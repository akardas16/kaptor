package com.akardas.kaptor.util

/** Current wall-clock time in epoch milliseconds. Implemented per platform. */
expect fun currentEpochMillis(): Long

/** Formats an epoch-millis instant as a human-readable local date/time string. */
expect fun formatEpochMillis(millis: Long): String
