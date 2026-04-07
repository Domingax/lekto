use keyring::Entry;

const KEYCHAIN_SERVICE: &str = "lekto";
const KEYCHAIN_ACCOUNT: &str = "stronghold-passphrase";

#[tauri::command]
pub fn get_or_create_vault_passphrase() -> Result<String, String> {
    let entry = Entry::new(KEYCHAIN_SERVICE, KEYCHAIN_ACCOUNT)
        .map_err(|e| format!("Keychain unavailable: {e}"))?;

    match entry.get_password() {
        Ok(passphrase) => Ok(passphrase),
        Err(keyring::Error::NoEntry) => {
            let passphrase = generate_passphrase()?;
            entry
                .set_password(&passphrase)
                .map_err(|e| format!("Keychain write failed: {e}"))?;
            Ok(passphrase)
        }
        Err(e) => Err(format!("Keychain read failed: {e}")),
    }
}

fn generate_passphrase() -> Result<String, String> {
    use std::fmt::Write;
    let mut bytes = [0u8; 32];
    getrandom::getrandom(&mut bytes[..]).map_err(|e| format!("entropy unavailable: {e}"))?;
    Ok(bytes.iter().fold(String::with_capacity(64), |mut s, b| {
        let _ = write!(s, "{b:02x}");
        s
    }))
}
