# One permanent signing identity

Both APK workflows and local APK packaging now require the same existing keystore. There is no key generation or automatic fallback to a runner-specific debug key. The certificate is checked before packaging; a mismatch fails the build.

In GitHub repository Settings > Secrets and variables > Actions, configure:
- JARVIS_RELEASE_KEYSTORE_BASE64: single-line Base64 of the ORIGINAL .jks/.keystore
- JARVIS_RELEASE_STORE_PASSWORD
- JARVIS_RELEASE_KEY_ALIAS
- JARVIS_RELEASE_KEY_PASSWORD
- JARVIS_RELEASE_CERT_SHA256: SHA-256 of that certificate

Keep an offline backup of the original key. Do not commit the keystore or passwords. For local builds set the existing JARVIS_RELEASE_STORE_FILE and the remaining four values as environment variables or private Gradle user properties.

The previous workflows generated fresh debug keys on ephemeral runners and did not retain them. A signing private key cannot be recovered from an APK or its public certificate. Updating an already-installed copy without uninstalling requires its actual original signing key. If that key is lost, this cannot retroactively fix compatibility with that copy; choose a single permanent publisher key going forward, with a one-time reinstall only if necessary.

Native compile/resource/unit-test tasks run before signing setup. No installable APK will be published until the original signing inputs are supplied. Application ID stays com.jarvis.assistant; version is 2.0.0, build 146.
