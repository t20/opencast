/**
 * Licensed to The Apereo Foundation under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 *
 * The Apereo Foundation licenses this file to you under the Educational
 * Community License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License
 * at:
 *
 *   http://opensource.org/licenses/ecl2.txt
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations under
 * the License.
 *
 */


package org.opencastproject.mediapackage;

import static java.lang.String.format;
import static javax.xml.XMLConstants.DEFAULT_NS_PREFIX;
import static javax.xml.XMLConstants.W3C_XML_SCHEMA_INSTANCE_NS_URI;
import static javax.xml.XMLConstants.XMLNS_ATTRIBUTE;
import static javax.xml.XMLConstants.XML_NS_URI;
import static org.opencastproject.util.EqualsUtil.eq;
import static org.opencastproject.util.EqualsUtil.hash;

import com.entwinemedia.fn.data.ImmutableMapWrapper;
import org.opencastproject.util.RequireUtil;
import org.opencastproject.util.XmlNamespaceBinding;
import org.opencastproject.util.XmlNamespaceContext;

import org.apache.commons.lang3.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.Attributes;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

/**
 * This is a basic implementation for handling simple catalogs of metadata. It provides utility methods to store
 * key-value data.
 * <p/>
 * For a definition of the terms <def>expanded name</def>, <def>qualified name</def> or <def>QName</def>, <def>namespace
 * prefix</def>, <def>local part</def> and <def>local name</def>, please see <a
 * href="http://www.w3.org/TR/REC-xml-names">http://www.w3.org/TR/REC-xml-names</a>
 * <p/>
 * By default the following namespace prefixes are bound:
 * <ul>
 * <li>xml - http://www.w3.org/XML/1998/namespace
 * <li>xmlns - http://www.w3.org/2000/xmlns/
 * <li>xsi - http://www.w3.org/2001/XMLSchema-instance
 * </ul>
 * <p/>
 * <h3>Limitations</h3>
 * XMLCatalog supports only <em>one</em> prefix binding per namespace name, so you cannot create documents like the
 * following using XMLCatalog:
 *
 * <pre>
 * &lt;root xmlns:x=&quot;http://x.demo.org&quot; xmlns:y=&quot;http://x.demo.org&quot;&gt;
 *   &lt;x:elem&gt;value&lt;/x:elem&gt;
 *   &lt;y:elem&gt;value&lt;/y:elem&gt;
 * &lt;/root&gt;
 * </pre>
 *
 * However, reading of those documents is supported.
 */
public abstract class XMLCatalogImpl extends CatalogImpl implements XMLCatalog {
  private static final long serialVersionUID = -7580292199527168951L;

  /** Expanded name of the XML language attribute <code>xml:lang</code>. */
  public static final EName XML_LANG_ATTR = new EName(XML_NS_URI, "lang");

  /** Namespace prefix for XML schema instance. */
  public static final String XSI_NS_PREFIX = "xsi";

  /**
   * Expanded name of the XSI type attribute.
   * <p/>
   * See <a href="http://www.w3.org/TR/xmlschema-1/#xsi_type">http://www.w3.org/TR/xmlschema-1/#xsi_type</a> for the
   * definition.
   */
  public static final EName XSI_TYPE_ATTR = new EName(W3C_XML_SCHEMA_INSTANCE_NS_URI, "type");

  /** Key (QName) value meta data */
  protected final Map<EName, List<CatalogEntry>> data = new HashMap<>();

  /** Namespace - prefix bindings */
  protected XmlNamespaceContext bindings;

  /**
   * Create an empty catalog and register the {@link javax.xml.XMLConstants#W3C_XML_SCHEMA_INSTANCE_NS_URI}
   * namespace.
   */
  protected XMLCatalogImpl() {
    super();
    bindings = XmlNamespaceContext.mk(XSI_NS_PREFIX, W3C_XML_SCHEMA_INSTANCE_NS_URI);
  }

  protected void addBinding(XmlNamespaceBinding binding) {
    bindings = bindings.add(binding);
  }

  protected XmlNamespaceContext getBindings() {
    return bindings;
  }

  /**
   * Clears the catalog.
   */
  protected void clear() {
    data.clear();
  }

  /**
   * Adds the element to the metadata collection.
   *
   * @param element
   *          the expanded name of the element
   * @param value
   *          the value
   */
  protected void addElement(EName element, String value) {
    if (element == null)
      throw new IllegalArgumentException("Expanded name must not be null");

    addElement(new CatalogEntry(element, value));
  }

  /**
   * Adds the element with the <code>xml:lang</code> attribute to the metadata collection.
   *
   * @param element
   *          the expanded name of the element
   * @param value
   *          the value
   * @param language
   *          the language identifier (two letter ISO 639)
   */
  protected void addLocalizedElement(EName element, String value, String language) {
    RequireUtil.notNull(element, "expanded name");
    RequireUtil.notNull(language, "language");

    Map<EName, String> attributes = new HashMap<>(1);
    attributes.put(XML_LANG_ATTR, language);
    addElement(new CatalogEntry(element, value, attributes));
  }

  /**
   * Adds the element with the <code>xsi:type</code> attribute to the metadata collection.
   *
   * @param value
   *          the value
   * @param type
   *          the element type
   */
  protected void addTypedElement(EName element, String value, EName type) {
    RequireUtil.notNull(element, "expanded name");
    RequireUtil.notNull(type, "type");

    Map<EName, String> attributes = new HashMap<>(1);
    attributes.put(XSI_TYPE_ATTR, toQName(type));
    addElement(new CatalogEntry(element, value, attributes));
  }

  /**
   * Adds an element with the <code>xml:lang</code> and <code>xsi:type</code> attributes to the metadata collection.
   *
   * @param element
   *          the expanded name of the element
   * @param value
   *          the value
   * @param language
   *          the language identifier (two letter ISO 639)
   * @param type
   *          the element type
   */
  protected void addTypedLocalizedElement(EName element, String value, String language, EName type) {
    if (element == null)
      throw new IllegalArgumentException("EName name must not be null");
    if (type == null)
      throw new IllegalArgumentException("Type must not be null");
    if (language == null)
      throw new IllegalArgumentException("Language must not be null");

    Map<EName, String> attributes = new HashMap<>(2);
    attributes.put(XML_LANG_ATTR, language);
    attributes.put(XSI_TYPE_ATTR, toQName(type));
    addElement(new CatalogEntry(element, value, attributes));
  }

  /**
   * Adds an element with attributes to the catalog.
   *
   * @param element
   *          the expanded name of the element
   * @param value
   *          the element's value
   * @param attributes
   *          the attributes. May be null
   */
  protected void addElement(EName element, String value, Attributes attributes) {
    if (element == null)
      throw new IllegalArgumentException("Expanded name must not be null");

    Map<EName, String> attributeMap = new HashMap<>();
    if (attributes != null) {
      for (int i = 0; i < attributes.getLength(); i++) {
        attributeMap.put(new EName(attributes.getURI(i), attributes.getLocalName(i)), attributes.getValue(i));
      }
    }
    addElement(new CatalogEntry(element, value, attributeMap));
  }

  /**
   * Adds the catalog element to the list of elements.
   *
   * @param element
   *          the element
   */
  private void addElement(CatalogEntry element) {
    if (element == null || StringUtils.trimToNull(element.getValue()) == null)
      return;
    List<CatalogEntry> values = data.get(element.getEName());
    if (values == null) {
      values = new ArrayList<>();
      data.put(element.getEName(), values);
    }
    values.add(element);
  }

  /**
   * Completely removes an element.
   *
   * @param element
   *          the expanded name of the element
   */
  protected void removeElement(EName element) {
    removeValues(element, null, true);
  }

  /**
   * Removes all entries in a certain language from an element.
   *
   * @param element
   *          the expanded name of the element
   * @param language
   *          the language code (two letter ISO 639) or null to <em>only</em> remove entries without an
   *          <code>xml:lang</code> attribute
   */
  protected void removeLocalizedValues(EName element, String language) {
    removeValues(element, language, false);
  }

  /**
   * Removes values from an element or the complete element from the catalog.
   *
   * @param element
   *          the expanded name of the element
   * @param language
   *          the language code (two letter ISO 639) to remove or null to remove entries without language code
   * @param all
   *          true - remove all entries for that element. This parameter overrides the language parameter.
   */
  private void removeValues(EName element, String language, boolean all) {
    if (all) {
      data.remove(element);
    } else {
      List<CatalogEntry> entries = data.get(element);
      if (entries != null) {
        for (Iterator<CatalogEntry> i = entries.iterator(); i.hasNext();) {
          CatalogEntry entry = i.next();
          if (equal(language, entry.getAttribute(XML_LANG_ATTR))) {
            i.remove();
          }
        }
      }
    }
  }

  /**
   * Returns the values that are associated with the specified key.
   *
   * @param element
   *          the expanded name of the element
   * @return the elements
   */
  protected CatalogEntry[] getValues(EName element) {
    List<CatalogEntry> values = data.get(element);
    if (values != null && values.size() > 0) {
      return values.toArray(new CatalogEntry[values.size()]);
    }
    return new CatalogEntry[] {};
  }

  /**
   * Returns the values that are associated with the specified key.
   *
   * @param element
   *          the expanded name of the element
   * @return all values of the element or an empty list if this element does not exist or does not have any values
   */
  @SuppressWarnings("unchecked")
  protected List<CatalogEntry> getValuesAsList(EName element) {
    List<CatalogEntry> values = data.get(element);
    return values != null ? values : Collections.EMPTY_LIST;
  }

  /**
   * Returns the values that are associated with the specified key.
   *
   * @param element
   *          the expandend name of the element
   * @param language
   *          a language code or null to get values without <code>xml:lang</code> attribute
   * @return all values of the element
   */
  @SuppressWarnings("unchecked")
  protected List<CatalogEntry> getLocalizedValuesAsList(EName element, String language) {
    List<CatalogEntry> values = data.get(element);

    if (values != null) {
      List<CatalogEntry> filtered = new ArrayList<>();
      for (CatalogEntry value : values) {
        if (equal(language, value.getAttribute(XML_LANG_ATTR))) {
          filtered.add(value);
        }
      }
      return filtered;
    } else {
      return Collections.EMPTY_LIST;
    }
  }

  /**
   * Returns the first value that is associated with the specified name.
   *
   * @param element
   *          the expanded name of the element
   * @return the first value
   */
  protected CatalogEntry getFirstValue(EName element) {
    List<CatalogEntry> elements = data.get(element);
    if (elements != null && elements.size() > 0) {
      return elements.get(0);
    }
    return null;
  }

  /**
   * Returns the first element that is associated with the specified name and attribute.
   *
   * @param element
   *          the expanded name of the element
   * @param attributeEName
   *          the expanded attribute name
   * @param attributeValue
   *          the attribute value
   * @return the first value
   */
  protected CatalogEntry getFirstValue(EName element, EName attributeEName, String attributeValue) {
    List<CatalogEntry> elements = data.get(element);
    if (elements != null) {
      for (CatalogEntry entry : elements) {
        String v = entry.getAttribute(attributeEName);
        if (equal(attributeValue, v))
          return entry;
      }
    }
    return null;
  }

  /**
   * Returns the first value that is associated with the specified name and language.
   *
   * @param element
   *          the expanded name of the element
   * @param language
   *          the language identifier or null to get only elements without <code>xml:lang</code> attribute
   * @return the first value
   */
  protected CatalogEntry getFirstLocalizedValue(EName element, String language) {
    return getFirstValue(element, XML_LANG_ATTR, language);
  }

  /**
   * Returns the first value that is associated with the specified name and language.
   *
   * @param element
   *          the expanded name of the element
   * @param type
   *          the <code>xsi:type</code> value
   * @return the element
   */
  protected CatalogEntry getFirstTypedValue(EName element, String type) {
    return getFirstValue(element, XSI_TYPE_ATTR, type);
  }

  /**
   * Tests two objects for equality.
   */
  protected boolean equal(Object a, Object b) {
    return (a == null && b == null) || (a != null && a.equals(b));
  }

  /**
   * Creates an xml document root and returns it.
   *
   * @return the document
   * @throws ParserConfigurationException
   *           If the xml parser environment is not correctly configured
   */
  protected Document newDocument() throws ParserConfigurationException {
    DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance();
    docBuilderFactory.setNamespaceAware(true);
    DocumentBuilder docBuilder = docBuilderFactory.newDocumentBuilder();
    return docBuilder.newDocument();
  }

  /**
   * Serializes the given xml document to the associated file. Please note that this method does <em>not</em> close the
   * output stream. Anyone using this method is responsible for doing it by itself.
   *
   * @param document
   *          the document
   * @param docType
   *          the document type definition (dtd)
   * @throws TransformerException
   *           if serialization fails
   */
  protected void saveToXml(Node document, String docType, OutputStream out) throws TransformerException, IOException {
    StreamResult streamResult = new StreamResult(out);
    TransformerFactory tf = TransformerFactory.newInstance();
    Transformer serializer = tf.newTransformer();
    serializer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
    if (docType != null)
      serializer.setOutputProperty(OutputKeys.DOCTYPE_SYSTEM, docType);
    serializer.setOutputProperty(OutputKeys.INDENT, "yes");
    serializer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
    serializer.transform(new DOMSource(document), streamResult);
    out.flush();
  }

  /**
   * @see org.opencastproject.mediapackage.AbstractMediaPackageElement#toManifest(org.w3c.dom.Document,
   *      org.opencastproject.mediapackage.MediaPackageSerializer)
   */
  @Override
  public Node toManifest(Document document, MediaPackageSerializer serializer) throws MediaPackageException {
    return super.toManifest(document, serializer);
  }

  /**
   * Get a prefix from {@link #bindings} but throw a {@link NamespaceBindingException} if none found.
   */
  @Nonnull
  protected String getPrefix(@Nonnull String namespaceURI) {
    final String prefix = bindings.getPrefix(namespaceURI);
    if (prefix != null) {
      return prefix;
    } else {
      throw new NamespaceBindingException(format("Namespace URI %s is not bound to a prefix", namespaceURI));
    }
  }

  /**
   * Transform an expanded name to a qualified name based on the registered binding.
   *
   * @param eName
   *          the expanded name to transform
   * @return the qualified name, e.g. <code>dcterms:title</code>
   * @throws NamespaceBindingException
   *           if the namespace name is not bound to a prefix
   */
  @Nonnull
  protected String toQName(EName eName) {
    if (eName.hasNamespace()) {
      return toQName(getPrefix(eName.getNamespaceURI()), eName.getLocalName());
    } else {
      return eName.getLocalName();
    }
  }

  /**
   * Transform an qualified name consisting of prefix and local part to an expanded name, based on the registered
   * binding.
   *
   * @param prefix
   *          the prefix
   * @param localName
   *          the local part
   * @return the expanded name
   * @throws NamespaceBindingException
   *           if the namespace name is not bound to a prefix
   */
  @Nonnull
  protected EName toEName(String prefix, String localName) {
    return new EName(bindings.getNamespaceURI(prefix), localName);
  }

  /**
   * Transform a qualified name to an expanded name, based on the registered binding.
   *
   * @param qName
   *          the qualified name, e.g. <code>dcterms:title</code> or <code>title</code>
   * @return the expanded name
   * @throws NamespaceBindingException
   *           if the namespace name is not bound to a prefix
   */
  protected EName toEName(String qName) {
    String[] parts = splitQName(qName);
    return new EName(bindings.getNamespaceURI(parts[0]), parts[1]);
  }

  /**
   * Splits a QName into its parts.
   *
   * @param qName
   *          the qname to split
   * @return an array of prefix (0) and local part (1). The prefix is "" if the qname belongs to the default namespace.
   */
  private static String[] splitQName(String qName) {
    final String[] parts = qName.split(":", 3);
    switch (parts.length) {
      case 1: return new String[] { DEFAULT_NS_PREFIX, parts[0] };
      case 2: return parts;
      default:
        throw new IllegalArgumentException("Local name must not contain ':'");
    }
  }

  /**
   * Returns a "prefixed name" consisting of namespace prefix and local name.
   *
   * @param prefix
   *          the namespace prefix, may be <code>null</code>
   * @param localName
   *          the local name
   * @return the "prefixed name" <code>prefix:localName</code>
   */
  private static String toQName(String prefix, String localName) {
    final StringBuilder b = new StringBuilder();
    if (prefix != null && !DEFAULT_NS_PREFIX.equals(prefix)) {
      b.append(prefix);
      b.append(":");
    }
    b.append(localName);
    return b.toString();
  }

  // --------------------------------------------------------------------------------------------

  private static final Map<EName, String> NO_ATTRIBUTES = new HashMap<>();

  /**
   * Element representation.
   */
  public final class CatalogEntry implements XmlElement, Comparable<CatalogEntry>, Serializable {

    /**
     * The serial version UID
     */
    private static final long serialVersionUID = 7195298081966562710L;

    private final EName name;

    private final String value;

    /**
     * The attributes of this element
     */
    private final Map<EName, String> attributes;

    /**
     * Creates a new catalog element representation with name, value and attributes.
     *
     * @param value
     *          the element value
     * @param attributes
     *          the element attributes
     */
    public CatalogEntry(EName name, String value, Map<EName, String> attributes) {
      this.name = name;
      this.value = value;
      this.attributes = attributes;
    }

    /**
     * Creates a new catalog element representation.
     *
     * @param value
     *          the element value
     */
    public CatalogEntry(EName name, String value) {
      this(name, value, NO_ATTRIBUTES);
    }

    /**
     * Returns the qualified name of the entry as a string. The namespace of the entry has to be bound to a prefix for
     * this method to succeed.
     */
    public String getQName() {
      return toQName(name);
    }

    /**
     * Returns the expanded name of the entry.
     */
    public EName getEName() {
      return name;
    }

    /**
     * Returns the element value.
     *
     * @return the value
     */
    public String getValue() {
      return value;
    }

    /**
     * Returns <code>true</code> if the element contains attributes.
     *
     * @return <code>true</code> if the element contains attributes
     */
    public boolean hasAttributes() {
      return attributes.size() > 0;
    }

    /**
     * Returns the element's attributes.
     *
     * @return the attributes
     */
    public Map<EName, String> getAttributes() {
      return new ImmutableMapWrapper<>(attributes);
    }

    /**
     * Returns <code>true</code> if the element contains an attribute with the given name.
     *
     * @return <code>true</code> if the element contains the attribute
     */
    public boolean hasAttribute(EName name) {
      return attributes.containsKey(name);
    }

    /**
     * Returns the attribute value for the given attribute.
     *
     * @return the attribute or null
     */
    public String getAttribute(EName name) {
      return attributes.get(name);
    }

    @Override
    public int hashCode() {
      return hash(name, value);
    }

    @Override public boolean equals(Object that) {
      return (this == that) || (that instanceof CatalogEntry && eqFields((CatalogEntry) that));
    }

    private boolean eqFields(CatalogEntry that) {
      return eq(name, that.name) && eq(value, that.value) && eq(attributes, that.attributes);
    }

    /**
     * Returns the XML representation of this entry.
     *
     * @param document
     *          the document
     * @return the xml node
     */
    @Override
    public Node toXml(Document document) {
      Element node = document.createElement(toQName(name));
      // Write prefix binding to document root element
      bindNamespaceFor(document, name);
      for (Map.Entry<EName, String> entry : attributes.entrySet()) {
        EName attrEName = entry.getKey();
        if (attrEName.hasNamespace()) {
          // Write prefix binding to document root element
          bindNamespaceFor(document, attrEName);
          if (XSI_TYPE_ATTR.equals(attrEName)) {
            // Special treatment for xsi:type attributes
            try {
              EName typeName = toEName(entry.getValue());
              bindNamespaceFor(document, typeName);
            } catch (NamespaceBindingException ignore) {
              // Type is either not a QName or its namespace is not bound.
              // We decide to gently ignore those cases.
            }
          }
        }
        node.setAttribute(toQName(entry.getKey()), entry.getValue());
      }
      if (value != null) {
        node.appendChild(document.createTextNode(value));
      }
      return node;
    }

    @Override
    public int compareTo(@Nonnull CatalogEntry o) {
      return name.getLocalName().compareTo(name.getLocalName());
    }

    /**
     * Writes a namespace binding for catalog entry <code>name</code> to the documents root element.
     * <code>xmlns:prefix="namespace"</code>
     */
    private void bindNamespaceFor(Document document, EName name) {
      Element root = (Element) document.getFirstChild();
      String namespace = name.getNamespaceURI();
      // Do not bind the "xml" namespace. It is bound by default
      if (!XML_NS_URI.equals(namespace)) {
        root.setAttribute(XMLNS_ATTRIBUTE + ":" + XMLCatalogImpl.this.getPrefix(name.getNamespaceURI()),
                name.getNamespaceURI());
      }
    }

    @Override
    public String toString() {
      return value;
    }
  }

  // --------------------------------------------------------------------------------------------

  // --

  /**
   * {@inheritDoc}
   *
   * @see org.opencastproject.mediapackage.XMLCatalog#toXml(java.io.OutputStream, boolean)
   */
  @Override
  public void toXml(OutputStream out, boolean format) throws IOException {
    try {
      Document doc = this.toXml();
      DOMSource domSource = new DOMSource(doc);
      StreamResult result = new StreamResult(out);
      Transformer transformer = TransformerFactory.newInstance().newTransformer();
      transformer.transform(domSource, result);
    } catch (ParserConfigurationException e) {
      throw new IOException("unable to parse document");
    } catch (TransformerException e) {
      throw new IOException("unable to transform dom to a stream");
    }
  }

  /**
   * {@inheritDoc}
   *
   * @see org.opencastproject.mediapackage.XMLCatalog#toXmlString()
   */
  @Override
  public String toXmlString() throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    toXml(out, true);
    return new String(out.toByteArray(), "UTF-8");
  }

}
