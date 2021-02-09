/* ********************************************************************
    Licensed to Jasig under one or more contributor license
    agreements. See the NOTICE file distributed with this work
    for additional information regarding copyright ownership.
    Jasig licenses this file to you under the Apache License,
    Version 2.0 (the "License"); you may not use this file
    except in compliance with the License. You may obtain a
    copy of the License at:

    http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing,
    software distributed under the License is distributed on
    an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
    KIND, either express or implied. See the License for the
    specific language governing permissions and limitations
    under the License.
*/
package org.bedework.util.deployment;

import org.apache.http.Header;
import org.apache.http.HttpException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.message.BasicHeader;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import javax.servlet.http.HttpServletResponse;
import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

/** Helper for DAV interactions
*
* @author Mike Douglass  douglm - rpi.edu
*/
public class NetUtil {
  public static final int SC_MULTI_STATUS = 207; // not defined for some reason

  public static final QName collection = makeQName("collection");
  public static final QName displayname = makeQName("displayname");
  public static final QName error = makeQName("error");
  public static final QName href = makeQName("href");
  public static final QName multistatus = makeQName("multistatus");
  public static final QName prop = makeQName("prop");
  public static final QName propstat = makeQName("propstat");
  public static final QName resourcetype = makeQName("resourcetype");
  public static final QName response = makeQName("response");
  public static final QName responseDescription = makeQName("responsedescription");
  public static final QName status = makeQName("status");

  private static QName makeQName(final String name) {
    return new QName("DAV:", name);
  }

  /** Represents the child of a collection
   *
   * @author Mike Douglass
   */
  public static class DavChild implements Comparable<DavChild>  {
    /** The href */
    public String uri;

    /** Always requested */
    public String displayName;

    /** Always requested */
    public boolean isCollection;

    /** Same order as supplied properties */
    public List<DavProp> propVals = new ArrayList<>();

    /* Extracted from returned resource types */
    public List<QName> resourceTypes = new ArrayList<>();

    /** */
    public int status;

    @Override
    public int compareTo(final DavChild that) {
      if (isCollection != that.isCollection) {
        if (!isCollection) {
          return -1;
        }

        return 1;
      }

      if (displayName == null) {
        return -1;
      }

      if (that.displayName == null) {
        return 1;
      }

      return displayName.compareTo(that.displayName);
    }
  }

  /** Represents a property
   *
   * @author Mike Douglass
   */
  public static class DavProp {
    /** */
    public QName name;
    /** */
    public Element element;
    /** */
    public int status;
  }

  /**
   * @param cl the client
   * @param uri for request
   * @return Collection<DavChild> - empty for no children - null for path not found.
   */
  public List<DavChild> getChildrenUrls(final CloseableHttpClient cl,
                                              final URI uri) {
    return processResponses(propfind(cl, uri), uri);
  }

  private final static String propfindRequest =
          "<?xml version=\"1.0\" encoding=\"utf-8\" ?>\n" +
          "<D:propfind xmlns:D=\"DAV:\">\n" +
          "  <D:prop>\n" +
          "    <D:resourcetype/>\n" +
          "    <D:displayname/>\n" +
          "  </D:prop>\n" +
          "</D:propfind>\n";

  /**
   * @param cl the client
   * @param uri to resource
   * @return List<Element> from multi-status response
   */
  public List<Element> propfind(final CloseableHttpClient cl,
                                final URI uri) {
    try (final CloseableHttpResponse hresp =
                 doPropfind(cl,
                            uri,
                            "1",
                            "text/xml",
                            propfindRequest)) {

       return processPropfindResponse(hresp);
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }

  final List<Element> processPropfindResponse(final CloseableHttpResponse resp) {
    try {
      final int status = resp.getStatusLine().getStatusCode();

      if (status != SC_MULTI_STATUS) {
        throw new RuntimeException("Failed response from server: status = " + status);
      }

      if (resp.getEntity() == null) {
        throw new RuntimeException("No content in response from server");
      }

      final InputStream is = resp.getEntity().getContent();

      final Document doc = parseContent(is);

      final Element root = doc.getDocumentElement();

      /*    <!ELEMENT multistatus (response+, responsedescription?) > */

      expect(root, multistatus);

      return getChildren(root);
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }

  /** Parse the content, and return the DOM representation.
   *
   * @param in         content as stream
   * @return Document  Parsed body or null for no body
   * @exception RuntimeException Some error occurred.
   */
  protected Document parseContent(final InputStream in) {
    try {
      final DocumentBuilderFactory factory = DocumentBuilderFactory
              .newInstance();
      factory.setNamespaceAware(true);

      final DocumentBuilder builder = factory.newDocumentBuilder();

      return builder
              .parse(new InputSource(new InputStreamReader(in)));
    } catch (final Throwable t) {
      if (t instanceof RuntimeException) {
        throw (RuntimeException)t;
      }
      throw new RuntimeException(t);
    }
  }

  /** Parse a DAV error response
   *
   * @param in response
   * @return a single error element or null
   */
  public Element parseError(final InputStream in) {
    try {
      final Document doc = parseContent(in);

      final Element root = doc.getDocumentElement();

      expect(root, error);

      final List<Element> els = getChildren(root);

      if (els.size() != 1) {
        return null;
      }

      return els.get(0);
    } catch (final Throwable ignored) {
      return null;
    }
  }

  /* ====================================================================
   *                   XmlUtil wrappers
   * ==================================================================== */

  /**
   * @param nd the node
   * @return List<Element>
   */
  public static List<Element> getChildren(final Node nd) {
    final List<Node> nodes = getNodes(nd);

    for (final Node n: nodes) {
      if (!(n instanceof Element)) {
        throw new RuntimeException("Required element. Found " + nd);
      }
    }

    //noinspection unchecked
    return (List<Element>)(Object)nodes;
  }
  public static List<Node> getNodes(final Node nd) {
    final List<Node> al = new ArrayList<>();

    final NodeList children = nd.getChildNodes();

    for (int i = 0; i < children.getLength(); i++) {
      final Node curnode = children.item(i);

      if (curnode.getNodeType() == Node.TEXT_NODE) {
        final String val = curnode.getNodeValue();

        if (val != null) {
          for (int vi= 0; vi < val.length(); vi++) {
            if (!Character.isWhitespace(val.charAt(vi))) {
              throw new RuntimeException("Non-whitespace text in element body for " +
                                                 nd.getLocalName() +
                                                 "\n text=" + val);
            }
          }
        }
      } else if (curnode.getNodeType() == Node.ELEMENT_NODE) {
        al.add(curnode);
      } else if (curnode.getNodeType() != Node.COMMENT_NODE) {
        throw new RuntimeException("Unexpected child node " + curnode.getLocalName() +
                                           " for " + nd.getLocalName());
      }
    }

    return al;
  }

  /**
   * @param nd the node
   * @return element array from node
   */
  public static Element[] getElementsArray(final Node nd) {
    final Collection<Element> al = getElements(nd);

    return al.toArray(new Element[0]);
  }

  /** All the children must be elements or white space text nodes.
   *
   * @param nd the node
   * @return Collection   element nodes. Always non-null
   * @throws RuntimeException on fatal error
   */
  public static List<Element> getElements(final Node nd) {
    final List<Node> nodes = getNodes(nd);

    for (final Node n: nodes) {
      if (!(n instanceof Element)) {
        throw new RuntimeException("Required element. Found " + nd);
      }
    }

    //noinspection unchecked
    return (List<Element>)(Object)nodes;
  }

  /** Get the single named element.
   *
   * @param el          Node
   * @param name        String tag name of required node
   * @return Node     node value or null
   */
  public static Node getOneTaggedNode(final Node el,
                                      final String name) {
    if (!el.hasChildNodes()) {
      return null;
    }

    final NodeList children = el.getChildNodes();

    for (int i = 0; i < children.getLength(); i++) {
      final Node n = children.item(i);

      if (name.equals(n.getNodeName())) {
        return n;
      }
    }

    return null;
  }

  /**
   * @param el element
   * @return String
   * @throws RuntimeException on error
   */
  public static String getElementContent(final Element el) {
    final StringBuilder sb = new StringBuilder();

    final NodeList children = el.getChildNodes();

    for (int i = 0; i < children.getLength(); i++) {
      final Node curnode = children.item(i);

      if (curnode.getNodeType() == Node.TEXT_NODE) {
        sb.append(curnode.getNodeValue());
      } else if (curnode.getNodeType() == Node.CDATA_SECTION_NODE) {
        sb.append(curnode.getNodeValue());
      } else if (curnode.getNodeType() != Node.COMMENT_NODE) {
        throw new RuntimeException("Unexpected child node " + curnode.getLocalName() +
                                           " for " + el.getLocalName());
      }
    }

    return sb.toString().trim();
  }

  /** Replace the content for the current element.
   *
   * @param n element
   * @param s string content
   */
  public static void setElementContent(final Node n,
                                       final String s) {
    NodeList children = n.getChildNodes();

    for (int i = 0; i < children.getLength(); i++) {
      Node curnode = children.item(i);

      n.removeChild(curnode);
    }

    Document d = n.getOwnerDocument();

    final Node textNode = d.createTextNode(s);

    n.appendChild(textNode);
  }

  /** Return the value of the named attribute of the given element.
   *
   * @param el          Element
   * @param name        String name of desired attribute
   * @return String     attribute value or null
   */
  public static String getAttrVal(final Element el,
                                  final String name) {
    Attr at = el.getAttributeNode(name);
    if (at == null) {
      return null;
    }

    return at.getValue();
  }

  public static QName fromNode(final Node nd) {
    String ns = nd.getNamespaceURI();

    if (ns == null) {
      /* It appears a node can have a NULL namespace but a QName has a zero length
       */
      ns = "";
    }

    return new QName(ns, nd.getLocalName());
  }

  /**
   * @param el xml element
   * @param tag QName
   * @throws RuntimeException on error
   */
  public static void expect(final Element el,
                            final QName tag) {
    if (!nodeMatches(el, tag)) {
      throw new RuntimeException("Expected " + tag);
    }
  }

  /**
   * @param responses   null for a default set
   * @param parentURI   null or uri of collection to excluse response
   * @return List<DavChild> - empty for no children.
   */
  public List<DavChild> processResponses(final Collection<Element> responses,
                                         final URI parentURI) {
    final List<DavChild> result = new ArrayList<>();

    if (responses == null) {
      return result;
    }

    int count = 0; // validity
    for (final Element resp: responses) {
      count++;

      if (nodeMatches(resp, responseDescription)) {
        // Has to be last
        if (responses.size() > count) {
          throw new RuntimeException("Bad multstatus Expected " +
                                      "(response+, responsedescription?)");
        }

        continue;
      }

      if (!nodeMatches(resp, response)) {
        throw new RuntimeException("Bad multstatus Expected " +
                                    "(response+, responsedescription?) found " + resp);
      }

      final DavChild dc = makeDavResponse(resp);

      /* We get the collection back as well - check for it and skip it. */
      final URI childURI;
      try {
        childURI = new URI(dc.uri);
      } catch (final URISyntaxException use) {
        throw new RuntimeException(use);
      }

      if ((parentURI != null) &&
              parentURI.getPath().equals(childURI.getPath())) {
        continue;
      }

      result.add(dc);
    }

    return result;
  }

  private DavChild makeDavResponse(final Element resp) {
    /*    <!ELEMENT response (href, ((href*, status)|(propstat+)),
          responsedescription?) >
     */
    final Iterator<Element> elit = getChildren(resp).iterator();

    Element nd = elit.next();

    final DavChild dc = new DavChild();

    if (!nodeMatches(nd, href)) {
      throw new RuntimeException("Bad response. Expected href found " + nd);
    }

    dc.uri = URLDecoder.decode(getElementContent(nd),
                               StandardCharsets.UTF_8); // href should be escaped

    while (elit.hasNext()) {
      nd = elit.next();

      if (nodeMatches(nd, status)) {
        dc.status = httpStatus(nd);
        continue;
      }

      dc.status = HttpServletResponse.SC_OK;

      if (!nodeMatches(nd, propstat)) {
        throw new RuntimeException("Bad response. Expected propstat found " + nd);
      }

      /*    <!ELEMENT propstat (prop, status, responsedescription?) > */

      final Iterator<Element> propstatit = getChildren(nd).iterator();
      final Node propnd = propstatit.next();

      if (!nodeMatches(propnd, prop)) {
        throw new RuntimeException("Bad response. Expected prop found " + propnd);
      }

      if (!propstatit.hasNext()) {
        throw new RuntimeException("Bad response. Expected propstat/status");
      }

      final int st = httpStatus(propstatit.next());

      if (propstatit.hasNext()) {
        final Node rdesc = propstatit.next();

        if (!nodeMatches(rdesc, responseDescription)) {
          throw new RuntimeException("Bad response, expected null or " +
                                             "responsedescription. Found: " + rdesc);
        }
      }

      /* process each property with this status */

      final Collection<Element> respProps = getChildren(propnd);

      for (final Element pr: respProps) {
        /* XXX This needs fixing to handle content that is xml
         */
        if (nodeMatches(pr, resourcetype)) {
          final Collection<Element> rtypeProps = getChildren(pr);

          for (final Element rtpr: rtypeProps) {
            if (nodeMatches(rtpr, collection)) {
              dc.isCollection = true;
            }

            dc.resourceTypes.add(fromNode(rtpr));
          }
        } else {
          final DavProp dp = new DavProp();

          dc.propVals.add(dp);

          dp.name = new QName(pr.getNamespaceURI(), pr.getLocalName());
          dp.status = st;
          dp.element = pr;

          if (nodeMatches(pr, displayname)) {
            dc.displayName = getElementContent(pr);
          }
        }
      }
    }

    return dc;
  }

  /**
   * @param el - must be status element
   * @return int status
   * @throws RuntimeException on bad status
   */
  public static int httpStatus(final Element el) {
    if (!nodeMatches(el, status)) {
      throw new RuntimeException("Bad response. Expected status found " + el);
    }

    final String s = getElementContent(el);

    if (s.length() == 0) {
      throw new RuntimeException("Bad http status. Found null");
    }

    try {
      final int start = s.indexOf(" ");
      final int end = s.indexOf(" ", start + 1);

      if (end < 0) {
        return Integer.parseInt(s.substring(start + 1));
      }

      return Integer.parseInt(s.substring(start + 1, end));
    } catch (final Throwable t) {
      throw new RuntimeException("Bad http status. Found " + s);
    }
  }

  public static boolean getBinary(final CloseableHttpClient cl,
                                  final URI uri,
                                  final OutputStream out) throws HttpException {
    try (final CloseableHttpResponse hresp =
                 doGet(cl,
                       uri,
                       "application/binary")) {
      final InputStream is = hresp.getEntity().getContent();

      if (is == null) {
        return false;
      }

      final int bufSize = 2048;
      final byte[] buf = new byte[bufSize];
      while (true) {
        final int len = is.read(buf, 0, bufSize);
        if (len == -1) {
          break;
        }
        out.write(buf, 0, len);
      }

      return true;
    } catch (final Throwable t) {
      throw new HttpException(t.getMessage(), t);
    }
  }

  public static CloseableHttpResponse doGet(final CloseableHttpClient cl,
                                            final URI uri,
                                            final String acceptContentType)
          throws IOException {
    final List<Header> headers = new ArrayList<>();

    if (acceptContentType != null) {
      headers.add(new BasicHeader("Accept", acceptContentType));
    }

    final HttpGet httpGet = new HttpGet(uri);

    httpGet.setHeaders(headers.toArray(new Header[0]));

    return cl.execute(httpGet);
  }

  public static CloseableHttpResponse doPropfind(final CloseableHttpClient cl,
                                                 final URI uri,
                                                 final String depth,
                                                 final String contentType,
                                                 final String content)
          throws IOException {
    final List<Header> headers = new ArrayList<>();

    if (depth != null) {
      headers.add(new BasicHeader("depth", depth));
    }

    if (contentType != null) {
      headers.add(new BasicHeader("Content-type", contentType));
    }

    final HttpPropfind httpPropfind = new HttpPropfind(uri);

    httpPropfind.setHeaders(headers.toArray(new Header[0]));

    final StringEntity entity = new StringEntity(content);
    httpPropfind.setEntity(entity);

    return cl.execute(httpPropfind);
  }

  public static class HttpPropfind extends HttpEntityEnclosingRequestBase {
    public static final String METHOD_NAME = "PROPFIND";

    public HttpPropfind(final URI uri) {
      this.setURI(uri);
    }

    public String getMethod() {
      return METHOD_NAME;
    }
  }

  public static boolean nodeMatches(final Node nd, final QName tag) {
    if (tag == null) {
      return false;
    }

    final String ns = nd.getNamespaceURI();

    if (ns == null) {
      /* It appears a node can have a NULL namespace but a QName has a zero length
       */
      if ((tag.getNamespaceURI() != null) && (!"".equals(tag.getNamespaceURI()))) {
        return false;
      }
    } else if (!ns.equals(tag.getNamespaceURI())) {
      return false;
    }

    final String ln = nd.getLocalName();

    if (ln == null) {
      if (tag.getLocalPart() != null) {
        return false;
      }
    } else if (!ln.equals(tag.getLocalPart())) {
      return false;
    }

    return true;
  }

  /**
   * @param nd the node
   * @return only child node
   * @throws RuntimeException  if not exactly one child elemnt
   */
  public static Element getOnlyElement(final Node nd) {
    final Element[] els = getElementsArray(nd);

    if (els.length != 1) {
      throw new RuntimeException("Expected exactly one child node for " +
                                         nd.getLocalName());
    }

    return els[0];
  }

  public static void printDocument(final Document doc,
                                   final OutputStream out) throws IOException, TransformerException {
    final TransformerFactory tf = TransformerFactory.newInstance();
    final Transformer transformer = tf.newTransformer();
    transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
    transformer.setOutputProperty(OutputKeys.METHOD, "xml");
    transformer.setOutputProperty(OutputKeys.INDENT, "yes");
    transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
    transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");

    transformer.transform(new DOMSource(doc),
                          new StreamResult(new OutputStreamWriter(out, "UTF-8")));
  }
}
