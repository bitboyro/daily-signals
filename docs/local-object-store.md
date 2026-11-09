# Local object protocol fixture

`LocalS3ObjectAdapter` exercises path-style `PUT /bucket/key` and `GET /bucket/key` against an unauthenticated local HTTP fixture. It models the object-key and transfer boundary used by S3-compatible services. It does **not** implement AWS Signature V4, bucket creation, retries, or hosted S3 authentication. The CI check proves only this local protocol subset; a real MinIO or hosted endpoint needs a separate authenticated integration check before deployment.

The runtime contract is configured with `OBJECT_STORE_ENDPOINT`, `OBJECT_STORE_BUCKET`,
`OBJECT_STORE_MANIFEST_KEY`, `OBJECT_STORE_ACCESS_KEY` and `OBJECT_STORE_SECRET_KEY`.
Access and secret keys must be provided together. The local compose fixture accepts the
configured path-style reads without authentication; production credentials are injected
as secrets and require the authenticated adapter planned for the production deployment.
