package com.telnyx.meet.ui.models

sealed class StreamStats

data class SelfVideoStreamStats(
    val bytesSent: Int,
    val codecId: String,
    val frameHeight: Int,
    val frameWidth: Int,
    val framesEncoded: Int,
    val framesPerSecond: Double,
    val framesSent: Int,
    val headerBytesSent: Int,
    val nackCount: Int,
    val packetsSent: Int,
) : StreamStats()

data class RemoteVideoStreamStats(
    val bytesReceived: Int,
    val frameHeight: Int,
    val frameWidth: Int,
    val framesDecoded: Int,
    val framesDropped: Int,
    val framesPerSecond: Double,
    val framesReceived: Int,
    val packetsLost: Int,
    val packetsReceived: Int,
    val totalInterFrameDelay: Double
) : StreamStats()

data class AudioBridgeOutputStreamStats(
    val bytesSent: Int,
    val codecId: String,
    val headerBytesSent: Int,
    val packetsSent: Int,
    val retransmittedBytesSent: Int,
    val retransmittedPacketsSent: Int,
) : StreamStats()

data class AudioBridgeInputStreamStats(
    val audioLevel: Double,
    val bytesReceived: Int,
    val codecId: String,
    val headerBytesReceived: Int,
    val jitter: Double,
    val packetsLost: Int,
    val packetsReceived: Int,
    val totalAudioEnergy: Double,
    val totalSamplesDuration: Double,
    val totalSamplesReceived: Int,
) : StreamStats()
