/* ********************************************************************
    Appropriate copyright notice
*/
package org.bedework.util.maven.deploy;

import org.apache.maven.artifact.versioning.ComparableVersion;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.bedework.util.deployment.Utils.compareStrings;

/**
 * User: mike Date: 2/12/21 Time: 23:33
 */
public class FileInfo implements Comparable<FileInfo> {
  @Parameter
  private String groupId;

  @Parameter
  private String artifactId;

  @Parameter
  private String version;

  @Parameter(defaultValue = "jar")
  private String type;

  private String repository;

  public FileInfo() {}

  FileInfo(final String groupId,
           final String artifactId,
           final String version,
           final String type,
           final String repository) {
    this.groupId = groupId;
    this.artifactId = artifactId;
    this.version = version;
    this.type = type;
    this.repository = repository;
  }

  String getGroupId() {
    return groupId;
  }

  String getArtifactId() {
    return artifactId;
  }

  String getVersion() {
    return version;
  }

  public void setVersion(final String val) {
    version = val;
  }

  String getType() {
    return type;
  }

  public String getRepository() {
    return repository;
  }

  public void setRepository(final String val) {
    repository = val;
  }

  /** .
   *
   * @param that FileInfo to test
   * @return true if artifactId and type match.
   */
  public boolean sameAs(final FileInfo that) {
    return getGroupId().equals(that.getGroupId()) &&
            getArtifactId().equals(that.getArtifactId()) &&
            getType().equals(that.getType());
  }

  /** artifactId and type must match.
   *
   * @param that FileInfo to test
   * @return true if this version also is greater than that version.
   */
  public boolean laterThan(final FileInfo that) {
    if (!sameAs(that)) {
      return false;
    }

    return new ComparableVersion(getVersion())
            .compareTo(new ComparableVersion(
                    that.getVersion())) > 0;
  }

  Path getRepoDir() throws MojoFailureException {
    if ((getRepository() == null) ||
            (getGroupId() == null) ||
            (getArtifactId() == null) ||
            (getVersion() == null)) {
      throw new MojoFailureException(
              "Insufficient information for " + this);
    }

    return Paths.get(getRepository())
                .resolve(getGroupId().replace('.', '/'))
                .resolve(getArtifactId())
                .resolve(getVersion());
  }

  public void toStringSegment(final StringBuilder sb) {
    sb.append("repository=");
    sb.append(getRepository());
    sb.append(", groupId=");
    sb.append(getGroupId());
    sb.append(", artifactId=");
    sb.append(getArtifactId());
    sb.append(", version=");
    sb.append(getVersion());
    sb.append(", type=");
    sb.append(getType());
  }

  @Override
  public int compareTo(final FileInfo that) {
    int res = compareStrings(getArtifactId(),
                             that.getArtifactId());
    if (res != 0) {
      return res;
    }

    res = compareStrings(getType(),
                         that.getType());
    if (res != 0) {
      return res;
    }

    return compareStrings(getVersion(),
                          that.getVersion());
  }

  public boolean equals(final Object o) {
    if (!(o instanceof FileInfo)) {
      return false;
    }

    return compareTo((FileInfo)o) == 0;
  }

  public String toString() {
    final StringBuilder sb = new StringBuilder(
            this.getClass().getSimpleName());

    sb.append("{");
    toStringSegment(sb);
    sb.append("}");

    return sb.toString();
  }
}
