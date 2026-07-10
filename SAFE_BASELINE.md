# Vita Link Updates Safe Baseline

Current safe version: `1.0.135`

This repository is the staff update source of truth. The `safe-1.0.135` tag marks the known-good update-distribution state that staff can safely update from.

## Safe Baseline References

- Updates tag: `safe-1.0.135`
- Updates commit: `2ddac6fea61e98418c01bfd0eef3484f9f5c2564`
- Source tag: `safe-1.0.135`
- Source commit: `081a8b479b0a4308025effacf2642fb0db3e5174`
- Staff update JAR: `windows/VitaNovaDatabase-1.0.135.jar`
- Staff update SHA-256: `2E5B7F85D2917F5414F06CE6CE7AE3958733F7560FF5B2193EAD41C7E26C58FF`
- Manifest: `latest.json`

## Release Guard

Before replacing this with a newer update:

- Confirm the source repo build was created from the intended commit.
- Confirm the new JAR hash in `latest.json` matches the uploaded JAR.
- Confirm staff launchers see the expected version from the raw GitHub manifest.
- Preserve the `1.0.135` behavior unless a future release intentionally changes it.

