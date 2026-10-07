package com.dogra.hindijarvis

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : Activity(), TextToSpeech.OnInitListener {

    private lateinit var chatOutput: TextView
    private lateinit var userInput: EditText
    private lateinit var statusText: TextView
    private lateinit var micButton: Button
    private lateinit var sendButton: Button
    private lateinit var speakToggle: CheckBox
    private lateinit var advancedToggle: CheckBox
    private lateinit var settingsPanel: LinearLayout
    private lateinit var serverUrl: EditText
    private lateinit var apiKey: EditText
    private lateinit var modelName: EditText

    private var tts: TextToSpeech? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private val executor = Executors.newSingleThreadExecutor()
    private val history = mutableListOf<Pair<String, String>>()
    private val prefs by lazy { getSharedPreferences("jarvis_settings", MODE_PRIVATE) }

    companion object {
        private const val AUDIO_PERMISSION_REQUEST = 200
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
        tts = TextToSpeech(this, this)
        setContentView(buildUi())
        loadSettings()
        setupSpeechRecognizer()
    }

    private fun buildUi(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
            setBackgroundColor(BG)
        }

        val title = TextView(this).apply {
            text = "J.A.R.V.I.S"
            textSize = 30f
            setTextColor(CYAN)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER_HORIZONTAL
        }
        val subtitle = TextView(this).apply {
            text = "Hindi AI Assistant • Phone Mode"
            textSize = 14f
            setTextColor(MUTED)
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, 0, 0, 12)
        }

        statusText = TextView(this).apply {
            text = "● PHONE MODE • Basic commands ready"
            setTextColor(CYAN)
            textSize = 13f
            setPadding(12, 10, 12, 10)
            setBackgroundColor(PANEL)
        }

        chatOutput = TextView(this).apply {
            text = "JARVIS: नमस्ते। मैं तैयार हूँ।\n\nआप बोल सकते हैं: “YouTube खोलो”, “समय बताओ”, “Google पर AI search करो”।"
            textSize = 17f
            setTextColor(TEXT)
            setPadding(18, 18, 18, 18)
            setBackgroundColor(PANEL)
        }

        val scroll = ScrollView(this).apply { addView(chatOutput) }
        val scrollParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ).apply { setMargins(0, 14, 0, 12) }

        userInput = EditText(this).apply {
            hint = "JARVIS से कुछ कहिए..."
            setHintTextColor(MUTED)
            setTextColor(TEXT)
            textSize = 17f
            minLines = 2
            maxLines = 4
            gravity = Gravity.TOP
            setBackgroundColor(PANEL)
            setPadding(16, 14, 16, 14)
        }

        val toggles = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        speakToggle = CheckBox(this).apply {
            text = "आवाज़ में जवाब"
            setTextColor(TEXT)
            isChecked = true
        }
        advancedToggle = CheckBox(this).apply {
            text = "Advanced AI"
            setTextColor(TEXT)
            isChecked = false
            setOnCheckedChangeListener { _, checked ->
                settingsPanel.visibility = if (checked) View.VISIBLE else View.GONE
                statusText.text = if (checked)
                    "● ADVANCED AI • Server required"
                else
                    "● PHONE MODE • Basic commands ready"
            }
        }
        toggles.addView(speakToggle, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        toggles.addView(advancedToggle, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        settingsPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(0, 6, 0, 6)
        }
        serverUrl = darkEdit("OpenJarvis server URL")
        apiKey = darkEdit("API key")
        apiKey.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        modelName = darkEdit("Model name")
        val saveButton = Button(this).apply {
            text = "SAVE AI SETTINGS"
            setOnClickListener { saveSettings(); toast("AI settings saved") }
        }
        settingsPanel.addView(serverUrl)
        settingsPanel.addView(apiKey)
        settingsPanel.addView(modelName)
        settingsPanel.addView(saveButton)

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        micButton = Button(this).apply {
            text = "🎙 MIC"
            setOnClickListener { startVoiceInput() }
        }
        sendButton = Button(this).apply {
            text = "SEND"
            setOnClickListener { sendMessage() }
        }
        val clearButton = Button(this).apply {
            text = "CLEAR"
            setOnClickListener {
                history.clear()
                chatOutput.text = "JARVIS: नई बातचीत शुरू।"
            }
        }
        controls.addView(micButton, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        controls.addView(sendButton, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        controls.addView(clearButton, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        root.addView(title)
        root.addView(subtitle)
        root.addView(statusText)
        root.addView(scroll, scrollParams)
        root.addView(userInput)
        root.addView(toggles)
        root.addView(settingsPanel)
        root.addView(controls)
        return root
    }

    private fun darkEdit(hintText: String): EditText = EditText(this).apply {
        hint = hintText
        setHintTextColor(MUTED)
        setTextColor(TEXT)
        setBackgroundColor(PANEL)
        setPadding(14, 10, 14, 10)
    }

    private fun loadSettings() {
        val savedUrl = prefs.getString("server_url", "").orEmpty()
        val host = runCatching { Uri.parse(savedUrl).host }.getOrNull()
        val emulator = android.os.Build.FINGERPRINT.contains("generic") ||
            android.os.Build.MODEL.contains("Emulator")
        if (host == "10.0.2.2" && !emulator) {
            prefs.edit().remove("server_url").apply()
            serverUrl.setText("")
        } else {
            serverUrl.setText(savedUrl)
        }
        apiKey.setText(prefs.getString("api_key", ""))
        modelName.setText(prefs.getString("model", "qwen3.5:4b"))
    }

    private fun saveSettings() {
        prefs.edit()
            .putString("server_url", serverUrl.text.toString().trim().trimEnd('/'))
            .putString("api_key", apiKey.text.toString().trim())
            .putString("model", modelName.text.toString().trim())
            .apply()
    }

    private fun sendMessage() {
        val text = userInput.text.toString().trim()
        if (text.isEmpty()) return
        userInput.setText("")
        appendChat("आप: $text")
        history.add("user" to text)

        if (!advancedToggle.isChecked) {
            val reply = handleOffline(text)
            history.add("assistant" to reply)
            appendChat("JARVIS: $reply")
            if (speakToggle.isChecked) speak(reply)
            return
        }

        val url = serverUrl.text.toString().trim()
        val parsed = runCatching { Uri.parse(url) }.getOrNull()
        val emulator = android.os.Build.FINGERPRINT.contains("generic") ||
            android.os.Build.MODEL.contains("Emulator")
        if (parsed?.host == "10.0.2.2" && !emulator) {
            appendChat("JARVIS: 10.0.2.2 केवल emulator पर चलता है। अपने चालू OpenJarvis server का LAN IP या HTTPS URL डालें।")
            return
        }
        if (url.isBlank() || parsed?.host.isNullOrBlank() ||
            parsed?.scheme !in listOf("http", "https")) {
            val reply = "Advanced AI के लिए server URL चाहिए। अभी Offline mode इस्तेमाल करें।"
            appendChat("JARVIS: $reply")
            if (speakToggle.isChecked) speak(reply)
            return
        }

        setBusy(true, "JARVIS सोच रहा है...")
        executor.execute {
            try {
                val reply = callOpenJarvis()
                history.add("assistant" to reply)
                runOnUiThread {
                    appendChat("JARVIS: $reply")
                    setBusy(false, "● ADVANCED AI • Connected")
                    if (speakToggle.isChecked) speak(reply)
                }
            } catch (e: Exception) {
                val fallback = handleOffline(text)
                runOnUiThread {
                    appendChat("JARVIS: AI server नहीं मिला। Offline जवाब: $fallback")
                    setBusy(false, "● OFFLINE FALLBACK")
                    if (speakToggle.isChecked) speak(fallback)
                }
            }
        }
    }

    private fun handleOffline(raw: String): String {
        val text = raw.lowercase(Locale.getDefault()).trim()

        if (text.contains("समय") || text.contains("time")) {
            return "अभी " + SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date()) + " बज रहे हैं।"
        }
        if (text.contains("तारीख") || text.contains("date") || text.contains("आज कौन")) {
            return "आज " + SimpleDateFormat("dd MMMM yyyy", Locale("hi", "IN")).format(Date()) + " है।"
        }
        if (text.contains("youtube") && (text.contains("खोल") || text.contains("open"))) {
            return openAppOrWeb("com.google.android.youtube", "https://www.youtube.com", "YouTube")
        }
        if (text.contains("whatsapp") && (text.contains("खोल") || text.contains("open"))) {
            return openAppOrWeb("com.whatsapp", "https://www.whatsapp.com", "WhatsApp")
        }
        if ((text.contains("chrome") || text.contains("browser")) && (text.contains("खोल") || text.contains("open"))) {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com"))
            startActivity(intent)
            return "Browser खोल रहा हूँ।"
        }
        if (text.startsWith("google ") || text.contains("search करो") || text.contains("सर्च करो")) {
            val q = raw
                .replace("google", "", ignoreCase = true)
                .replace("search करो", "", ignoreCase = true)
                .replace("सर्च करो", "", ignoreCase = true)
                .trim()
            if (q.isBlank()) return "क्या search करना है?"
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(q))))
            return "$q search कर रहा हूँ।"
        }
        if (text.contains("नमस्ते") || text.contains("hello") || text.contains("hi ") || text == "hi") {
            return "नमस्ते। मैं तैयार हूँ।"
        }
        if (text.contains("तुम कौन") || text.contains("who are you")) {
            return "मैं JARVIS हूँ, आपका Android assistant।"
        }
        if (text.contains("क्या कर सकते") || text.contains("help")) {
            return "मैं बिना server के समय और तारीख बता सकता हूँ, YouTube, WhatsApp और browser खोल सकता हूँ, Google search कर सकता हूँ, और आवाज़ में जवाब दे सकता हूँ। Advanced AI mode के लिए OpenJarvis server जोड़ सकते हैं।"
        }
        return "Offline mode में यह सवाल समझ नहीं पाया। आप 'help' बोलें, या Advanced AI चालू करके OpenJarvis server जोड़ें।"
    }

    private fun openAppOrWeb(packageName: String, webUrl: String, label: String): String {
        val launch = packageManager.getLaunchIntentForPackage(packageName)
        return if (launch != null) {
            startActivity(launch)
            "$label खोल रहा हूँ।"
        } else {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(webUrl)))
            "$label app नहीं मिला, website खोल रहा हूँ।"
        }
    }

    private fun callOpenJarvis(): String {
        val base = serverUrl.text.toString().trim().trimEnd('/')
        require(base.startsWith("http://") || base.startsWith("https://")) {
            "Server URL गलत है"
        }
        val connection = (URL("$base/v1/chat/completions").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 8000
            readTimeout = 60000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            val key = apiKey.text.toString().trim()
            if (key.isNotEmpty()) setRequestProperty("Authorization", "Bearer $key")
        }

        val messages = JSONArray()
        messages.put(JSONObject().put("role", "system").put(
            "content",
            "You are JARVIS, a concise Hindi/Hinglish Android assistant. Reply in the user's language."
        ))
        history.takeLast(20).forEach { (role, content) ->
            messages.put(JSONObject().put("role", role).put("content", content))
        }

        val payload = JSONObject()
            .put("model", modelName.text.toString().trim().ifEmpty { "qwen3.5:4b" })
            .put("messages", messages)
            .put("temperature", 0.5)
            .put("max_tokens", 1024)
            .put("stream", false)

        connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(payload.toString()) }
        val code = connection.responseCode
        val body = if (code in 200..299) {
            connection.inputStream.bufferedReader().use { it.readText() }
        } else {
            connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $code"
        }
        connection.disconnect()
        if (code !in 200..299) throw IllegalStateException("Server error $code")

        return JSONObject(body)
            .getJSONArray("choices")
            .getJSONObject(0)
            .getJSONObject("message")
            .optString("content")
            .ifBlank { "मुझे जवाब नहीं मिला।" }
    }

    private fun appendChat(line: String) {
        chatOutput.append("\n\n$line")
    }

    private fun setBusy(busy: Boolean, status: String) {
        sendButton.isEnabled = !busy
        micButton.isEnabled = !busy
        statusText.text = status
    }

    private fun setupSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            micButton.isEnabled = false
            return
        }
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) { statusText.text = "● सुन रहा हूँ..." }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() { statusText.text = "● आवाज़ समझ रहा हूँ..." }
                override fun onError(error: Int) { statusText.text = "● Voice input retry करें" }
                override fun onResults(results: Bundle?) {
                    val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                    if (text.isNotBlank()) {
                        userInput.setText(text)
                        sendMessage()
                    }
                    statusText.text = if (advancedToggle.isChecked) "● ADVANCED AI" else "● PHONE MODE • Basic commands ready"
                }
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
    }

    private fun startVoiceInput() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), AUDIO_PERMISSION_REQUEST)
            return
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }
        speechRecognizer?.startListening(intent)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == AUDIO_PERMISSION_REQUEST && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            startVoiceInput()
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale("hi", "IN"))
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.language = Locale.getDefault()
            }
        }
    }

    private fun speak(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis_reply")
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        speechRecognizer?.destroy()
        tts?.stop()
        tts?.shutdown()
        executor.shutdownNow()
        super.onDestroy()
    }
}
