package com.termux.shared.markdown;

import android.content.Context;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.text.style.QuoteSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.text.style.URLSpan;
import android.text.util.Linkify;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.termux.shared.R;
import com.termux.shared.theme.ThemeUtils;

import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.BlockQuote;
import org.commonmark.node.BulletList;
import org.commonmark.node.Code;
import org.commonmark.node.Emphasis;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.Heading;
import org.commonmark.node.IndentedCodeBlock;
import org.commonmark.node.Link;
import org.commonmark.node.ListItem;
import org.commonmark.node.Node;
import org.commonmark.node.OrderedList;
import org.commonmark.node.Paragraph;
import org.commonmark.node.HardLineBreak;
import org.commonmark.node.SoftLineBreak;
import org.commonmark.node.StrongEmphasis;
import org.commonmark.node.Text;
import org.commonmark.parser.Parser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Renders the small subset of CommonMark actually used by this app: headings (H1-H3), bold,
 * italic, inline code, fenced/indented code blocks, links and (defensively) block quotes and
 * lists. Backed directly by {@code org.commonmark:commonmark} (the actively maintained
 * community project, published under the same {@code org.commonmark.*} package names used here)
 * rather than Markwon, whose upstream repository was archived in 2022.
 * <p/>
 * Two rendering entry points are provided, matching the app's two real consumers:
 * <ul>
 *     <li>{@link #getSpannedMarkdownText(Context, String)} - a single {@link Spanned} for
 *     notification bodies (used by {@code TermuxPluginUtils}/{@code TermuxCrashUtils}).</li>
 *     <li>{@link #parseMarkdownBlocks(Context, String)} - a list of {@link MarkdownBlock}s split
 *     at fenced-code-block boundaries, for {@code ReportActivity}'s
 *     {@link com.termux.shared.markdown.MarkdownBlockAdapter}-backed {@code RecyclerView}, which
 *     needs a distinct horizontally-scrollable row for code blocks.</li>
 * </ul>
 */
public class MarkdownUtils {

    public static final String backtick = "`";
    public static final Pattern backticksPattern = Pattern.compile("(" + backtick + "+)");

    /**
     * Get the markdown code {@link String} for a {@link String}. This ensures all backticks "`" are
     * properly escaped so that markdown does not break.
     *
     * @param string The {@link String} to convert.
     * @param codeBlock If the {@link String} is to be converted to a code block or inline code.
     * @return Returns the markdown code {@link String}.
     */
    public static String getMarkdownCodeForString(String string, boolean codeBlock) {
        if (string == null) return null;
        if (string.isEmpty()) return "";

        int maxConsecutiveBackTicksCount = getMaxConsecutiveBackTicksCount(string);

        // markdown requires surrounding backticks count to be at least one more than the count
        // of consecutive ticks in the string itself
        int backticksCountToUse;
        if (codeBlock)
            backticksCountToUse = maxConsecutiveBackTicksCount + 3;
        else
            backticksCountToUse = maxConsecutiveBackTicksCount + 1;

        // create a string with n backticks where n==backticksCountToUse
        String backticksToUse = String.join("", Collections.nCopies(backticksCountToUse, backtick));

        if (codeBlock)
            return backticksToUse + "\n" + string + "\n" + backticksToUse;
        else {
            // add a space to any prefixed or suffixed backtick characters
            if (string.startsWith(backtick))
                string = " " + string;
            if (string.endsWith(backtick))
                string = string + " ";

            return backticksToUse + string + backticksToUse;
        }
    }

    /**
     * Get the max consecutive backticks "`" in a {@link String}.
     *
     * @param string The {@link String} to check.
     * @return Returns the max consecutive backticks count.
     */
    public static int getMaxConsecutiveBackTicksCount(String string) {
        if (string == null || string.isEmpty()) return 0;

        int maxCount = 0;
        int matchCount;
        String match;

        Matcher matcher = backticksPattern.matcher(string);
        while(matcher.find()) {
            match = matcher.group(1);
            matchCount = match != null ? match.length() : 0;
            if (matchCount > maxCount)
                maxCount = matchCount;
        }

        return maxCount;
    }



    public static String getLiteralSingleLineMarkdownStringEntry(String label, Object object, String def) {
        return "**" + label + "**: " + (object != null ? object.toString() : def) +  "  ";
    }

    public static String getSingleLineMarkdownStringEntry(String label, Object object, String def) {
        if (object != null)
            return "**" + label + "**: " + getMarkdownCodeForString(object.toString(), false) +  "  ";
        else
            return "**" + label + "**: " + def +  "  ";
    }

    public static String getMultiLineMarkdownStringEntry(String label, Object object, String def) {
        if (object != null)
            return "**" + label + "**:\n" + getMarkdownCodeForString(object.toString(), true) + "\n";
        else
            return "**" + label + "**: " + def + "\n";
    }

    public static String getLinkMarkdownString(String label, String url) {
        if (url != null)
            return "[" + label.replaceAll("]", "\\\\]") + "](" + url.replaceAll("\\)", "\\\\)") +  ")";
        else
            return label;
    }



    /**
     * A single renderable block of a report, split at fenced-code-block boundaries so that a
     * {@code RecyclerView} can give code blocks their own horizontally-scrollable row. See
     * {@link #parseMarkdownBlocks(Context, String)}.
     */
    public static final class MarkdownBlock {
        public enum Type { TEXT, CODE_BLOCK }

        public final Type type;
        public final CharSequence content;

        MarkdownBlock(Type type, CharSequence content) {
            this.type = type;
            this.content = content;
        }
    }

    /**
     * Parse {@code markdown} into a {@link Spanned} suitable for a single {@code TextView}, e.g.
     * a notification body. Bare URLs/emails are auto-linked via {@link Linkify}, matching the
     * behaviour relied on by at least one existing string resource (see {@code msg_report_issue}).
     *
     * @param context The context, used to resolve theme colors for inline code spans.
     * @param string The markdown {@link String} to render.
     * @return Returns the rendered {@link Spanned}, or {@code null} if either argument is {@code null}.
     */
    @Nullable
    public static Spanned getSpannedMarkdownText(Context context, String string) {
        if (context == null || string == null) return null;

        Node document = Parser.builder().build().parse(string);
        SpannableRenderer renderer = new SpannableRenderer(context);
        document.accept(renderer);
        SpannableStringBuilder result = renderer.build();

        // Linkify.addLinks() removes any pre-existing URLSpans before re-scanning (so that
        // calling it repeatedly on the same Spannable stays idempotent). It must therefore run
        // BEFORE the explicit [label](url) link spans are applied, or they would be wiped out.
        Linkify.addLinks(result, Linkify.WEB_URLS | Linkify.EMAIL_ADDRESSES);
        renderer.applyExplicitLinkSpans();

        return result;
    }

    /**
     * Parse {@code markdown} into a list of {@link MarkdownBlock}s for {@code ReportActivity}'s
     * {@link MarkdownBlockAdapter}. Consecutive non-code top-level blocks (headings, paragraphs,
     * lists, etc.) are batched into a single {@code TEXT} block; each fenced code block becomes
     * its own {@code CODE_BLOCK} entry. This mirrors the row-splitting behaviour of the original
     * {@code MarkwonAdapter.builderTextViewIsRoot(default).include(FencedCodeBlock.class, ...)}
     * setup exactly.
     *
     * @param context The context, used to resolve theme colors for inline code spans.
     * @param markdown The markdown {@link String} to render.
     * @return Returns the list of blocks, empty if either argument is {@code null}.
     */
    @NonNull
    public static List<MarkdownBlock> parseMarkdownBlocks(Context context, String markdown) {
        List<MarkdownBlock> blocks = new ArrayList<>();
        if (context == null || markdown == null) return blocks;

        Node document = Parser.builder().build().parse(markdown);

        SpannableRenderer batch = null;
        Node child = document.getFirstChild();
        while (child != null) {
            if (child instanceof FencedCodeBlock) {
                if (batch != null) {
                    blocks.add(new MarkdownBlock(MarkdownBlock.Type.TEXT, finishTextBatch(batch)));
                    batch = null;
                }
                String literal = ((FencedCodeBlock) child).getLiteral();
                blocks.add(new MarkdownBlock(MarkdownBlock.Type.CODE_BLOCK, literal == null ? "" : literal.trim()));
            } else {
                if (batch == null) batch = new SpannableRenderer(context);
                child.accept(batch);
            }
            child = child.getNext();
        }
        if (batch != null) {
            blocks.add(new MarkdownBlock(MarkdownBlock.Type.TEXT, finishTextBatch(batch)));
        }

        return blocks;
    }

    /**
     * Finalizes one batched {@link SpannableRenderer}: applies auto-linkification (matching the
     * original recycler renderer's LinkifyPlugin) and then re-applies any explicit
     * {@code [label](url)} link spans on top, since {@link Linkify#addLinks} removes pre-existing
     * {@link URLSpan}s before re-scanning.
     */
    private static SpannableStringBuilder finishTextBatch(SpannableRenderer batch) {
        SpannableStringBuilder result = batch.build();
        Linkify.addLinks(result, Linkify.WEB_URLS | Linkify.EMAIL_ADDRESSES);
        batch.applyExplicitLinkSpans();
        return result;
    }

    /**
     * Walks a CommonMark AST (or subtree) and appends the rendered result to an internal
     * {@link SpannableStringBuilder}. One top-level block is rendered per call to
     * {@link Node#accept(org.commonmark.node.Visitor)}; call {@link #build()} once all desired
     * top-level nodes have been visited to get the trimmed result.
     */
    private static final class SpannableRenderer extends AbstractVisitor {

        private final Context context;
        private final SpannableStringBuilder sb = new SpannableStringBuilder();
        private final List<int[]> pendingLinkRanges = new ArrayList<>();
        private final List<String> pendingLinkDestinations = new ArrayList<>();

        SpannableRenderer(Context context) {
            this.context = context;
        }

        SpannableStringBuilder build() {
            while (sb.length() > 0 && sb.charAt(sb.length() - 1) == '\n') {
                sb.delete(sb.length() - 1, sb.length());
            }
            return sb;
        }

        /**
         * Applies the {@code [label](url)} link spans collected while visiting, deferred until
         * after {@link Linkify#addLinks} has run (see {@link #visit(Link)}).
         */
        void applyExplicitLinkSpans() {
            int max = sb.length();
            for (int i = 0; i < pendingLinkRanges.size(); i++) {
                int[] range = pendingLinkRanges.get(i);
                int start = Math.min(range[0], max);
                int end = Math.min(range[1], max);
                if (start < end) {
                    sb.setSpan(new URLSpan(pendingLinkDestinations.get(i)), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            }
        }

        private void appendBlockSeparator() {
            sb.append("\n\n");
        }

        @Override
        public void visit(Paragraph paragraph) {
            visitChildren(paragraph);
            appendBlockSeparator();
        }

        @Override
        public void visit(Heading heading) {
            int start = sb.length();
            visitChildren(heading);
            int end = sb.length();
            sb.setSpan(new StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            float sizeMultiplier = heading.getLevel() == 1 ? 1.3f : heading.getLevel() == 2 ? 1.15f : 1.05f;
            sb.setSpan(new RelativeSizeSpan(sizeMultiplier), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            appendBlockSeparator();
        }

        @Override
        public void visit(StrongEmphasis strongEmphasis) {
            int start = sb.length();
            visitChildren(strongEmphasis);
            sb.setSpan(new StyleSpan(Typeface.BOLD), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        @Override
        public void visit(Emphasis emphasis) {
            int start = sb.length();
            visitChildren(emphasis);
            sb.setSpan(new StyleSpan(Typeface.ITALIC), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        @Override
        public void visit(Code code) {
            int start = sb.length();
            sb.append(code.getLiteral());
            applyInlineCodeSpans(start, sb.length());
        }

        @Override
        public void visit(FencedCodeBlock fencedCodeBlock) {
            appendCodeBlock(fencedCodeBlock.getLiteral());
        }

        @Override
        public void visit(IndentedCodeBlock indentedCodeBlock) {
            appendCodeBlock(indentedCodeBlock.getLiteral());
        }

        private void appendCodeBlock(String literal) {
            int start = sb.length();
            sb.append(literal == null ? "" : literal.trim());
            sb.setSpan(new TypefaceSpan("monospace"), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            appendBlockSeparator();
        }

        private void applyInlineCodeSpans(int start, int end) {
            // Do not change color for night themes, matching the original factory's behaviour.
            if (!ThemeUtils.isNightModeEnabled(context)) {
                sb.setSpan(new BackgroundColorSpan(ContextCompat.getColor(context, R.color.background_markdown_code_inline)),
                    start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            sb.setSpan(new TypefaceSpan("monospace"), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        @Override
        public void visit(Link link) {
            int start = sb.length();
            visitChildren(link);
            String destination = link.getDestination();
            if (destination != null) {
                // Deferred: see applyExplicitLinkSpans().
                pendingLinkRanges.add(new int[]{start, sb.length()});
                pendingLinkDestinations.add(destination);
            }
        }

        @Override
        public void visit(BlockQuote blockQuote) {
            int start = sb.length();
            visitChildren(blockQuote);
            sb.setSpan(new QuoteSpan(), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            appendBlockSeparator();
        }

        @Override
        public void visit(BulletList bulletList) {
            visitChildren(bulletList);
            appendBlockSeparator();
        }

        @Override
        public void visit(OrderedList orderedList) {
            visitChildren(orderedList);
            appendBlockSeparator();
        }

        @Override
        public void visit(ListItem listItem) {
            sb.append("\u2022 ");
            visitChildren(listItem);
        }

        @Override
        public void visit(Text text) {
            sb.append(text.getLiteral());
        }

        @Override
        public void visit(SoftLineBreak softLineBreak) {
            sb.append("\n");
        }

        @Override
        public void visit(HardLineBreak hardLineBreak) {
            sb.append("\n");
        }
    }

}
