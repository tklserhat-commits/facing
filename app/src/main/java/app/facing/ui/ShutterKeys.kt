package app.facing.ui

/** Lautstärketasten als Auslöser: die Kamera meldet sich hier an, MainActivity leitet Tastendrücke weiter. */
object ShutterKeys {
    @Volatile var onPress: (() -> Unit)? = null
}
