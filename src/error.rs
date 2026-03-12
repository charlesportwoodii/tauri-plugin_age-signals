use serde::Serializer;

pub type Result<T> = std::result::Result<T, Error>;

#[derive(Debug, thiserror::Error)]
pub enum Error {
    #[error("User is below minimum age {minimum_age}")]
    BelowMinimumAge { minimum_age: u8 },

    #[error("Age signals API not available: {0}")]
    ApiNotAvailable(String),

    #[error("Network error: {0}")]
    NetworkError(String),

    #[error("Play Store not found")]
    PlayStoreNotFound,

    #[error("App not installed via Play Store")]
    AppNotOwned,

    #[error("Invalid request: {0}")]
    InvalidRequest(String),

    #[error("Internal error: {0}")]
    InternalError(String),

    #[cfg(mobile)]
    #[error(transparent)]
    PluginInvoke(#[from] tauri::plugin::mobile::PluginInvokeError),
}

impl serde::Serialize for Error {
    fn serialize<S>(&self, serializer: S) -> std::result::Result<S::Ok, S::Error>
    where
        S: Serializer,
    {
        serializer.serialize_str(self.to_string().as_ref())
    }
}
