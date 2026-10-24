pub mod server;
pub mod manifest;
//! Published-audio streaming boundary. Editorial decisions remain in Java.
pub fn health_body() -> &'static str { r#"{"status":"ok"}"# }

#[cfg(test)]
mod tests {
    #[test]
    fn health_is_stable_json() {
        assert_eq!(super::health_body(), r#"{"status":"ok"}"#);
    }
}
