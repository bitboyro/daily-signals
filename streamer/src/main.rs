use std::path::Path;
fn main() -> Result<(), Box<dyn std::error::Error>> {
    let args: Vec<String> = std::env::args().collect();
    if args.len() != 4 && args.len() != 5 {
        return Err("usage: streamer PORT MANIFEST_PATH AUDIO_ROOT | streamer PORT OBJECT_STORE_ENDPOINT BUCKET MANIFEST_KEY".into());
    }
    let port: u16 = args[1].parse()?;
    let bind = std::env::var("STREAMER_BIND_ADDRESS").unwrap_or_else(|_| "127.0.0.1".into());
    if args.len() == 5 {
        daily_signals_streamer::server::listen_from_object_store(
            &bind, port, &args[2], &args[3], &args[4],
        )
    } else {
        daily_signals_streamer::server::listen(
            &bind,
            port,
            Path::new(&args[2]),
            Path::new(&args[3]),
        )
    }
}
