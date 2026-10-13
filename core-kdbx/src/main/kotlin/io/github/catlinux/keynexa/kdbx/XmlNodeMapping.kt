package io.github.catlinux.keynexa.kdbx

import io.github.catlinux.keynexa.vault.XmlElement
import org.redundent.kotlin.xml.Node
import org.redundent.kotlin.xml.TextElement
import org.redundent.kotlin.xml.node

/** Converts an XML element the KDBX library did not understand into the domain's own minimal element. */
internal fun Node.toXmlElement(): XmlElement =
    XmlElement(
        name = nodeName,
        attributes = attributes.mapValues { (_, value) -> value?.toString().orEmpty() },
        text = children.filterIsInstance<TextElement>().firstOrNull()?.text,
        children = children.filterIsInstance<Node>().map(Node::toXmlElement),
    )

/** Converts a domain element back into an XML node, so it can be written again unchanged. */
internal fun XmlElement.toNode(): Node {
    val element = node(name)
    for ((key, value) in attributes) {
        element.attribute(key, value)
    }
    this.text?.let { element.text(it) }
    for (child in children) {
        element.addElement(child.toNode())
    }
    return element
}
