#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
  tauri::Builder::default()
    .plugin(tauri_plugin_sql::Builder::default().build())
    .plugin(tauri_plugin_fs::init())
    .plugin(tauri_plugin_dialog::init())
    .plugin(tauri_plugin_store::Builder::default().build())
    .plugin(
      tauri_plugin_stronghold::Builder::new(|password| {
        use argon2::{Argon2, PasswordHasher};
        use argon2::password_hash::SaltString;
        let salt = SaltString::encode_b64(b"letko-stronghold-salt-v1").unwrap();
        let argon2 = Argon2::default();
        let hash = argon2
          .hash_password(password.as_ref(), &salt)
          .expect("stronghold hash error")
          .to_string();
        hash.as_bytes().to_vec()
      })
      .build(),
    )
    .run(tauri::generate_context!())
    .expect("error while running tauri application")
}
