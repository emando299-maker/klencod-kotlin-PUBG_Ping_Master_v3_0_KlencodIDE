package com.pingmaster.pubg

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.concurrent.Executors
import kotlin.math.abs

class MainActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()
    private val hosts = listOf("1.1.1.1", "8.8.8.8")
    private val names = listOf("Cloudflare", "Google")
    private data class Stats(
        val tcp: MutableList<Long> = mutableListOf(),
        val udp: MutableList<Long> = mutableListOf(),
        var tcpFail: Int = 0, var udpFail: Int = 0, var attempts: Int = 0
    ) {
        fun jitter(list: List<Long>): Double? =
            if (list.size < 2) null else list.zipWithNext { a,b -> abs(b-a).toDouble() }.average()
        fun avg(list: List<Long>): Double? = list.takeIf { it.isNotEmpty() }?.average()
        fun loss(fail: Int): Double = if (attempts == 0) 0.0 else fail * 100.0 / attempts
    }
    private val stats = names.associateWith { Stats() }
    private val cards = mutableMapOf<String, TextView>()
    private lateinit var network: TextView
    private lateinit var summary: TextView
    private lateinit var history: TextView
    private lateinit var start: Button
    private lateinit var stop: Button
    private var running = false
    private var rounds = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        updateNetwork()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 22, 18, 18)
            setBackgroundColor(Color.rgb(10,15,28))
        }
        val scroll = ScrollView(this)
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(body)
        body.addView(txt("PUBG PING MASTER", 25, Color.WHITE, true))
        body.addView(txt("NETWORK DIAGNOSTICS • VERSION 3.0", 12, 0xFF00DCAA.toInt(), true))
        network = txt("Checking network…", 14, Color.LTGRAY)
        body.addView(network)
        body.addView(txt("TCP + UDP DNS TESTS", 15, Color.WHITE, true).apply { setPadding(0,22,0,8) })

        names.forEach { name ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
                setPadding(12,16,12,16); setBackgroundColor(Color.rgb(22,31,49))
            }
            card.addView(txt(name, 17, Color.WHITE, true))
            val values = txt("TCP -- ms   |   UDP DNS -- ms", 21, 0xFF00E6A0.toInt(), true)
            cards[name] = values
            card.addView(values)
            card.addView(txt("TCP avg/jitter/loss and UDP DNS avg/jitter/loss shown below", 11, Color.LTGRAY))
            body.addView(card, LinearLayout.LayoutParams(-1,-2).apply { setMargins(0,6,0,6) })
        }
        summary = txt("No test data yet", 13, Color.LTGRAY)
        body.addView(summary)
        start = Button(this).apply { text = "START TEST"; setOnClickListener { startTest() } }
        stop = Button(this).apply { text = "STOP"; isEnabled = false; setOnClickListener { stopTest() } }
        body.addView(start)
        body.addView(stop)
        body.addView(txt("DETAILED RESULTS", 15, Color.WHITE, true).apply { setPadding(0,20,0,8) })
        history = txt("No tests yet", 12, Color.LTGRAY)
        body.addView(history)
        body.addView(txt(
            "UDP here is a DNS request/response to port 53, not a direct measurement of PUBG UDP traffic. TCP is a connection-time check to port 443. Neither result is the live PUBG game-server ping. Use the in-game indicator for that. This app diagnoses general network paths; it does not reduce ping.",
            12, 0xFFFFC35F.toInt()
        ).apply { setPadding(0,22,0,12) })
        root.addView(scroll, LinearLayout.LayoutParams(-1,0,1f))
        setContentView(root)
    }

    private fun txt(s: String, size: Int, color: Int, bold: Boolean = false) =
        TextView(this).apply {
            text=s; textSize=size.toFloat(); setTextColor(color); gravity=Gravity.CENTER
            if (bold) typeface=Typeface.DEFAULT_BOLD
        }

    private fun updateNetwork() {
        val cm = getSystemService(CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        network.text = when {
            caps == null -> "No network connection"
            caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) -> "Connected via Wi-Fi"
            caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) -> "Connected via mobile data"
            else -> "Connected to network"
        }
    }

    private fun startTest() {
        if (running) return
        running = true; rounds = 0
        stats.values.forEach {
            it.tcp.clear(); it.udp.clear(); it.tcpFail=0; it.udpFail=0; it.attempts=0
        }
        start.isEnabled=false; stop.isEnabled=true
        history.text="Testing TCP and UDP DNS separately…"
        runRound(0)
    }

    private fun runRound(index: Int) {
        if (!running) return
        if (index >= hosts.size) {
            rounds++; updateResults()
            handler.postDelayed({ runRound(0) }, 900)
            return
        }
        val host=hosts[index]; val name=names[index]
        executor.execute {
            val tcp=PingTester.measureTcp(host)
            val udp=PingTester.measureUdpDns(host)
            handler.post {
                if (!running) return@post
                val s=stats.getValue(name); s.attempts++
                if (tcp == null) s.tcpFail++ else s.tcp.add(tcp)
                if (udp == null) s.udpFail++ else s.udp.add(udp)
                cards.getValue(name).text =
                    "TCP ${tcp?.let { "$it ms" } ?: "timeout"}   |   UDP DNS ${udp?.let { "$it ms" } ?: "timeout"}"
                updateResults()
                runRound(index+1)
            }
        }
    }

    private fun fmt(v: Double?) = v?.let { "%.1f".format(it) } ?: "--"
    private fun updateResults() {
        summary.text="Rounds: $rounds • Network: ${network.text}"
        history.text=names.joinToString("\n\n") { name ->
            val s=stats.getValue(name)
            "$name\nTCP avg ${fmt(s.avg(s.tcp))} ms | jitter ${fmt(s.jitter(s.tcp))} ms | loss ${fmt(s.loss(s.tcpFail))}%\n" +
            "UDP DNS avg ${fmt(s.avg(s.udp))} ms | jitter ${fmt(s.jitter(s.udp))} ms | loss ${fmt(s.loss(s.udpFail))}%"
        }
    }

    private fun stopTest() {
        running=false; start.isEnabled=true; stop.isEnabled=false
        updateResults(); history.append("\nTest stopped.")
    }

    override fun onDestroy() {
        running=false; executor.shutdownNow(); super.onDestroy()
    }
}