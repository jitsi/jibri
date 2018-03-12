package org.jitsi.jibri.util

import java.lang.reflect.Field

/**
 * Mimic the "pid" member of Java 9's [Process].  This can't be
 * an extension function as it gets called from a Java context
 * (which wouldn't see the extension function as a normal
 * member)
 */
fun pid(p: Process): Long {
    var pid: Long = -1

    try {
        if (p.javaClass.name.equals("java.lang.UNIXProcess")) {
            val field: Field = p.javaClass.getDeclaredField("pid")
            field.isAccessible = true
            pid = field.getLong(p)
            field.isAccessible = false
        }
    } catch (e: Exception ) {
        pid = -1
    }
    return pid
}
