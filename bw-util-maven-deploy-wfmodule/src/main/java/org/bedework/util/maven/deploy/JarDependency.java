/* ********************************************************************
    Appropriate copyright notice
*/
package org.bedework.util.maven.deploy;

import org.apache.maven.plugins.annotations.Parameter;

import java.util.List;

/**
 * User: mike Date: 2/12/21 Time: 23:34
 */
public class JarDependency extends FileInfo {
  // For maven
  public JarDependency() {}

  JarDependency(final String moduleName,
                final String groupId,
                final String artifactId,
                final String version,
                final String type,
                final String repository,
                final List<String> moduleDependencies) {
    super(groupId, artifactId, version, type, repository);
    this.moduleName = moduleName;
    this.moduleDependencies = moduleDependencies;
  }

  static JarDependency fromFileInfo(final String moduleName,
                                    final FileInfo fileInfo,
                                    final String repository,
                                    final List<String> moduleDependencies) {
    return new JarDependency(moduleName,
                             fileInfo.getGroupId(),
                             fileInfo.getArtifactId(),
                             fileInfo.getVersion(),
                             fileInfo.getType(),
                             repository,
                             moduleDependencies);
  }

  @Parameter
  private String moduleName;

  @Parameter
  private List<String> moduleDependencies;

  /**
   * Name of the module - e.g com.fasterxml.jackson.core.annotations
   */
  String getModuleName() {
    return moduleName;
  }

  List<String> getModuleDependencies() {
    return moduleDependencies;
  }

  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder(
            this.getClass().getSimpleName());

    sb.append("{");
    sb.append("moduleName=").append(getModuleName());
    sb.append(", ");
    super.toStringSegment(sb);
    sb.append("}");

    return sb.toString();
  }
}
