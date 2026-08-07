package com.akardas.kaptor.ui

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSString
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.writeToFile
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication

/**
 * iOS implementation of [KaptorShareHandler] using `UIActivityViewController`, presented from
 * the key window's root view controller.
 */
class IosShareHandler : KaptorShareHandler {

    override fun shareText(text: String, subject: String) {
        present(listOf(text))
    }

    @OptIn(BetaInteropApi::class, ExperimentalForeignApi::class)
    override fun shareFile(fileName: String, text: String, mimeType: String) {
        val path = NSTemporaryDirectory() + fileName
        (text as NSString).writeToFile(path, atomically = true, encoding = NSUTF8StringEncoding, error = null)
        present(listOf(NSURL.fileURLWithPath(path)))
    }

    private fun present(items: List<*>) {
        val controller = UIActivityViewController(activityItems = items, applicationActivities = null)
        val root = UIApplication.sharedApplication.keyWindow?.rootViewController
        root?.presentViewController(controller, animated = true, completion = null)
    }
}
