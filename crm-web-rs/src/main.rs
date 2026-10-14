//! Štart servera. Zastaví sa na SIGTERM (docker stop) – inak by Docker čakal 10 s a proces zabil.

use axum::serve::ListenerExt;
use crm_web_rs::config::Config;
use crm_web_rs::{AppState, app};
use tracing_subscriber::EnvFilter;

#[tokio::main]
async fn main() {
    tracing_subscriber::fmt()
        .with_env_filter(EnvFilter::try_from_default_env().unwrap_or_else(|_| EnvFilter::new("info")))
        .init();
    let config = Config::from_env();
    let bind = config.bind;
    let state = AppState::production(config).unwrap_or_else(|e| fail("Konfigurácia appky zlyhala", &e));
    let listener = tokio::net::TcpListener::bind(bind).await.unwrap_or_else(|e| fail("Port sa nedá otvoriť", &e));
    // axum 0.8 už TCP_NODELAY nenastavuje (Tomcat áno) – bez neho Nagle + delayed ACK pridá ~40 ms na keep-alive spojení.
    let listener = listener.tap_io(|tcp| {
        if let Err(e) = tcp.set_nodelay(true) {
            tracing::debug!("TCP_NODELAY sa nedá nastaviť: {e}");
        }
    });
    tracing::info!("crm-web-rs počúva na {bind}");
    if let Err(e) = axum::serve(listener, app(state)).with_graceful_shutdown(shutdown_signal()).await {
        fail("Server zlyhal", &e);
    }
}

fn fail(what: &str, error: &dyn std::fmt::Display) -> ! {
    tracing::error!("{what}: {error}");
    std::process::exit(1)
}

async fn shutdown_signal() {
    let ctrl_c = async {
        let _ = tokio::signal::ctrl_c().await;
    };
    #[cfg(unix)]
    let terminate = async {
        if let Ok(mut signal) = tokio::signal::unix::signal(tokio::signal::unix::SignalKind::terminate()) {
            signal.recv().await;
        }
    };
    #[cfg(not(unix))]
    let terminate = std::future::pending::<()>();
    tokio::select! {
        _ = ctrl_c => {},
        _ = terminate => {},
    }
}
