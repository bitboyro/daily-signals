use crate::manifest::PublicationManifest;
use std::io::{Read, Write};
use std::net::{TcpListener, TcpStream};
use std::path::Path;

pub struct Response { pub status: u16, pub headers: Vec<(String, String)>, pub body: Vec<u8> }
impl Response {
    fn new(status: u16, body: Vec<u8>, content_type: &str) -> Self {
        Self { status, headers: vec![("Content-Type".into(), content_type.into())], body }
    }
    fn wire(&self) -> Vec<u8> {
        let reason = match self.status { 200 => "OK", 206 => "Partial Content", 304 => "Not Modified", 404 => "Not Found", 405 => "Method Not Allowed", 416 => "Range Not Satisfiable", _ => "Internal Server Error" };
        let mut wire = format!("HTTP/1.1 {} {}\r\nContent-Length: {}\r\nConnection: close\r\n", self.status, reason, self.body.len()).into_bytes();
        for (key, value) in &self.headers { wire.extend_from_slice(format!("{}: {}\r\n", key, value).as_bytes()); }
        wire.extend_from_slice(b"\r\n"); wire.extend_from_slice(&self.body); wire
    }
}
pub fn route(request: &str, manifest: &PublicationManifest, audio: &[u8]) -> Response {
    let mut parts = request.lines().next().unwrap_or("").split_whitespace();
    let method = parts.next().unwrap_or(""); let path = parts.next().unwrap_or("");
    if method != "GET" { return Response::new(405, vec![], "text/plain"); }
    if path == "/health" { return Response::new(200, crate::health_body().as_bytes().to_vec(), "application/json"); }
    if path != format!("/editions/{}/audio", manifest.edition_id) { return Response::new(404, vec![], "text/plain"); }
    let etag = format!("\"{}\"", manifest.checksum_sha256);
    let mut response = Response::new(200, audio.to_vec(), &manifest.content_type);
    response.headers.push(("ETag".into(), etag.clone()));
    response.headers.push(("Accept-Ranges".into(), "bytes".into()));
    if request.lines().any(|line| line.strip_prefix("If-None-Match: ") == Some(etag.as_str())) {
        response.status = 304; response.body.clear();
        return response;
    }
    let range = request.lines().find_map(|line| line.strip_prefix("Range: bytes="));
    // Full-object responses keep their ETag; ranges retain the same identity.
    if let Some(range) = range {
        let (start, end) = match range.split_once('-') { Some(value) => value, None => return Response::new(416, vec![], "text/plain") };
        let start = match start.parse::<usize>() { Ok(value) => value, Err(_) => return Response::new(416, vec![], "text/plain") };
        let end = if end.is_empty() { audio.len().saturating_sub(1) } else { match end.parse::<usize>() { Ok(value) => value, Err(_) => return Response::new(416, vec![], "text/plain") } };
        if start >= audio.len() || end < start || end >= audio.len() { return Response::new(416, vec![], "text/plain"); }
        response.status = 206; response.body = audio[start..end].to_vec();
        response.headers.push(("Content-Range".into(), format!("bytes {}-{}/{}", start, end, audio.len())));
    }
    response
}
fn serve(mut stream: TcpStream, manifest: &PublicationManifest, audio: &[u8]) -> std::io::Result<()> {
    let mut buffer = [0_u8; 8192]; let size = stream.read(&mut buffer)?;
    let request = String::from_utf8_lossy(&buffer[..size]);
    stream.write_all(&route(&request, manifest, audio).wire())
}
pub fn listen(port: u16, manifest_path: &Path, audio_root: &Path) -> Result<(), Box<dyn std::error::Error>> {
    let manifest: PublicationManifest = serde_json::from_slice(&std::fs::read(manifest_path)?)?;
    manifest.validate()?;
    let audio = std::fs::read(audio_root.join(&manifest.audio_path))?;
    if audio.len() as u64 != manifest.size_bytes { return Err("audio size does not match manifest".into()); }
    let listener = TcpListener::bind(("127.0.0.1", port))?;
    for incoming in listener.incoming() { if let Ok(stream) = incoming { let _ = serve(stream, &manifest, &audio); } }
    Ok(())
}
#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn serves_range_and_etag() {
        let manifest: PublicationManifest = serde_json::from_str(include_str!("../../contracts/publication-manifest-v1.json")).unwrap();
        let response = route("GET /editions/demo-2026-01-15/audio HTTP/1.1\r\nRange: bytes=1-3\r\n", &manifest, b"RIFF-demo");
        assert_eq!(response.status, 206);
        assert_eq!(response.body, b"IFF");
        assert!(response.headers.iter().any(|(k, v)| k == "Content-Range" && v == "bytes 1-3/9"));
        assert!(response.headers.iter().any(|(k, _)| k == "ETag"));
        assert_eq!(route("GET /editions/demo-2026-01-15/audio HTTP/1.1\r\nRange: bytes=99-100\r\n", &manifest, b"RIFF-demo").status, 416);
        let cached = route(&format!("GET /editions/demo-2026-01-15/audio HTTP/1.1\r\nIf-None-Match: \"{}\"\r\n", manifest.checksum_sha256), &manifest, b"RIFF-demo");
        assert_eq!(cached.status, 304); assert!(cached.body.is_empty());
    }
    #[test]
    fn serves_only_published_route() {
        let manifest: PublicationManifest = serde_json::from_str(include_str!("../../contracts/publication-manifest-v1.json")).unwrap();
        let audio = b"RIFF-demo";
        let response = route("GET /editions/demo-2026-01-15/audio HTTP/1.1\r\n", &manifest, audio);
        assert_eq!(response.status, 200); assert_eq!(response.body, audio);
        assert_eq!(route("GET /editions/other/audio HTTP/1.1\r\n", &manifest, audio).status, 404);
    }
}
