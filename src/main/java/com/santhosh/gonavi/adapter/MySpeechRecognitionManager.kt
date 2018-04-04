package com.santhosh.gonavi.adapter

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import com.santhosh.gonavi.inf.MySpeechRecognitionCallback
import java.util.*


class MySpeechRecognitionManager(private val context: Context,
                                 private val activationKeyword: String,
                                 private val shouldMute: Boolean? = false,
                                 private val callback: MySpeechRecognitionCallback? = null) : RecognitionListener {

    var recognizerIntent: Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)

    private var isActivated: Boolean = false
    private var isListening: Boolean = false
    private var speech: SpeechRecognizer? = null
    private var mode: Int = 100
    private var audioManager: AudioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    init {
        //recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, Locale.getDefault());
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH)
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)

        initializeRecognizer()
    }

    private fun initializeRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            speech = SpeechRecognizer.createSpeechRecognizer(context)
            speech?.setRecognitionListener(this)
            //callback?.onPrepared(if (speech != null) RecognitionStatus.SUCCESS else RecognitionStatus.FAILURE)
        } else {
            //callback?.onPrepared(RecognitionStatus.UNAVAILABLE)
        }
    }

    fun destroyRecognizer() {
        muteRecognition(false)
        speech?.destroy()
    }

    fun startRecognition(mode: Int) {
        if (!isListening) {
            isListening = true
            this.mode = mode
            speech?.startListening(recognizerIntent)
        }
    }

    fun stopRecognition() {
        speech?.stopListening()
    }

    fun cancelRecognition() {
        speech?.cancel()
    }

     fun muteRecognition(mute: Boolean) {
         if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
             val flag = if (mute) AudioManager.ADJUST_MUTE else AudioManager.ADJUST_UNMUTE
             //audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, flag, 0)
             //audioManager.adjustStreamVolume(AudioManager.STREAM_SYSTEM, flag, 0)
         } else {
             //audioManager.setStreamMute(AudioManager.STREAM_MUSIC, mute);
             //audioManager.setStreamMute(AudioManager.STREAM_SYSTEM, mute);
         }

    }

    override fun onBeginningOfSpeech() {
        //callback?.onBeginningOfSpeech()
    }

    override fun onReadyForSpeech(params: Bundle) {
        muteRecognition((shouldMute != null && shouldMute) || !isActivated)
        //callback?.onReadyForSpeech(params)
    }

    override fun onBufferReceived(buffer: ByteArray) {
        //callback?.onBufferReceived(buffer)
    }

    override fun onRmsChanged(rmsdB: Float) {
        //callback?.onRmsChanged(rmsdB)
    }

    override fun onEndOfSpeech() {
        callback?.onEndOfSpeech()
    }

    override fun onError(errorCode: Int) {
        if (isActivated) {
            callback?.onError(getErrorText(errorCode))
        }
        isActivated = false
        isListening = false

        when (errorCode) {
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> cancelRecognition()
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                destroyRecognizer()
                initializeRecognizer()
            }
        }

        startRecognition(100)
    }

    override fun onEvent(eventType: Int, params: Bundle) {
        //callback?.onEvent(eventType, params)
    }

    override fun onPartialResults(partialResults: Bundle) {
        /*val matches = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        if (isActivated && matches != null) {
            callback?.onPartialResults(matches)
        }*/
    }

    override fun onResults(results: Bundle) {
        val matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val scores = results.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)
        Log.d("santhosh", "onResults 0 : $matches, $scores")
        if (matches != null) {
            if (isActivated) {
                isActivated = false
                callback?.onResults(matches, scores, mode)
            } else {
                matches.forEach {
                    if (it.contains(other = activationKeyword, ignoreCase = true)) {
                        isActivated = true
                        callback?.onKeywordDetected()
                        //Log.d("santhosh", "onKeywordDetected")
                        return@forEach
                    }
                }
            }
        }

        isListening = false
        startRecognition(100)
    }

    private fun getErrorText(errorCode: Int): String {
        when (errorCode) {
            SpeechRecognizer.ERROR_AUDIO -> return "Audio recording error"
            SpeechRecognizer.ERROR_CLIENT -> return "Client side error"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> return "Insufficient permissions"
            SpeechRecognizer.ERROR_NETWORK -> return "Network error"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> return "Network timeout"
            SpeechRecognizer.ERROR_NO_MATCH -> return "No match"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> return "RecognitionService busy"
            SpeechRecognizer.ERROR_SERVER -> return "Error from server"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> return "No speech input"
            else -> return "Didn't understand, please try again."
        }
    }
}