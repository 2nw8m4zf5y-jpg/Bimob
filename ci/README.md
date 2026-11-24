# CI debug signing key

This keystore is intentionally committed for **debug builds only** so APKs produced by GitHub Actions keep the same Android signing identity and can be installed as updates over earlier CI debug APKs.

- File: `ci/bimob-debug.keystore`
- Store type: JKS
- Alias: `bimob-debug`
- Store password: `android`
- Key password: `android`

This is a public, non-secret development key. Never use it for release, production, Play Store, or customer-distributed builds. Release signing must use a separate protected key that is not stored in this repository.
