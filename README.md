# Prosemirror Kotlin
[![Atlassian license](https://img.shields.io/badge/license-Apache%202.0-blue.svg?style=flat-square)](LICENSE) [![PRs Welcome](https://img.shields.io/badge/PRs-welcome-brightgreen.svg?style=flat-square)](CONTRIBUTING.md)

Java/Kotlin implementation of [Prosemirror](https://prosemirror.net/)

## Documentation

- [collab](collab/README.md)
- [history](history/README.md)
- [model](model/README.md)
- [state](state/README.md)
- [test-builder](test-builder/README.md)
- [transform](transform/README.md)

## Contributions

Contributions to prosemirror-kotlin are welcome! Please see [CONTRIBUTING.md](CONTRIBUTING.md) for details.

## Maven / Gradle dependency
Add prosemirror-kotlin dependencies using prosemirror.<moduleIdentifier> in place of the normal artifact specifier.

Check the latest packages at Maven central on: https://packages.atlassian.com/maven-central/com/atlassian/prosemirror/<moduleIdentifier>.

### Maven:
```xml
<dependency>
    <groupId>com.atlassian.prosemirror</groupId>
    <artifactId>{moduleIdentifier}</artifactId>
    <version>{latest}</version>
</dependency>
```

### Gradle:
```kotlin
implementation("com.atlassian.prosemirror:{moduleIdentifier}:{latest}") // this version number should be updated to update the version of prosemirror-kotlin
```

## License

Copyright (c) 2024 Atlassian and others.
Apache 2.0 licensed, see [LICENSE](LICENSE) file.

<br/> 


[![With â¤ï¸ from Atlassian](https://raw.githubusercontent.com/atlassian-internal/oss-assets/master/banner-cheers.png)](https://www.atlassian.com)
