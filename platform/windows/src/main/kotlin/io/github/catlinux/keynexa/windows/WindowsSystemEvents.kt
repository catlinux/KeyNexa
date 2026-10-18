package io.github.catlinux.keynexa.windows

import com.sun.jna.Native
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinUser
import com.sun.jna.win32.StdCallLibrary
import io.github.catlinux.keynexa.desktop.SystemEvents

/** The name of the thread that owns the hidden window, so it is easy to tell apart while debugging. */
private const val THREAD_NAME = "keynexa-system-events"

/** The window class the hidden window is registered under. */
private const val WINDOW_CLASS = "KeyNexaSystemEvents"

/** WM_WTSSESSION_CHANGE, with WTS_SESSION_LOCK as its wParam. */
private const val WM_WTSSESSION_CHANGE = 0x02B1
private const val WTS_SESSION_LOCK = 0x7

/** WM_POWERBROADCAST, with PBT_APMSUSPEND as its wParam. */
private const val WM_POWERBROADCAST = 0x0218
private const val PBT_APMSUSPEND = 0x4

/** NOTIFY_FOR_THIS_SESSION: report only what happens to this session. */
private const val NOTIFY_FOR_THIS_SESSION = 0x0

/** How long [WindowsSystemEvents.close] waits for the thread to end. */
private const val CLOSE_TIMEOUT_MILLIS = 2_000L

/** WTSRegisterSessionNotification and its undo, which jna-platform does not expose. */
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
private interface Wts32 : StdCallLibrary {
    fun WTSRegisterSessionNotification(
        hwnd: WinDef.HWND,
        flags: Int,
    ): Boolean

    fun WTSUnRegisterSessionNotification(hwnd: WinDef.HWND): Boolean

    companion object {
        val INSTANCE: Wts32 = Native.load("wtsapi32", Wts32::class.java)
    }
}

/**
 * [SystemEvents] over the Windows session and power messages.
 *
 * A dedicated daemon thread creates a hidden top-level window and runs the message loop; the window is top-level and
 * not message-only, because message-only windows never receive broadcast messages such as WM_POWERBROADCAST. The
 * thread asks for the session notifications with WTSRegisterSessionNotification and answers WM_WTSSESSION_CHANGE with
 * WTS_SESSION_LOCK and WM_POWERBROADCAST with PBT_APMSUSPEND. The callbacks run on that thread, so a caller that
 * touches its own state must hop to its own thread first. [close] asks the window to close and waits briefly for the
 * thread to end.
 */
class WindowsSystemEvents : SystemEvents {
    private val guard = Any()
    private var thread: Thread? = null

    @Volatile
    private var window: WinDef.HWND? = null

    /** Held so the garbage collector never drops the callback the window class points at. */
    @Volatile
    private var windowProc: WinUser.WindowProc? = null

    @Volatile
    private var onSessionLocked: () -> Unit = {}

    @Volatile
    private var onSuspend: () -> Unit = {}

    override fun listen(
        onSessionLocked: () -> Unit,
        onSuspend: () -> Unit,
    ) {
        synchronized(guard) {
            if (thread != null) return
            this.onSessionLocked = onSessionLocked
            this.onSuspend = onSuspend
            val started = Thread(::runLoop, THREAD_NAME)
            started.isDaemon = true
            thread = started
            started.start()
        }
    }

    override fun close() {
        val running = synchronized(guard) { thread } ?: return
        window?.let { user32.PostMessage(it, WinUser.WM_CLOSE, WinDef.WPARAM(0), WinDef.LPARAM(0)) }
        running.join(CLOSE_TIMEOUT_MILLIS)
        synchronized(guard) { thread = null }
    }

    /** Creates the hidden window, watches its messages and cleans everything up on the way out. */
    private fun runLoop() {
        val callback = WinUser.WindowProc { hwnd, message, wParam, lParam -> dispatch(hwnd, message, wParam, lParam) }
        windowProc = callback
        val module = Kernel32.INSTANCE.GetModuleHandle(null)
        val windowClass =
            WinUser.WNDCLASSEX().apply {
                cbSize = size()
                lpfnWndProc = callback
                hInstance = module
                lpszClassName = WINDOW_CLASS
            }
        user32.RegisterClassEx(windowClass)
        val created = user32.CreateWindowEx(0, WINDOW_CLASS, WINDOW_CLASS, 0, 0, 0, 0, 0, null, null, module, null)
        if (created == null) {
            user32.UnregisterClass(WINDOW_CLASS, module)
            return
        }
        window = created
        wts.WTSRegisterSessionNotification(created, NOTIFY_FOR_THIS_SESSION)
        val message = WinUser.MSG()
        while (user32.GetMessage(message, null, 0, 0) > 0) {
            user32.TranslateMessage(message)
            user32.DispatchMessage(message)
        }
        wts.WTSUnRegisterSessionNotification(created)
        user32.DestroyWindow(created)
        user32.UnregisterClass(WINDOW_CLASS, module)
        window = null
    }

    /** Answers the two messages that matter and hands everything else back to Windows. */
    private fun dispatch(
        hwnd: WinDef.HWND,
        message: Int,
        wParam: WinDef.WPARAM,
        lParam: WinDef.LPARAM,
    ): WinDef.LRESULT =
        when (message) {
            WM_WTSSESSION_CHANGE -> {
                if (wParam.toInt() == WTS_SESSION_LOCK) notify(onSessionLocked)
                WinDef.LRESULT(0)
            }
            WM_POWERBROADCAST -> {
                if (wParam.toInt() == PBT_APMSUSPEND) notify(onSuspend)
                WinDef.LRESULT(1)
            }
            WinUser.WM_CLOSE -> {
                user32.PostQuitMessage(0)
                WinDef.LRESULT(0)
            }
            else -> user32.DefWindowProc(hwnd, message, wParam, lParam)
        }

    // A callback that throws must not unwind through the native window procedure and take the message loop down.
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private fun notify(callback: () -> Unit) {
        try {
            callback()
        } catch (failure: RuntimeException) {
            // The caller's own problem; the watch keeps running.
        }
    }

    private companion object {
        val user32 = User32.INSTANCE
        val wts = Wts32.INSTANCE
    }
}
