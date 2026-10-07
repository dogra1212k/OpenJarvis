package com.dogra.hindijarvis

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.*
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : Activity(), TextToSpeech.OnInitListener {
    private lateinit var status: TextView
    private lateinit var chat: TextView
    private lateinit var scroll: ScrollView
    private lateinit var input: EditText
    private lateinit var send: Button
    private lateinit var mic: Button
    private lateinit var clear: Button
    private lateinit var stop: Button
    private lateinit var speakToggle: CheckBox
    private val worker = Executors.newSingleThreadExecutor()
    private val history = mutableListOf<Pair<String, String>>()
    private var ai: OfflineAi? = null
    private var tts: TextToSpeech? = null
    private var recognizer: SpeechRecognizer? = null
    private var localSpeechAvailable = false
    private var ready = false
    private var busy = true
    private var streaming = ""
    private val modelFile by lazy { File(filesDir, "qwen-offline-q4.gguf") }
    private val prefs by lazy { getSharedPreferences("offline_ai", MODE_PRIVATE) }

    companion object {
        private const val MODEL_SHA = "7671c0c304e6ce5a7fc577bcb12aba01e2c155cc2efd29b2213c95b18edaf6ed"
        private const val BG = 0xFF07131C.toInt()
        private const val PANEL = 0xFF0E2533.toInt()
        private const val CYAN = 0xFF38E8FF.toInt()
        private const val TEXT = 0xFFEAFBFF.toInt()
        private const val MUTED = 0xFF91AAB4.toInt()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = BG
        window.navigationBarColor = BG
        setContentView(buildUi())
        tts = TextToSpeech(this, this)
        configureSpeech()
        prepareModel()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun buildUi(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            setBackgroundColor(BG)
            setOnApplyWindowInsetsListener { view, insets ->
                view.setPadding(dp(16) + insets.systemWindowInsetLeft,
                    dp(12) + insets.systemWindowInsetTop,
                    dp(16) + insets.systemWindowInsetRight,
                    dp(12) + insets.systemWindowInsetBottom)
                insets
            }
        }
        root.addView(TextView(this).apply {
            text = "J.A.R.V.I.S • OFFLINE AI"
            textSize = 24f
            setTextColor(CYAN)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
        })
        root.addView(TextView(this).apply {
            text = "AI आपके फोन पर • कोई server URL या API key नहीं"
            textSize = 12f
            setTextColor(MUTED)
            gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, dp(8))
        })
        status = TextView(this).apply {
            text = "पहली बार: offline model तैयार हो रहा है…"
            setTextColor(CYAN)
            setPadding(dp(10), dp(8), dp(10), dp(8))
            setBackgroundColor(PANEL)
        }
        root.addView(status)
        chat = TextView(this).apply {
            setTextColor(TEXT)
            textSize = 17f
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setTextIsSelectable(true)
            setBackgroundColor(PANEL)
        }
        scroll = ScrollView(this).apply { addView(chat) }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f).apply {
            setMargins(0, dp(10), 0, dp(10))
        })
        input = EditText(this).apply {
            hint = "कुछ पूछिए…"
            setHintTextColor(MUTED)
            setTextColor(TEXT)
            textSize = 17f
            minLines = 1
            maxLines = 3
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setBackgroundColor(PANEL)
        }
        root.addView(input)
        speakToggle = CheckBox(this).apply {
            text = "आवाज़ में जवाब (फोन का voice pack)"
            setTextColor(TEXT)
            isChecked = true
        }
        root.addView(speakToggle)
        val row = LinearLayout(this)
        mic = Button(this).apply { text = "MIC"; setOnClickListener { startListening() } }
        send = Button(this).apply { text = "SEND"; setOnClickListener { sendMessage() } }
        stop = Button(this).apply { text = "STOP"; setOnClickListener { ai?.setCancelled(true) } }
        clear = Button(this).apply {
            text = "CLEAR"
            setOnClickListener { history.clear(); streaming = ""; renderChat() }
        }
        listOf(mic, send, stop, clear).forEach {
            row.addView(it, LinearLayout.LayoutParams(0, -2, 1f))
        }
        root.addView(row)
        renderChat()
        return root
    }

    private fun ui(action: () -> Unit) {
        runOnUiThread { if (!isFinishing && !isDestroyed) action() }
    }

    private fun setBusy(value: Boolean, message: String) {
        busy = value
        status.text = message
        send.isEnabled = ready && !value
        input.isEnabled = ready && !value
        clear.isEnabled = !value
        mic.isEnabled = ready && !value && localSpeechAvailable
        stop.isEnabled = ready && value
        if (value) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun prepareModel() {
        setBusy(true, "Offline model तैयार हो रहा है… पहली बार थोड़ा समय लगेगा।")
        worker.execute {
            try {
                val cached = prefs.getString("verified_sha", "") == MODEL_SHA &&
                    modelFile.isFile && modelFile.length() == prefs.getLong("model_size", -1)
                if (!cached) {
                    require(filesDir.usableSpace > 500_000_000L) {
                        "फोन में कम से कम 500 MB खाली जगह चाहिए। जगह खाली करके ऐप फिर खोलें।"
                    }
                    val temporary = File(filesDir, "offline-model.part")
                    val digest = MessageDigest.getInstance("SHA-256")
                    var total = 0L
                    var lastProgress = 0L
                    assets.open("offline-model.gguf").use { source ->
                        temporary.outputStream().buffered().use { destination ->
                            val buffer = ByteArray(1024 * 1024)
                            while (true) {
                                val size = source.read(buffer)
                                if (size < 0) break
                                destination.write(buffer, 0, size)
                                digest.update(buffer, 0, size)
                                total += size
                                if (total - lastProgress > 16_000_000L) {
                                    lastProgress = total
                                    val megabytes = total / 1_000_000
                                    ui { status.text = "मॉडल तैयार: $megabytes / लगभग 429 MB" }
                                }
                            }
                        }
                    }
                    val sha = digest.digest().joinToString("") { "%02x".format(it) }
                    if (sha != MODEL_SHA) {
                        temporary.delete()
                        error("मॉडल की जाँच असफल हुई। APK फिर डाउनलोड करके install करें।")
                    }
                    if (modelFile.exists()) modelFile.delete()
                    check(temporary.renameTo(modelFile)) { "मॉडल save नहीं हुआ। ऐप दोबारा खोलें।" }
                    prefs.edit().putString("verified_sha", MODEL_SHA).putLong("model_size", total).apply()
                }
                ai = OfflineAi()
                ui {
                    ready = true
                    setBusy(false, "● OFFLINE AI READY • इंटरनेट बंद करके chat कर सकते हैं")
                    renderChat()
                }
            } catch (e: Throwable) {
                ui {
                    setBusy(false, "Setup असफल: ${e.message ?: "फोन पर AI engine नहीं खुला"}")
                    chat.text = "${e.message}\n\nऐप बंद करके फिर खोलें। यह APK 64-bit Android के लिए है।"
                }
            }
        }
    }

    private fun renderChat() {
        val welcome = "JARVIS: नमस्ते! अब मैं फोन पर AI मॉडल से जवाब दूँगा।\nयह छोटा test मॉडल है; हिन्दी और तथ्य वाले जवाबों में गलतियाँ हो सकती हैं।\n\nपूछिए: ‘आप कैसे हैं?’ या ‘दो और दो कितने होते हैं?’\n"
        chat.text = buildString {
            append(welcome)
            history.forEach { (role, text) -> append("\n\n${if (role == "user") "आप" else "JARVIS"}: $text") }
            if (streaming.isNotEmpty()) append("\n\nJARVIS: $streaming")
        }
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun sendMessage() {
        if (busy || !ready) return
        val message = input.text.toString().trim()
        if (message.isBlank()) return
        if (message.length > 1000) {
            status.text = "Test मॉडल के लिए छोटा सवाल भेजें (1000 अक्षर तक)।"
            return
        }
        input.setText("")
        history.add("user" to message)
        renderChat()
        val command = runCatching { phoneCommand(message) }.getOrElse { "यह app या browser नहीं खुल पाया।" }
        if (command != null) {
            history.add("assistant" to command)
            renderChat()
            speak(command)
            return
        }
        val prompt = buildString {
            append("<|im_start|>system\nYou are JARVIS, a helpful assistant. Reply briefly in the user's language. If unsure, say so. You are running offline and have no live internet information.<|im_end|>\n")
            history.takeLast(5).forEach { (role, text) ->
                val safe = text.take(500).replace("<|", "< |")
                append("<|im_start|>$role\n$safe<|im_end|>\n")
            }
            append("<|im_start|>assistant\n")
        }
        ai?.setCancelled(false)
        setBusy(true, "● फोन पर AI सोच रहा है… पहली reply थोड़ी धीमी हो सकती है")
        worker.execute {
            try {
                val result = ai!!.generate(modelFile.absolutePath.toByteArray(Charsets.UTF_8),
                    prompt.toByteArray(Charsets.UTF_8), TokenCallback { bytes ->
                        val partial = bytes.toString(Charsets.UTF_8)
                        ui { streaming = partial; renderChat() }
                    }).toString(Charsets.UTF_8).trim().ifEmpty { "Reply रुक गई। फिर से पूछिए।" }
                ui {
                    streaming = ""
                    history.add("assistant" to result)
                    while (history.size > 20) history.removeAt(0)
                    renderChat()
                    setBusy(false, "● OFFLINE AI READY")
                    speak(result)
                }
            } catch (e: Throwable) {
                ui {
                    streaming = ""
                    history.add("assistant" to "AI error: ${e.message}")
                    renderChat()
                    setBusy(false, "● Retry करें • जरूरत हो तो बाकी apps बंद करें")
                }
            }
        }
    }

    private fun phoneCommand(raw: String): String? {
        val text = raw.lowercase(Locale.ROOT)
        val opening = text.contains("खोल") || text.contains("open") || text.contains("kholo")
        val apps = listOf(Triple("youtube", "com.google.android.youtube", "https://www.youtube.com"),
            Triple("whatsapp", "com.whatsapp", "https://www.whatsapp.com"))
        apps.forEach { (name, pkg, web) ->
            if (opening && (text.contains(name) || (name == "youtube" && text.contains("यूट्यूब")))) {
                startActivity(packageManager.getLaunchIntentForPackage(pkg) ?: Intent(Intent.ACTION_VIEW, Uri.parse(web)))
                return "$name खोल रहा हूँ। इस app को इंटरनेट की जरूरत हो सकती है।"
            }
        }
        if (text == "समय बताओ" || text == "time batao" || text == "what time is it")
            return "अभी ${SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())} बज रहे हैं।"
        if (text == "तारीख बताओ" || text == "date batao")
            return "आज ${SimpleDateFormat("dd MMMM yyyy", Locale("hi", "IN")).format(Date())} है।"
        return null
    }

    private fun configureSpeech() {
        localSpeechAvailable = Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(this)
        if (!localSpeechAvailable) {
            mic.text = "MIC ×"
            mic.isEnabled = false
            return
        }
        if (Build.VERSION.SDK_INT >= 31) recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(this)
        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { status.text = "● सुन रहा हूँ…" }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { status.text = "● आवाज़ समझ रहा हूँ…" }
            override fun onError(error: Int) {
                setBusy(false, "Offline हिन्दी speech उपलब्ध नहीं: फोन का voice pack चाहिए। अभी type करें।")
            }
            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                setBusy(false, "● OFFLINE AI READY")
                input.setText(text)
                if (text.isNotBlank()) sendMessage()
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    private fun startListening() {
        if (busy || !localSpeechAvailable) return
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 200)
            return
        }
        tts?.stop()
        setBusy(true, "● Offline voice शुरू हो रही है…")
        stop.isEnabled = false
        runCatching {
            recognizer?.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            })
        }.onFailure { setBusy(false, "Voice शुरू नहीं हुई। अभी type करें।") }
    }

    override fun onRequestPermissionsResult(code: Int, permissions: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(code, permissions, results)
        if (code == 200 && results.firstOrNull() == PackageManager.PERMISSION_GRANTED) startListening()
    }

    override fun onInit(code: Int) {
        if (code == TextToSpeech.SUCCESS) {
            tts?.language = Locale("hi", "IN")
            tts?.voices?.firstOrNull { it.locale.language == "hi" && !it.isNetworkConnectionRequired }?.let { tts?.voice = it }
        }
    }
    private fun speak(text: String) {
        if (speakToggle.isChecked && tts?.voice?.isNetworkConnectionRequired == false)
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "offline_reply")
    }
    override fun onDestroy() {
        ai?.setCancelled(true)
        recognizer?.destroy()
        tts?.stop()
        tts?.shutdown()
        worker.execute { ai?.close() }
        worker.shutdown()
        super.onDestroy()
    }
}
