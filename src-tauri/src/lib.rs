mod keychain;

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
  let mut builder = tauri::Builder::default()
    .invoke_handler(tauri::generate_handler![keychain::get_or_create_vault_passphrase])
    .plugin(tauri_plugin_sql::Builder::default().build())
    .plugin(tauri_plugin_fs::init())
    .plugin(tauri_plugin_dialog::init())
    .plugin(tauri_plugin_store::Builder::default().build())
    .plugin(
      tauri_plugin_stronghold::Builder::new(|password| {
        use argon2::{Argon2, PasswordHasher};
        use argon2::password_hash::SaltString;
        let salt = SaltString::encode_b64(b"lekto-stronghold-salt-v1").unwrap();
        let argon2 = Argon2::default();
        let hash = argon2
          .hash_password(password.as_ref(), &salt)
          .expect("stronghold hash error")
          .to_string();
        hash.as_bytes().to_vec()
      })
      .build(),
    );

  #[cfg(debug_assertions)]
  {
    builder = builder.plugin(tauri_plugin_mcp_bridge::init());
  }

  builder
    .run(tauri::generate_context!())
    .expect("error while running tauri application")
}
