fn main() -> Result<(), Box<dyn std::error::Error>> {
    let args: Vec<String> = std::env::args().collect();
    if args.len() != 2 && args.len() != 5 {
        return Err("usage: streamer PORT [OBJECT_STORE_ENDPOINT BUCKET MANIFEST_KEY]".into());
    }
    let port: u16 = args[1].parse()?;
    let bind = std::env::var("STREAMER_BIND_ADDRESS").unwrap_or_else(|_| "127.0.0.1".into());
    if args.len() == 2 {
        let endpoint = std::env::var("OBJECT_STORE_ENDPOINT")?;
        let bucket = std::env::var("OBJECT_STORE_BUCKET")?;
        let manifest_key = std::env::var("OBJECT_STORE_MANIFEST_KEY")?;
        let access_key = std::env::var("OBJECT_STORE_ACCESS_KEY").ok();
        let secret_key = std::env::var("OBJECT_STORE_SECRET_KEY").ok();
        if access_key.is_some() != secret_key.is_some() {
            return Err("object-store access and secret keys must be configured together".into());
        }
        daily_signals_streamer::server::listen_from_object_store(
            &bind,
            port,
            &endpoint,
            &bucket,
            &manifest_key,
        )
    } else {
        daily_signals_streamer::server::listen_from_object_store(
            &bind, port, &args[2], &args[3], &args[4],
        )
    }
}
