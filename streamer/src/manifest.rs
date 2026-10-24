use serde::Deserialize;
use std::path::{Component, Path};

#[derive(Debug, Clone, Deserialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
pub struct PublicationManifest {
    pub schema_version: u32,
    pub edition_id: String,
    pub status: String,
    pub audio_path: String,
    pub content_type: String,
    pub size_bytes: u64,
    pub checksum_sha256: String,
    /// Required from schema version 2; absent in version 1 manifests.
    #[serde(default)]
    pub air_date: Option<String>,
}
impl PublicationManifest {
    pub fn validate(&self) -> Result<(), &'static str> {
        if !matches!(self.schema_version, 1 | 2) || self.status != "PUBLISHED" || self.edition_id.is_empty() {
            return Err("unpublished or unsupported manifest");
        }
        let dated = self.air_date.as_deref().is_some_and(|d| {
            d.len() == 10 && d.bytes().enumerate().all(|(i, b)| if i == 4 || i == 7 { b == b'-' } else { b.is_ascii_digit() })
        });
        if (self.schema_version == 2) != dated || (self.schema_version == 1 && self.air_date.is_some()) {
            return Err("air date does not match schema version");
        }
        if self.content_type != "audio/wav" || self.size_bytes == 0 || self.checksum_sha256.len() != 64 {
            return Err("invalid audio metadata");
        }
        let path = Path::new(&self.audio_path);
        if path.is_absolute() || path.components().any(|c| !matches!(c, Component::Normal(_))) {
            return Err("audio path escapes root");
        }
        Ok(())
    }
}
#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn parses_shared_published_fixture() {
        let manifest: PublicationManifest = serde_json::from_str(include_str!("../../contracts/publication-manifest-v2.json")).unwrap();
        manifest.validate().unwrap();
        assert!(manifest.audio_path.ends_with(".wav"));
    }
    #[test]
    fn still_accepts_v1_manifests_without_air_date() {
        let v1 = r#"{"schemaVersion":1,"editionId":"demo-2026-01-15","status":"PUBLISHED","audioPath":"fixtures/demo-edition.wav","contentType":"audio/wav","sizeBytes":16044,"checksumSha256":"f9efab5003000788f6851091398494c71506d3319e03f2abea702aa8214bc8f0"}"#;
        let manifest: PublicationManifest = serde_json::from_str(v1).unwrap();
        manifest.validate().unwrap();
        assert!(manifest.air_date.is_none());
    }
    #[test]
    fn parses_shared_v2_fixture_with_air_date() {
        let manifest: PublicationManifest = serde_json::from_str(include_str!("../../contracts/publication-manifest-v2.json")).unwrap();
        manifest.validate().unwrap();
        assert_eq!(manifest.air_date.as_deref(), Some("2026-01-15"));
    }
    #[test]
    fn air_date_must_match_schema_version() {
        let mut manifest: PublicationManifest = serde_json::from_str(include_str!("../../contracts/publication-manifest-v2.json")).unwrap();
        manifest.air_date = None;
        assert!(manifest.validate().is_err());
        manifest.air_date = Some("15/01/2026".into());
        assert!(manifest.validate().is_err());
        manifest.schema_version = 1;
        manifest.air_date = Some("2026-01-15".into());
        assert!(manifest.validate().is_err());
    }
    #[test]
    fn rejects_unpublished_manifest_and_traversal() {
        let mut manifest: PublicationManifest = serde_json::from_str(include_str!("../../contracts/publication-manifest-v2.json")).unwrap();
        manifest.status = "READY".into();
        assert!(manifest.validate().is_err());
        manifest.status = "PUBLISHED".into();
        manifest.audio_path = "../secret".into();
        assert!(manifest.validate().is_err());
    }
}
