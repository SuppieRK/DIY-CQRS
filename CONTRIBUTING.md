# Contributing

When contributing to this repository, please first discuss the change you wish to make via issue, email, or any other
method with the owners of this repository before making a change.

Please note we have a code of conduct — follow it in all your interactions within the project.

## How to build the project locally?

```shell
./gradlew clean spotlessApply build jacocoTestReport pitest
```

## Pull Request Process

- We recommend taking a look
  at [creating a pull request from fork](https://docs.github.com/en/pull-requests/collaborating-with-pull-requests/proposing-changes-to-your-work-with-pull-requests/creating-a-pull-request-from-a-fork)
  official documentation to understand the overall process.
- Please make sure that you update the library version in both `gradle.properties` and `README.md` according to our
  modified [SemVer](http://semver.org/) versioning scheme:
    - Keep the version number defined as `MAJOR.MINOR.PATCH`.
    - Increment `MAJOR` version when you make incompatible API changes (e.g., big change in execution flow, API method /
      class removals, new API, etc.).
    - **Set** `MINOR` version to the concatenated `MAJOR` and `MINOR` versions of the jOOQ dependency, if it has been
      updated (e.g., if jOOQ dependency has version `3.18.18`, `MINOR` version of this artifact must be `318`).
    - Increment `PATCH` version when you make backward compatible adjustments or bug fixes which do not affect API.
- You may merge the Pull Request in once you have the sign-off from maintainers.

## Reference Documentation

- Reach out to our [Wiki](https://github.com/SuppieRK/DIY-CQRS/wiki).
    - You might find some links from the wiki useful to understand concepts we are using.
- Take a look at the extensive [jOOQ user manual](https://www.jooq.org/doc/latest/manual/).

### Third-party libraries

- [jOOQ](https://github.com/jOOQ/jOOQ)
- [java-throwable-utils](https://github.com/SuppieRK/java-throwable-utils)