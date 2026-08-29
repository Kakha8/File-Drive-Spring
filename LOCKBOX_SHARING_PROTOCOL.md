# Lockbox sharing protocol

## Recipient envelope V1 authorization

The create-share API accepts the exact 1,858-byte `FDSHENV1` package defined by
the Lockbox Recipient Share Envelope V1 client protocol. The authenticated owner
authorizes that complete package with pure ML-DSA-87.

The exact signed message is:

```text
ASCII("FD-CSE-V3-SHARE-GRANT-V1\0") || envelopePackage
```

The ASCII domain is exactly 25 bytes, including its final zero byte. The package
bytes are the exact bytes decoded from the request's standard padded Base64;
they are not parsed and reconstructed, hashed, or otherwise transformed before
verification. The separately supplied 32-byte signing-key ID must identify an
active ML-DSA-87 signing key on an active device belonging to the authenticated
owner. An unsigned or invalidly signed package never creates an active share.
