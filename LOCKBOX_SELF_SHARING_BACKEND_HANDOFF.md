# Lockbox self-sharing backend handoff

## Own-device discovery

`GET /api/lockbox/devices?excludeDeviceId={deviceUuid}` requires authentication
and returns `Cache-Control: no-store`. Only the current user's active devices and
their active `ENCRYPTION` / `ML_KEM_1024` keys are returned. The optional
`excludeDeviceId` should be the device preparing and signing the share.

```json
{
  "devices": [{
    "deviceId": "7f18a604-b38b-4c01-b93f-0c85cf102103",
    "deviceName": "Laptop",
    "deviceStatus": "ACTIVE",
    "encryptionKeys": [{
      "keyId": "base64-32-byte-key-id",
      "algorithm": "ML_KEM_1024",
      "publicKey": "base64-public-key"
    }]
  }]
}
```

## Creating a self-share

Use the existing `POST /api/lockbox/shares` request and the unchanged 1,858-byte
`FDSHENV1` envelope/signature domains. Set both envelope owner and recipient
public UUIDs to the authenticated user's public UUID, and encrypt to a key from a
different active device. A target matching the signing-key device is rejected
with HTTP 400 and `LOCKBOX_SELF_SHARE_SAME_DEVICE`. Duplicate file/target-device
shares return HTTP 409 and `LOCKBOX_SHARE_ALREADY_EXISTS`.

Shares are append-only per file and target device: revocation does not permit a
replacement row and the backend does not silently reactivate revoked shares.

## Receiving on a device

These existing routes now require `deviceId`:

- `GET /api/lockbox/shares/received?deviceId={deviceUuid}`
- `GET /api/lockbox/shares/received/{shareUuid}?deviceId={deviceUuid}`
- `GET /api/lockbox/shares/received/{shareUuid}/container?deviceId={deviceUuid}`

The selected device must be active and owned by the authenticated user. A share
is visible only when its persisted target device, recipient envelope key device,
and envelope recipient key ID all agree. Wrong-device, revoked-device,
revoked-key, expired, revoked-share, and deleted-file cases use the existing
unavailable/not-found policy without leaking cross-device metadata.

## Deployment

Apply `src/main/resources/db/manual-migrations/V20260826__lockbox_share_target_device_h2.sql`
to existing H2 databases while the application is stopped. It backfills the
target from each stored recipient envelope key before enforcing the foreign key,
indexes, and `(lockbox_file_id, target_device_id)` uniqueness constraint.
