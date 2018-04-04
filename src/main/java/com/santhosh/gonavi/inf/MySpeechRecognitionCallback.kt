package com.santhosh.gonavi.inf

import android.os.Bundle

/**
 * Created by stephenvinouze on 18/05/2017.
 */
public interface MySpeechRecognitionCallback {
    fun onKeywordDetected()
    fun onResults(results: List<String>, scores: FloatArray?, mode: Int)
    fun onError(errorMsg: String)
    fun onEndOfSpeech()
}