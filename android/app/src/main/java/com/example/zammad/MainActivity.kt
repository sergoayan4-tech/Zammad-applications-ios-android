package com.example.zammad

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.zammad.core.Prefs
import com.example.zammad.session.SessionStore

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = Prefs(applicationContext)
        val session = SessionStore(prefs)
        session.applyLanguage()

        setContent {
            ZammadApp(session)
        }
    }
}
