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
}
impl PublicationManifest {
    pub fn validate(&self) -> Result<(), &'static str> {
        if self.schema_version != 1 || self.status != "PUBLISHED" || self.edition_id.is_empty() {
            return Err("unpublished or unsupported manifest");
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
        let manifest: PublicationManifest = serde_json::from_str(include_str!("../../contracts/publication-manifest-v1.json")).unwrap();
        manifest.validate().unwrap();
        assert!(manifest.audio_path.ends_with(".wav"));
    }
    #[test]
    fn rejects_unpublished_manifest_and_traversal() {
        let mut manifest: PublicationManifest = serde_json::from_str(include_str!("../../contracts/publication-manifest-v1.json")).unwrap();
        manifest.status = "READY".into();
        assert!(manifest.validate().is_err());
        manifest.status = "PUBLISHED".into();
        manifest.audio_path = "../secret".into();
        assert!(manifest.validate().is_err());
    }
}
