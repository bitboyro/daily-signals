use std::path::Path;
fn main() -> Result<(), Box<dyn std::error::Error>> {
    let args: Vec<String> = std::env::args().collect();
    if args.len() != 4 { return Err("usage: streamer PORT MANIFEST_PATH AUDIO_ROOT".into()); }
    let port: u16 = args[1].parse()?;
    let bind = std::env::var("STREAMER_BIND_ADDRESS").unwrap_or_else(|_| "127.0.0.1".into());
    daily_signals_streamer::server::listen(&bind, port, Path::new(&args[2]), Path::new(&args[3]))
}
