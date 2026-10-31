package org.koitharu.kotatsu.core.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Migration27To28 : Migration(27, 28) {

	override fun migrate(db: SupportSQLiteDatabase) {
		db.execSQL("ALTER TABLE chapters ADD COLUMN is_locked INTEGER NOT NULL DEFAULT 0")
	}
}
