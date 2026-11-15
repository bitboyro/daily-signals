use serde_json::json;
use std::time::{SystemTime, UNIX_EPOCH};

pub fn event(
    level: &str,
    service: &str,
    version: &str,
    sc_id: &str,
    class_name: &str,
    event_name: &str,
    message: &str,
) -> Result<String, &'static str> {
    let timestamp = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map_err(|_| "system clock precedes Unix epoch")?
        .as_millis() as u64;
    event_at(
        timestamp, level, service, version, sc_id, class_name, event_name, message,
    )
}

#[allow(clippy::too_many_arguments)]
pub fn event_at(
    timestamp: u64,
    level: &str,
    service: &str,
    version: &str,
    sc_id: &str,
    class_name: &str,
    event_name: &str,
    message: &str,
) -> Result<String, &'static str> {
    if !matches!(level, "DEBUG" | "INFO" | "WARN" | "ERROR")
        || [service, version, sc_id, class_name, message]
            .iter()
            .any(|value| value.trim().is_empty())
        || !valid_event_name(event_name)
    {
        return Err("invalid log envelope");
    }
    Ok(json!({
        "schemaVersion": 1,
        "timestamp": timestamp,
        "level": level,
        "service": service,
        "version": version,
        "scId": sc_id,
        "class": class_name,
        "event": event_name,
        "message": message,
        "fields": {}
    })
    .to_string())
}

fn valid_event_name(value: &str) -> bool {
    let mut characters = value.chars();
    matches!(characters.next(), Some('a'..='z'))
        && characters.all(|character| {
            character.is_ascii_lowercase() || character.is_ascii_digit() || character == '.'
        })
        && value.contains('.')
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn emits_the_cross_service_log_contract() {
        let line = event_at(
            1_794_744_000_000,
            "INFO",
            "daily-signals-streamer",
            "0.1.0",
            "startup",
            "streamer.main",
            "streamer.started",
            "Daily Signals streamer listening",
        )
        .unwrap();
        let value: serde_json::Value = serde_json::from_str(&line).unwrap();
        assert_eq!(value["timestamp"], 1_794_744_000_000_u64);
        assert_eq!(value["level"], "INFO");
        assert_eq!(value["service"], "daily-signals-streamer");
        assert_eq!(value["version"], "0.1.0");
        assert_eq!(value["scId"], "startup");
        assert_eq!(value["class"], "streamer.main");
        assert_eq!(value["message"], "Daily Signals streamer listening");
    }

    #[test]
    fn rejects_unknown_levels_and_event_names() {
        assert!(event_at(
            0,
            "TRACE",
            "streamer",
            "1",
            "x",
            "main",
            "streamer.started",
            "x"
        )
        .is_err());
        assert!(event_at(
            0,
            "INFO",
            "streamer",
            "1",
            "x",
            "main",
            "STREAMER STARTED",
            "x"
        )
        .is_err());
    }
}
