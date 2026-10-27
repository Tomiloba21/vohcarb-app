package app.vohcarb

import app.cash.sqldelight.db.SqlDriver

/** A fresh, empty database with the app's schema. */
expect fun testDriver(): SqlDriver
