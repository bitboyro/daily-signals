use crate::manifest::PublicationManifest;
use std::io::{Read, Write};
use std::net::{TcpListener, TcpStream};

pub struct Response {
    pub status: u16,
    pub headers: Vec<(String, String)>,
    pub body: Vec<u8>,
}
#[derive(Debug, PartialEq)]
pub struct ThroughputProfile {
    pub requests: usize,
    pub bytes: usize,
}

fn object_store_get(
    endpoint: &str,
    bucket: &str,
    key: &str,
) -> Result<Option<Vec<u8>>, Box<dyn std::error::Error>> {
    let authority = endpoint
        .strip_prefix("http://")
        .ok_or("HTTP object-store endpoint required")?;
    if authority.is_empty()
        || authority.contains('/')
        || bucket.is_empty()
        || key.is_empty()
        || bucket.contains('/')
        || key.starts_with('/')
        || key.split('/').any(|part| part.is_empty() || part == "..")
    {
        return Err("invalid object-store location".into());
    }
    let (host, port) = authority
        .rsplit_once(':')
        .map_or((authority, 80), |(host, port)| {
            (host, port.parse::<u16>().unwrap_or(0))
        });
    if host.is_empty() || port == 0 {
        return Err("invalid object-store authority".into());
    }
    let mut stream = TcpStream::connect((host, port))?;
    stream.write_all(
        format!("GET /{bucket}/{key} HTTP/1.1\r\nHost: {authority}\r\nConnection: close\r\n\r\n")
            .as_bytes(),
    )?;
    let mut response = Vec::new();
    stream.read_to_end(&mut response)?;
    let split = response
        .windows(4)
        .position(|window| window == b"\r\n\r\n")
        .ok_or("invalid object-store HTTP response")?;
    let headers = std::str::from_utf8(&response[..split])?;
    let status = headers
        .lines()
        .next()
        .and_then(|line| line.split_whitespace().nth(1))
        .ok_or("missing object-store status")?;
    if status == "404" {
        return Ok(None);
    }
    if status != "200" {
        return Err(format!("object GET failed: {status}").into());
    }
    Ok(Some(response[split + 4..].to_vec()))
}

pub fn load_from_object_store(
    endpoint: &str,
    bucket: &str,
    manifest_key: &str,
) -> Result<(PublicationManifest, Option<Vec<u8>>), Box<dyn std::error::Error>> {
    let manifest_bytes = object_store_get(endpoint, bucket, manifest_key)?
        .ok_or("publication manifest not found")?;
    let manifest: PublicationManifest = serde_json::from_slice(&manifest_bytes)?;
    manifest.validate()?;
    let audio = object_store_get(endpoint, bucket, &manifest.audio_path)?;
    if let Some(bytes) = &audio {
        if bytes.len() as u64 != manifest.size_bytes {
            return Err("audio size does not match manifest".into());
        }
    }
    Ok((manifest, audio))
}

pub fn profile_routes(
    requests: usize,
    manifest: &PublicationManifest,
    audio: &[u8],
) -> ThroughputProfile {
    let request = format!("GET /editions/{}/audio HTTP/1.1\r\n", manifest.edition_id);
    let bytes = (0..requests)
        .map(|_| route(&request, manifest, audio).body.len())
        .sum();
    ThroughputProfile { requests, bytes }
}
impl Response {
    fn new(status: u16, body: Vec<u8>, content_type: &str) -> Self {
        Self {
            status,
            headers: vec![("Content-Type".into(), content_type.into())],
            body,
        }
    }
    fn wire(&self) -> Vec<u8> {
        let reason = match self.status {
            200 => "OK",
            206 => "Partial Content",
            304 => "Not Modified",
            403 => "Forbidden",
            404 => "Not Found",
            405 => "Method Not Allowed",
            416 => "Range Not Satisfiable",
            _ => "Internal Server Error",
        };
        let mut wire = format!(
            "HTTP/1.1 {} {}\r\nContent-Length: {}\r\nConnection: close\r\n",
            self.status,
            reason,
            self.body.len()
        )
        .into_bytes();
        for (key, value) in &self.headers {
            wire.extend_from_slice(format!("{}: {}\r\n", key, value).as_bytes());
        }
        wire.extend_from_slice(b"\r\n");
        wire.extend_from_slice(&self.body);
        wire
    }
}
/// Object key prefixes listeners may read; `None` leaves every published object readable.
#[derive(Debug, Clone, Default)]
pub struct AccessPolicy {
    public_prefixes: Option<Vec<String>>,
}
impl AccessPolicy {
    pub fn from_prefixes(value: Option<&str>) -> Self {
        let prefixes = value.map(|v| {
            v.split(',')
                .map(str::trim)
                .filter(|p| !p.is_empty())
                .map(String::from)
                .collect()
        });
        Self {
            public_prefixes: prefixes,
        }
    }
    pub fn allows(&self, object_key: &str) -> bool {
        self.public_prefixes.as_ref().map_or(true, |prefixes| {
            prefixes.iter().any(|p| object_key.starts_with(p.as_str()))
        })
    }
}
pub fn route_with_policy(
    request: &str,
    manifest: &PublicationManifest,
    audio: Option<&[u8]>,
    policy: &AccessPolicy,
) -> Response {
    let path = request
        .lines()
        .next()
        .unwrap_or("")
        .split_whitespace()
        .nth(1)
        .unwrap_or("");
    if path.starts_with("/editions/") && !policy.allows(&manifest.audio_path) {
        return Response::new(403, vec![], "text/plain");
    }
    route_for_object(request, manifest, audio)
}
pub fn route_for_object(
    request: &str,
    manifest: &PublicationManifest,
    audio: Option<&[u8]>,
) -> Response {
    match audio {
        Some(bytes) => route(request, manifest, bytes),
        None => Response::new(404, vec![], "text/plain"),
    }
}
pub fn route(request: &str, manifest: &PublicationManifest, audio: &[u8]) -> Response {
    let mut parts = request.lines().next().unwrap_or("").split_whitespace();
    let method = parts.next().unwrap_or("");
    let path = parts.next().unwrap_or("");
    if method != "GET" {
        return Response::new(405, vec![], "text/plain");
    }
    if path == "/health" {
        return Response::new(
            200,
            crate::health_body().as_bytes().to_vec(),
            "application/json",
        );
    }
    if path != format!("/editions/{}/audio", manifest.edition_id) {
        return Response::new(404, vec![], "text/plain");
    }
    let etag = format!("\"{}\"", manifest.checksum_sha256);
    let mut response = Response::new(200, audio.to_vec(), &manifest.content_type);
    response.headers.push(("ETag".into(), etag.clone()));
    response
        .headers
        .push(("Accept-Ranges".into(), "bytes".into()));
    response.headers.push((
        "Cache-Control".into(),
        "public, max-age=300, immutable".into(),
    ));
    if request
        .lines()
        .any(|line| line.strip_prefix("If-None-Match: ") == Some(etag.as_str()))
    {
        response.status = 304;
        response.body.clear();
        return response;
    }
    let range = request
        .lines()
        .find_map(|line| line.strip_prefix("Range: bytes="));
    // Full-object responses keep their ETag; ranges retain the same identity.
    if let Some(range) = range {
        let (start, end) = match range.split_once('-') {
            Some(value) => value,
            None => return Response::new(416, vec![], "text/plain"),
        };
        let start = match start.parse::<usize>() {
            Ok(value) => value,
            Err(_) => return Response::new(416, vec![], "text/plain"),
        };
        let end = if end.is_empty() {
            audio.len().saturating_sub(1)
        } else {
            match end.parse::<usize>() {
                Ok(value) => value,
                Err(_) => return Response::new(416, vec![], "text/plain"),
            }
        };
        if start >= audio.len() || end < start || end >= audio.len() {
            return Response::new(416, vec![], "text/plain");
        }
        response.status = 206;
        response.body = audio[start..=end].to_vec();
        response.headers.push((
            "Content-Range".into(),
            format!("bytes {}-{}/{}", start, end, audio.len()),
        ));
    }
    response
}
fn serve(
    mut stream: TcpStream,
    manifest: &PublicationManifest,
    audio: Option<&[u8]>,
    policy: &AccessPolicy,
) -> std::io::Result<()> {
    let mut buffer = [0_u8; 8192];
    let size = stream.read(&mut buffer)?;
    let request = String::from_utf8_lossy(&buffer[..size]);
    stream.write_all(&route_with_policy(&request, manifest, audio, policy).wire())
}
pub fn listen_from_object_store(
    bind: &str,
    port: u16,
    endpoint: &str,
    bucket: &str,
    manifest_key: &str,
) -> Result<(), Box<dyn std::error::Error>> {
    let (manifest, audio) = load_from_object_store(endpoint, bucket, manifest_key)?;
    listen_loaded(bind, port, manifest, audio)
}
fn listen_loaded(
    bind: &str,
    port: u16,
    manifest: PublicationManifest,
    audio: Option<Vec<u8>>,
) -> Result<(), Box<dyn std::error::Error>> {
    let policy =
        AccessPolicy::from_prefixes(std::env::var("STREAMER_PUBLIC_PREFIXES").ok().as_deref());
    let listener = TcpListener::bind((bind, port))?;
    for incoming in listener.incoming() {
        if let Ok(stream) = incoming {
            let _ = serve(stream, &manifest, audio.as_deref(), &policy);
        }
    }
    Ok(())
}
#[cfg(test)]
mod tests {
    use super::*;
    use std::thread;
    #[test]
    fn serves_range_and_etag() {
        let manifest: PublicationManifest =
            serde_json::from_str(include_str!("../../contracts/publication-manifest-v2.json"))
                .unwrap();
        let response = route(
            "GET /editions/demo-2026-01-15/audio HTTP/1.1\r\nRange: bytes=1-3\r\n",
            &manifest,
            b"RIFF-demo",
        );
        assert_eq!(response.status, 206);
        assert_eq!(response.body, b"IFF");
        assert!(response
            .headers
            .iter()
            .any(|(k, v)| k == "Content-Range" && v == "bytes 1-3/9"));
        assert!(response.headers.iter().any(|(k, _)| k == "ETag"));
        assert_eq!(
            route(
                "GET /editions/demo-2026-01-15/audio HTTP/1.1\r\nRange: bytes=99-100\r\n",
                &manifest,
                b"RIFF-demo"
            )
            .status,
            416
        );
        let cached = route(
            &format!(
                "GET /editions/demo-2026-01-15/audio HTTP/1.1\r\nIf-None-Match: \"{}\"\r\n",
                manifest.checksum_sha256
            ),
            &manifest,
            b"RIFF-demo",
        );
        assert_eq!(cached.status, 304);
        assert!(cached.body.is_empty());
        assert!(cached
            .headers
            .iter()
            .any(|(k, v)| k == "Cache-Control" && v == "public, max-age=300, immutable"));
    }
    #[test]
    fn inclusive_range_end_and_single_byte_boundary() {
        let manifest: PublicationManifest =
            serde_json::from_str(include_str!("../../contracts/publication-manifest-v2.json"))
                .unwrap();
        let final_byte = route(
            "GET /editions/demo-2026-01-15/audio HTTP/1.1\r\nRange: bytes=8-8\r\n",
            &manifest,
            b"RIFF-demo",
        );
        assert_eq!(final_byte.status, 206);
        assert_eq!(final_byte.body, b"o");
        assert!(final_byte
            .headers
            .iter()
            .any(|(k, v)| k == "Content-Range" && v == "bytes 8-8/9"));
    }
    #[test]
    fn missing_audio_object_is_not_found() {
        let manifest: PublicationManifest =
            serde_json::from_str(include_str!("../../contracts/publication-manifest-v2.json"))
                .unwrap();
        let response = route_for_object(
            "GET /editions/demo-2026-01-15/audio HTTP/1.1\r\n",
            &manifest,
            None,
        );
        assert_eq!(response.status, 404);
    }
    #[test]
    fn access_policy_limits_listener_reads_to_public_prefixes() {
        let manifest: PublicationManifest =
            serde_json::from_str(include_str!("../../contracts/publication-manifest-v2.json"))
                .unwrap();
        let request = "GET /editions/demo-2026-01-15/audio HTTP/1.1\r\n";
        let open = AccessPolicy::from_prefixes(None);
        assert_eq!(
            route_with_policy(request, &manifest, Some(b"RIFF-demo"), &open).status,
            200
        );
        let fixtures = AccessPolicy::from_prefixes(Some("editions/, fixtures/"));
        assert_eq!(
            route_with_policy(request, &manifest, Some(b"RIFF-demo"), &fixtures).status,
            200
        );
        let editions_only = AccessPolicy::from_prefixes(Some("editions/"));
        assert_eq!(
            route_with_policy(request, &manifest, Some(b"RIFF-demo"), &editions_only).status,
            403
        );
        assert_eq!(
            route_with_policy(
                "GET /health HTTP/1.1\r\n",
                &manifest,
                Some(b"RIFF-demo"),
                &editions_only
            )
            .status,
            200
        );
    }
    #[test]
    fn serves_only_published_route() {
        let manifest: PublicationManifest =
            serde_json::from_str(include_str!("../../contracts/publication-manifest-v2.json"))
                .unwrap();
        let audio = b"RIFF-demo";
        let response = route(
            "GET /editions/demo-2026-01-15/audio HTTP/1.1\r\n",
            &manifest,
            audio,
        );
        assert_eq!(response.status, 200);
        assert_eq!(response.body, audio);
        assert_eq!(
            route("GET /editions/other/audio HTTP/1.1\r\n", &manifest, audio).status,
            404
        );
    }
    #[test]
    fn profiles_a_fixed_number_of_stream_routes() {
        let manifest: PublicationManifest =
            serde_json::from_str(include_str!("../../contracts/publication-manifest-v2.json"))
                .unwrap();
        assert_eq!(
            profile_routes(100, &manifest, b"RIFF-demo"),
            ThroughputProfile {
                requests: 100,
                bytes: 900
            }
        );
    }

    #[test]
    fn loads_manifest_and_recording_from_path_style_object_store() {
        let listener = TcpListener::bind(("127.0.0.1", 0)).unwrap();
        let port = listener.local_addr().unwrap().port();
        let manifest = include_bytes!("../../contracts/publication-manifest-v2.json").to_vec();
        let audio =
            include_bytes!("../../radio-api/src/main/resources/fixtures/demo-edition.wav").to_vec();
        let fixture = thread::spawn(move || {
            for expected in [
                "/radio/editions/demo.json",
                "/radio/fixtures/demo-edition.wav",
            ] {
                let (mut stream, _) = listener.accept().unwrap();
                let mut request = [0_u8; 2048];
                let size = stream.read(&mut request).unwrap();
                let request = String::from_utf8_lossy(&request[..size]);
                assert!(request.starts_with(&format!("GET {expected} HTTP/1.1")));
                let body = if expected.ends_with(".json") {
                    &manifest
                } else {
                    &audio
                };
                stream
                    .write_all(
                        format!(
                            "HTTP/1.1 200 OK\r\nContent-Length: {}\r\nConnection: close\r\n\r\n",
                            body.len()
                        )
                        .as_bytes(),
                    )
                    .unwrap();
                stream.write_all(body).unwrap();
            }
        });
        let (loaded, bytes) = load_from_object_store(
            &format!("http://127.0.0.1:{port}"),
            "radio",
            "editions/demo.json",
        )
        .unwrap();
        fixture.join().unwrap();
        assert_eq!(loaded.edition_id, "demo-2026-01-15");
        assert_eq!(bytes.as_ref().unwrap().len() as u64, loaded.size_bytes);
    }

    #[test]
    fn missing_object_store_recording_becomes_not_found() {
        let listener = TcpListener::bind(("127.0.0.1", 0)).unwrap();
        let port = listener.local_addr().unwrap().port();
        let manifest = include_bytes!("../../contracts/publication-manifest-v2.json").to_vec();
        let fixture = thread::spawn(move || {
            for (expected, status, body) in [
                ("/radio/editions/demo.json", "200 OK", manifest.as_slice()),
                ("/radio/fixtures/demo-edition.wav", "404 Not Found", &[][..]),
            ] {
                let (mut stream, _) = listener.accept().unwrap();
                let mut request = [0_u8; 2048];
                let size = stream.read(&mut request).unwrap();
                assert!(String::from_utf8_lossy(&request[..size])
                    .starts_with(&format!("GET {expected} HTTP/1.1")));
                stream
                    .write_all(
                        format!(
                            "HTTP/1.1 {status}\r\nContent-Length: {}\r\nConnection: close\r\n\r\n",
                            body.len()
                        )
                        .as_bytes(),
                    )
                    .unwrap();
                stream.write_all(body).unwrap();
            }
        });
        let (loaded, audio) = load_from_object_store(
            &format!("http://127.0.0.1:{port}"),
            "radio",
            "editions/demo.json",
        )
        .unwrap();
        fixture.join().unwrap();
        assert!(audio.is_none());
        assert_eq!(
            route_for_object(
                "GET /editions/demo-2026-01-15/audio HTTP/1.1\r\n",
                &loaded,
                None
            )
            .status,
            404
        );
    }
}
