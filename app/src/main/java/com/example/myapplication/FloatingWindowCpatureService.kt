package com.example.myapplication

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.graphics.Rect
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import androidx.annotation.RequiresApi
import org.json.JSONArray
import org.json.JSONObject

@RequiresApi(Build.VERSION_CODES.DONUT)
class FloatingWindowCaptureService : AccessibilityService() {

    private val tag = "WindowCaptureService"
    // !! IMPORTANT: Replace this with the actual package name of the app (App B) that owns the floating window
    //private val targetAppPackageName = "com.alipay.hulu" // <<<< CHANGE THIS
    private val targetAppPackageName = "com.eg.android.AlipayGphone" // <<<< CHANGE THIS

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(tag, "Accessibility Service Connected. Targeting package: $targetAppPackageName")

        // Configure the service programmatically (can also be done via XML)
        val serviceInfo = AccessibilityServiceInfo()
        serviceInfo.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                AccessibilityEvent.TYPE_VIEW_SCROLLED // Add other event types if needed

        // Only listen to events from the target application if specified
        // If targetAppPackageName is empty, it will listen to all apps (more resource intensive)
        if (targetAppPackageName.isNotEmpty()) {
            serviceInfo.packageNames = arrayOf(targetAppPackageName)
        }

        serviceInfo.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC // Or other appropriate feedback type
        serviceInfo.notificationTimeout = 100 // Milliseconds

        // Crucial flags for getting window content and more details
        serviceInfo.flags = serviceInfo.flags or
                AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                4

        this.serviceInfo = serviceInfo

        // Perform an initial scan
        Log.d(tag, "Performing initial scan for floating windows from $targetAppPackageName.")
        captureTargetWindowLayout()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        Log.d(tag, "Accessibility Event Received: ${event?.eventType}")
        if (event == null) return

        val packageName = event.packageName?.toString() ?: ""
        Log.d(tag, "Event: ${AccessibilityEvent.eventTypeToString(event.eventType)}, Package: $packageName, Source: ${event.source?.className}")

        // Check if the event is from the target package (if specified) or if we are listening to all
        if (targetAppPackageName.isNotEmpty() && packageName != targetAppPackageName) {
            return
        }

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                Log.d(tag, "Window state or content changed for package: $packageName. Capturing layout.")
                captureTargetWindowLayout()
            }
            // You can handle other events like TYPE_VIEW_SCROLLED if the window content might change without a window event
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                Log.d(tag, "View scrolled in package: $packageName. Re-capturing layout.")
                captureTargetWindowLayout()
            }
        }
    }

    private fun captureTargetWindowLayout() {
        val windows: List<AccessibilityWindowInfo>? = getWindows() // Property of AccessibilityService
        if (windows.isNullOrEmpty()) {
            Log.w(tag, "No windows found by the service.")
            return
        }

        var foundTargetWindow = false
        for (window in windows) {
            val rootNode: AccessibilityNodeInfo? = window.root
            if (rootNode != null) {
                val windowPackageName = rootNode.packageName?.toString() ?: ""
                val windowTitle = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) window.title else "N/A (API < 28)"
                val windowType = window.type

                // Log.d(tag, "Scanning Window ID: ${window.id}, Type: $windowType, Package: $windowPackageName, Title: '$windowTitle', Layer: ${window.layer}, Focused: ${window.isFocused}")

                // Identify the target floating window.
                // This logic might need adjustment based on how App B's floating window is characterized.
                // Common checks:
                // 1. Package Name
                // 2. Window Type (Application windows, System Alert windows etc. might have different types)
                // 3. Absence of certain flags (e.g., not a system decor window if that's not what you want)
                // 4. Window Title (if it's consistent)
                Log.i(tag, "Scanning Window ID: ${window.id}, Type: $windowType, Package: $windowPackageName, Title: '$windowTitle', Layer: ${window.layer}, Focused: ${window.isFocused}")
                if (windowPackageName == targetAppPackageName) {
                    // Further checks to ensure it's the *floating* window, not the main app window if App B is also in foreground
                    // For example, floating windows might be of TYPE_APPLICATION but not have the main activity's title,
                    // or they might be of a different type like TYPE_PHONE or TYPE_SYSTEM_ALERT.
                    // If 'adb shell dumpsys window windows' gives you a specific title or flag for the floater, use it.

                    Log.i(tag, "Found a window from target package '$targetAppPackageName': Window ID ${window.id}, Title: '$windowTitle', Type: $windowType")
                    // You might need more specific checks here if App B can have multiple windows
                    // For now, we assume any window from App B could be the target

                    val layoutJson = dumpNodeHierarchyToJson(rootNode)
                    Log.i(tag, "Captured Layout for Window ID ${window.id} from $targetAppPackageName:\n${layoutJson.toString(2)}")
                    // TODO: Do something with the layoutJson (e.g., send it to your test runner, save to file)
                    foundTargetWindow = true
                    rootNode.recycle() // Recycle the root node after processing
                    // break // Remove break if App B can have multiple relevant floating windows
                } else {
                    rootNode.recycle() // Recycle if not the target
                }
            }
        }
        if (!foundTargetWindow && targetAppPackageName.isNotEmpty()) {
            Log.w(tag, "No window matching package '$targetAppPackageName' found in the current window list.")
        } else if (!foundTargetWindow) {
            Log.d(tag, "No windows processed, or target package not specified.")
        }
    }

    private fun dumpNodeHierarchyToJson(node: AccessibilityNodeInfo?, depth: Int = 0): JSONObject {
        val jsonNode = JSONObject()
        if (node == null) {
            jsonNode.put("error", "Node is null")
            return jsonNode
        }

        jsonNode.put("className", node.className?.toString() ?: "null")
        jsonNode.put("packageName", node.packageName?.toString() ?: "null")
        jsonNode.put("text", node.text?.toString() ?: "")
        jsonNode.put("contentDescription", node.contentDescription?.toString() ?: "")
        jsonNode.put("resourceId", node.viewIdResourceName ?: "")

        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        val boundsJson = JSONObject()
        boundsJson.put("left", bounds.left)
        boundsJson.put("top", bounds.top)
        boundsJson.put("right", bounds.right)
        boundsJson.put("bottom", bounds.bottom)
        jsonNode.put("boundsInScreen", boundsJson)

        jsonNode.put("isClickable", node.isClickable)
        jsonNode.put("isLongClickable", node.isLongClickable)
        jsonNode.put("isFocusable", node.isFocusable)
        jsonNode.put("isFocused", node.isFocused)
        jsonNode.put("isVisibleToUser", node.isVisibleToUser)
        jsonNode.put("isEnabled", node.isEnabled)
        jsonNode.put("isPassword", node.isPassword)
        jsonNode.put("isScrollable", node.isScrollable)
        jsonNode.put("isSelected", node.isSelected)
        jsonNode.put("isChecked", node.isChecked)
        jsonNode.put("childCount", node.childCount)


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            jsonNode.put("hintText", node.hintText?.toString() ?: "")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) { // API 28+
            val drawingOrder = node.drawingOrder
            jsonNode.put("drawingOrder", drawingOrder)
        }


        val childrenArray = JSONArray()
        for (i in 0 until node.childCount) {
            val childNode = node.getChild(i)
            if (childNode != null) {
                childrenArray.put(dumpNodeHierarchyToJson(childNode, depth + 1))
                // Child nodes are "owned" by the parent in terms of recycling
                // when obtained via getChild(). Only recycle the initial root node.
            } else {
                val nullChildJson = JSONObject()
                nullChildJson.put("error", "Child node at index $i is null")
                childrenArray.put(nullChildJson)
            }
        }
        if (childrenArray.length() > 0) {
            jsonNode.put("children", childrenArray)
        }

        // Do NOT recycle 'node' here if it's being used by its caller (in the loop)
        // Recycling should happen for the root node once the entire hierarchy is processed.
        return jsonNode
    }


    override fun onInterrupt() {
        Log.w(tag, "Accessibility Service Interrupted.")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(tag, "Accessibility Service Destroyed.")
    }
}