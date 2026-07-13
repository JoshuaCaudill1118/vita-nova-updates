# Vita Link Updates Safe Baseline

Current safe version: `1.0.146`

This repository is the staff update source of truth. The `safe-1.0.146` tag marks the known-good update-distribution state that staff can safely update from.

## Safe Baseline References

- Updates tag: `safe-1.0.146`
- Updates commit: `b1b16729a5d95a91a5ed35133a8c1b7fb50fa6a6`
- Source tag: `safe-1.0.146`
- Source commit: `b8762c40f14a8625eab66abfdce64607c2b7dda0`
- Staff update JAR: `windows/VitaNovaDatabase-1.0.146.jar`
- Staff update SHA-256: `C265C40DC721096879AF13FA8EB1345C8A5E07C278EED3EA95674FC4F5A3406B`
- Manifest: `latest.json`

## Release Guard

Before replacing this with a newer update:

- Confirm the source repo build was created from the intended commit.
- Confirm the new JAR hash in `latest.json` matches the uploaded JAR.
- Confirm staff launchers see the expected version from the raw GitHub manifest.
- Preserve the `1.0.146` behavior unless a future release intentionally changes it.
