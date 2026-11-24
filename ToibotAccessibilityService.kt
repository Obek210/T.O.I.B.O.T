package com.example.toibot

import android.accessibilityservice.AccessibilityService
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class ToibotAccessibilityService : AccessibilityService() {

    companion object {
        var instance: ToibotAccessibilityService? = null
    }

    private val handler = Handler(Looper.getMainLooper())

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    fun takeSystemScreenshot(onResult: (Boolean) -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            takeScreenshot(
                Display.DEFAULT_DISPLAY,
                mainExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(screenshot: ScreenshotResult) {
                        screenshot.hardwareBuffer.close()
                        onResult(true)
                    }
                    override fun onFailure(errorCode: Int) {
                        onResult(false)
                    }
                }
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val ok = performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
            onResult(ok)
        } else {
            onResult(false)
        }
    }

    fun scrollDownActiveScreen(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            performGlobalAction(GLOBAL_ACTION_SCROLL_FORWARD)
        } else {
            false
        }
    }

    fun goBack(): Boolean = performGlobalAction(GLOBAL_ACTION_BACK)

    fun goHome(): Boolean = performGlobalAction(GLOBAL_ACTION_HOME)

    fun clickFirstVideoResultAfterDelay(delayMs: Long = 2200L, onDone: ((Boolean) -> Unit)? = null) {
        handler.postDelayed({
            val clicked = clickFirstClickableVideoCandidate()
            onDone?.invoke(clicked)
        }, delayMs)
    }

    private fun clickFirstClickableVideoCandidate(): Boolean {
        val root = rootInActiveWindow ?: return false
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)

        val candidates = mutableListOf<AccessibilityNodeInfo>()
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            val desc = node.contentDescription?.toString()?.trim() ?: ""
            val txt = node.text?.toString()?.trim() ?: ""
            val hasMeaningfulLabel = desc.length > 6 || txt.length > 6

            if (node.isClickable && hasMeaningfulLabel) {
                val lowerLabel = "$desc $txt".lowercase()
                val isNavOrFilter = lowerLabel.contains("home") ||
                    lowerLabel.contains("shorts") ||
                    lowerLabel.contains("subscriptions") ||
                    lowerLabel.contains("library") ||
                    lowerLabel.contains("all") ||
                    lowerLabel.contains("filter") ||
                    lowerLabel.contains("search")
                if (!isNavOrFilter) {
                    candidates.add(node)
                }
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }

        // Birinchi mos keluvchi video kartochkasini bosish
        for (candidate in candidates) {
            if (candidate.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                return true
            }
            var parent = candidate.parent
            var depth = 0
            while (parent != null && depth < 3) {
                if (parent.isClickable && parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    return true
                }
                parent = parent.parent
                depth++
            }
        }
        return false
    }

    fun clickSendButtonInActiveApp(): Boolean {
        val root = rootInActiveWindow ?: return false
        val sendKeywords = listOf("Send", "Отправить", "Yuborish", "Jo'natish")
        for (kw in sendKeywords) {
            val nodes = root.findAccessibilityNodeInfosByText(kw)
            for (node in nodes) {
                if (node.isClickable) {
                    return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                }
            }
        }
        return false
    }
}
