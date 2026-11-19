package com.caglaryildiz.alarm

import android.app.backup.BackupAgent
import android.app.backup.BackupDataInput
import android.app.backup.BackupDataOutput
import android.os.ParcelFileDescriptor

/**
 * Uses Android's Auto Backup for the files listed in the backup rules (fullBackupOnly), and once a
 * restore finishes, schedules the restored alarms right away, before the app is opened.
 */
class BengBackupAgent : BackupAgent() {
    override fun onBackup(oldState: ParcelFileDescriptor?, data: BackupDataOutput?, newState: ParcelFileDescriptor?) = Unit
    override fun onRestore(data: BackupDataInput?, appVersionCode: Int, newState: ParcelFileDescriptor?) = Unit

    override fun onRestoreFinished() {
        super.onRestoreFinished()
        runCatching { Alarms(this).syncAll() }
        runCatching { WakeCheck(this).restore() }
    }
}
