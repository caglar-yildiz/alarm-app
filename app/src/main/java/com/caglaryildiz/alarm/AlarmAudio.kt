package com.caglaryildiz.alarm

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.util.Log

internal fun playAlarmTone(
    context: Context,
    tone: Uri?,
    loop: Boolean = true,
    keepAwake: Boolean = false
): MediaPlayer? {
    val defaultAlarm = RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM)
    for (uri in listOfNotNull(tone, defaultAlarm, Settings.System.DEFAULT_NOTIFICATION_URI).distinct()) {
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
            Log.w("AlarmAudio", "Could not play alarm tone", error)
        }
    }
    return null
}
