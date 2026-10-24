# Recording retention

Rendered recordings are kept according to their role. Retention is applied to object keys, not to editions, because a replaced recording and its replacement are separate objects.

| Class | Object keys | Minimum retention | Notes |
| --- | --- | --- | --- |
| Published | the `audioPath` of a current manifest | while published, then 365 days | the checksum in the manifest identifies the exact bytes |
| Replaced | objects superseded by a recording replacement | 30 days | kept so a faulty replacement can be rolled back |
| Staging | staged but never published objects | 7 days | includes failed transfer attempts |
| Incident hold | any object named in an open incident note | until the hold is released | overrides the classes above |

A retention job must never delete the object named by a current manifest. It deletes replaced and staging objects only after their minimum age, and it logs each deletion with the object key, checksum and policy class.

The machine-readable policy lives in `examples/operations/retention-policy.json`; deployments copy it into their object-store lifecycle configuration. Validate it with `python3 tools/check-docs.py` before applying it.

Synthetic fixture recordings under `fixtures/` are part of the test corpus and are not subject to retention.
