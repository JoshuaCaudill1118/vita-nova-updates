# macOS Download

The Mac first-install package belongs in this folder as `Vita_Nova_Database_macOS_<version>.zip`.

Build it from a Mac with:

```bash
./tools/build-macos-app.sh
```

The Mac app uses a stable launcher. On first launch, it copies the runnable JAR to `~/Library/Application Support/VitaNova/app`; after that, it checks `latest.json`, verifies the downloaded JAR with SHA-256, and updates that local JAR automatically.
