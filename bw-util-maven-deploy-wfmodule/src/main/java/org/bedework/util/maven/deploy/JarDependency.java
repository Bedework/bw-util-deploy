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
  @Parameter(defaultValue = "true")
  boolean export= true;

  @Parameter
  private String moduleName;

  @Parameter
  private List<JarDependency> jarDependencies;

  @Parameter
  private List<ModuleDependency> moduleDependencies;

  // For maven
  public JarDependency() {}

  JarDependency(final String moduleName,
                final String groupId,
                final String artifactId,
                final String version,
                final String type,
                final String repository,
                final List<JarDependency> jarDependencies,
                final List<ModuleDependency> moduleDependencies) {
    super(groupId, artifactId, version, type, repository);
    this.moduleName = moduleName;
    this.jarDependencies = jarDependencies;
    this.moduleDependencies = moduleDependencies;
  }

  static JarDependency fromFileInfo(final String moduleName,
                                    final FileInfo fileInfo,
                                    final String repository,
                                    final List<JarDependency> jarDependencies,
                                    final List<ModuleDependency> moduleDependencies) {
    return new JarDependency(moduleName,
                             fileInfo.getGroupId(),
                             fileInfo.getArtifactId(),
                             fileInfo.getVersion(),
                             fileInfo.getType(),
                             repository,
                             jarDependencies,
                             moduleDependencies);
  }

  static JarDependency forNoArtifact(final String moduleName,
                                     final List<JarDependency> jarDependencies,
                                     final List<ModuleDependency> moduleDependencies) {
    return new JarDependency(moduleName,
                             null,
                             null,
                             null,
                             null,
                             null,
                             jarDependencies,
                             moduleDependencies);
  }

  /**
   * Name of the module - e.g com.fasterxml.jackson.core.annotations
   */
  String getModuleName() {
    return moduleName;
  }

  public boolean isExport() {
    return export;
  }

  public List<JarDependency> getJarDependencies() {
    return jarDependencies;
  }

  List<ModuleDependency> getModuleDependencies() {
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
