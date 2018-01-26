package org.jitsi.jibri.util

import java.util.concurrent.TimeUnit

/**
 * Class which is used to represent both a duration and time unit, so that APIs
 * don't have to choose a unit and force callers to use it
 */
data class Duration(val duration: Long, val unit: TimeUnit)