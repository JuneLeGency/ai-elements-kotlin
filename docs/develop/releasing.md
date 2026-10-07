# Releasing

Releases are cut from `main` by pushing a tag. The `Release` workflow
(`.github/workflows/release.yml`) then:

1. validates a stable `vX.Y.Z` tag, an exact final CHANGELOG heading, matching `VERSION_NAME`,
   matching README/installation BOM coordinates without an unreleased notice,
   and that the commit belongs to `main`;
2. runs the reusable CI workflow on that commit: API checks, unit tests, lint, release/R8,
   isolated Maven consumers, the complete documentation site, and emulator E2E with the reference server;
3. publishes every artifact, the BOM included, to Maven Central, signed
   (`publishAndReleaseToMavenCentral`, [vanniktech/gradle-maven-publish-plugin](https://github.com/vanniktech/gradle-maven-publish-plugin));
4. creates the GitHub release with that CHANGELOG section as notes and the demo APK attached.

## Once per repository

- Verify the `io.github.junelegency` namespace on the [Central Portal](https://central.sonatype.com)
  and create a user token.
- Create a GPG key for signing and publish its public key to a key server.
- Add the repository secrets `MAVEN_CENTRAL_USERNAME`, `MAVEN_CENTRAL_PASSWORD` (the token),
  `SIGNING_KEY` (the ASCII-armoured private key) and `SIGNING_KEY_PASSWORD`.
- For the documentation site: enable GitHub Pages with "GitHub Actions" as the source and set the
  repository variable `PAGES_ENABLED=true`; CI then deploys `site/` on every push to `main`.

## Each release

```bash
# 1. In CHANGELOG.md, rename "## X.Y.Z (unreleased)" to "## X.Y.Z"; set VERSION_NAME=X.Y.Z in gradle.properties.
# 2. Commit, tag and push:
git commit -am "Release X.Y.Z"
git tag vX.Y.Z
git push origin main vX.Y.Z
# 3. Bump VERSION_NAME to the next -SNAPSHOT and open a new "## (unreleased)" section.
```

Stable public APIs are preserved from the first public release, including 0.x. Read the
[compatibility policy](api-compatibility.md) before changing declarations or accepting an API diff.

Before tagging, run `tools/check-published-consumer.sh`, `tools/build-docs.sh`, `./gradlew apiCheck`,
and the affected device/live tests. Update the installation BOM version and snapshot notice,
README, CHANGELOG and roadmap together. Confirm the hosted site (including API symbol pages) is
accessible. Central namespace verification, signing credentials and Pages setup are maintainer
prerequisites; local publication does not verify them.

No remote publication takes place until all required jobs succeed. The demo APK is built before
publishing to Central, so a packaging failure cannot leave a half-finished release.
The demo APK on the release is signed with the CI's debug key: uninstall an earlier one before
installing it.
