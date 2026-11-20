package com.omrscanner.app.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.omrscanner.app.data.lecturer.LECTURER_URL

/** App-wide settings, persisted in SharedPreferences and observable from Compose. */
class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    /** The Lecturer.bd session, kept apart from the other settings and out of backups (see the backup rules in res/xml). */
    private val auth = context.getSharedPreferences("lecturer_auth", Context.MODE_PRIVATE)

    private var _lecturerUrl by mutableStateOf(prefs.getString(K_LURL, LECTURER_URL) ?: LECTURER_URL)
    private var _lecturerToken by mutableStateOf(auth.getString(K_TOKEN, "") ?: "")
    private var _lecturerName by mutableStateOf(auth.getString(K_NAME, "") ?: "")

    /** The Lecturer.bd server: the public one unless changed. */
    var lecturerUrl: String
        get() = _lecturerUrl
        set(v) { _lecturerUrl = v.trim().ifEmpty { LECTURER_URL }; prefs.edit().putString(K_LURL, _lecturerUrl).apply() }

    val lecturerToken: String get() = _lecturerToken
    /** The teacher signed in to Lecturer.bd, as Lecturer.bd names them. */
    val lecturerName: String get() = _lecturerName
    val lecturerSignedIn: Boolean get() = _lecturerToken.isNotEmpty()

    fun lecturerSignIn(token: String, name: String) {
        _lecturerToken = token; _lecturerName = name
        auth.edit().putString(K_TOKEN, token).putString(K_NAME, name).apply()
    }

    fun lecturerSignOut() {
        _lecturerToken = ""; _lecturerName = ""
        auth.edit().clear().apply()
    }

    private var _autoCapture by mutableStateOf(prefs.getBoolean(K_AUTO, true))
    private var _maxTilt by mutableStateOf(prefs.getInt(K_TILT, 25))
    private var _erasePct by mutableStateOf(prefs.getInt(K_ERASE, 55))
    private var _school by mutableStateOf(prefs.getString(K_SCHOOL, "") ?: "")
    private var _bankUrl by mutableStateOf(prefs.getString(K_BANK, "") ?: "")

    /** OMR scanner: capture as soon as the 4 corners are steady. */
    var autoCapture: Boolean
        get() = _autoCapture
        set(v) { _autoCapture = v; prefs.edit().putBoolean(K_AUTO, v).apply() }

    /** Steeper photos are refused — bubbles foreshorten too much to read reliably. */
    var maxTilt: Int
        get() = _maxTilt
        set(v) { _maxTilt = v.coerceIn(10, 40); prefs.edit().putInt(K_TILT, _maxTilt).apply() }

    /** A second mark lighter than this % of the darker one counts as erased. */
    var erasePct: Int
        get() = _erasePct
        set(v) { _erasePct = v.coerceIn(30, 80); prefs.edit().putInt(K_ERASE, _erasePct).apply() }

    var schoolName: String
        get() = _school
        set(v) { _school = v; prefs.edit().putString(K_SCHOOL, v).apply() }

    /** Base URL of the question-bank API. Empty = built-in sample bank. */
    var bankUrl: String
        get() = _bankUrl
        set(v) { _bankUrl = v.trim(); prefs.edit().putString(K_BANK, _bankUrl).apply() }

    private companion object {
        const val K_AUTO = "auto_capture"
        const val K_TILT = "max_tilt"
        const val K_ERASE = "erase_pct"
        const val K_SCHOOL = "school"
        const val K_BANK = "bank_url"
        const val K_LURL = "lecturer_url"
        const val K_TOKEN = "token"
        const val K_NAME = "name"
    }
}
