# Releasing

Releases are cut from `main` by pushing a tag. The `Release` workflow
(`.github/workflows/release.yml`) then:

1. checks that `CHANGELOG.md` has a `## X.Y.Z` section and that the publishing secrets are set;
2. runs the unit tests and lint;
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

Until 1.0, a minor version may change the public API; every change is listed in the CHANGELOG.
The demo APK on the release is signed with the CI's debug key: uninstall an earlier one before
installing it.
