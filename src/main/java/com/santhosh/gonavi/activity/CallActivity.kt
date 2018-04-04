package com.santhosh.gonavi.activity

import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.widget.TextView
import com.santhosh.gonavi.R

class CallActivity : AppCompatActivity() {

    private var mStatus: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        window.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        setContentView(R.layout.activity_call)

        mStatus = findViewById(R.id.tv_call_status);
        //mStatus.text = savedInstanceState.
    }
}