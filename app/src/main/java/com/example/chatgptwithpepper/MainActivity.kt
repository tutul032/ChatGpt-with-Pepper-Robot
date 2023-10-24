package com.example.chatgptwithpepper

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.aldebaran.qi.sdk.QiContext
import com.aldebaran.qi.sdk.QiSDK
import com.aldebaran.qi.sdk.RobotLifecycleCallbacks
import com.aldebaran.qi.sdk.builder.SayBuilder
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.*

@RequiresApi(Build.VERSION_CODES.DONUT)
class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener, RobotLifecycleCallbacks {

    private lateinit var responseTextView: String

    private lateinit var resultText: String
    private lateinit var startButton: Button
    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var messageContainer: LinearLayout
    private lateinit var textToSpeech: TextToSpeech
    private val chatHistory = HashMap<String, String>()
    var activation = false


    @DelicateCoroutinesApi
    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        hideSystemUI()
        QiSDK.register(this, this)

        textToSpeech = TextToSpeech(this, this)
        messageContainer = findViewById(R.id.messageContainer)
        startButton = findViewById(R.id.startButton)


        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {}
            @SuppressLint("SetTextI18n")
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val result = matches[0]
                    resultText = result
                    addMessage(false, "You: $result")

                    val answer = chatHistory[result]
                    if (answer != null) {
                        // If the answer exists in chat history, display it
                        addMessage(true, "Pepper: $answer")
                        textToSpeech.speak(answer, TextToSpeech.QUEUE_FLUSH, null, null)
                    } else {
                        // Send the question to GPT-3 and store the response
                        val client = OkHttpClient()


                        val content = JSONObject().apply {
                            put("prompt", "Answer this question in a concise manner:$resultText")
                            put("max_tokens", 200) // Adjust the number of tokens as needed
                        }.toString()

                        val requestBody = content.toRequestBody("application/json".toMediaType())

                        val request = Request.Builder()
                            .url("https://api.openai.com/v1/engines/text-davinci-003/completions")
                            .header("Content-Type", "application/json")
                            .header("Authorization", "Bearer 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")
                            .post(requestBody)
                            .build()

                        GlobalScope.launch(Dispatchers.IO) {

                            val response = client.newCall(request).execute()
                            val responseBody = response.body?.string()
                            val jsonObject = JSONObject(responseBody.toString())

                            val choices = jsonObject.getJSONArray("choices")

                            val text = choices.getJSONObject(0).getString("text")

                            Thread.sleep(1000)
                            runOnUiThread {
                                val sentences = text.split("(?<=[.!?])\\s+".toRegex())
                                val completeSentences = sentences.filter { sentence ->
                                    sentence.endsWith('.')}
                                val filteredText = completeSentences.joinToString(" ")

                                // Replace line breaks with spaces
                                val formattedText = filteredText.replace("\n", "")
                                responseTextView = formattedText
                                addMessage(true, "Pepper: $formattedText")
                                activation = true
                                //textToSpeech.speak(responseTextView, TextToSpeech.QUEUE_FLUSH, null, null)

                            }

                        }
                    }


                }
            }

            override fun onPartialResults(partialResults: Bundle?) {}

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        startButton.setOnClickListener {

            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.RECORD_AUDIO),
                    RECORD_AUDIO_PERMISSION_REQUEST_CODE
                )
            } else {
                //activation = true
                startSpeechRecognition()

            }
        }
    }


    private fun startSpeechRecognition() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak something...")
        speechRecognizer.startListening(intent)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == RECORD_AUDIO_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startSpeechRecognition()
            }
        }
    }

    companion object {
        private const val RECORD_AUDIO_PERMISSION_REQUEST_CODE = 101
    }



    private fun hideSystemUI() {
        window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE
                        or View.SYSTEM_UI_FLAG_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                )
    }


    private fun addMessage(isUser: Boolean, message: String) {
        val textView = TextView(this)
        val layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )

        if (isUser) {
            layoutParams.gravity = android.view.Gravity.END
            layoutParams.marginStart = 200
            layoutParams.marginEnd = 20
            layoutParams.topMargin = 16
            textView.setBackgroundResource(R.drawable.right_bubble_background)
            textView.textSize = 18F
        } else {
            layoutParams.gravity = android.view.Gravity.START
            layoutParams.marginStart = 20
            layoutParams.marginEnd = 200
            textView.textSize = 18F
            layoutParams.topMargin = 16
            textView.setBackgroundResource(R.drawable.left_bubble_background)
        }

        textView.layoutParams = layoutParams
        textView.text = message
        textView.setTextColor(resources.getColor(android.R.color.black))

        messageContainer.addView(textView)
        // Automatically scroll to the bottom
        val scrollView = findViewById<ScrollView>(R.id.scrollView)
        scrollView.post {
            scrollView.fullScroll(View.FOCUS_DOWN) // Scroll to the bottom
        }
    }


    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer.destroy()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            //val text = ""
            textToSpeech.language = Locale.getDefault() // Set the language, you can use other locales
            //textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
        }
    }

    override fun onRobotFocusGained(qiContext: QiContext?) {
        val systemLanguage = Locale.getDefault().language
        var greetText = ""
        greetText = if (systemLanguage == "de") {
            "Gruß. Ich bin Pepper, ein humanoider Roboter hier, um Ihnen bei Ihren Anfragen zu helfen. Wie kann ich Ihnen heute helfen?"
        } else {
            "Greetings. I am Pepper, a humanoid robot here to assist with your inquiries. How may I help you today?"
        }

        val sayInt = SayBuilder.with(qiContext)
            .withText(greetText)
            .build()
        sayInt.run()

        while (true){
            if (activation){
                val say = SayBuilder.with(qiContext)
                    .withText(responseTextView)
                    .build()
                say.run()
                activation = false
            }
        }
    }

    override fun onRobotFocusLost() {
        TODO("Not yet implemented")
    }

    override fun onRobotFocusRefused(reason: String?) {
        TODO("Not yet implemented")
    }

}
