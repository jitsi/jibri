package org.jitsi.capture

interface Ffmpeg : Capturer {
    override fun start(capturerParams: CapturerParams)
    override fun stop()
}