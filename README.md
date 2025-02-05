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

### 4.0.28
 * Update library versions.
 * Add wildfly module deployer
 * Remove all dependencies on other bedework projects - mostly by duplicating code.
 * Make SplitName comparable and rename fields.

### 4.1.0
 * Update library versions.

### 5.0.0
 * Update library versions.
 * Add more classifiers
 * Build output in target/ then copy to final destination
 * Add parameters so we can run the build to produce a set of modules appropriate for the wildfly galleon framework build.
 * FUrther support for feature packs

### 5.0.1
 * iml changes only

### 5.0.2
 * Update library versions.
 * Allow specification of optional module dependencies

### 5.0.3
 * Update library versions.
 * Support classifier
 * Remove unused properties, parameters and fields

### 5.0.4
 * Still had SNAPSHOT dependency when released. Thought that was invalid...

### 5.0.5
 * Revert a change which broke deployment.

### 5.0.6
 * Update parent - avoid some maven dependencies

### 5.1.0
 * Update parent

### 5.1.1
 * Deployment no longer requires a property file. All defaults are for a standalone wildfly. Should be possible to override the defaults.
 * Update parent
 * Last pre-jakarta release


