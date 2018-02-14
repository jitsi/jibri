package org.jitsi.jibri.util

import java.lang.reflect.Field

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
        pid = -1;
    }
    return pid;
}