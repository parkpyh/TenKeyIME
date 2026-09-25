package com.umpire.tenkeyime

import android.app.Activity
import android.content.Intent
import android.os.Bundle

class MainActivity : Activity() {

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        val intent =
            Intent(
                this,
                KeyboardSwitchService::class.java
            )

        startForegroundService(intent)

        finish()
    }
}