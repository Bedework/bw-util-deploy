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

import java.util.List;

/** Result of splitting a name into its component parts, e.g.
 *
 * anapp-3.10.5.war
 *
 * has prefix = "anapp"
 * version = "3.10.5"
 * suffix = "."
 *
 * <p>Note the prefix must be longer than 3 characters - to avoid the
 * "bw-" part of the name</p>
 *
 */
public class SplitName implements Comparable<SplitName> {
  private final String name;

  private final String prefix;
  private String version;
  private String suffix;

  SplitName(final String name,
            final String prefix) {
    this.name = name;
    this.prefix = prefix;

    final int dashPos = prefix.length();
    if (name.charAt(prefix.length()) != '-') {
      throw new RuntimeException("Bad name/prefix");
    }

    final int dotPos = name.lastIndexOf(".");

    if (dotPos > 0) {
      version = name.substring(dashPos + 1, dotPos);
      suffix = name.substring(dotPos + 1);
    } else {
      version = name.substring(dashPos + 1);
    }
  }

  SplitName(final String name,
            final String prefix,
            final String version,
            final String suffix) {
    this(name, prefix);
    this.version = version;
    this.suffix = suffix;
  }

  /** Tries to figure out what the prefix is for the name and then
   * splits it. Assumes we have a "-" in the name.
   *
   * @param name the prefixed name
   * @return split name or null if unable to split.
   */
  public static SplitName testName(final String name) {
    /* Try to figure out the prefix */
    final int dashPos;
    final int snapShotPos = name.indexOf("-SNAPSHOT");

    if (snapShotPos < 0) {
      dashPos = name.lastIndexOf("-");
    } else {
      dashPos = name.lastIndexOf("-", snapShotPos - 1);
    }

    if (dashPos < 0) {
      return null;
    }

    final int dotPos = name.lastIndexOf(".");

    if (dotPos > dashPos) {
      return new SplitName(name, name.substring(0, dashPos));
    }

    return null;
  }

  public static SplitName testName(final String name,
                                   final List<String> prefixes) {
    for (final String prefix: prefixes) {
      if (name.startsWith(prefix) &&
              // Next char must be "-"
              (name.charAt(prefix.length()) == '-')) {
        final int dotPos = name.lastIndexOf(".");

        if (dotPos > prefix.length()) {
          return new SplitName(name, prefix);
        }
      }
    }

    return null;
  }

  /** .
   *
   * @param that SplitName to test
   * @return true if prefix and suffix match.
   */
  public boolean sameAs(final SplitName that) {
    return prefix.equals(that.prefix) &&
            getSuffix().equals(that.getSuffix());
  }

  /** prefix and suffix must match.
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

  public String getPrefix() {
    return prefix;
  }

  public String getSuffix() {
    return suffix;
  }

  public String getName() {
    return name;
  }

  public String getVersion() {
    return version;
  }

  @Override
  public int compareTo(final SplitName that) {
    int res = compareStrings(getPrefix(),
                             that.getPrefix());
    if (res != 0) {
      return res;
    }

    res = compareStrings(getSuffix(),
                         that.getSuffix());
    if (res != 0) {
      return res;
    }

    return compareStrings(getVersion(),
                          that.getVersion());
  }

  public boolean equals(final Object o) {
    if (!(o instanceof SplitName)) {
      return false;
    }

    return compareTo((SplitName)o) == 0;
  }

  public String toString() {
    final StringBuilder sb = new StringBuilder(
            this.getClass().getSimpleName());

    sb.append("{");
    sb.append("name=").append(getName());
    sb.append(", prefix=").append(prefix);
    sb.append(", version=").append(getVersion());
    sb.append(", suffix=").append(getSuffix());
    sb.append("}");

    return sb.toString();
  }

  private static int compareStrings(final String s1,
                                    final String s2) {
    if (s1 == null) {
      if (s2 != null) {
        return -1;
      }

      return 0;
    }

    if (s2 == null) {
      return 1;
    }

    return s1.compareTo(s2);
  }
}

