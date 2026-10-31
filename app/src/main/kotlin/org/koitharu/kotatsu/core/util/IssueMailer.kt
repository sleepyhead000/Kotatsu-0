package org.koitharu.kotatsu.core.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import org.koitharu.kotatsu.BuildConfig
import org.koitharu.kotatsu.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object IssueMailer {

	fun send(context: Context, error: Throwable? = null): Boolean {
		val body = buildString {
			appendLine(context.getString(R.string.issue_report_body_hint))
			appendLine()
			appendLine("--- diagnostics ---")
			appendLine("App: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}), ${BuildConfig.BUILD_TYPE}")
			appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
			appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
			appendLine("Locale: ${Locale.getDefault()}")
			appendLine("Time: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(Date())}")
			if (error != null) {
				appendLine("Error code: ${error.javaClass.name}")
				appendLine()
				appendLine(error.stackTraceToString())
			}
		}
		val intent = Intent(Intent.ACTION_SENDTO).apply {
			data = Uri.parse("mailto:" + Uri.encode(context.getString(R.string.issue_email)))
			putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.issue_report_subject))
			putExtra(Intent.EXTRA_TEXT, body)
			addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
		}
		return runCatching { context.startActivity(intent) }.isSuccess
	}
}
