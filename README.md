# bw-util-deploy
Deployment related utility classes

## Requirements

1. JDK 11
2. Maven 3

## Building Locally

> mvn clean install

## Releasing

Releases of this fork are published to Maven Central via Sonatype.

To create a release, you must have:

1. Permissions to publish to the `org.bedework` groupId.
2. `gpg` installed with a published key (release artifacts are signed).

To perform a new release:

> mvn -P bedework-dev release:clean release:prepare

When prompted, select the desired version; accept the defaults for scm tag and next development version.
When the build completes, and the changes are committed and pushed successfully, execute:

> mvn -P bedework-dev release:perform

For full details, see [Sonatype's documentation for using Maven to publish releases](http://central.sonatype.org/pages/apache-maven.html).

## Release Notes
### 4.0.22
    * Split off from bw-util. Start at same version.

### 4.0.23
  * Add a dump props method and use it

### 4.0.24
  * Update dependencies
    
### 4.0.25
  * Remove source/target from compiler plugin. Set in profile
  * Update plugin versions

### 4.0.26
  * Update javadoc plugin config
  * Switch to PooledHttpClient
  * Add extra dependencies for bw-util refactor

### 4.0.27
  * Added assembly to create a runnable app. Will be used
    for deploying the quickstart without building.
  * Minor fixes to messages.
  * Fixed checking of deployed version for case when 
    no version deployed.
    
