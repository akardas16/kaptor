package com.akardas.kaptor.util

import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.timeIntervalSince1970

actual fun currentEpochMillis(): Long =
    (NSDate().timeIntervalSince1970 * 1000).toLong()

private val shareDateFormatter = NSDateFormatter().apply {
    dateFormat = "EEE MMM dd HH:mm:ss zzz yyyy"
}

actual fun formatEpochMillis(millis: Long): String =
    shareDateFormatter.stringFromDate(NSDate.dateWithTimeIntervalSince1970(millis / 1000.0))
