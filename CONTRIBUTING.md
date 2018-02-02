# Contributing

Please refer to Cloudsmith's standard guide on [Open-Source Contributing](https://help.cloudsmith.io/docs/contributing).


## Contributor License Agreement

By making any contributions to Cloudsmith Ltd projects you agree to be bound by the terms of the Cloudsmith Ltd [Contributor License Agreement](https://help.cloudsmith.io/docs/contributor-license-agreement).


## Coding Conventions

Please ensure code conforms to the [Maven Code Style And Code Conventions](https://maven.apache.org/developers/conventions/code.html).


## Releasing

Use the Maven versions plugin to bump the version:

```shell
mvn versions:set -DnewVersion=0.2.0
```

Then deploy the Maven Wagon to Cloudsmith (assumes appropriate `CLOUDSMITH_API_KEY` has been set properly):

```shell
mvn deploy
```

Then move the version back to the next snapshot:

```shell
mvn versions:set -DnewVersion=0.2.1-SNAPSHOT
```


## Need Help?

See the section for raising a question in the [Contributing Guide](https://help.cloudsmith.io/docs/contributing).
