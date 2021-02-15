/* ********************************************************************
    Appropriate copyright notice
*/
package org.bedework.util.maven.deploy;

import org.apache.maven.artifact.Artifact;

import java.util.List;

/**
 * User: mike Date: 2/15/21 Time: 13:56
 */
public class FileArtifact extends JarDependency {
  private Artifact mavenArtifact;

  public FileArtifact() {}

  public FileArtifact(final String moduleName,
                      final String groupId,
                      final String artifactId,
                      final String version,
                      final String type,
                      final String repository,
                      final List<String> moduleDependencies) {
    super(moduleName, groupId, artifactId, version, type,
          repository, moduleDependencies);
  }

  public static FileArtifact fromMavenArtifact(final Artifact val) {
    final FileArtifact fa = new FileArtifact(null,
                                             val.getGroupId(),
                                             val.getArtifactId(),
                                             val.getVersion(),
                                             val.getType(),
                                             null,
                                             null);
    fa.mavenArtifact = val;

    return fa;
  }

  public Artifact getMavenArtifact() {
    return mavenArtifact;
  }
}
