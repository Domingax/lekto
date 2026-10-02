package app.lekto.core.vault

/**
 * A vault file that cannot be read back: a missing record the manifest names, or
 * an export whose format this build does not understand.
 *
 * It is deliberately distinct from a malformed-input error at the platform edge
 * so a caller can tell "this vault is damaged" from "this call was wrong".
 */
class VaultFormatException(message: String) : Exception(message)
