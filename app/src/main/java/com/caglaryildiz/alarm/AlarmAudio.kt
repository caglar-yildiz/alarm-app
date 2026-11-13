package com.caglaryildiz.alarm

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.PowerManager
import android.provider.OpenableColumns
import android.provider.Settings
import android.util.Log

internal data class BuiltInTone(
    val id: String,
    val title: String,
    val description: String,
    val rawResId: Int,
    val premium: Boolean = false
)

internal object BuiltInTones {
    val list = listOf(
        BuiltInTone("beng_digital", "Dijital Saat", "Klasik çalar saat bip sesi", R.raw.beng_digital, premium = false),
        BuiltInTone("beng_gentle", "Sakin Sabah", "Hafif ve huzurlu marimba/çan melodisi", R.raw.beng_gentle, premium = false),
        BuiltInTone("beng_energetic", "Enerjik Uyanış", "Ritmik ve dinamik sabah alarmı", R.raw.beng_energetic, premium = false),
        BuiltInTone("beng_nature", "Doğa & Kuş Sesi", "Kuş cıvıltıları ve dingin sabah", R.raw.beng_nature, premium = true),
        BuiltInTone("beng_heavy", "Derin Uyku Sireni", "Ağır uykudan kesin uyandıran siren", R.raw.beng_heavy, premium = true)
    )

    fun toUri(tone: BuiltInTone): Uri = Uri.parse("beng://tone/${tone.id}")

    fun fromUri(uri: Uri?): BuiltInTone? {
        if (uri == null || uri.scheme != "beng" || uri.host != "tone") return null
        val id = uri.lastPathSegment ?: return null
        return list.firstOrNull { it.id == id }
    }

    fun resolvePlayableUri(context: Context, uri: Uri?): Uri? {
        val builtIn = fromUri(uri)
        if (builtIn != null) {
            return Uri.parse("android.resource://${context.packageName}/${builtIn.rawResId}")
        }
        return uri
    }
}

internal fun resolveToneTitle(context: Context, uriString: String?): String {
    if (uriString.isNullOrBlank()) return "Cihazın varsayılan alarm sesi"
    val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return "Özel alarm sesi"
    val builtIn = BuiltInTones.fromUri(uri)
    if (builtIn != null) {
        val badge = if (builtIn.premium) " ✦" else ""
        return "${builtIn.title}$badge"
    }
    if (uri.scheme == "content") {
        var name: String? = null
        try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) name = cursor.getString(index)
                }
            }
        } catch (_: Exception) {}
        if (!name.isNullOrBlank()) return name
    }
    return runCatching {
        RingtoneManager.getRingtone(context, uri)?.getTitle(context)
    }.getOrNull() ?: "Özel alarm sesi"
}

internal fun playAlarmTone(
    context: Context,
    tone: Uri?,
    loop: Boolean = true,
    keepAwake: Boolean = false
): MediaPlayer? {
    val playableUri = BuiltInTones.resolvePlayableUri(context, tone)
    val defaultAlarm = RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM)
    for (uri in listOfNotNull(playableUri, defaultAlarm, Settings.System.DEFAULT_NOTIFICATION_URI).distinct()) {
        val player = MediaPlayer()
        try {
            player.setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build())
            if (keepAwake) player.setWakeMode(context, PowerManager.PARTIAL_WAKE_LOCK)
            player.setDataSource(context, uri)
            player.isLooping = loop
            player.prepare()
            player.start()
            return player
        } catch (error: Exception) {
            player.release()
            Log.w("AlarmAudio", "Could not play alarm tone: $uri", error)
        }
    }
    return null
}
