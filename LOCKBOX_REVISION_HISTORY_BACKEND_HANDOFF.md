# Lockbox revision-history backend handoff

The backend now stores one logical `lockbox_files` row per stable client file and
one immutable `lockbox_file_revisions` row per encrypted artifact set. Development
H2 is intentionally recreated at startup because the old single-row schema cannot
be safely transformed by Hibernate `update`; back up any development data first.

## API

- `POST /api/lockbox/files` creates logical file revision 1 and requires a zero
  previous-manifest hash.
- `PUT /api/lockbox/files/{fileId}/revisions?expectedRevision=N` uploads revision
  `N + 1`. It returns 409 `LOCKBOX_REVISION_CONFLICT` for stale state.
- `GET /api/lockbox/files/{fileId}/revisions` lists immutable revisions newest first.
- `GET /api/lockbox/files/{fileId}/revisions/{revision}/{container|manifest|signature}`
  downloads an owner-authorized historical artifact.
- Existing artifact and private-metadata endpoints resolve only the current revision.

The next-revision manifest must contain the same `clientFileId`, the next contiguous
revision number, and `SHA3-512` of the exact previous stored manifest bytes. Uploads
use request-unique storage keys and transaction rollback cleanup.

Revision uploads also require an enabled Lockbox profile before multipart staging.
Logical deletion flushes its database tombstone first and deletes every revision
artifact only after commit. Cleanup attempts continue after individual object-store
failures; failures are logged for manual retry. A durable cleanup retry queue remains
intentionally deferred because this project currently has no object-cleanup job.

Current and historical downloads share the same 1 MiB buffered streaming response
path and reject deleted or permanently deleted logical files.

Create-share requests accept an optional numeric `revision`; omission resolves the
logical file's current revision for compatibility. Shares and duplicate constraints
are revision/device-specific, so older recipients neither move to nor discover a
new private revision. Share responses now include `revision`.
