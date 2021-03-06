# **DEPRECATED**
The Cloudsmith Maven Wagon is no longer needed for publishing. Please use the native-based publishing instead (see image below), which is better supported across different technologies. It also supports POM-only and sub-module uploads. Cloudsmith will continue to maintain this library for users that are currently using it or for legacy reasons (e.g. old versions of tooling that don't support native uploading). Otherwise, please consider upgrading now.

![Native Maven Publishing](https://user-images.githubusercontent.com/2248287/62480275-4796d700-b7a7-11e9-82d9-c6e893e336c8.png)

# Cloudsmith Maven Wagon

[![Latest Version @ Cloudsmith](https://api-prd.cloudsmith.io/badges/version/cloudsmith/api/maven/cloudsmith-maven-wagon/latest/x/?render=true)](https://cloudsmith.io/~cloudsmith/repos/api/packages/detail/maven/cloudsmith-maven-wagon/latest/)

The Cloudsmith [Maven Wagon](http://maven.apache.org/wagon/) provides an
integration between Cloudsmith and [Maven](https://maven.apache.org/),
[Gradle](https://gradle.org/), [SBT/Scala](https://www.scala-sbt.org/), and
more, for automated native/in-tool uploads of your artefacts. The configuration
for Maven, Gradle and SBT vary, so please refer to the relevant section for
details and examples.

The Cloudsmith Maven Wagon library isn't available on [Maven Central](https://search.maven.org/)
yet (but we're working on it). You can access the latest releases for the
library on the public [Cloudsmith API libraries repository](https://cloudsmith.io/~cloudsmith/repos/api/packages/).


## How Does It Work

The Maven Wagon utilises the [Cloudsmith API](https://api.cloudsmith.io) to push your packages to Cloudsmith.

The flow of this as follows:

1. Maven builds your artefacts (JAR, POM, etc.) using the standard build process.
2. Maven starts the deploy process by delegating to this library/plugin.
3. The library authenticates with the Cloudsmith API via HTTPS to check your credentials.
4. The library asks the Cloudsmith API via HTTPS for existing repository metadata.
5. The library checks to see if your artefacts are already located in the upstream repository.
6. Assuming your artefacts are new, the library performs the following for each artefacts:
  a. It requests upload authorisation from the Cloudsmith API via HTTPS.
  b. It receives the pre-signed upload request details for uploading the file.
  c. It uses these to push the artefact to S3 via HTTPS (the actual file upload).
7. When all artefacts uploaded, the library tells the Cloudsmith API via HTTPS that the uploads are completed.
8. The library uses the status endpoint of the Cloudsmith API to wait for the package to synchronise.
9. The library informs Maven of the success or failure of the deployment.

As noted above HTTPS is used to communicate with the Cloudsmith API and S3 - If you encounter issues with
timeouts, please refer to the HTTP Timeout Configuration below for help.


## Common

### Authentication Configuration

#### Cloudsmith API Key

In order for the library to communicate with Cloudsmith, you'll need to obtain your Cloudsmith API Key.

For convenience you can fetch this using the [Cloudsmith CLI](https://github.com/cloudsmith-io/cloudsmith-cli):

```shell
pip install cloudsmith-cli
cloudsmith token
```

You can also get it via the [User API Tokens on the Cloudsmith Website](https://cloudsmith.io/settings/api-tokens/) (requires sign-in).

*Note:* If you're automating upload via a CI/CD system, we recommend creating a least-privilege bot user for this task.


### Debug Output

If you need help with debugging issues you can enable debug output via (in order of precedence):

1. Set the `CLOUDSMITH_DEBUG` environment variable to `true`.
2. Set the `cloudsmith.debug` property to `true`.


### HTTP Timeouts

The library uses HTTPS to communicate with the Cloudsmith API and S3 to coordinate
package creation and upload of the package artefacts. This is usually problem-free,
but if you have an unstable or slow connection you may encounter timeouts during the
retrieval or upload portions of the deployment workflow. If this happens, you can
configure larger values for the HTTP timeouts, as below.

#### HTTP Timeout Configuration

The following defaults are used:

- HTTP connect timeout: 15 seconds
- HTTP read timeout: 30 seconds
- HTTP write timeout: 120 seconds

Configuring the HTTP connect timeout (in order of precedence):

1. Set the `CLOUDSMITH_HTTP_CONNECT_TIMEOUT` environment variable to a positive integer value (e.g. `30` for 30 seconds).
2. Set the `cloudsmith.http.connect.timeout` property to a positive integer value (e.g. `60` for 60 seconds).

Configuring the HTTP read timeout (in order of precedence):

1. Set the `CLOUDSMITH_HTTP_READ_TIMEOUT` environment variable to a positive integer value (e.g. `60` for 60 seconds).
2. Set the `cloudsmith.http.read.timeout` property to a positive integer value (e.g. `120` for 120 seconds).

Configuring the HTTP write timeout (in order of precedence):

1. Set the `CLOUDSMITH_HTTP_WRITE_TIMEOUT` environment variable to a positive integer value (e.g. `240` for 240 seconds).
2. Set the `cloudsmith.http.write.timeout` property to a positive integer value (e.g. `480` for 480 seconds).


### Synchronisation Wait

Packages that are uploaded to Cloudsmith are "eventually consistent" - By this
we mean that a package that is uploaded isn't instantaneous made available
(published) to users of the target repository. This is due to Cloudsmith using
background workers to process packages after they are uploaded, and after
a small back-off period these will begin to process packages as they are
uploaded.

For this reason, when a package is uploaded, by default the library will
initiate a mechanism to wait on the status of the uploaded package until
synchronisation is complete (or has failed).

The following is an example of what this looks like when package synchronisation is enabled:

```shell
[INFO] Creating a new Maven Package ...
[INFO] Waiting for the package to synchronise ...
[INFO] Status = Sync In Progress , Stage = Preparing for Synch , Progress = 15
[INFO] Status = Sync In Progress , Stage = Verifying Package File(s) , Progress = 35
[INFO] Status = Sync In Progress , Stage = Verifying Package File(s) , Progress = 35
[INFO] Status = Sync In Progress , Stage = Verifying Package File(s) , Progress = 35
[INFO] Status = Sync In Progress , Stage = Verifying Package File(s) , Progress = 35
[INFO] Status = Sync In Progress , Stage = Indexing Package File(s) , Progress = 65
[INFO] Status = Sync In Progress , Stage = Synching Repository , Progress = 85
[INFO] Status = Sync Completed , Stage = Fully Synchronised , Progress = 100
[INFO] Created: cloudsmith/examples/cloudsmith-maven-example-001-snapshotjar-2
```

#### Synchronisation Wait Configuration

By default synchronisation is enabled. If you'd prefer to not wait for package
synchronisation, with the caveat that you'll not be able to tell if the
synchronisation process succeeds or not, you can disable it in on the following
ways (in order of precedence):

1. Set the `CLOUDSMITH_SYNC_WAIT_ENABLED` environment variable to `false`.
2. Set the `cloudsmith.sync_wait.enabled` property to `false`.

If synchronisation wait is disabled, then the above example output will instead be something like:

```shell
[INFO] Creating a new Maven package ...
[INFO] Not waiting on the package to synchronise (note: failures won't be reported here)
[INFO] Created: cloudsmith/examples/cloudsmith-maven-example-001-snapshotjar-3
```

#### Synchronisation Wait Verbosity Configuration

By default synchronisation is verbose. If you'd prefer to turn off details of
synchronisation, but still want to wait on the synchronisation to complete or
fail (along with a status message if it does), then you turn off verbosity in
the following ways (in order of precedence):

1. Set the `CLOUDSMITH_SYNC_WAIT_VERBOSE` environment variable to `false`.
2. Set the `cloudsmith.sync_wait.verbose` property to `false`.

#### Synchronisation Wait Interval Configuration

By default the synchronisation waits for an interval of 5000ms (5 seconds)
between updates. If you'd like to shorten or length this interval, then you can
set it in the following ways (in order of precedence):

1. Set the `CLOUDSMITH_SYNC_WAIT_INTERVAL` environment variable to a positive integer value (e.g. `20` for 20 seconds).
1. Set the `cloudsmith.sync_wait.interval` property to an positive integer value (e.g. `10` to 10 seconds).

*Note:* If you set it to zero or a negative number, then the synchronisation wait process will be disabled.


## Maven

### Deployment Configuration

#### Library Repository

The Cloudsmith Maven Wagon library isn't available on [Maven Central](https://search.maven.org/) yet (but we're working on it).

Until it is, you'll need to add the following configuration to your project `pom.xml`:

```xml
  <pluginRepositories>
    <pluginRepository>
      <id>cloudsmith-api</id>
      <name>Cloudsmith API Releases</name>
      <url>https://dl.cloudsmith.io/public/cloudsmith/api/maven</url>
    </pluginRepository>
  </pluginRepositories>

  <repositories>
    <repository>
      <id>cloudsmith-api</id>
      <name>Cloudsmith API Releases</name>
      <url>https://dl.cloudsmith.io/public/cloudsmith/api/maven</url>
    </repository>
  </repositories>
```

This will allow Maven to fetch it as a build/deploy dependency from Cloudsmith.

#### Library Dependency

To bring the library into your Maven project, add the following to your project `pom.xml` file within `<build>` and `<extensions>`:

```xml
  <build>
    <extensions>
      <extension>
        <groupId>io.cloudsmith.maven.wagon</groupId>
        <artifactId>cloudsmith-maven-wagon</artifactId>
        <version>0.4.0</version>
      </extension>
    </extensions>
  </build>
```

*Note:* Please refer to the [Cloudsmith API libraries repository](https://cloudsmith.io/~cloudsmith/repos/api/packages/) to replace *0.4.0* with the latest library version.

#### Upload Repositories

The upload repositories specify which Cloudsmith repository you'd like to upload your artefacts to.

To configure the upload repositories for your project, add the following to your project `pom.xml` file within `<distributionManagement>`:

```xml
  <distributionManagement>
    <snapshotRepository>
      <id>cloudsmith-snapshots</id>
      <url>cloudsmith+https://api.cloudsmith.io/your-namespace/your-snapshots-repo</url>
    </snapshotRepository>
    <repository>
      <id>cloudsmith-releases</id>
      <url>cloudsmith+https://api.cloudsmith.io/your-namespace/your-releases-repo</url>
    </repository>
  </distributionManagement>
```

Replacing the following terms with your own configuration:

- `your-namespace`: Replace with your user or organization slug.
- `your-snapshots-repo`: Replace with your snapshots (edge releases) repository slug.
- `your-releases-repo`: Replace with your releases (non-edge releases) repository slug.

*Note 1:* The repositories *must* exist prior to deployment - Create them first!

*Note 2:* You can configure the snapshots and releases repositories to be the same, they do not need to be different.

*Note 3:* You can replace `cloudsmith-snapshots` and `cloudsmith-releases` with your own identifiers.

### Authentication Configuration

#### Cloudsmith API Key

Please see the common setup above to obtain your API Key for Cloudsmith.

#### Precedence

You can configure the library with your API Key in one of following ways (in order of precedence):

1. System Property
2. Environment Variable
3. User-specific Settings

#### System Property

You can set the `cloudsmith.api_key` system property with your API Key:

```xml
<properties>
  <cloudsmith.api_key>your-API-key</cloudsmith.api_key>
</properties>
```

Replacing the following terms with your own configuration:

- `your-API-key`: Your Cloudsmith user API key (see above for how to retrieve it).

**Note**: This applies to *all* repositories. If you need more granularity, use the user settings file approach below.

#### Environment Variable

You can export your API Key using the `CLOUDSMITH_API_KEY` environment variable, such as (Linux example):

```shell
export CLOUDSMITH_API_KEY=your-API-key

```

Replacing the following terms with your own configuration:

- `your-API-key`: Your Cloudsmith user API key (see above for how to retrieve it).

**Note**: This applies to *all* repositories. If you need more granularity, use the user settings file approach below.

#### User-specific Settings

You can configure your `$HOME/.m2/settings.xml` file with your Cloudsmith API Key:

```xml
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"
  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
  xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0
                      https://maven.apache.org/xsd/settings-1.0.0.xsd">
  <servers>
    <server>
      <id>cloudsmith-snapshots</id>
      <password>your-API-key</password>
    </server>
    <server>
      <id>cloudsmith-releases</id>
      <password>your-API-key</password>
    </server>
  </servers>
</settings>
```

Replacing the following terms with your own configuration:

- `your-API-key`: Your Cloudsmith user API key (see above for how to retrieve it).

*Note 1:* Rather than putting your API keys as plaintext, you can encrypt them using [Maven Password Encryption](https://maven.apache.org/guides/mini/guide-encryption.html).

*Note 2:* The `<id>` for each server needs to match those in your `pom.xml` file under `<distributionManagement>`.

*Note 3:* You can replace `cloudsmith-snapshots` and `cloudsmith-releases` with your own identifiers.

### Usage

Assuming you have authentication and configuration setup, as above, you'll be able to publish to Cloudsmith via:

```shell
mvn deploy
```

### Example Project

We have a fully-worked [example project for Maven](https://github.com/cloudsmith-io/cloudsmith-examples/tree/master/projects/maven/src) that you can use as a reference.

The output of this is uploaded to the publicly available [Cloudsmith examples repository](https://cloudsmith.io/~cloudsmith/repos/examples/packages/) as part of our testing processes.


## Gradle

### Note

The configuration for Gradle use the "old-style" Maven plugin documented in the [Gradle Maven Plugin documentation](https://docs.gradle.org/current/userguide/maven_plugin.html).

### Deployment Configuration

#### Library Repository

The Cloudsmith Maven Wagon library isn't available on [Maven Central](https://search.maven.org/) yet (but we're working on it).

Until it is, you'll need to add the following configuration to your project `build.gradle` file within `repositories`:

```groovy
repositories {
  maven {
    name = "Cloudsmith API Releases"
    url = "https://dl.cloudsmith.io/public/cloudsmith/api/maven"
  }

  // You might have references to mavenLocal() and mavenCentral() here too
}
```

This will allow Gradle to fetch it as a build/deploy dependency from Cloudsmith.

#### Library Dependency

To bring the library into your Gradle project, add the following to your project `build.gradle` file:

```groovy
apply plugin: 'maven'

configurations {
  deployerJars
}

dependencies {
  deployerJars 'io.cloudsmith.maven.wagon:cloudsmith-maven-wagon:0.4.0'
}
```

*Note:* Please refer to the [Cloudsmith API libraries repository](https://cloudsmith.io/~cloudsmith/repos/api/packages/) to replace *0.4.0* with the latest library version.


#### Upload Repositories

The upload repositories specify which Cloudsmith repository you'd like to upload your artefacts to.

To configure the upload repositories for your project, add the following to your project `build.gradle` file:

```groovy
uploadArchives {
   repositories {
     mavenDeployer {
       configuration = configurations.deployerJars

       repository(url: "cloudsmith+https://api.cloudsmith.io/your-namespace/your-releases-repo") {
         authentication(password: "$cloudsmithApiKey")
       }

       snapshotRepository(url: "cloudsmith+https://api.cloudsmith.io/your-namespace/your-snapshots-repo") {
         authentication(password: "$cloudsmithApiKey")
       }
   }
}
```

Replacing the following terms with your own configuration:

- `your-namespace`: Replace with your user or organization slug.
- `your-snapshots-repo`: Replace with your snapshots (edge releases) repository slug.
- `your-releases-repo`: Replace with your releases (non-edge releases) repository slug.

*Note 1:* The repositories *must* exist prior to deployment - Create them first!

*Note 2:* You can configure the snapshots and releases repositories to be the same, they do not need to be different.

*Note 3:* You need to define `$cloudsmithApiKey` as a gradle property. See below.

### Authentication Configuration

#### Cloudsmith API Key

Please see the common setup above to obtain your API Key for Cloudsmith.

#### Precedence

You can configure the library with your API Key in one of the following ways (in order of precedence):

1. Environment Variable
2. Gradle Property

#### Environment Variable

You can export your API Key using the `CLOUDSMITH_API_KEY` environment variable, such as (Linux example):

```
export CLOUDSMITH_API_KEY=your-API-key

```

Replacing the following terms with your own configuration:

- `your-API-key`: Your Cloudsmith user API key (see above for how to retrieve it).

**Note**: This applies to *all* repositories. If you need more granularity, use the user Gradle Property file approach below.

#### Gradle Property

You can configure your `gradle.properties` file with your Cloudsmith API Key:

```shell
cloudsmithApiKey=your-API-key
```

Replacing the following terms with your own configuration:

- `your-API-key`: Your Cloudsmith user API key (see above for how to retrieve it).

### Usage

Assuming you have authentication and configuration setup, as above, you'll be able to publish to Cloudsmith via:

```shell
gradle uploadArchives
```

### Example Project

We have a fully-worked [example project for Gradle](https://github.com/cloudsmith-io/cloudsmith-examples/tree/master/projects/gradle/src) that you can use as a reference.

The output of this is uploaded to the publicly available [Cloudsmith examples repository](https://cloudsmith.io/package/ns/cloudsmith/repos/examples/packages/) as part of our testing processes.


## Scala/SBT

### Deployment Configuration

#### Library Dependency

Cloudsmith uses [sbt-aether-deploy](https://github.com/arktekk/sbt-aether-deploy) to wrap/use this library for publishing within SBT.

To bring the library into your SBT project, add the following to your project `project/plugins.sbt` file:

```
addSbtPlugin("no.arktekk.sbt" % "aether-deploy" % "0.20.0")
libraryDependencies += "io.cloudsmith.maven.wagon" % "cloudsmith-maven-wagon" % "0.4.0"
resolvers += Resolver.mavenLocal
```

*Note1 :* They have removed the WagonWrapper functionality (we think temporarily) from 0.21.0 of aether-deploy so use 0.20.0 - it is supported and available from maven central and our cloudsmith/api public repository.

*Note 2:* Please refer to the [Cloudsmith API libraries repository](https://cloudsmith.io/~cloudsmith/repos/api/packages/) to replace *0.4.0* with the latest library version.


#### Upload Repositories

The upload repositories specify which Cloudsmith repository you'd like to upload your artefacts to.

To configure the upload repositories for your project, add the following to your project `build.sbt` file:

```
credentials += Credentials(Path.userHome / ".sbt" / ".credentials")
aetherWagons := Seq(aether.WagonWrapper("cloudsmith+https", "io.cloudsmith.maven.wagon.CloudsmithWagon"))
publishTo := {
    Some("cloudsmith+https" at "cloudsmith+https://api.cloudsmith.io/your-namespace/your-repo")
}
```
Replacing the following terms with your own configuration:
- `your-namespace`: Replace with your user or organization slug.
- `your-repo`: Replace with your repository slug.

*Note:* The repositories must exist prior to deployment - Create them first!

### Authentication Configuration

#### Cloudsmith API Key

Please see the common setup above to obtain your API Key for Cloudsmith.

#### Sbt Property

Configure your `.credentials` file with the following details:

```
realm = cloudsmith
host = api.cloudsmith.io
user =
password = your-API-key
```

Replacing the following terms with your own configuration:

- `your-API-key`: Your Cloudsmith user API key (see above for how to retrieve it).

### Usage
Assuming you have authentication and configuration setup, as above, you'll be able to publish to Cloudsmith via:

```
sbt aetherDeploy
```

### Example Project

We have a fully-worked [example project for SBT](https://github.com/cloudsmith-io/cloudsmith-examples/tree/master/projects/sbt/src) that you can use as a reference.

The output of this is uploaded to the publicly available [Cloudsmith examples repository](https://cloudsmith.io/~cloudsmith/repos/examples/packages/) as part of our testing processes.


## Leiningen

Details coming soon.


## Kotlin/Kobalt

Details coming soon.


## License

Copyright 2018 Cloudsmith Ltd

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.

     http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.


## EOF

This quality product was brought to you by [Cloudsmith](https://cloudsmith.io) and the [fine folks who have contributed](https://github.com/cloudsmith-io/cloudsmith-maven-wagon/blob/master/CONTRIBUTORS.md).
