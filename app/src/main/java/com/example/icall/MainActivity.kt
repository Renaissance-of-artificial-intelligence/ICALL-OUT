package com.example.icall

import android.Manifest
import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.net.sip.*
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import java.util.concurrent.Executors

class MainActivity : Activity() {

    private lateinit var etDomain: EditText
    private lateinit var etUsername: EditText
    private lateinit var etPass: EditText
    private lateinit var etPhoneNumber: EditText
    private lateinit var btnCall: Button
    private lateinit var tvStatus: TextView

    private var sipManager: SipManager? = null
    private var sipProfile: SipProfile? = null
    private var sipCall: SipAudioCall? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etDomain = findViewById(R.id.etDomain)
        etUsername = findViewById(R.id.etUsername)
        etPass = findViewById(R.id.etPassword)
        etPhoneNumber = findViewById(R.id.etPhoneNumber)
        btnCall = findViewById(R.id.btnCall)
        tvStatus = findViewById(R.id.tvStatus)

        // Initialize SIP Manager
        if (SipManager.isVoipSupported(this) && SipManager.isApiSupported(this)) {
            sipManager = SipManager.newInstance(this)
        } else {
            tvStatus.text = "Status: VoIP not supported on this device"
            btnCall.isEnabled = false
        }

        btnCall.setOnClickListener {
            checkPermissionsAndInit()
        }
    }

    private fun checkPermissionsAndInit() {
        val permissions = arrayOf(
            Manifest.permission.USE_SIP,
            Manifest.permission.RECORD_AUDIO
        )

        var allGranted = true
        for (perm in permissions) {
            if (checkSelfPermission(perm) != PackageManager.PERMISSION_GRANTED) {
                allGranted = false
                break
            }
        }

        if (allGranted) {
            initiateCall()
        } else {
            requestPermissions(permissions, 102)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 102 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            initiateCall()
        }
    }

    private fun initiateCall() {
        val domain = etDomain.text.toString()
        val username = etUsername.text.toString()
        val password = etPass.text.toString()
        val number = etPhoneNumber.text.toString()

        if (domain.isBlank() || username.isBlank() || password.isBlank() || number.isBlank()) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
            return
        }

        // Run network operations on background thread
        Executors.newSingleThreadExecutor().execute {
            try {
                closeLocalProfile()

                val builder = SipProfile.Builder(username, domain)
                builder.setPassword(password)
                sipProfile = builder.build()

                val intent = Intent()
                intent.action = "android.SipDemo.INCOMING_CALL"
                val pendingIntent = PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
                
                sipManager?.open(sipProfile, pendingIntent, null)

                // Wait a moment for registration (simple approach)
                runOnUiThread { tvStatus.text = "Status: Registering..." }
                Thread.sleep(1000)

                makeAudioCall(number, domain)

            } catch (e: Exception) {
                runOnUiThread {
                    tvStatus.text = "Error: ${e.message}"
                    e.printStackTrace()
                }
            }
        }
    }

    private fun makeAudioCall(number: String, domain: String) {
        try {
            val listener = object : SipAudioCall.Listener() {
                override fun onCallEstablished(call: SipAudioCall) {
                    call.startAudio()
                    call.setSpeakerMode(false)
                    if (call.isMuted) call.toggleMute()
                    runOnUiThread { tvStatus.text = "Status: Connected" }
                }

                override fun onCallEnded(call: SipAudioCall) {
                    runOnUiThread { tvStatus.text = "Status: Call Ended" }
                }

                override fun onError(call: SipAudioCall, errorCode: Int, errorMessage: String) {
                    runOnUiThread { tvStatus.text = "Error: $errorMessage" }
                }
            }

            val sipAddress = "$number@$domain"
            
            // Initiate call
            sipCall = sipManager?.makeAudioCall(sipProfile?.uriString, sipAddress, listener, 30)
            
            runOnUiThread { tvStatus.text = "Status: Calling $number..." }

        } catch (e: Exception) {
            runOnUiThread { 
                tvStatus.text = "Call Error: ${e.message}"
                e.printStackTrace()
            }
            if (sipProfile != null) {
                try {
                    sipManager?.close(sipProfile?.uriString)
                } catch (closeEx: Exception) {
                    // Ignore
                }
            }
        }
    }

    private fun closeLocalProfile() {
        if (sipManager == null) return
        try {
            if (sipProfile != null) {
                sipManager?.close(sipProfile?.uriString)
            }
        } catch (e: Exception) {
            // Ignore
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        closeLocalProfile()
        if (sipCall != null) {
            sipCall?.close()
        }
    }
}
