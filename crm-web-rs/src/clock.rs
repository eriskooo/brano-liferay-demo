//! Hodiny ako závislosť (Java `ClockConfig` bean) – pravidlá s „dnešným dátumom“ sa testujú s pevným časom.

use chrono::{Local, NaiveDate, NaiveDateTime};

/// Zdroj aktuálneho času.
pub trait Clock: Send + Sync {
    /// Aktuálny lokálny čas.
    fn now(&self) -> NaiveDateTime;

    /// Dnešný dátum.
    fn today(&self) -> NaiveDate {
        self.now().date()
    }
}

/// Systémové hodiny (v kontajneri UTC rovnako ako Java image).
pub struct SystemClock;

impl Clock for SystemClock {
    fn now(&self) -> NaiveDateTime {
        Local::now().naive_local()
    }
}

/// Pevný čas pre testy.
pub struct FixedClock(pub NaiveDateTime);

impl Clock for FixedClock {
    fn now(&self) -> NaiveDateTime {
        self.0
    }
}
