package com.example.myapplication

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel
import kotlin.concurrent.thread

/**
 * Local VPN proxy that intercepts Growtopia UDP traffic (port 17198) and replaces
 * the `mac|...` field in the login handshake with the admin-configured whitelist MAC.
 * All other UDP traffic is relayed transparently. TCP is silently dropped (Growtopia
 * is exclusively UDP-based for game communication).
 *
 * Only started when the signed-in account is the designated admin AND AAP Bypass is
 * enabled in Settings. Access is enforced in MainActivity before this service is started.
 */
class LocalProxyVpnService : VpnService() {

    companion object {
        const val ACTION_START   = "com.example.myapplication.AAP_VPN_START"
        const val ACTION_STOP    = "com.example.myapplication.AAP_VPN_STOP"
        const val EXTRA_MAC      = "spoof_mac"
        const val GROWTOPIA_PORT = 17198
        private const val NOTIF_ID      = 801
        private const val NOTIF_CHANNEL = "aap_vpn"
    }

    private var vpnFd: ParcelFileDescriptor? = null
    @Volatile private var running = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopVpn()
            return START_NOT_STICKY
        }
        val mac = intent?.getStringExtra(EXTRA_MAC)
        if (mac.isNullOrBlank()) { stopSelf(); return START_NOT_STICKY }
        startVpn(mac)
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        vpnFd?.close()
        super.onDestroy()
    }

    // ─── Lifecycle ─────────────────────────────────────────────────────────────

    private fun startVpn(spoofMac: String) {
        if (running) return
        ensureNotificationChannel()
        startForeground(
            NOTIF_ID,
            NotificationCompat.Builder(this, NOTIF_CHANNEL)
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .setContentTitle("AAP Bypass active")
                .setContentText("Spoofing MAC → $spoofMac")
                .setOngoing(true)
                .build()
        )
        val fd = Builder()
            .setSession("GrowLauncher AAP")
            .addAddress("10.88.0.1", 30)
            .addRoute("0.0.0.0", 0)
            .addDnsServer("8.8.8.8")
            .addAllowedApplication("com.rtsoft.growtopia")
            .setMtu(1500)
            .establish() ?: run { stopSelf(); return }
        vpnFd = fd
        running = true
        thread(name = "aap-vpn-reader", isDaemon = true) { runPacketLoop(fd, spoofMac) }
    }

    private fun stopVpn() {
        running = false
        vpnFd?.close()
        vpnFd = null
        stopForeground(true)
        stopSelf()
    }

    // ─── Packet relay loop ────────────────────────────────────────────────────

    private data class Session(
        val channel: DatagramChannel,
        val remoteIp: ByteArray,
        val remotePort: Int
    ) {
        // ByteArray identity is address-based by default; override for value equality.
        override fun equals(other: Any?) =
            other is Session && channel == other.channel &&
                    remoteIp.contentEquals(other.remoteIp) && remotePort == other.remotePort
        override fun hashCode() = 31 * (31 * channel.hashCode() + remoteIp.contentHashCode()) + remotePort
    }

    private fun runPacketLoop(fd: ParcelFileDescriptor, spoofMac: String) {
        val input   = FileInputStream(fd.fileDescriptor)
        val output  = FileOutputStream(fd.fileDescriptor)
        val buf     = ByteArray(32_767)
        // srcPort → outbound DatagramChannel (one channel per game-side UDP port)
        val sessions = HashMap<Int, Session>()

        while (running) {
            val len = runCatching { input.read(buf) }.getOrElse { -1 }
            if (len < 28) continue                                      // min IPv4 + UDP

            // ── IPv4 header ──────────────────────────────────────────────────
            if (buf[0].toInt() and 0xF0 != 0x40) continue              // not IPv4
            val ihl = (buf[0].toInt() and 0x0F) * 4
            if (ihl < 20 || len < ihl + 8) continue
            if (buf[9].toInt() and 0xFF != 17) continue                 // not UDP

            val srcIp   = buf.copyOfRange(12, 16)
            val dstIp   = buf.copyOfRange(16, 20)
            val srcPort = buf.u16(ihl)
            val dstPort = buf.u16(ihl + 2)

            val plOff = ihl + 8
            val plLen = len - plOff
            if (plLen <= 0) continue

            var payload = buf.copyOfRange(plOff, plOff + plLen)
            if (dstPort == GROWTOPIA_PORT) payload = replaceMac(payload, spoofMac)

            val session = sessions.getOrPut(srcPort) {
                val ch = DatagramChannel.open()
                protect(ch.socket())
                ch.connect(InetSocketAddress(InetAddress.getByAddress(dstIp), dstPort))
                val s = Session(ch, dstIp, dstPort)
                thread(name = "aap-recv-$srcPort", isDaemon = true) {
                    readFromRemote(s, output, srcIp, srcPort)
                }
                s
            }

            runCatching { session.channel.write(ByteBuffer.wrap(payload)) }
                .onFailure { sessions.remove(srcPort)?.channel?.runCatching { close() } }
        }

        sessions.values.forEach { it.channel.runCatching { close() } }
    }

    private fun readFromRemote(
        session: Session,
        output: FileOutputStream,
        clientIp: ByteArray,
        clientPort: Int
    ) {
        val buf = ByteBuffer.allocate(32_767)
        while (running) {
            buf.clear()
            runCatching {
                session.channel.read(buf)
                buf.flip()
                if (buf.limit() == 0) return@runCatching
                val data = ByteArray(buf.limit()).also { buf.get(it) }
                val pkt  = buildIpUdp(session.remoteIp, session.remotePort, clientIp, clientPort, data)
                synchronized(output) { output.write(pkt) }
            }.onFailure { if (running) Thread.sleep(5) }
        }
    }

    // ─── MAC field replacement ────────────────────────────────────────────────

    private val macPattern = Regex("mac\\|[0-9A-Fa-f]{2}(?::[0-9A-Fa-f]{2}){5}")

    private fun replaceMac(payload: ByteArray, spoof: String): ByteArray {
        val str = runCatching { String(payload, Charsets.ISO_8859_1) }.getOrNull() ?: return payload
        if (!macPattern.containsMatchIn(str)) return payload
        return str.replace(macPattern, "mac|$spoof").toByteArray(Charsets.ISO_8859_1)
    }

    // ─── Raw IPv4/UDP packet builder ─────────────────────────────────────────

    private fun buildIpUdp(
        srcIp: ByteArray, srcPort: Int,
        dstIp: ByteArray, dstPort: Int,
        payload: ByteArray
    ): ByteArray {
        val udpLen = 8 + payload.size
        val ipLen  = 20 + udpLen
        val out    = ByteArray(ipLen)               // zero-initialised by JVM

        // IPv4 header (no options → IHL = 5 words = 20 bytes)
        out[0]  = 0x45.toByte()                     // version=4, IHL=5
        out[2]  = (ipLen shr 8).toByte()
        out[3]  = (ipLen and 0xFF).toByte()
        out[6]  = 0x40.toByte()                     // DF flag
        out[8]  = 64                                // TTL
        out[9]  = 17                                // protocol = UDP
        // bytes 10-11: checksum computed below
        srcIp.copyInto(out, 12)
        dstIp.copyInto(out, 16)
        val ck  = ipChecksum(out, 0, 20)
        out[10] = (ck shr 8).toByte()
        out[11] = (ck and 0xFF).toByte()

        // UDP header
        out[20] = (srcPort shr 8).toByte();  out[21] = (srcPort and 0xFF).toByte()
        out[22] = (dstPort shr 8).toByte();  out[23] = (dstPort and 0xFF).toByte()
        out[24] = (udpLen shr 8).toByte();   out[25] = (udpLen and 0xFF).toByte()
        // bytes 26-27: UDP checksum = 0 (optional in IPv4, accepted by Android TUN)
        payload.copyInto(out, 28)
        return out
    }

    private fun ipChecksum(buf: ByteArray, off: Int, len: Int): Int {
        var sum = 0
        var i = off
        while (i < off + len - 1) {
            sum += ((buf[i].toInt() and 0xFF) shl 8) or (buf[i + 1].toInt() and 0xFF)
            i += 2
        }
        while (sum ushr 16 != 0) sum = (sum and 0xFFFF) + (sum ushr 16)
        return sum.inv() and 0xFFFF
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private fun ByteArray.u16(offset: Int) =
        ((this[offset].toInt() and 0xFF) shl 8) or (this[offset + 1].toInt() and 0xFF)

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(NOTIF_CHANNEL) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(NOTIF_CHANNEL, "AAP Bypass VPN", NotificationManager.IMPORTANCE_LOW)
                )
            }
        }
    }
}
