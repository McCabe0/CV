package com.skill2career.adapter.jobboard

import org.jsoup.Jsoup
import org.jsoup.nodes.TextNode
import org.jsoup.select.NodeTraversor
import org.jsoup.select.NodeVisitor

/** A board's html description as plain text: its words, a line for each paragraph, heading and list item. */
internal object PlainText {
    private const val BLOCKS =
        "p, div, li, h1, h2, h3, h4, h5, h6, tr, ul, ol, section, article, header, footer, blockquote, pre, table"
    private val spaces = Regex("[\\s\\u00A0]+")

    fun of(html: String): String {
        val body = Jsoup.parse(html).body()
        body.select("script, style").remove()
        // As a browser does: a run of whitespace in the html is one space, and only the tags break lines.
        NodeTraversor.traverse(NodeVisitor { node, _ -> if (node is TextNode) node.text(node.wholeText.replace(spaces, " ")) }, body)
        body.select("br").forEach { it.before(TextNode("\n")) }
        body.select(BLOCKS).forEach {
            it.before(TextNode("\n"))
            it.after(TextNode("\n"))
        }
        return tidy(body.wholeText())
    }

    /** Each line's spaces collapsed and trimmed, and blank lines dropped. For text that is already plain. */
    fun tidy(text: String): String =
        text
            .lines()
            .map { it.replace(spaces, " ").trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")
}
