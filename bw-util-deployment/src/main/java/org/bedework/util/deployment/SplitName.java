/*
#    Copyright (c) 2007-2013 Cyrus Daboo. All rights reserved.
#
#    Licensed under the Apache License, Version 2.0 (the "License");
#    you may not use this file except in compliance with the License.
#    You may obtain a copy of the License at
#
#        http://www.apache.org/licenses/LICENSE-2.0
#
#    Unless required by applicable law or agreed to in writing, software
#    distributed under the License is distributed on an "AS IS" BASIS,
#    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#    See the License for the specific language governing permissions and
#    limitations under the License.
*/
package org.bedework.util.deployment;

import org.apache.maven.artifact.versioning.ComparableVersion;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.bedework.util.deployment.Utils.compareStrings;

/** Result of splitting a name into its component parts, e.g.
 *
 * anapp-3.10.5.war
 *
 * has artifactId = "anapp"
 * version = "3.10.5"
 * type = "war"
 *
 * <p>Note the artifactId must be longer than 3 characters - to avoid the
 * "bw-" part of the name</p>
 *
 */
public class SplitName implements Comparable<SplitName> {
  private final String name;

  private final String artifactId;
  private String classifier;
  private String version;
  private String type;

  SplitName(final String name,
            final String artifactId) {
    this.name = name;
    this.artifactId = artifactId;

    final int dashPos = artifactId.length();
    if (name.charAt(artifactId.length()) != '-') {
      throw new RuntimeException("Bad name/artifactId");
    }

    final int dotPos = name.lastIndexOf(".");

    if (dotPos > 0) {
      final var vc = getVersionClassifier(name.substring(dashPos + 1, dotPos));
      version = vc.version();
      classifier = vc.classifier();
      type = name.substring(dotPos + 1);
    } else {
      final var vc = getVersionClassifier(name.substring(dashPos + 1));
      version = vc.version();
      classifier = vc.classifier();
    }
  }

  SplitName(final String name,
            final String artifactId,
            final String version,
            final String type) {
    this(name, artifactId);
    final var vc = getVersionClassifier(version);
    this.version = vc.version();
    classifier = vc.classifier();
    this.type = type;
  }

  public static List<String> classifiers =
          new ArrayList<>(Arrays.asList(
                  "-SNAPSHOT.",
                  "-GA.",
                  "-javadoc.",
                  "-jre.",
                  "-jakarta.",
                  "-sources.",
                  "-tests.",
                  ".0-1.",
                  ".0-1.",
                  "-min."));

  public static void addClassifier(final String val) {
    if (!classifiers.contains(val)) {
      classifiers.add(val);
    }
  }

  /** Tries to figure out what the artifactId is for the name and then
   * splits it. Assumes we have a "-" in the name.
   *
   * @param name the artifact name
   * @return split name or null if unable to split.
   */
  public static SplitName testName(final String name) {
    /* Try to figure out the artifactId */

    int testPos = -1;

    for (final String possibleClassifier: classifiers) {
      testPos = name.indexOf(possibleClassifier);

      if (testPos > 0) {
        break;
      }
    }

    final int dashPos;
    if (testPos < 0) {
      dashPos = name.lastIndexOf("-");
    } else {
      dashPos = name.lastIndexOf("-", testPos - 1);
    }

    if (dashPos < 0) {
      // Not versioned
      return null;
    }

    final int dotPos = name.lastIndexOf(".");

    if (dotPos > dashPos) {
      return new SplitName(name, name.substring(0, dashPos));
    }

    return null;
  }

  public static SplitName testName(final String name,
                                   final String specificName) {
    if (name.startsWith(specificName) &&
            // Next char must be "-"
            (name.charAt(specificName.length()) == '-')) {
      final int dotPos = name.lastIndexOf(".");

      if (dotPos > specificName.length()) {
        return new SplitName(name, specificName);
      }
    }

    return null;
  }

  /** .
   *
   * @param that SplitName to test
   * @return true if artifactId and type match.
   */
  public boolean sameAs(final SplitName that) {
    return artifactId.equals(that.artifactId) &&
            getType().equals(that.getType());
  }

  /** artifactId and type must match.
   *
   * @param that SplitName to test
   * @return true if this version also is greater than that version.
   */
  public boolean laterThan(final SplitName that) {
    if (!sameAs(that)) {
      return false;
    }

    return new ComparableVersion(getVersion()).compareTo(
            new ComparableVersion(that.getVersion())) > 0;
  }

  /**
   *
   * @param them SplitName list to test
   * @return true if this is later than same file in list.
   */
  public boolean laterThan(final List<SplitName> them) {
    if (them == null) {
      return false;
    }

    boolean foundSame = false;

    for (final SplitName sn: them) {
      if (!sameAs(sn)) {
        continue;
      }

      foundSame = true;

      if (laterThan(sn)) {
        return true;
      }
    }

    return !foundSame;
  }

  public String getArtifactId() {
    return artifactId;
  }

  public String getType() {
    if (type == null) {
      return "jar";
    }
    return type;
  }

  public String getName() {
    return name;
  }

  public String getClassifier() {
    return classifier;
  }

  public String getVersion() {
    return version;
  }

  private record VersionClassifier(String version,
                                   String classifier) {}

  public VersionClassifier getVersionClassifier(final String val) {
    if (val == null) {
      return new VersionClassifier(null, null);
    }

    final int index = val.lastIndexOf("-");

    if (index < 0) {
      return new VersionClassifier(val, null);
    }

    return new VersionClassifier(val.substring(0, index),
                                 val.substring(index + 1));
  }

  @Override
  public int compareTo(final SplitName that) {
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

    res = compareStrings(getVersion(),
                         that.getVersion());
    if (res != 0) {
      return res;
    }

    return compareStrings(getClassifier(),
                          that.getClassifier());
  }

  @Override
  public boolean equals(final Object o) {
    return compareTo((SplitName)o) == 0;
  }

  public String toString() {
    return new StringBuilder(getClass().getSimpleName())
            .append("{")
            .append("name=").append(getName())
            .append(", artifactId=").append(artifactId)
            .append(", classifier=").append(getClassifier())
            .append(", version=").append(getVersion())
            .append(", type=").append(getType())
            .append("}")
            .toString();
  }
}

