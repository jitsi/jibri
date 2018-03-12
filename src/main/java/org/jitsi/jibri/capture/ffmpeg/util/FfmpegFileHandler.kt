package org.jitsi.jibri.capture.ffmpeg.util

import java.util.logging.FileHandler

/**
 * A distinct [FileHandler] so that we can configure the file
 * Ffmpeg logs to separately in the logging config
 */
class FfmpegFileHandler : FileHandler()
