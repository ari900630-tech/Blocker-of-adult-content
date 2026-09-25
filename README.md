# Blocker of Adult Content

Android app that provides an on-device VPN-based protection layer for adult-content blocking.

## Build
Use the included GitHub Actions workflow. The generated APK is uploaded as a workflow artifact.

## Important
Android VPN traffic must be forwarded correctly to avoid breaking connectivity. This repository therefore starts with a safe VPN baseline rather than pretending that a non-forwarding tunnel is a complete blocker. A production implementation should use a tested packet-forwarding/DNS engine and a maintained blocklist.

The app does not silently install or force-download an APK to a phone. Android and GitHub do not permit a repository workflow to silently install an APK on a user's device. After a successful build, download the APK artifact and install it with the user's approval.
