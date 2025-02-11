// SettingsActivity.kt
package com.example.agentapp

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.agentapp.databinding.ActivitySettingsBinding
import com.google.android.material.snackbar.Snackbar

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var prefs: SharedPreferences

    companion object {
        const val PREFS_NAME = "AgentAppPrefs"
        const val KEY_SERVER_IP = "server_ip"
        const val KEY_SERVER_PORT = "server_port"
        const val KEY_SCREENSHOT_COUNT = "screenshot_count"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        binding.ipEditText.setText(prefs.getString(KEY_SERVER_IP, "100.xx.xx.xx"))
        binding.portEditText.setText(prefs.getString(KEY_SERVER_PORT, "5000"))
        binding.screenshotCountEditText.setText(prefs.getInt(KEY_SCREENSHOT_COUNT, 3).toString())

        binding.saveSettingsButton.setOnClickListener {
            val ip = binding.ipEditText.text.toString().trim()
            val port = binding.portEditText.text.toString().trim()
            val screenshotCount = binding.screenshotCountEditText.text.toString().toIntOrNull() ?: 3

            prefs.edit().apply {
                putString(KEY_SERVER_IP, ip)
                putString(KEY_SERVER_PORT, port)
                putInt(KEY_SCREENSHOT_COUNT, screenshotCount)
                apply()
            }

            Snackbar.make(binding.root, "Changes will take effect on the next interaction.", Snackbar.LENGTH_SHORT).show()

            val newUrl = "http://$ip:$port/"
            RetrofitClient.setBaseUrl(newUrl)

            binding.root.postDelayed({ finish() }, 1500)
        }
    }
}
