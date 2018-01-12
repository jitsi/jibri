package org.jitsi.capture.ffmpeg

import org.jitsi.sink.Sink
import org.jitsi.capture.Capturer
import org.jitsi.capture.CapturerParams
import org.jitsi.capture.UnsupportedOsException
import org.jitsi.capture.ffmpeg.executors.FfmpegExecutor
import org.jitsi.capture.ffmpeg.executors.FfmpegExecutorParams
import org.jitsi.capture.ffmpeg.executors.LinuxFfmpegExecutor
import org.jitsi.capture.ffmpeg.executors.MacFfmpegExecutor

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
    val ffmpegExecutor: FfmpegExecutor

    init
    {
        println("OS: ${System.getProperty("os.name")}")
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