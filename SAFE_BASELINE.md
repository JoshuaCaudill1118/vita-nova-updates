# Vita Link Updates Safe Baseline

Current safe version: `1.0.169`

This repository is the staff update source of truth. The `safe-1.0.169` tag marks the known-good update-distribution state that staff can safely update from.

## Safe Baseline References

- Updates tag: `safe-1.0.169`
- Updates commit: `1f97c929208a8751d896bbd728733564f7a278b7`
- Source tag: `safe-1.0.169`
- Source commit: `13ccc0dd5caf5519e0645cc600a3ea68c4f40c64`
- Staff update JAR: `windows/VitaNovaDatabase-1.0.169.jar`
- Staff update SHA-256: `A018455BC3E3A06E091779197A519F79B2F2966D800AF89EE0BC58AC45A9C073`
- Manifest: `latest.json`

## Release Guard

Before replacing this with a newer update:

- Confirm the source repo build was created from the intended commit.
- Confirm the new JAR hash in `latest.json` matches the uploaded JAR.
- Confirm staff launchers see the expected version from the raw GitHub manifest.
- Preserve the `1.0.169` behavior unless a future release intentionally changes it.
