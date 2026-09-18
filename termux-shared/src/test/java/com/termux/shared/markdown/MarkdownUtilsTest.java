package com.termux.shared.markdown;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.text.Spanned;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.text.style.URLSpan;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.List;

/**
 * Unit tests for {@link MarkdownUtils#getSpannedMarkdownText(android.content.Context, String)}
 * and {@link MarkdownUtils#parseMarkdownBlocks(android.content.Context, String)}, the
 * commonmark-java-backed replacement for the abandoned Markwon library (beads-47b).
 */
@RunWith(RobolectricTestRunner.class)
public class MarkdownUtilsTest {

    // -----------------------------------------------------------------------
    // getMarkdownCodeForString / getLinkMarkdownString (unchanged string builders, sanity check)
    // -----------------------------------------------------------------------

    @Test
    public void getMarkdownCodeForString_wrapsInlineCodeInBackticks() {
        assertEquals("`hello`", MarkdownUtils.getMarkdownCodeForString("hello", false));
    }

    @Test
    public void getLinkMarkdownString_producesMarkdownLinkSyntax() {
        assertEquals("[label](http://example.com)",
            MarkdownUtils.getLinkMarkdownString("label", "http://example.com"));
    }

    // -----------------------------------------------------------------------
    // getSpannedMarkdownText
    // -----------------------------------------------------------------------

    @Test
    public void getSpannedMarkdownText_returnsNullForNullArgs() {
        assertNull(MarkdownUtils.getSpannedMarkdownText(null, "text"));
        assertNull(MarkdownUtils.getSpannedMarkdownText(RuntimeEnvironment.getApplication(), null));
    }

    @Test
    public void getSpannedMarkdownText_boldTextGetsStyleSpan() {
        Spanned spanned = MarkdownUtils.getSpannedMarkdownText(RuntimeEnvironment.getApplication(), "**bold**");
        assertEquals("bold", spanned.toString());
        StyleSpan[] spans = spanned.getSpans(0, spanned.length(), StyleSpan.class);
        assertEquals(1, spans.length);
        assertEquals(android.graphics.Typeface.BOLD, spans[0].getStyle());
    }

    @Test
    public void getSpannedMarkdownText_italicTextGetsStyleSpan() {
        Spanned spanned = MarkdownUtils.getSpannedMarkdownText(RuntimeEnvironment.getApplication(), "*italic*");
        assertEquals("italic", spanned.toString());
        StyleSpan[] spans = spanned.getSpans(0, spanned.length(), StyleSpan.class);
        assertEquals(1, spans.length);
        assertEquals(android.graphics.Typeface.ITALIC, spans[0].getStyle());
    }

    @Test
    public void getSpannedMarkdownText_inlineCodeGetsMonospaceSpan() {
        Spanned spanned = MarkdownUtils.getSpannedMarkdownText(RuntimeEnvironment.getApplication(), "`code`");
        assertEquals("code", spanned.toString());
        TypefaceSpan[] spans = spanned.getSpans(0, spanned.length(), TypefaceSpan.class);
        assertEquals(1, spans.length);
        assertEquals("monospace", spans[0].getFamily());
    }

    @Test
    public void getSpannedMarkdownText_fencedCodeBlockPreservesLiteralContent() {
        String markdown = "```\nline1\nline2\n```";
        Spanned spanned = MarkdownUtils.getSpannedMarkdownText(RuntimeEnvironment.getApplication(), markdown);
        assertEquals("line1\nline2", spanned.toString());
    }

    @Test
    public void getSpannedMarkdownText_explicitLinkGetsUrlSpan() {
        Spanned spanned = MarkdownUtils.getSpannedMarkdownText(RuntimeEnvironment.getApplication(), "[label](http://example.com)");
        assertEquals("label", spanned.toString());
        URLSpan[] spans = spanned.getSpans(0, spanned.length(), URLSpan.class);
        assertEquals(1, spans.length);
        assertEquals("http://example.com", spans[0].getURL());
    }

    @Test
    public void getSpannedMarkdownText_bareUrlIsAutoLinkified() {
        // Replicates the behaviour relied on by R.string.msg_report_issue, which embeds a bare
        // URL fragment with no explicit [label](url) syntax.
        Spanned spanned = MarkdownUtils.getSpannedMarkdownText(RuntimeEnvironment.getApplication(),
            "Check http://example.com/wiki for details.");
        URLSpan[] spans = spanned.getSpans(0, spanned.length(), URLSpan.class);
        assertEquals(1, spans.length);
        assertEquals("http://example.com/wiki", spans[0].getURL());
    }

    @Test
    public void getSpannedMarkdownText_headingIsBold() {
        Spanned spanned = MarkdownUtils.getSpannedMarkdownText(RuntimeEnvironment.getApplication(), "## Heading");
        assertEquals("Heading", spanned.toString());
        StyleSpan[] spans = spanned.getSpans(0, spanned.length(), StyleSpan.class);
        assertEquals(1, spans.length);
        assertEquals(android.graphics.Typeface.BOLD, spans[0].getStyle());
    }

    @Test
    public void getSpannedMarkdownText_hardLineBreakProducesNewline() {
        // Mirrors the "**Label**: value  \n" pattern used throughout the app (trailing double
        // space forces a CommonMark hard line break).
        Spanned spanned = MarkdownUtils.getSpannedMarkdownText(RuntimeEnvironment.getApplication(),
            "**Label**: value  \nNext line");
        assertTrue(spanned.toString().contains("value\nNext line"));
    }

    @Test
    public void getSpannedMarkdownText_trimsTrailingBlankLines() {
        Spanned spanned = MarkdownUtils.getSpannedMarkdownText(RuntimeEnvironment.getApplication(), "text\n\n\n");
        assertEquals("text", spanned.toString());
    }

    @Test
    public void getSpannedMarkdownText_multipleParagraphsSeparatedByBlankLine() {
        Spanned spanned = MarkdownUtils.getSpannedMarkdownText(RuntimeEnvironment.getApplication(),
            "paragraph one\n\nparagraph two");
        assertEquals("paragraph one\n\nparagraph two", spanned.toString());
    }

    // -----------------------------------------------------------------------
    // parseMarkdownBlocks
    // -----------------------------------------------------------------------

    @Test
    public void parseMarkdownBlocks_emptyForNullArgs() {
        assertTrue(MarkdownUtils.parseMarkdownBlocks(null, "text").isEmpty());
        assertTrue(MarkdownUtils.parseMarkdownBlocks(RuntimeEnvironment.getApplication(), null).isEmpty());
    }

    @Test
    public void parseMarkdownBlocks_singleTextBlockForPlainMarkdown() {
        List<MarkdownUtils.MarkdownBlock> blocks = MarkdownUtils.parseMarkdownBlocks(
            RuntimeEnvironment.getApplication(), "## Title\n\nSome **bold** text.");

        assertEquals(1, blocks.size());
        assertEquals(MarkdownUtils.MarkdownBlock.Type.TEXT, blocks.get(0).type);
        assertTrue(blocks.get(0).content.toString().contains("Title"));
        assertTrue(blocks.get(0).content.toString().contains("Some bold text."));
    }

    @Test
    public void parseMarkdownBlocks_splitsFencedCodeBlockIntoOwnEntry() {
        String markdown = "Some text before.\n\n```\ncode line\n```\n\nSome text after.";
        List<MarkdownUtils.MarkdownBlock> blocks = MarkdownUtils.parseMarkdownBlocks(
            RuntimeEnvironment.getApplication(), markdown);

        assertEquals(3, blocks.size());
        assertEquals(MarkdownUtils.MarkdownBlock.Type.TEXT, blocks.get(0).type);
        assertEquals("Some text before.", blocks.get(0).content.toString());
        assertEquals(MarkdownUtils.MarkdownBlock.Type.CODE_BLOCK, blocks.get(1).type);
        assertEquals("code line", blocks.get(1).content.toString());
        assertEquals(MarkdownUtils.MarkdownBlock.Type.TEXT, blocks.get(2).type);
        assertEquals("Some text after.", blocks.get(2).content.toString());
    }

    @Test
    public void parseMarkdownBlocks_multipleFencedCodeBlocksEachGetOwnEntry() {
        String markdown = "```\nfirst\n```\n\n```\nsecond\n```";
        List<MarkdownUtils.MarkdownBlock> blocks = MarkdownUtils.parseMarkdownBlocks(
            RuntimeEnvironment.getApplication(), markdown);

        assertEquals(2, blocks.size());
        assertEquals(MarkdownUtils.MarkdownBlock.Type.CODE_BLOCK, blocks.get(0).type);
        assertEquals("first", blocks.get(0).content.toString());
        assertEquals(MarkdownUtils.MarkdownBlock.Type.CODE_BLOCK, blocks.get(1).type);
        assertEquals("second", blocks.get(1).content.toString());
    }

    @Test
    public void parseMarkdownBlocks_codeBlockContentIsNotLinkified() {
        // Bare URLs inside a fenced code block are literal code, not auto-linked text.
        String markdown = "```\nhttp://example.com\n```";
        List<MarkdownUtils.MarkdownBlock> blocks = MarkdownUtils.parseMarkdownBlocks(
            RuntimeEnvironment.getApplication(), markdown);

        assertEquals(1, blocks.size());
        assertEquals(MarkdownUtils.MarkdownBlock.Type.CODE_BLOCK, blocks.get(0).type);
        assertFalse(blocks.get(0).content instanceof Spanned);
    }

}
