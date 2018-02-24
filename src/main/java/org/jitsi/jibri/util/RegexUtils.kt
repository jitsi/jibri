package org.jitsi.jibri.util

// Regex definitions for parsing an ffmpeg output line

val digit = """\d"""
val oneOrMoreDigits = "$digit+"
val decimal = """$oneOrMoreDigits\.$oneOrMoreDigits"""
val string = """[a-zA-Z]+"""
val dataSize = "$oneOrMoreDigits$string"
val timestamp = """$oneOrMoreDigits\:$oneOrMoreDigits\:$oneOrMoreDigits\.$oneOrMoreDigits"""
val bitrate = """$decimal$string\/$string"""
val speed = "${decimal}x"
val space = """\s"""
val zeroOrMoreSpaces = "$space*"