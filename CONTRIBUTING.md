# Contributing to prosemirror-kotlin

Thank you for considering a contribution to prosemirror-kotlin! Pull requests, issues and comments are welcome. For pull requests, please:

* Add tests for new features and bug fixes
* Follow the existing style
* Separate unrelated changes into multiple pull requests

See the existing issues for things to start contributing.

For bigger changes, please make sure you start a discussion first by creating an issue and explaining the intended change.

Atlassian requires contributors to sign a Contributor License Agreement, known as a CLA. This serves as a record stating that the contributor is entitled to contribute the code/documentation/translation to the project and is willing to have it used in distributions and derivative works (or is willing to transfer ownership).

Prior to accepting your contributions we ask that you please follow the appropriate link below to digitally sign the CLA. The Corporate CLA is for those who are contributing as a member of an organization and the individual CLA is for those contributing as an individual.

* [CLA for corporate contributors](https://opensource.atlassian.com/corporate)
* [CLA for individuals](https://opensource.atlassian.com/individual)

## Releases
To create a release, follow these steps:
1. Update the version in the `version.properties` file.
2. Create a PR with these changes, targeting the `main` branch.
3. Once the PR is merged, create a PR to merge `main` into `release` branch.
4. Once the 2nd PR is merged, the CI/CD pipeline will:
   1. Tag the commit with the new version.
   2. Create a Github release.
   3. Publish Android binaries to Artifactory.
   4. Upload iOS assets to the Github release.
5. Edit the release in the Github UI to add release notes for this release (using `Generate release notes`).
