package org.bedework.util.deployment;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Represent an xml file in a directory.
 *
 * @author douglm
 */
public class XmlFile extends BaseClass {
  protected final File theXml;

  protected Document doc;
  protected Element root;

  protected boolean updated;

  public XmlFile(final Utils utils,
                 final File dir,
                 final String name,
                 final boolean nameSpaced) throws Throwable {
    super(utils);
    theXml = utils.file(dir, name, true);

    init(nameSpaced);
  }

  public XmlFile(final Utils utils,
                 final String path,
                 final boolean nameSpaced) throws Throwable {
    super(utils);
    theXml = utils.file(path);

    init(nameSpaced);
  }

  public XmlFile(final Utils utils,
                 final Path path,
                 final boolean nameSpaced) throws Throwable {
    super(utils);
    theXml = utils.file(path);

    init(nameSpaced);
  }

  private void init(final boolean nameSpaced) throws Throwable {
    doc = utils.parseXml(new FileReader(theXml),
                         nameSpaced,
                         true);  // pretend offline

    root = doc.getDocumentElement();
  }

  public boolean getUpdated() {
    return updated;
  }

  public void output() throws Throwable {
    final OutputStream out = new FileOutputStream(theXml, false);

    NetUtil.printDocument(doc, out);
  }

  protected Element findElement(final Element root,
                                final String tagName) {
    for (final Element el: NetUtil.getElementsArray(root)) {
      if (tagName.equals(el.getTagName())) {
        return el;
      }
    }

    return null;
  }

  protected List<Element> findElements(final Element root,
                                       final String tagName) {
    final List<Element> els = new ArrayList<>();

    for (final Element el: NetUtil.getElementsArray(root)) {
      if (tagName.equals(el.getTagName())) {
        els.add(el);
      }
    }

    return els;
  }

  protected String findElementContent(final Element root,
                                      final String tagName) {
    final Element el = findElement(root, tagName);
    if (el == null) {
      return null;
    }

    return NetUtil.getElementContent(el);
  }

  /** Update the value if it has a property replacement pattern.
   * Set updated true if changed.
   *
   * @param root    search below this for named element
   * @param props to lookup new value
   * @param tagnames path to element to set content for
   */
  protected void propsReplaceContent(final Element root,
                                     final PropertiesChain props,
                                     final String... tagnames) {
    Element el = root;
    if (tagnames != null) {
      for (final String nm: tagnames) {
        el = findElement(el, nm);
        if (el == null) {
          return;
        }
      }
    }

    final String s = NetUtil.getElementContent(el);

    final String newS = props.replace(s);

    if (s.equals(newS)) {
      return;
    }

    NetUtil.setElementContent(el, newS);
    updated = true;
  }

  /** Update the value if it has a property replacement pattern.
   * Set updated true if changed.
   *
   * @param root    search below this for named element
   * @param tagname element to set content for
   * @param props to lookup new value
   */
  protected void propsReplaceContent(final Element root,
                                     final String tagname,
                                     final PropertiesChain props) {
    final Node n = NetUtil.getOneTaggedNode(root, tagname);

    if (n == null) {
      //utils.info("no element with name " + tagname);
      return;
    }

    final String s = NetUtil.getElementContent((Element)n);

    final String newS = props.replace(s);

    if (s.equals(newS)) {
      return;
    }

    NetUtil.setElementContent(n, newS);
    updated = true;
  }

  /** Update the value if it has a property replacement pattern.
   * Set updated true if changed.
   *
   * @param el    element
   * @param attrname attribute to change
   * @param props to lookup new value
   */
  protected void propsReplaceAttr(final Element el,
                                  final String attrname,
                                  final PropertiesChain props) {
    final String s = NetUtil.getAttrVal(el, attrname);

    final String newS = props.replace(s);

    if ((s != null) && s.equals(newS)) {
      return;
    }

    el.setAttribute(attrname, newS);
    updated = true;
  }
}
