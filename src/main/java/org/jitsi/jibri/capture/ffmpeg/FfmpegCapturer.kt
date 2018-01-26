package org.jitsi.jibri.capture.ffmpeg

import org.jitsi.jibri.sink.Sink
import org.jitsi.jibri.capture.Capturer
import org.jitsi.jibri.capture.CapturerParams
import org.jitsi.jibri.capture.UnsupportedOsException
import org.jitsi.jibri.capture.ffmpeg.executor.FfmpegExecutor
import org.jitsi.jibri.capture.ffmpeg.executor.FfmpegExecutorParams
import org.jitsi.jibri.capture.ffmpeg.executor.impl.LinuxFfmpegExecutor
import org.jitsi.jibri.capture.ffmpeg.executor.impl.MacFfmpegExecutor
import org.jitsi.jibri.util.debug
import java.util.logging.Logger

// Taken from https://stackoverflow.com/questions/35421699/how-to-invoke-external-command-from-within-kotlin-code
//TODO: not sure if we'll want to use this or just use the Runtime.getRuntime().exec()
//fun String.runCommand(workingDir: File): Process? {
//    try {
//        println("raw command string: $this")
//        val parts = this.split("\\s".toRegex())
//        println("parts: $parts")
//        val proc = ProcessBuilder(*parts.toTypedArray())
//                .directory(workingDir)
//                .redirectOutput(ProcessBuilder.Redirect.to(File("/tmp/jibri-ffmpeg.out")))
//                .redirectErrorStream(true)
//                //.redirectError(ProcessBuilder.Redirect.PIPE)
//                .start()
//
//        return proc
//        //proc.waitFor(60, TimeUnit.MINUTES)
//        //return proc.inputStream.bufferedReader().readText()
//    } catch (e: IOException) {
//        e.printStackTrace()
//        return null
//    }
//}

class FfmpegCapturer : Capturer {
    private val logger = Logger.getLogger(this::class.simpleName)
    val ffmpegExecutor: FfmpegExecutor

    init
    {
        logger.debug("OS: ${System.getProperty("os.name")}")
        ffmpegExecutor = when (System.getProperty("os.name"))
        {
            "Mac OS X" -> MacFfmpegExecutor()
            "Linux" -> LinuxFfmpegExecutor()
            else -> throw UnsupportedOsException()
        }
    }

    override fun start(capturerParams: CapturerParams, sink: Sink)
    {
        ffmpegExecutor.launchFfmpeg(FfmpegExecutorParams(), sink)
    }

    override fun isAlive(): Boolean = ffmpegExecutor.isAlive()

    override fun getExitCode(): Int? = ffmpegExecutor.getExitCode()

    override fun stop()
    {
        ffmpegExecutor.stopFfmpeg()
    }
}