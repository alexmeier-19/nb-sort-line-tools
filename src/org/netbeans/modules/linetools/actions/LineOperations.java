/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 1997-2007 Sun Microsystems, Inc. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common
 * Development and Distribution License("CDDL") (collectively, the
 * "License"). You may not use this file except in compliance with the
 * License. You can obtain a copy of the License at
 * http://www.netbeans.org/cddl-gplv2.html
 * or nbbuild/licenses/CDDL-GPL-2-CP. See the License for the
 * specific language governing permissions and limitations under the
 * License.  When distributing the software, include this License Header
 * Notice in each file and include the License file at
 * nbbuild/licenses/CDDL-GPL-2-CP.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the GPL Version 2 section of the License file that
 * accompanied this code. If applicable, add the following below the
 * License Header, with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 *
 * Contributor(s):
 *
 * The Original Software is NetBeans. The Initial Developer of the Original
 * Software is Sun Microsystems, Inc. Portions Copyright 1997-2006 Sun
 * Microsystems, Inc. All Rights Reserved.
 *
 * If you wish your version of this file to be governed by only the CDDL
 * or only the GPL Version 2, indicate your decision by adding
 * "[Contributor] elects to include this software in this distribution
 * under the [CDDL or GPL Version 2] license." If you do not indicate a
 * single choice of license, a recipient has the option to distribute
 * your version of this file under either the CDDL, the GPL Version 2 or
 * to extend the choice of license to its licensees as provided above.
 * However, if you add GPL Version 2 code and therefore, elected the GPL
 * Version 2 license, then the option applies only if the new code is
 * made subject to such option by the copyright holder.
 */
package org.netbeans.modules.linetools.actions;

import java.awt.Toolkit;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.Caret;
import javax.swing.text.Document;
import javax.swing.text.Element;
import javax.swing.text.JTextComponent;
import org.netbeans.api.annotations.common.CheckForNull;
import org.netbeans.editor.BaseDocument;
import org.netbeans.modules.csl.api.OffsetRange;
import org.openide.DialogDisplayer;
import org.openide.ErrorManager;
import org.openide.NotifyDescriptor;
import org.openide.windows.IOProvider;
import org.openide.windows.InputOutput;

/**o
 *
 * @author Sandip V. Chitale (Sandip.Chitale@Sun.Com)
 * @author markiewb@netbeans.org (applied fixes)
 */
public final class LineOperations {

    public static final String FILE_SEPARATORS = "/\\"; // NOI18N
    private static final String DOT = "."; // NOI18N
    private static final String DASH = "-"; // NOI18N
    public static final String FILE_SEPARATOR_DOT = File.separatorChar + DOT;
    public static final String FILE_SEPARATOR_DOT_DASH = FILE_SEPARATOR_DOT + DASH;
    public static final String FILE_SEPARATORS_DOT_DASH = FILE_SEPARATORS + DOT + DASH;
    private static final Comparator<String> STRING_COMPARATOR = new CustomComparator(Locale.ENGLISH);
    private static final Comparator<String> REVERSE_STRING_COMPARATOR = Collections.reverseOrder(new CustomComparator(Locale.ENGLISH));
    private static final Comparator<String> STRING_COMPARATOR_CASE_INSENSITIVE = new CustomComparator(Locale.ENGLISH, false);
    private static final Comparator<String> REVERSE_STRING_COMPARATOR_CASE_INSENSITIVE = Collections.reverseOrder(new CustomComparator(Locale.ENGLISH, false));

    private static volatile boolean removeDuplicateLines;
    private static volatile boolean matchCase = true;

    private LineOperations() {
    }

    static void exchangeDotAndMark(JTextComponent textComponent) {
        Caret caret = textComponent.getCaret();
        // check if there is a selection
        if (caret.isSelectionVisible()) {
            int selStart = caret.getDot();
            int selEnd = caret.getMark();
            caret.setDot(selStart);
            caret.moveDot(selEnd);
        }
    }

    static final void sortLinesAscending(JTextComponent textComponent) {
        sortLines(textComponent);
    }

    static final void sortLinesDescending(JTextComponent textComponent) {
        sortLines(textComponent, true);
    }

    static final void sortLines(JTextComponent textComponent) {
        sortLines(textComponent, false);
    }

    static final void sortLines(final JTextComponent textComponent, final boolean descending) {
        if (!textComponent.isEditable() || !textComponent.getCaret().isSelectionVisible()) {
            beep();
            return;
        }
        runModificationTaskOnDocument(textComponent.getDocument(), new SortLinesTask(textComponent, descending, matchCase));
    }

    private static void runModificationTaskOnDocument(Document doc, Runnable runnable) {
        if (doc instanceof BaseDocument) {
            ((BaseDocument) doc).runAtomic(runnable);
        } else {
            runnable.run();
        }
    }

    /**
     * Getter for property removeDuplicateLines.
     *
     * @return Value of property removeDuplicateLines.
     */
    static boolean isRemoveDuplicateLines() {
        return removeDuplicateLines;
    }

    /**
     * Setter for property removeDuplicateLines.
     *
     * @param removeDuplicateLines New value of property removeDuplicateLines.
     */
    static void setRemoveDuplicateLines(boolean removeDuplicateLines) {
        LineOperations.removeDuplicateLines = removeDuplicateLines;
    }

    /**
     * Return wheather the sorting shoul be done in a case sensitive fashion.
     *
     * @return
     */
    public static boolean isMatchCase() {
        return matchCase;
    }

    /**
     * Set wheather the sorting shoul be done in a case sensitive fashion.
     *
     * @param matchCase
     */
    public static void setMatchCase(boolean matchCase) {
        LineOperations.matchCase = matchCase;
    }

    static void filter(final JTextComponent textComponent) {
        if (textComponent.isEditable() && textComponent.getCaret().isSelectionVisible()) {

            Runnable runnable = new Runnable() {
                @Override
                public void run() {
                    Document doc = textComponent.getDocument();
                    Caret caret = textComponent.getCaret();
                    Element rootElement = doc.getDefaultRootElement();

                    int selStart = caret.getDot();
                    int selEnd = caret.getMark();
                    int start = Math.min(selStart, selEnd);
                    int end = Math.max(selStart, selEnd) - 1;

                    int zeroBaseStartLineNumber = rootElement.getElementIndex(start);
                    int zeroBaseEndLineNumber = rootElement.getElementIndex(end);

                    if (zeroBaseStartLineNumber == -1 || zeroBaseEndLineNumber == -1) {
                        // could not get line number or same line
                        beep();
                        return;
                    }

                    NotifyDescriptor.InputLine filterCommand = new NotifyDescriptor.InputLine("Enter Filter command:",
                            "Filter command", NotifyDescriptor.OK_CANCEL_OPTION, NotifyDescriptor.PLAIN_MESSAGE);

                    if (DialogDisplayer.getDefault().notify(filterCommand) == NotifyDescriptor.OK_OPTION) {
                        int startOffset = rootElement.getElement(zeroBaseStartLineNumber).getStartOffset();
                        int endOffset = rootElement.getElement(zeroBaseEndLineNumber).getEndOffset();

                        try {
                            int numberOfLines = zeroBaseEndLineNumber - zeroBaseStartLineNumber + 1;
                            String[] linesText = new String[numberOfLines];
                            for (int i = 0; i < numberOfLines; i++) {
                                // get line text
                                Element lineElement = rootElement.getElement(zeroBaseStartLineNumber + i);
                                int lineStartOffset = lineElement.getStartOffset();
                                int lineEndOffset = lineElement.getEndOffset();

                                linesText[i] = doc.getText(lineStartOffset, (lineEndOffset - lineStartOffset - 1));
                            }

                            try {
                                FilterProcess filterProcess = new FilterProcess(filterCommand.getInputText().split(" "));

                                try (PrintWriter in = filterProcess.exec()) {
                                    for (String line : linesText) {
                                        in.println(line);
                                    }
                                }
                                if (filterProcess.waitFor() == 0) {
                                    linesText = filterProcess.getStdOutOutput();
                                    if (linesText != null) {
                                        StringBuilder sb = new StringBuilder();
                                        for (String line : linesText) {
                                            sb.append(line).append("\n"); // NOI18N
                                        }

                                        // remove the lines
                                        doc.remove(startOffset, Math.min(doc.getLength(), endOffset) - startOffset);

                                        // insert the sorted text
                                        doc.insertString(startOffset, sb.toString(), null);
                                    }
                                }
                                filterProcess.destroy();
                            } catch (IOException fe) {
                                ErrorManager.getDefault().notify(ErrorManager.USER, fe);
                            }
                        } catch (BadLocationException ex) {
                            ErrorManager.getDefault().notify(ex);
                        }
                    }
                }
            };
            runModificationTaskOnDocument(textComponent.getDocument(), runnable);
        } else {
            beep();
        }
    }

    static void filterOutput(final JTextComponent textComponent) {
        if (textComponent.isEditable() && textComponent.getCaret().isSelectionVisible()) {
            Runnable runnable = new Runnable() {
                @Override
                public void run() {
                    Document doc = textComponent.getDocument();
                    Caret caret = textComponent.getCaret();

                    Element rootElement = doc.getDefaultRootElement();

                    int selStart = caret.getDot();
                    int selEnd = caret.getMark();
                    int start = Math.min(selStart, selEnd);
                    int end = Math.max(selStart, selEnd) - 1;

                    int zeroBaseStartLineNumber = rootElement.getElementIndex(start);
                    int zeroBaseEndLineNumber = rootElement.getElementIndex(end);

                    if (zeroBaseStartLineNumber == -1 || zeroBaseEndLineNumber == -1) {
                        // could not get line number or same line
                        beep();
                        return;
                    }

                    NotifyDescriptor.InputLine filterCommand = new NotifyDescriptor.InputLine("Enter Filter command (output sent to Output window):",
                            "Filter command", NotifyDescriptor.OK_CANCEL_OPTION, NotifyDescriptor.PLAIN_MESSAGE);

                    if (DialogDisplayer.getDefault().notify(filterCommand) == NotifyDescriptor.OK_OPTION) {
                        int startOffset = rootElement.getElement(zeroBaseStartLineNumber).getStartOffset();
                        int endOffset = rootElement.getElement(zeroBaseEndLineNumber).getEndOffset();

                        try {
                            int numberOfLines = zeroBaseEndLineNumber - zeroBaseStartLineNumber + 1;
                            String[] linesText = new String[numberOfLines];
                            for (int i = 0; i < numberOfLines; i++) {
                                // get line text
                                Element lineElement = rootElement.getElement(zeroBaseStartLineNumber + i);
                                int lineStartOffset = lineElement.getStartOffset();
                                int lineEndOffset = lineElement.getEndOffset();

                                linesText[i] = doc.getText(lineStartOffset, (lineEndOffset - lineStartOffset - 1));
                            }

                            try {
                                FilterProcess filterProcess = new FilterProcess(filterCommand.getInputText().split(" "));

                                try (PrintWriter in = filterProcess.exec()) {
                                    for (String line : linesText) {
                                        in.println(line);
                                    }
                                }
                                if (filterProcess.waitFor() == 0) {
                                    InputOutput io = IOProvider.getDefault().getIO(filterCommand.getInputText(), true);
                                    linesText = filterProcess.getStdOutOutput();
                                    if (linesText != null) {
                                        try (PrintWriter pw = new PrintWriter(io.getOut())) {
                                            for (String line : linesText) {
                                                pw.println(line);
                                            }
                                        }
                                    }
                                    linesText = filterProcess.getStdErrOutput();
                                    if (linesText != null) {
                                        try (PrintWriter pw = new PrintWriter(io.getErr())) {
                                            for (String line : linesText) {
                                                pw.println(line);
                                            }
                                        }
                                    }
                                }
                                filterProcess.destroy();
                            } catch (IOException fe) {
                                ErrorManager.getDefault().notify(ErrorManager.USER, fe);
                            }
                        } catch (BadLocationException ex) {
                            ErrorManager.getDefault().notify(ex);
                        }
                    }
                }
            };
            runModificationTaskOnDocument(textComponent.getDocument(), runnable);

        } else {
            beep();
        }
    }

    static final void fromChar(final JTextComponent textComponent, final char fromChar, final boolean matchCase, final int times) {
        if (textComponent.isEditable()) {
            Runnable runnable = new Runnable() {
                @Override
                public void run() {
                    Document doc = textComponent.getDocument();
                    Element rootElement = doc.getDefaultRootElement();

                    Caret caret = textComponent.getCaret();
                    int start = textComponent.getCaretPosition();

                    int zeroBaseStartLineNumber = rootElement.getElementIndex(start);

                    if (zeroBaseStartLineNumber == -1) {
                        // could not get line number
                        beep();
                        return;
                    } else {
                        int startLineStartOffset = rootElement.getElement(zeroBaseStartLineNumber).getStartOffset();
                        try {
                            String text = doc.getText(startLineStartOffset, start - startLineStartOffset);

                            char lowercaseFromChar = Character.toLowerCase(fromChar);
                            int textLength = text.length();
                            int timesCounter = times;
                            for (int i = textLength - 1; i >= 0; i--) {
                                char charAt = text.charAt(i);
                                if (charAt == fromChar || (!matchCase && Character.toLowerCase(charAt) == lowercaseFromChar)) {
                                    timesCounter--;
                                    if (timesCounter == 0) {
                                        caret.moveDot(startLineStartOffset + i);
                                        break;
                                    }
                                }
                            }
                        } catch (BadLocationException ex) {
                            ErrorManager.getDefault().notify(ErrorManager.INFORMATIONAL, ex);
                        }
                    }
                }
            };
            runModificationTaskOnDocument(textComponent.getDocument(), runnable);
        } else {
            beep();
        }
    }

    static final void afterChar(final JTextComponent textComponent, final char afterChar, final boolean matchCase, final int times) {
        if (textComponent.isEditable()) {
            Runnable runnable = new Runnable() {
                @Override
                public void run() {
                    Document doc = textComponent.getDocument();
                    Element rootElement = doc.getDefaultRootElement();

                    Caret caret = textComponent.getCaret();
                    int start = textComponent.getCaretPosition();

                    int zeroBaseStartLineNumber = rootElement.getElementIndex(start);

                    if (zeroBaseStartLineNumber == -1) {
                        // could not get line number
                        beep();
                        return;
                    } else {
                        int startLineStartOffset = rootElement.getElement(zeroBaseStartLineNumber).getStartOffset();
                        try {
                            String text = doc.getText(startLineStartOffset, start - startLineStartOffset);

                            char lowercaseAfterChar = Character.toLowerCase(afterChar);
                            int textLength = text.length();
                            int countTimes = times;
                            for (int i = textLength - 1; i >= 0; i--) {
                                char charAt = text.charAt(i);
                                if (charAt == afterChar || (!matchCase && Character.toLowerCase(charAt) == lowercaseAfterChar)) {
                                    countTimes--;
                                    if (countTimes == 0) {
                                        caret.moveDot(startLineStartOffset + i + 1);
                                        break;
                                    }
                                }
                            }
                        } catch (BadLocationException ex) {
                            ErrorManager.getDefault().notify(ErrorManager.INFORMATIONAL, ex);
                        }
                    }
                }
            };
            runModificationTaskOnDocument(textComponent.getDocument(), runnable);
        } else {
            beep();
        }
    }

    static final void uptoChar(final JTextComponent textComponent, final char uptoChar, final boolean matchCase, final int times) {
        if (textComponent.isEditable()) {
            Runnable runnable = new Runnable() {
                @Override
                public void run() {
                    Document doc = textComponent.getDocument();
                    Element rootElement = doc.getDefaultRootElement();

                    Caret caret = textComponent.getCaret();
                    int start = textComponent.getCaretPosition();

                    int zeroBaseStartLineNumber = rootElement.getElementIndex(start);

                    if (zeroBaseStartLineNumber == -1) {
                        // could not get line number
                        beep();
                        return;
                    } else {
                        int startLineEndOffset = rootElement.getElement(zeroBaseStartLineNumber).getEndOffset();
                        try {
                            String text = doc.getText(start + 1, startLineEndOffset - start - 1);

                            char lowercaseUptoChar = Character.toLowerCase(uptoChar);
                            int textLength = text.length();
                            int countTimes = times;

                            for (int i = 0; i < textLength; i++) {
                                char charAt = text.charAt(i);
                                if (charAt == uptoChar || (!matchCase && Character.toLowerCase(charAt) == lowercaseUptoChar)) {
                                    countTimes--;
                                    if (countTimes == 0) {
                                        caret.moveDot(start + 1 + i);
                                        break;
                                    }
                                }
                            }
                        } catch (BadLocationException ex) {
                            ErrorManager.getDefault().notify(ErrorManager.INFORMATIONAL, ex);
                        }
                    }
                }
            };
            runModificationTaskOnDocument(textComponent.getDocument(), runnable);
        } else {
            beep();
        }
    }

    static final void toChar(final JTextComponent textComponent, final char toChar, final boolean matchCase, final int times) {
        if (textComponent.isEditable()) {
            Runnable runnable = new Runnable() {
                @Override
                public void run() {
                    Document doc = textComponent.getDocument();

                    Element rootElement = doc.getDefaultRootElement();

                    Caret caret = textComponent.getCaret();
                    int start = textComponent.getCaretPosition();

                    int zeroBaseStartLineNumber = rootElement.getElementIndex(start);

                    if (zeroBaseStartLineNumber == -1) {
                        // could not get line number
                        beep();
                        return;
                    } else {
                        int startLineEndOffset = rootElement.getElement(zeroBaseStartLineNumber).getEndOffset();
                        try {
                            String text = doc.getText(start + 1, startLineEndOffset - start - 1);

                            char lowercaseToChar = Character.toLowerCase(toChar);
                            int textLength = text.length();
                            int countTimes = times;
                            for (int i = 0; i < textLength; i++) {
                                char charAt = text.charAt(i);
                                if (charAt == toChar || (!matchCase && Character.toLowerCase(charAt) == lowercaseToChar)) {
                                    countTimes--;
                                    if (countTimes == 0) {
                                        caret.moveDot(start + 1 + i + 1);
                                        break;
                                    }
                                }
                            }
                        } catch (BadLocationException ex) {
                            ErrorManager.getDefault().notify(ErrorManager.INFORMATIONAL, ex);
                        }
                    }
                }
            };
            runModificationTaskOnDocument(textComponent.getDocument(), runnable);
        } else {
            beep();
        }
    }

    static final void cycle(final JTextComponent textComponent, final String cycleString) {
        if (textComponent.isEditable()) {
            Runnable runnable = new Runnable() {
                @Override
                public void run() {
                    Document doc = textComponent.getDocument();

                    Element rootElement = doc.getDefaultRootElement();

                    Caret caret = textComponent.getCaret();
                    boolean selection = false;
                    boolean backwardSelection = false;
                    int start = textComponent.getCaretPosition();
                    int end = start;

                    // check if there is a selection
                    if (caret.isSelectionVisible()) {
                        int selStart = caret.getDot();
                        int selEnd = caret.getMark();
                        start = Math.min(selStart, selEnd);
                        end = Math.max(selStart, selEnd);
                        selection = true;
                        backwardSelection = (selStart >= selEnd);
                    }

                    int zeroBaseStartLineNumber = rootElement.getElementIndex(start);
                    int zeroBaseEndLineNumber = rootElement.getElementIndex(end);

                    if (zeroBaseStartLineNumber == -1) {
                        // could not get line number
                        beep();
                        return;
                    } else {
                        try {
                            // get line text
                            Element startLineElement = rootElement.getElement(zeroBaseStartLineNumber);
                            int startLineStartOffset = startLineElement.getStartOffset();

                            Element endLineElement = rootElement.getElement(zeroBaseEndLineNumber);
                            int endLineEndOffset = endLineElement.getEndOffset();

                            if (!selection) {
                                start = startLineStartOffset;
                                end = endLineEndOffset;
                            }

                            String linesText = doc.getText(start, (end - start));

                            linesText = cycle(linesText, cycleString);

                            // replace the line or selection
                            doc.remove(start, Math.min(doc.getLength(), end) - start);

                            // insert the text before the previous line
                            doc.insertString(start, linesText, null);

                            if (selection) {
                                if (backwardSelection) {
                                    caret.setDot(start);
                                    caret.moveDot(end);
                                } else {
                                    caret.setDot(end);
                                    caret.moveDot(start);
                                }
                            } else {
                                // set caret position
                                textComponent.setCaretPosition(start);
                            }
                        } catch (BadLocationException ex) {
                            ErrorManager.getDefault().notify(ex);
                        }
                    }
                }
            };
            runModificationTaskOnDocument(textComponent.getDocument(), runnable);
        } else {
            beep();
        }
    }

    public static String cycle(String target, String cycleChars) {
        if (target == null) {
            return null;
        }

        if (cycleChars == null) {
            return target;
        }

        Set<Character> cycleSet = getCharSet(cycleChars);
        if (cycleSet.size() <= 1) {
            return target;
        }

        Set<Character> set = getCharSet(target);
        set.retainAll(cycleSet);
        switch (set.size()) {
            case 0:
                return target;
            case 1:
                char from = set.iterator().next();
                List<Character> cycleList = new ArrayList<>(cycleSet);
                char to = cycleList.get((cycleList.indexOf(from) + 1) % cycleList.size());
                return target.replace(from, to);
            default:
                char first = set.iterator().next();
                cycleSet.remove(first);
                Iterator<Character> cycleSetIterator = cycleSet.iterator();
                while (cycleSetIterator.hasNext()) {
                    target = target.replace(cycleSetIterator.next(), first);
                }
                return target;
        }
    }

    private static Set<Character> getCharSet(String target) {
        if (target == null) {
            return null;
        }

        if (target.length() == 0) {
            return new LinkedHashSet<>();
        }

        if (target.length() == 1) {
            return new LinkedHashSet<>(Collections.<Character>singleton(target.charAt(0)));
        }

        char[] targetarray = target.toCharArray();
        Character[] targetArray = new Character[targetarray.length];
        for (int i = 0; i < targetarray.length; i++) {
            targetArray[i] = targetarray[i];
        }

        Set<Character> targetCharsSet = new LinkedHashSet<>(Arrays.<Character>asList(targetArray));

        return targetCharsSet;
    }

    static void beep() {
        Toolkit.getDefaultToolkit().beep();
    }

    //~ inner classes
    private static class SortLinesTask implements Runnable {

        private final JTextComponent textComponent;
        private final boolean descending;
        private final boolean matchCase;

        public SortLinesTask(JTextComponent textComponent, boolean descending, boolean matchCase) {
            this.textComponent = textComponent;
            this.descending = descending;
            this.matchCase = matchCase;
        }

        @Override
        public void run() {
            OffsetRange zeroBaselineNumberRange = getZeroBaseLineNumberRange();
            if (!canRun(zeroBaselineNumberRange)) {
                // could not get line number or same line
                beep();
                return;
            }
            Document doc = textComponent.getDocument();
            try {
                String[] lines = getLines(doc, zeroBaselineNumberRange);
                if (isRemoveDuplicateLines()) {
                    lines = removeDuplicateLines(lines);
                }
                sortLines(lines, getComparator(descending, matchCase));
                OffsetRange removalLineRange = getRemovalLineRange(doc, zeroBaselineNumberRange);
                doc.remove(removalLineRange.getStart(), removalLineRange.getLength());
                doc.insertString(removalLineRange.getStart(), joinLines(lines), null);
            } catch (BadLocationException ex) {
                ErrorManager.getDefault().notify(ex);
            }
        }

        private OffsetRange getZeroBaseLineNumberRange() {
            Document doc = textComponent.getDocument();
            Caret caret = textComponent.getCaret();
            Element rootElement = doc.getDefaultRootElement();

            int selStart = caret.getDot();
            int selEnd = caret.getMark();
            int start = Math.min(selStart, selEnd);
            int end = Math.max(selStart, selEnd) - 1;

            int zeroBaseStartLineNumber = rootElement.getElementIndex(start);
            int zeroBaseEndLineNumber = rootElement.getElementIndex(end);
            return new OffsetRange(zeroBaseStartLineNumber, zeroBaseEndLineNumber);
        }

        private String[] getLines(Document doc, OffsetRange lineRange) throws BadLocationException {
            int numberOfLines = lineRange.getLength() + 1;
            int zeroBaseStartLineNumber = lineRange.getStart();
            String[] lines = new String[numberOfLines];
            for (int i = 0; i < numberOfLines; i++) {
                // get line text
                Element lineElement = doc.getDefaultRootElement().getElement(zeroBaseStartLineNumber + i);
                int lineStartOffset = lineElement.getStartOffset();
                int lineEndOffset = lineElement.getEndOffset();
                lines[i] = doc.getText(lineStartOffset, (lineEndOffset - lineStartOffset));
            }
            return lines;
        }

        private String[] removeDuplicateLines(String[] lines) {
            SortedSet<String> uniqifySet = new TreeSet<>(matchCase ? null : String.CASE_INSENSITIVE_ORDER);
            uniqifySet.addAll(Arrays.asList(lines));
            lines = uniqifySet.toArray(new String[0]);
            return lines;
        }

        private void sortLines(String[] linesText, Comparator<String> comparator) {
            if (comparator == null) {
                Arrays.sort(linesText);
            } else {
                Arrays.sort(linesText, comparator);
            }
        }

        private OffsetRange getRemovalLineRange(Document doc, OffsetRange lineNumberRange) {
            Element rootElement = doc.getDefaultRootElement();
            int startOffset = rootElement.getElement(lineNumberRange.getStart()).getStartOffset();
            int endOffset = rootElement.getElement(lineNumberRange.getEnd()).getEndOffset();
            endOffset = Math.min(doc.getLength(), endOffset);
            return new OffsetRange(startOffset, endOffset);
        }

        private String joinLines(String[] lines) {
            StringBuilder sb = new StringBuilder();
            for (String line : lines) {
                sb.append(line);
            }
            return sb.toString();
        }

        private static boolean canRun(OffsetRange zeroBaselineNumberRange) {
            return zeroBaselineNumberRange.getStart() != -1
                    && zeroBaselineNumberRange.getEnd() != -1
                    && zeroBaselineNumberRange.getLength() != 0;
        }

        @CheckForNull
        private static Comparator<String> getComparator(boolean descending, boolean matchCase) {
            Comparator<String> comparator = null;
            if (descending) {
                if (matchCase) {
                    comparator = REVERSE_STRING_COMPARATOR;
                } else {
                    comparator = REVERSE_STRING_COMPARATOR_CASE_INSENSITIVE;
                }
            } else {
                if (matchCase) {
                    comparator = STRING_COMPARATOR;
                } else {
                    comparator = STRING_COMPARATOR_CASE_INSENSITIVE;
                }
            }
            return comparator;
        }
    }
}
