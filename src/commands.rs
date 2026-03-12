use tauri::{command, AppHandle, Runtime};

use crate::AgeSignalsExt;

#[command]
pub(crate) async fn check_age_range<R: Runtime>(
    app: AppHandle<R>,
    minimum_age: u8,
) -> crate::Result<Option<bool>> {
    app.age_signals().check_age_range(minimum_age).await
}
