package com.grafito.finanzas.notifications

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import java.text.Normalizer
import java.util.Locale

internal class AccessibilityReceiptReader(
    private val service: AccessibilityService,
    private val sourcePackage: String
) {
    data class Probe(val parts: List<List<String>>, val signals: Int, val rootChildren: Int)

    fun initialWindowId(): Int? {
        val root = service.rootInActiveWindow ?: return null
        return try {
            root.windowId.takeIf { root.packageName?.toString() == sourcePackage }
        } finally {
            release(root)
        }
    }

    fun snapshots(
        windowId: Int,
        confirmationTitle: String? = null,
        searchOtherApplicationWindows: Boolean = false
    ): List<List<String>> {
        var windowRoots = emptyList<AccessibilityNodeInfo>()
        var activeRoot: AccessibilityNodeInfo? = null
        return try {
            windowRoots = applicationWindowRoots(windowId, searchOtherApplicationWindows)
            activeRoot = service.rootInActiveWindow
            (listOfNotNull(activeRoot) + windowRoots).distinctBy { it.windowId }
                .filter { it.packageName?.toString() == sourcePackage }
                .map { root ->
                    if (confirmationTitle == null || hasConfirmationTitle(root, confirmationTitle)) visibleText(root)
                    else emptyList()
                }
        } finally {
            windowRoots.forEach(::release)
            if (windowRoots.none { it === activeRoot }) activeRoot?.let(::release)
        }
    }

    fun probeWindows(
        windowId: Int?,
        searchOtherApplicationWindows: Boolean = false,
        onlyEventWindow: Boolean = false
    ): Probe {
        val windowRoots = mutableListOf<AccessibilityNodeInfo>()
        var activeRoot: AccessibilityNodeInfo? = null
        var signals = WINDOW_QUERY
        fun <T> query(block: () -> T): T? = try { block() } catch (_: SecurityException) {
            signals = signals or QUERY_DENIED
            null
        } catch (error: java.util.concurrent.CancellationException) {
            throw error
        } catch (_: Exception) {
            signals = signals or QUERY_FAILED
            null
        }
        return try {
            val windows = query { service.windows }.orEmpty()
            try {
                windows.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION &&
                    (it.id == windowId || searchOtherApplicationWindows)
                }.sortedBy { if (it.id == windowId) 0 else 1 }.forEach { window ->
                    val eventWindow = window.id == windowId
                    if (eventWindow) signals = signals or EVENT_WINDOW_LISTED
                    val root = query { if (Build.VERSION.SDK_INT >= 33) window.getRoot(0) else window.root }
                    if (root != null) windowRoots.add(root)
                    else if (eventWindow) signals = signals or EVENT_WINDOW_ROOT_MISSING
                }
            } finally {
                @Suppress("DEPRECATION")
                if (Build.VERSION.SDK_INT < 33) windows.forEach { it.recycle() }
            }
            if (!onlyEventWindow) activeRoot = query {
                if (Build.VERSION.SDK_INT >= 33) service.getRootInActiveWindow(0) else service.rootInActiveWindow
            }
            if (activeRoot != null) signals = signals or ACTIVE_ROOT_AVAILABLE
            if (activeRoot?.packageName?.toString() == sourcePackage) signals = signals or ACTIVE_ROOT_MATCHES
            val candidates = (listOfNotNull(activeRoot) + windowRoots)
                .filter { it.packageName?.toString() == sourcePackage }
                .distinctBy { it.windowId }
            if (candidates.isNotEmpty()) signals = signals or SOURCE_WINDOW_AVAILABLE
            var rootChildren = 0
            val parts = candidates.map { root ->
                query {
                    if (!root.isPassword && !root.isEditable && !isSensitive(root)) {
                        rootChildren += root.childCount
                    }
                    visibleText(root, skipSensitive = true)
                }.orEmpty()
            }
            Probe(parts, signals, rootChildren)
        } finally {
            windowRoots.forEach(::release)
            if (windowRoots.none { it === activeRoot }) activeRoot?.let(::release)
        }
    }

    fun sourceSnapshot(source: AccessibilityNodeInfo): List<String> {
        if (source.packageName?.toString() != sourcePackage || source.isPassword || source.isEditable || isSensitive(source)) {
            return emptyList()
        }
        val parents = mutableListOf<AccessibilityNodeInfo>()
        var root = source
        try {
            for (depth in 0 until 24) {
                val parent = root.parent ?: break
                parents.add(parent)
                if (parent.packageName?.toString() != sourcePackage) break
                if (parent.isPassword || parent.isEditable || isSensitive(parent)) return emptyList()
                root = parent
            }
            return visibleText(root, skipSensitive = true)
        } finally {
            parents.forEach(::release)
        }
    }

    private fun hasConfirmationTitle(root: AccessibilityNodeInfo, title: String): Boolean {
        val matches = root.findAccessibilityNodeInfosByText(title.substringAfterLast(' '))
        val expected = normalizedTitle(title)
        return try {
            matches.any { node ->
                node.packageName?.toString() == sourcePackage && node.isVisibleToUser &&
                    !node.isPassword && !node.isEditable &&
                    (normalizedTitle(node.text) == expected || normalizedTitle(node.contentDescription) == expected)
            }
        } finally {
            matches.forEach(::release)
        }
    }

    private fun normalizedTitle(text: CharSequence?): String =
        Normalizer.normalize(text?.toString().orEmpty(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
            .replace(Regex("[\\s\\p{Z}]+"), " ").trim().trim('¡', '!').trim().lowercase(Locale.ROOT)

    @Suppress("DEPRECATION")
    private fun applicationWindowRoots(windowId: Int, searchOtherWindows: Boolean): List<AccessibilityNodeInfo> {
        val windows = service.windows
        val roots = mutableListOf<AccessibilityNodeInfo>()
        return try {
            windows.filter {
                it.type == AccessibilityWindowInfo.TYPE_APPLICATION && (it.id == windowId || searchOtherWindows)
            }.sortedBy { if (it.id == windowId) 0 else 1 }
                .forEach { window -> window.root?.let(roots::add) }
            roots
        } catch (error: Exception) {
            roots.forEach(::release)
            throw error
        } finally {
            if (Build.VERSION.SDK_INT < 33) windows.forEach { it.recycle() }
        }
    }

    private fun isSensitive(node: AccessibilityNodeInfo): Boolean =
        Build.VERSION.SDK_INT >= 34 && node.isAccessibilityDataSensitive

    private fun visibleText(root: AccessibilityNodeInfo, skipSensitive: Boolean = false): List<String> {
        val lines = mutableListOf<String>()
        var budget = 200
        fun visit(node: AccessibilityNodeInfo, depth: Int) {
            if (--budget < 0 || node.packageName?.toString() != sourcePackage || node.isPassword || node.isEditable) return
            if (skipSensitive && isSensitive(node)) return
            if (node.isVisibleToUser) {
                val text = node.text?.take(300)?.toString()
                val description = node.contentDescription?.take(300)?.toString()
                if (skipSensitive && Build.VERSION.SDK_INT >= 28) {
                    val paneTitle = node.paneTitle?.take(300)?.toString()
                    if (!paneTitle.isNullOrBlank() && paneTitle != text && paneTitle != description) lines.add(paneTitle)
                }
                if (!text.isNullOrBlank()) lines.add(text)
                if (!description.isNullOrBlank() && description != text) lines.add(description)
            }
            if (depth == 24) return
            for (index in 0 until node.childCount) {
                if (budget <= 0) break
                val child = node.getChild(index) ?: continue
                try { visit(child, depth + 1) } finally { release(child) }
            }
        }
        visit(root, 0)
        return lines
    }

    @Suppress("DEPRECATION")
    private fun release(node: AccessibilityNodeInfo) {
        if (Build.VERSION.SDK_INT < 33) node.recycle()
    }

    companion object {
        const val ACTIVE_ROOT_AVAILABLE = 1
        const val ACTIVE_ROOT_MATCHES = 2
        const val SOURCE_WINDOW_AVAILABLE = 8
        const val READ_FROM_EVENT = 128
        const val WINDOW_QUERY = 256
        const val QUERY_DENIED = 512
        const val QUERY_FAILED = 1024
        const val EVENT_RECEIVED = 2048
        const val TEXT_AVAILABLE = 4096
        const val CONFIRMATION_RECOGNIZED = 8192
        const val EVENT_WINDOW_LISTED = 16384
        const val EVENT_WINDOW_ROOT_MISSING = 32768
        const val SYSTEM_WINDOW_EVENT = 65536

        fun enabledInAndroid(context: Context, serviceClass: Class<out AccessibilityService>): Boolean? {
            val setting = try {
                Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            } catch (_: SecurityException) {
                return null
            }
            val component = ComponentName(context, serviceClass)
            return setting.orEmpty().split(':').any { ComponentName.unflattenFromString(it) == component }
        }
    }
}
