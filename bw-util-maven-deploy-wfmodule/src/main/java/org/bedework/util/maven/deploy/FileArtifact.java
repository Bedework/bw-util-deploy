/* ********************************************************************
    Appropriate copyright notice
*/
package org.bedework.util.maven.deploy;

import org.bedework.util.deployment.SplitName;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Dependency;

import java.util.List;

/**
 * User: mike Date: 2/15/21 Time: 13:56
 */
public class FileArtifact extends JarDependency {
  private Artifact mavenArtifact;
  private Dependency mavenDependency;

  public FileArtifact() {}

  public FileArtifact(final String moduleName,
                      final String groupId,
                      final String artifactId,
                      final String classifier,
                      final String version,
                      final String type,
                      final String repository,
                      final List<ModuleDependency> moduleDependencies) {
    super(moduleName, groupId, artifactId, classifier, version, type,
          repository, null, moduleDependencies);
  }

  public static FileArtifact from(final Dependency val) {
    final FileArtifact fa = new FileArtifact(null,
                                             val.getGroupId(),
                                             val.getArtifactId(),
                                             val.getClassifier(),
                                             val.getVersion(),
                                             val.getType(),
                                             null,
                                             null);
    fa.mavenDependency = val;

    return fa;
  }

  public static FileArtifact from(final Artifact val) {
    final FileArtifact fa = new FileArtifact(null,
                                             val.getGroupId(),
                                             val.getArtifactId(),
                                             null,
                                             val.getVersion(),
                                             val.getType(),
                                             null,
                                             null);
    fa.mavenArtifact = val;

    return fa;
  }

  public static FileArtifact from(final SplitName val) {
    return new FileArtifact(null,
                            null, // groupId
                            val.getArtifactId(),
                            val.getClassifier(),
                            val.getVersion(),
                            val.getType(),
                            null,
                            null);
  }

  public Artifact getMavenArtifact() {
    return mavenArtifact;
  }
}
