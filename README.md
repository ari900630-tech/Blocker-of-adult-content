# Blocker of Adult Content

Android VPN-based content-filtering project.

## Current implementation

- Android VPN permission flow.
- Foreground VPN service for long-running protection.
- Central blocklist at `blocklist/domains.txt`.
- GitHub Actions builds a debug APK and uploads it as `blocker-apk`.

## Important limitation

The current VPN service establishes the Android TUN interface but does **not yet implement packet forwarding or DNS interception**. Therefore this repository is a development baseline, not a finished 100% content blocker. A production release must add and test a real DNS/packet-forwarding engine before enabling the VPN by default.

The APK cannot silently install itself on an Android phone. Android requires the user to approve the VPN connection and installation.

## Build

GitHub Actions runs:

```
gradle --no-daemon assembleDebug
```

The resulting APK is uploaded as a workflow artifact.
