/* ********************************************************************
    Appropriate copyright notice
*/
package org.bedework.util.maven.deploy;

import org.bedework.util.deployment.Utils;
import org.bedework.util.deployment.XmlFile;

import org.w3c.dom.Element;

import java.nio.file.Path;

/**
 * User: mike Date: 2/11/21 Time: 00:35
 */
public class ModuleXml extends XmlFile {
  static final String moduleXmlStr =
      "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
      "<!--\n" +
      "  ~ JBoss, Home of Professional Open Source.\n" +
      "  ~ Copyright 2010, Red Hat, Inc., and individual contributors\n" +
      "  ~ as indicated by the @author tags. See the copyright.txt file in the\n" +
      "  ~ distribution for a full listing of individual contributors.\n" +
      "  ~\n" +
      "  ~ This is free software; you can redistribute it and/or modify it\n" +
      "  ~ under the terms of the GNU Lesser General Public License as\n" +
      "  ~ published by the Free Software Foundation; either version 2.1 of\n" +
      "  ~ the License, or (at your option) any later version.\n" +
      "  ~\n" +
      "  ~ This software is distributed in the hope that it will be useful,\n" +
      "  ~ but WITHOUT ANY WARRANTY; without even the implied warranty of\n" +
      "  ~ MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU\n" +
      "  ~ Lesser General Public License for more details.\n" +
      "  ~\n" +
      "  ~ You should have received a copy of the GNU Lesser General Public\n" +
      "  ~ License along with this software; if not, write to the Free\n" +
      "  ~ Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA\n" +
      "  ~ 02110-1301 USA, or see the FSF site: http://www.fsf.org.\n" +
      "  -->\n" +
      "<module xmlns=\"urn:jboss:module:1.8\">\n" +
      "    <resources>\n" +
      "    </resources>\n" +
      "\n" +
      "</module>\n";
  public ModuleXml(final Utils utils,
                   final Path path,
                   final String moduleName) throws Throwable {
    super(utils, path.toAbsolutePath().toString(), true);

    root.setAttribute("name", moduleName);
    updated = true;
  }

  void addResource(final String name) throws Throwable {
    final Element el = findElement(root, "resources");
    if (el == null) {
      utils.error("Cannot locate element resources");
      return;
    }

    final Element resNode = doc.createElement("resource-root");
    resNode.setAttribute("path",
                         name);

    el.appendChild(resNode);
  }

  void addDependency(final ModuleDependency val) throws Throwable {
    Element el = findElement(root, "dependencies");
    if (el == null) {
      el = doc.createElement("dependencies");
      root.appendChild(el);
    }

    final Element mNode = doc.createElement("module");
    mNode.setAttribute("name", val.name);

    if (val.export) {
      mNode.setAttribute("export", "true");
    }

    el.appendChild(mNode);
  }
}
