package app.vohcarb.core

import java.util.TimeZone

actual fun deviceTimeZone(): String = TimeZone.getDefault().id
