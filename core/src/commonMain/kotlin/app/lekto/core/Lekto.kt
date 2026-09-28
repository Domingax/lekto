package app.lekto.core

/**
 * The product's identity, kept in the domain so every module can name it
 * without depending on the application. This is deliberately trivial: the
 * skeleton exists to prove the build, not to hold behaviour.
 */
object Lekto {
    const val NAME: String = "Lekto"

    fun greeting(): String = "Hello from $NAME"
}
