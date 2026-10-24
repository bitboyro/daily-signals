# Local object protocol fixture

`LocalS3ObjectAdapter` exercises path-style `PUT /bucket/key` and `GET /bucket/key` against an unauthenticated local HTTP fixture. It models the object-key and transfer boundary used by S3-compatible services. It does **not** implement AWS Signature V4, bucket creation, retries, or hosted S3 authentication. The CI check proves only this local protocol subset; a real MinIO or hosted endpoint needs a separate authenticated integration check before deployment.
