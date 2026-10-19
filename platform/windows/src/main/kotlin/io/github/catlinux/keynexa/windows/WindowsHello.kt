package io.github.catlinux.keynexa.windows

import com.sun.jna.Function
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.WString
import com.sun.jna.platform.win32.Guid
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.Ole32
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.ptr.ByteByReference
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference
import com.sun.jna.win32.StdCallLibrary
import io.github.catlinux.keynexa.desktop.HelloResult
import java.util.concurrent.atomic.AtomicReference

/** combase.dll entry points for the Windows Runtime, which jna-platform does not expose. */
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
private interface Combase32 : StdCallLibrary {
    fun RoInitialize(initType: Int): Int

    fun RoUninitialize()

    fun RoGetActivationFactory(
        activatableClassId: Pointer,
        iid: Guid.GUID,
        factory: PointerByReference,
    ): Int

    fun WindowsCreateString(
        sourceString: WString,
        length: Int,
        string: PointerByReference,
    ): Int

    fun WindowsDeleteString(string: Pointer): Int

    companion object {
        val INSTANCE: Combase32 = Native.load("combase", Combase32::class.java)
    }
}

/** AllowSetForegroundWindow and keybd_event, which jna-platform does not expose. */
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
private interface AllowSetForegroundWindow32 : StdCallLibrary {
    fun AllowSetForegroundWindow(processId: Int): Boolean

    fun keybd_event(
        virtualKey: Byte,
        scanCode: Byte,
        flags: Int,
        extraInfo: Pointer?,
    )

    companion object {
        val INSTANCE: AllowSetForegroundWindow32 = Native.load("user32", AllowSetForegroundWindow32::class.java)
    }
}

/** A failed WinRT call; the message is a short reason without secrets. */
private class HelloException(
    message: String,
) : Exception(message)

/**
 * Windows Hello through the documented `Windows.Security.Credentials.KeyCredentialManager` API (ADR-0014), called over
 * its COM ABI with JNA: there is no WinRT projection for the JVM.
 *
 * KeyNexa keeps one RSA key credential per Windows user. The key never leaves the Hello container; the only thing read
 * from it is the signature of a challenge, which is deterministic (PKCS#1 v1.5), so the same challenge always yields
 * the same bytes and a wrapping key can be derived from them. Each [sign] shows the Hello prompt.
 *
 * Every call runs on its own short-lived thread initialised as a multithreaded apartment, so it neither depends on nor
 * disturbs the COM state of the caller (the AWT thread is a single-threaded apartment). Call it from a background
 * dispatcher: it blocks until the user answers the prompt.
 */
class WindowsHello(
    private val credentialName: String = DEFAULT_CREDENTIAL_NAME,
) {
    /** The native handle of [window], to bring it in front of the Hello prompt; null if it has none yet. */
    fun handleOf(window: java.awt.Window): Long? =
        runCatching { Native.getComponentID(window) }.getOrNull()?.takeIf { it != 0L }

    /** True when this Windows user has Hello (PIN, face or fingerprint) set up. A failure is answered with false. */
    fun isSupported(): Boolean =
        runCatching {
            onComThread {
                Com().use { com ->
                    val statics = com.activationFactory(KEY_CREDENTIAL_MANAGER, IID_KEY_CREDENTIAL_MANAGER_STATICS)
                    val operation = com.out { statics.call(SLOT_IS_SUPPORTED_ASYNC, it) }
                    com.await(operation, SHORT_TIMEOUT_MS)
                    val supported = ByteByReference()
                    check(operation.call(SLOT_GET_RESULTS, supported) >= 0) { "GetResults" }
                    supported.value.toInt() != 0
                }
            }
        }.getOrDefault(false)

    /**
     * Signs [challenge] with the Hello key, creating the key first if this user has none (which also shows a prompt).
     * [window] is the native handle of the KeyNexa window, brought to the foreground first so the Hello dialog
     * opens over it instead of behind it.
     */
    fun sign(
        challenge: ByteArray,
        window: Long? = null,
    ): HelloResult =
        try {
            onComThread { signOnThread(challenge, window) }
        } catch (e: HelloException) {
            HelloResult.Failed(e.message ?: "Hello error")
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            HelloResult.Failed(e.javaClass.simpleName)
        }

    private fun signOnThread(
        challenge: ByteArray,
        window: Long?,
    ): HelloResult =
        Com().use { com ->
            val statics = com.activationFactory(KEY_CREDENTIAL_MANAGER, IID_KEY_CREDENTIAL_MANAGER_STATICS)
            val buffers = com.activationFactory(CRYPTOGRAPHIC_BUFFER, IID_CRYPTOGRAPHIC_BUFFER_STATICS)
            if (window != null) bringToFront(WinDef.HWND(Pointer(window)))

            val credential =
                when (val found = com.openOrCreate(statics)) {
                    is Retrieval.Found -> found.credential
                    is Retrieval.Refused -> return@use statusResult(found.status)
                }

            val input = Memory(challenge.size.toLong())
            try {
                input.write(0, challenge, 0, challenge.size)
                val buffer = com.out { buffers.call(SLOT_CREATE_FROM_BYTE_ARRAY, challenge.size, input, it) }
                val operation = com.out { credential.call(SLOT_REQUEST_SIGN_ASYNC, buffer, it) }
                com.await(operation, SIGN_TIMEOUT_MS, front = true)
                val result = com.out { operation.call(SLOT_GET_RESULTS, it) }
                val status = IntByReference()
                check(result.call(SLOT_RESULT_STATUS, status) >= 0) { "Status" }
                if (status.value != STATUS_SUCCESS) return@use statusResult(status.value)
                val signature = com.out { result.call(SLOT_RESULT_VALUE, it) }
                HelloResult.Signed(copyToByteArray(buffers, signature))
            } finally {
                input.clear()
                input.close()
            }
        }

    /**
     * Windows only lets the foreground process take the foreground, and after a first Hello prompt closes KeyNexa no
     * longer is it. A synthetic Alt press lifts that lock for this process, and ASFW_ANY lets the Hello broker, which
     * we cannot identify beforehand, show its dialog with keyboard focus.
     */
    private fun bringToFront(hwnd: WinDef.HWND) {
        val user32 = User32.INSTANCE
        val foreground = AllowSetForegroundWindow32.INSTANCE
        foreground.keybd_event(VK_MENU, 0, 0, null)
        foreground.keybd_event(VK_MENU, 0, KEYEVENTF_KEYUP, null)
        user32.ShowWindow(hwnd, SW_RESTORE)
        user32.SetForegroundWindow(hwnd)
        foreground.AllowSetForegroundWindow(ASFW_ANY)
    }

    private fun copyToByteArray(
        buffers: Pointer,
        buffer: Pointer,
    ): ByteArray {
        val size = IntByReference()
        val data = PointerByReference()
        check(buffers.call(SLOT_COPY_TO_BYTE_ARRAY, buffer, size, data) >= 0) { "CopyToByteArray" }
        val native = data.value ?: return ByteArray(0)
        return try {
            native.getByteArray(0, size.value)
        } finally {
            native.clear(size.value.toLong())
            Ole32.INSTANCE.CoTaskMemFree(native)
        }
    }

    private fun statusResult(status: Int): HelloResult =
        when (status) {
            STATUS_USER_CANCELED, STATUS_USER_PREFERS_PASSWORD -> HelloResult.Cancelled
            else -> HelloResult.Failed("Hello status ${statusName(status)}")
        }

    /** Opens the Hello key, or creates it if missing (FailIfExists: replacing it would change the signatures). */
    private fun Com.openOrCreate(statics: Pointer): Retrieval {
        val opened = retrieve(statics, SLOT_OPEN_ASYNC, SHORT_TIMEOUT_MS)
        if (opened !is Retrieval.Refused || opened.status != STATUS_NOT_FOUND) return opened
        return retrieve(statics, SLOT_REQUEST_CREATE_ASYNC, SIGN_TIMEOUT_MS, create = true)
    }

    private sealed interface Retrieval {
        class Found(
            val credential: Pointer,
        ) : Retrieval

        class Refused(
            val status: Int,
        ) : Retrieval
    }

    /** OpenAsync, or RequestCreateAsync when [create] is set, awaited and unpacked. */
    private fun Com.retrieve(
        statics: Pointer,
        slot: Int,
        timeoutMs: Long,
        create: Boolean = false,
    ): Retrieval {
        val operation =
            withHString(credentialName) { name ->
                out {
                    if (create) statics.call(slot, name, CREATION_FAIL_IF_EXISTS, it) else statics.call(slot, name, it)
                }
            }
        await(operation, timeoutMs, front = create)
        val result = out { operation.call(SLOT_GET_RESULTS, it) }
        val status = IntByReference()
        check(result.call(SLOT_RESULT_STATUS, status) >= 0) { "Status" }
        if (status.value != STATUS_SUCCESS) return Retrieval.Refused(status.value)
        return Retrieval.Found(out { result.call(SLOT_RESULT_VALUE, it) })
    }

    private fun <T> onComThread(block: () -> T): T {
        val outcome = AtomicReference<Result<T>>()
        val thread =
            Thread {
                // RPC_E_CHANGED_MODE cannot happen on a fresh thread; S_FALSE only means "already initialised".
                val initialised = combase.RoInitialize(RO_INIT_MULTITHREADED) >= 0
                outcome.set(runCatching(block))
                if (initialised) combase.RoUninitialize()
            }
        thread.isDaemon = true
        thread.start()
        thread.join()
        return outcome.get().getOrThrow()
    }

    private class Com : AutoCloseable {
        private val held = ArrayDeque<Pointer>()

        fun own(pointer: Pointer): Pointer {
            held.addFirst(pointer)
            return pointer
        }

        /** Calls [block] with an out parameter and keeps the interface pointer it returns. */
        fun out(block: (PointerByReference) -> Int): Pointer {
            val ref = PointerByReference()
            val hr = block(ref)
            if (hr < 0) throw HelloException("HRESULT 0x" + Integer.toHexString(hr))
            return own(ref.value ?: throw HelloException("null result"))
        }

        fun activationFactory(
            className: String,
            iid: String,
        ): Pointer =
            withHString(className) { name ->
                out { combase.RoGetActivationFactory(name, Guid.GUID.fromString(iid), it) }
            }

        fun <T> withHString(
            text: String,
            block: (Pointer) -> T,
        ): T {
            val hstring = PointerByReference()
            val hr = combase.WindowsCreateString(WString(text), text.length, hstring)
            if (hr < 0) throw HelloException("WindowsCreateString")
            try {
                return block(hstring.value)
            } finally {
                combase.WindowsDeleteString(hstring.value)
            }
        }

        /** Polls IAsyncInfo until the operation completes; completion callbacks would need a COM object of our own. */
        fun await(
            operation: Pointer,
            timeoutMs: Long,
            front: Boolean = false,
        ) {
            val info = out { operation.call(0, Guid.GUID.fromString(IID_ASYNC_INFO), it) }
            val deadline = System.nanoTime() + timeoutMs * NANOS_PER_MS
            val status = IntByReference()
            var ticks = 0
            while (true) {
                check(info.call(SLOT_ASYNC_STATUS, status) >= 0) { "IAsyncInfo.Status" }
                when (status.value) {
                    ASYNC_COMPLETED -> return
                    ASYNC_STARTED -> Unit
                    else -> throw HelloException("operation failed")
                }
                if (System.nanoTime() > deadline) {
                    info.call(SLOT_ASYNC_CANCEL)
                    throw HelloException("timeout")
                }
                if (front && ticks++ % FRONT_EVERY_TICKS == 0) frontHelloDialog()
                Thread.sleep(POLL_MS)
            }
        }

        override fun close() {
            held.forEach { it.call(SLOT_RELEASE) }
            held.clear()
        }

        private companion object {
            const val NANOS_PER_MS = 1_000_000L
        }
    }

    companion object {
        const val DEFAULT_CREDENTIAL_NAME = "KeyNexa.QuickUnlock"

        private val combase = Combase32.INSTANCE

        private const val KEY_CREDENTIAL_MANAGER = "Windows.Security.Credentials.KeyCredentialManager"
        private const val CRYPTOGRAPHIC_BUFFER = "Windows.Security.Cryptography.CryptographicBuffer"

        // IIDs and vtable slots from the Windows SDK 10.0.26100 headers (windows.security.credentials.idl and
        // windows.security.cryptography.idl). Slots 0-5 are IUnknown and IInspectable.
        private const val IID_KEY_CREDENTIAL_MANAGER_STATICS = "{6AAC468B-0EF1-4CE0-8290-4106DA6A63B5}"
        private const val IID_CRYPTOGRAPHIC_BUFFER_STATICS = "{320B7E22-3CB0-4CDF-8663-1D28910065EB}"
        private const val IID_ASYNC_INFO = "{00000036-0000-0000-C000-000000000046}"

        private const val SLOT_RELEASE = 2
        private const val SLOT_IS_SUPPORTED_ASYNC = 6
        private const val SLOT_REQUEST_CREATE_ASYNC = 8
        private const val SLOT_OPEN_ASYNC = 9
        private const val SLOT_REQUEST_SIGN_ASYNC = 9
        private const val SLOT_GET_RESULTS = 8
        private const val SLOT_RESULT_VALUE = 6
        private const val SLOT_RESULT_STATUS = 7
        private const val SLOT_CREATE_FROM_BYTE_ARRAY = 9
        private const val SLOT_COPY_TO_BYTE_ARRAY = 10
        private const val SLOT_ASYNC_STATUS = 7
        private const val SLOT_ASYNC_CANCEL = 9

        private const val RO_INIT_MULTITHREADED = 1
        private const val VK_MENU: Byte = 0x12
        private const val KEYEVENTF_KEYUP = 0x2
        private const val SW_RESTORE = 9
        private const val ASFW_ANY = -1
        private const val CREATION_FAIL_IF_EXISTS = 1

        private const val ASYNC_STARTED = 0
        private const val ASYNC_COMPLETED = 1

        private const val STATUS_SUCCESS = 0
        private const val STATUS_UNKNOWN_ERROR = 1
        private const val STATUS_NOT_FOUND = 2
        private const val STATUS_USER_CANCELED = 3
        private const val STATUS_USER_PREFERS_PASSWORD = 4
        private const val STATUS_ALREADY_EXISTS = 5
        private const val STATUS_DEVICE_LOCKED = 6

        private const val SHORT_TIMEOUT_MS = 30_000L
        private const val SIGN_TIMEOUT_MS = 5 * 60_000L
        private const val POLL_MS = 25L
        private const val FRONT_EVERY_TICKS = 6

        private fun statusName(status: Int): String =
            when (status) {
                STATUS_UNKNOWN_ERROR -> "UnknownError"
                STATUS_NOT_FOUND -> "NotFound"
                STATUS_ALREADY_EXISTS -> "CredentialAlreadyExists"
                STATUS_DEVICE_LOCKED -> "SecurityDeviceLocked"
                else -> status.toString()
            }
    }
}

private fun Pointer.call(
    slot: Int,
    vararg args: Any?,
): Int {
    val vtable = getPointer(0)
    val function = Function.getFunction(vtable.getPointer(slot.toLong() * Native.POINTER_SIZE))
    return function.invokeInt(arrayOf<Any?>(this, *args))
}

private const val HELLO_BROKER = "CredentialUIBroker.exe"
private const val PROCESS_QUERY_LIMITED_INFORMATION = 0x1000
private const val IMAGE_NAME_CHARS = 1024

/**
 * Brings the Hello prompt in front. It belongs to CredentialUIBroker.exe, not to us, so Windows often opens it behind
 * the window that asked; the window is found by its process and, when it is not already in front, raised.
 */
private fun frontHelloDialog() {
    val user32 = User32.INSTANCE
    var dialog: WinDef.HWND? = null
    user32.EnumWindows({ hwnd, _ ->
        if (user32.IsWindowVisible(hwnd) && isHelloBroker(hwnd)) dialog = hwnd
        true
    }, null)
    val target = dialog ?: return
    if (user32.GetForegroundWindow() == target) return
    val keys = AllowSetForegroundWindow32.INSTANCE
    keys.keybd_event(VK_MENU_KEY, 0, 0, null)
    keys.keybd_event(VK_MENU_KEY, 0, KEYEVENTF_KEY_UP, null)
    user32.BringWindowToTop(target)
    user32.SetForegroundWindow(target)
}

private const val VK_MENU_KEY: Byte = 0x12
private const val KEYEVENTF_KEY_UP = 0x2

private fun isHelloBroker(hwnd: WinDef.HWND): Boolean {
    val pid = IntByReference()
    User32.INSTANCE.GetWindowThreadProcessId(hwnd, pid)
    val process = Kernel32.INSTANCE.OpenProcess(PROCESS_QUERY_LIMITED_INFORMATION, false, pid.value) ?: return false
    return try {
        val name = CharArray(IMAGE_NAME_CHARS)
        val size = IntByReference(name.size)
        Kernel32.INSTANCE.QueryFullProcessImageName(process, 0, name, size) &&
            String(name, 0, size.value).endsWith(HELLO_BROKER, ignoreCase = true)
    } finally {
        Kernel32.INSTANCE.CloseHandle(process)
    }
}
