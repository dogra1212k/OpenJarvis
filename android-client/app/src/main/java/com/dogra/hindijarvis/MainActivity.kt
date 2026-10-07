package com.dogra.hindijarvis

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.RecognitionListener
import android.speech.tts.TextToSpeech
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : Activity(), TextToSpeech.OnInitListener {

    private lateinit var serverUrl: EditText
    private lateinit var apiKey: EditText
    private lateinit var modelName: EditText
    private lateinit var userInput: EditText
    private lateinit var chatOutput: TextView
    private lateinit var statusText: TextView
    private lateinit var sendButton: Button
    private lateinit var micButton: Button
    private lateinit var speakToggle: CheckBox

    private var tts: TextToSpeech? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private val executor = Executors.newSingleThreadExecutor()
    private val history = mutableListOf<Pair<String, String>>()
    private val prefs by lazy { getSharedPreferences("jarvis_settings", MODE_PRIVATE) }

    companion object {
        private const val AUDIO_PERMISSION_REQUEST = 200
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)
        setContentView(buildUi())
        loadSettings()
        setupSpeechRecognizer()
    }

    private fun buildUi(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 28, 28, 28)
        }

        val title = TextView(this).apply {
            text = "Hindi JARVIS"
            textSize = 26f
            setTypeface(typeface, Typeface.BOLD)
        }

        val subtitle = TextView(this).apply {
            text = "OpenJarvis Android Client"
            textSize = 14f
        }

        serverUrl = EditText(this).apply {
            hint = "Server URL, e.g. http://192.168.1.10:8000"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        }

        apiKey = EditText(this).apply {
            hint = "API key (optional on local loopback)"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }

        modelName = EditText(this).apply {
            hint = "Model"
            setText("qwen3.5:4b")
        }

        val saveButton = Button(this).apply {
            text = "Save Settings"
            setOnClickListener {
                saveSettings()
                toast("Settings saved")
            }
        }

        statusText = TextView(this).apply {
            text = "Ready"
            textSize = 13f
            setPadding(0, 8, 0, 8)
        }

        chatOutput = TextView(this).apply {
            text = "JARVIS: नमस्ते। मैं तैयार हूँ।"
            textSize = 16f
            setPadding(16, 16, 16, 16)
        }

        val scroll = ScrollView(this).apply {
            addView(chatOutput)
        }

        val scrollParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0,
            1f
        )

        userInput = EditText(this).apply {
            hint = "कुछ पूछिए..."
            minLines = 2
            maxLines = 5
            gravity = Gravity.TOP
        }

        speakToggle = CheckBox(this).apply {
            text = "Reply बोलकर सुनाओ"
            isChecked = true
        }

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        micButton = Button(this).apply {
            text = "🎙 Mic"
            setOnClickListener { startVoiceInput() }
        }

        sendButton = Button(this).apply {
            text = "Send"
            setOnClickListener { sendMessage() }
        }

        val clearButton = Button(this).apply {
            text = "Clear"
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
        root.addView(serverUrl)
        root.addView(apiKey)
        root.addView(modelName)
        root.addView(saveButton)
        root.addView(statusText)
        root.addView(scroll, scrollParams)
        root.addView(userInput)
        root.addView(speakToggle)
        root.addView(controls)

        return root
    }

    private fun loadSettings() {
        serverUrl.setText(prefs.getString("server_url", "http://10.0.2.2:8000"))
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

        saveSettings()
        userInput.setText("")
        history.add("user" to text)
        appendChat("आप: $text")
        setBusy(true, "JARVIS सोच रहा है...")

        executor.execute {
            try {
                val reply = callOpenJarvis()
                history.add("assistant" to reply)
                runOnUiThread {
                    appendChat("JARVIS: $reply")
                    setBusy(false, "Ready")
                    if (speakToggle.isChecked) speak(reply)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    appendChat("Error: ${e.message ?: "Unknown error"}")
                    setBusy(false, "Connection failed")
                }
            }
        }
    }

    private fun callOpenJarvis(): String {
        val base = serverUrl.text.toString().trim().trimEnd('/')
        require(base.startsWith("http://") || base.startsWith("https://")) {
            "Server URL http:// या https:// से शुरू होना चाहिए"
        }

        val connection = (URL("$base/v1/chat/completions").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15000
            readTimeout = 90000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            val key = apiKey.text.toString().trim()
            if (key.isNotEmpty()) {
                setRequestProperty("Authorization", "Bearer $key")
            }
        }

        val messages = JSONArray()
        messages.put(
            JSONObject()
                .put("role", "system")
                .put(
                    "content",
                    "You are JARVIS, a concise personal assistant. Reply in Hindi when the user speaks Hindi, Hinglish when they use Hinglish, and English when they use English."
                )
        )
        history.takeLast(20).forEach { (role, content) ->
            messages.put(JSONObject().put("role", role).put("content", content))
        }

        val payload = JSONObject()
            .put("model", modelName.text.toString().trim().ifEmpty { "qwen3.5:4b" })
            .put("messages", messages)
            .put("temperature", 0.5)
            .put("max_tokens", 1024)
            .put("stream", false)

        connection.outputStream.bufferedWriter(Charsets.UTF_8).use {
            it.write(payload.toString())
        }

        val code = connection.responseCode
        val body = if (code in 200..299) {
            connection.inputStream.bufferedReader().use { it.readText() }
        } else {
            connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $code"
        }
        connection.disconnect()

        if (code !in 200..299) {
            val detail = try {
                JSONObject(body).optString("detail", body)
            } catch (_: Exception) {
                body
            }
            throw IllegalStateException("Server error $code: $detail")
        }

        val json = JSONObject(body)
        return json
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
                override fun onReadyForSpeech(params: Bundle?) {
                    statusText.text = "सुन रहा हूँ..."
                }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    statusText.text = "आवाज़ समझ रहा हूँ..."
                }
                override fun onError(error: Int) {
                    statusText.text = "Voice input error: $error"
                }
                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull().orEmpty()
                    if (text.isNotBlank()) userInput.setText(text)
                    statusText.text = "Ready"
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
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "hi-IN")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
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
            val hindi = Locale("hi", "IN")
            val result = tts?.setLanguage(hindi)
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
