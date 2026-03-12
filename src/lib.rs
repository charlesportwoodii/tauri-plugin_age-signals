use tauri::{
    plugin::{Builder, TauriPlugin},
    Manager, Runtime,
};

#[cfg(desktop)]
mod desktop;
#[cfg(mobile)]
mod mobile;

mod commands;
mod error;
mod mapping;
mod models;

pub use error::{Error, Result};

#[cfg(desktop)]
use desktop::AgeSignals;
#[cfg(mobile)]
use mobile::AgeSignals;

/// Extensions to [`tauri::App`], [`tauri::AppHandle`] and [`tauri::Window`] to access the age-signals APIs.
pub trait AgeSignalsExt<R: Runtime> {
    fn age_signals(&self) -> &AgeSignals<R>;
}

impl<R: Runtime, T: Manager<R>> crate::AgeSignalsExt<R> for T {
    fn age_signals(&self) -> &AgeSignals<R> {
        self.state::<AgeSignals<R>>().inner()
    }
}

/// Initializes the plugin.
pub fn init<R: Runtime>() -> TauriPlugin<R> {
    Builder::new("age-signals")
        .invoke_handler(tauri::generate_handler![commands::check_age_range])
        .setup(|app, api| {
            #[cfg(mobile)]
            let age_signals = mobile::init(app, api)?;
            #[cfg(desktop)]
            let age_signals = desktop::init(app, api)?;
            app.manage(age_signals);
            Ok(())
        })
        .build()
}
