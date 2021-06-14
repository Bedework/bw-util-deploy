/* ********************************************************************
    Appropriate copyright notice
*/
package org.bedework.util.deployment;

import org.w3c.dom.Element;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * User: mike Date: 6/12/21 Time: 22:07
 */
public class Pom extends XmlFile {
  public Pom(final Utils utils,
             final File dir,
             final String name,
             final boolean nameSpaced)
          throws Throwable {
    super(utils, dir, name, nameSpaced);
  }

  public Pom(final Utils utils,
             final Path path,
             final boolean nameSpaced) throws Throwable {
    super(utils, path, nameSpaced);
  }

  public String getArtifactId() {
    return getArtifactId(null);
  }

  public String getArtifactId(final Element el) {
    return findElementContent(el, "artifactId");
  }

  public String getGroupId() {
    return getGroupId(null);
  }

  public String getGroupId(final Element el) {
    return findElementContent(el, "groupId");
  }

  public List<Element> findPlugins() {
    return findPlugins(null, null);
  }

  public List<Element> findPlugins(final String groupId,
                                   final String artifactId) {
    final List<Element> plugins = new ArrayList<>();
    final Element buildEl = findElement(root, "build");
    if (buildEl == null) {
      return plugins;
    }

    final Element pluginsEl = findElement(buildEl, "plugins");
    if (pluginsEl == null) {
      return plugins;
    }

    for (final Element el: NetUtil.getElementsArray(pluginsEl)) {
      if ("plugin".equals(el.getTagName())) {
        if ((groupId != null) &&
                !groupId.equals(getGroupId(el))) {
          continue;
        }

        if ((artifactId != null) &&
                !artifactId.equals(getArtifactId(el))) {
          continue;
        }

        plugins.add(el);
      }
    }

    return plugins;
  }
}
