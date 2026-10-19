package io.github.catlinux.keynexa.windows

import com.sun.jna.Native
import io.github.catlinux.keynexa.desktop.HelloResult
import java.awt.EventQueue
import java.awt.Frame
import java.awt.Label
import java.security.SecureRandom
import kotlin.system.exitProcess

/**
 * Manual probe for [WindowsHello] (ADR-0014, step 0), run with `.\gradlew.bat :platform:windows:helloProbe`.
 * It needs the owner's PIN, so it is not part of the automated tests. It prints only booleans and lengths, never the
 * signature itself.
 */
fun main() {
    val hello = WindowsHello(WindowsHello.DEFAULT_CREDENTIAL_NAME + ".Probe")
    var handle: Long? = null
    EventQueue.invokeAndWait {
        val frame = Frame("KeyNexa - Hello probe")
        frame.add(Label("Probando Windows Hello: sigue las ventanas de PIN", Label.CENTER))
        frame.setSize(520, 140)
        frame.setLocationRelativeTo(null)
        frame.isVisible = true
        handle = Native.getComponentID(frame)
    }
    Thread.sleep(800)

    println("isSupported: ${hello.isSupported()}")
    val challenge = ByteArray(32).also { SecureRandom().nextBytes(it) }

    println("--- Firma 1 (escribe el PIN; la primera vez puede pedirlo también al crear la clave) ---")
    val first = hello.sign(challenge, handle)
    println("firma 1: ${describe(first)}")

    println("--- Firma 2 del mismo reto (escribe el PIN) ---")
    val second = hello.sign(challenge, handle)
    println("firma 2: ${describe(second)}")
    if (first is HelloResult.Signed && second is HelloResult.Signed) {
        println("bytes identicos: ${first.bytes.contentEquals(second.bytes)}")
        println("longitud: ${first.bytes.size}")
        println("no es todo ceros: ${first.bytes.any { it != 0.toByte() }}")
    }

    println("--- Firma 3: CANCELA la ventana de Hello ---")
    println("firma 3: ${describe(hello.sign(challenge, handle))}")

    println("--- Firma 4: reto distinto (escribe el PIN) ---")
    val other = hello.sign(ByteArray(32).also { SecureRandom().nextBytes(it) }, handle)
    if (first is HelloResult.Signed && other is HelloResult.Signed) {
        println("firma con otro reto distinta: ${!first.bytes.contentEquals(other.bytes)}")
    } else {
        println("firma 4: ${describe(other)}")
    }
    exitProcess(0)
}

private fun describe(result: HelloResult): String =
    when (result) {
        is HelloResult.Signed -> "Signed (${result.bytes.size} bytes)"
        HelloResult.Cancelled -> "Cancelled"
        is HelloResult.Failed -> "Failed (${result.reason})"
    }
