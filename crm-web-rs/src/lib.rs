//! Kópia Java appky `crm-web` (zmigrovaný blacklist) v Ruste: rovnaké stránky pod /crm-rs, rovnaká identita
//! zo SSO a rovnaký JSON kontrakt zdieľanej session v Redise.

/// Enum s menom ako v Jave (`ACTIVE`) – meno ide do formulárov, i18n kľúčov, JSP atribútov aj JSON.
macro_rules! named_enum {
    ($(#[$meta:meta])* $name:ident { $($variant:ident => $text:literal),+ $(,)? }) => {
        $(#[$meta])*
        #[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
        pub enum $name { $($variant),+ }

        impl $name {
            /// Všetky hodnoty v poradí deklarácie (Java `values()`).
            pub const ALL: &'static [$name] = &[$($name::$variant),+];

            /// Meno ako v Jave (`name()`).
            pub fn name(self) -> &'static str {
                match self { $($name::$variant => $text),+ }
            }

            /// Hodnota podľa Java mena, alebo `None`.
            pub fn from_name(name: &str) -> Option<Self> {
                Self::ALL.iter().copied().find(|value| value.name() == name)
            }
        }
    };
}

pub mod acl;
pub mod blacklist;
pub mod clock;
pub mod config;
pub mod session;
pub mod web;

/// Kontext appky – ekvivalent `server.servlet.context-path=/crm` Java verzie.
pub const CONTEXT_PATH: &str = "/crm-rs";

/// Boxovaný future pre traity používané ako `dyn` (async fn v traite nie je dyn-kompatibilná).
pub type BoxFuture<'a, T> = std::pin::Pin<Box<dyn std::future::Future<Output = T> + Send + 'a>>;

pub use web::app::{AppState, app};

