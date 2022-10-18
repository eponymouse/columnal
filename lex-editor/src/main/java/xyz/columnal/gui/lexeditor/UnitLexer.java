/*
 * Columnal: Safer, smoother data table processing.
 * Copyright (c) Neil Brown, 2016-2020, 2022.
 *
 * This file is part of Columnal.
 *
 * Columnal is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 *
 * Columnal is distributed in the hope that it will be useful, but WITHOUT 
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or 
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for 
 * more details.
 *
 * You should have received a copy of the GNU General Public License along 
 * with Columnal. If not, see <https://www.gnu.org/licenses/>.
 */

package xyz.columnal.gui.lexeditor;

import annotation.identifier.qual.UnitIdentifier;
import annotation.recorded.qual.Recorded;
import annotation.units.CanonicalLocation;
import annotation.units.RawInputLocation;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.checkerframework.checker.i18n.qual.LocalizableKey;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.data.datatype.TypeManager;
import xyz.columnal.data.unit.SingleUnit;
import xyz.columnal.data.unit.UnitManager;
import xyz.columnal.gui.lexeditor.TopLevelEditor.DisplayType;
import xyz.columnal.transformations.expression.CanonicalSpan;
import xyz.columnal.gui.lexeditor.Lexer.LexerResult.CaretPos;
import xyz.columnal.gui.lexeditor.completion.InsertListener;
import xyz.columnal.gui.lexeditor.completion.LexCompletion;
import xyz.columnal.gui.lexeditor.completion.LexCompletionGroup;
import xyz.columnal.transformations.expression.Expression.SaveDestination;
import xyz.columnal.transformations.expression.InvalidSingleUnitExpression;
import xyz.columnal.transformations.expression.SingleUnitExpression;
import xyz.columnal.transformations.expression.UnitExpression;
import xyz.columnal.transformations.expression.UnitExpression.UnitLookupException;
import xyz.columnal.transformations.expression.UnitExpressionIntLiteral;
import xyz.columnal.styled.StyledCSS;
import xyz.columnal.styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.IdentifierUtility;
import xyz.columnal.utility.adt.Pair;
import xyz.columnal.utility.Utility;
import xyz.columnal.utility.TranslationUtility;

import java.util.ArrayList;
import java.util.BitSet;

public class UnitLexer extends Lexer<UnitExpression, CodeCompletionContext>
{
    private final TypeManager typeManager;

    public static enum UnitOp implements ExpressionToken
    {
        MULTIPLY("*", "op.times"), DIVIDE("/", "op.divide"), RAISE("^", "op.raise");

        private final String op;
        private final @LocalizableKey String localNameKey;

        private UnitOp(String op, @LocalizableKey String localNameKey)
        {
            this.op = op;
            this.localNameKey = localNameKey;
        }

        @Override
        @OnThread(Tag.Any)
        public String getContent()
        {
            return op;
        }
    }

    public static enum UnitBracket implements ExpressionToken
    {
        OPEN_ROUND("("), CLOSE_ROUND(")");

        private final String bracket;

        private UnitBracket(String bracket)
        {
            this.bracket = bracket;
        }

        @Override
        @OnThread(Tag.Any)
        public String getContent()
        {
            return bracket;
        }

        public boolean isClosing()
        {
            return this == CLOSE_ROUND;
        }
    }

    private final UnitManager unitManager;
    private final boolean requireConcrete;

    public UnitLexer(TypeManager typeManager, boolean requireConcrete)
    {
        this.typeManager = typeManager;
        this.unitManager = typeManager.getUnitManager();
        this.requireConcrete = requireConcrete;
    }

    @Override
    public LexerResult<UnitExpression, CodeCompletionContext> process(String content, @Nullable Integer curCaretPos, InsertListener insertListener)
    {
        UnitSaver saver = new UnitSaver(typeManager, insertListener);
        RemovedCharacters removedCharacters = new RemovedCharacters();
        ArrayList<ContentChunk> chunks = new ArrayList<>();
        @RawInputLocation int curIndex = RawInputLocation.ZERO;
        nextToken: while (curIndex < content.length())
        {
            for (UnitBracket bracket : UnitBracket.values())
            {
                if (content.startsWith(bracket.getContent(), curIndex))
                {
                    saver.saveBracket(bracket, removedCharacters.map(curIndex, bracket.getContent()));
                    chunks.add(new ContentChunk(bracket.getContent(), bracket.isClosing() ? ChunkType.CLOSING : ChunkType.OPENING));
                    curIndex += rawLength(bracket.getContent());
                    continue nextToken;
                }
            }
            for (UnitOp op : UnitOp.values())
            {
                if (content.startsWith(op.getContent(), curIndex))
                {
                    saver.saveOperator(op, removedCharacters.map(curIndex, op.getContent()));
                    chunks.add(new ContentChunk(op.getContent(), ChunkType.OPENING));
                    curIndex += rawLength(op.getContent());
                    continue nextToken;
                }
            }
            
            if ((content.charAt(curIndex) >= '0' && content.charAt(curIndex) <= '9') || 
                (content.charAt(curIndex) == '-' && curIndex + 1 < content.length() && (content.charAt(curIndex + 1) >= '0' && content.charAt(curIndex + 1) <= '9')))
            {
                @RawInputLocation int startIndex = curIndex;
                // Minus only allowed at start:
                if (content.charAt(curIndex) == '-')
                {
                    curIndex += RawInputLocation.ONE;
                }
                while (curIndex < content.length() && content.charAt(curIndex) >= '0' && content.charAt(curIndex) <= '9')
                    curIndex += RawInputLocation.ONE;
                String numContent = content.substring(startIndex, curIndex);
                saver.saveOperand(new UnitExpressionIntLiteral(Integer.parseInt(numContent)), removedCharacters.map(startIndex, curIndex));
                chunks.add(new ContentChunk(numContent, ChunkType.IDENT));
                continue nextToken;
            }

            @Nullable Pair<@UnitIdentifier String, @RawInputLocation Integer> parsed = IdentifierUtility.consumeUnitIdentifier(content, curIndex);
            if (parsed != null && parsed.getSecond() > curIndex)
            {
                saver.saveOperand(new SingleUnitExpression(parsed.getFirst()), removedCharacters.map(curIndex, parsed.getSecond()));
                chunks.add(new ContentChunk(parsed.getFirst(), ChunkType.IDENT));
                curIndex = parsed.getSecond();
                continue nextToken;
            }

            CanonicalSpan invalidCharLocation = removedCharacters.map(curIndex, curIndex + RawInputLocation.ONE);
            String badChar = content.substring(curIndex, curIndex + 1);
            saver.saveOperand(new InvalidSingleUnitExpression(badChar), invalidCharLocation);
            chunks.add(new ContentChunk(badChar, ChunkType.OPENING));
            saver.locationRecorder.addErrorAndFixes(invalidCharLocation, StyledString.concat(TranslationUtility.getStyledString("error.illegalCharacter", Utility.codePointToString(content.charAt(curIndex))), StyledString.s("\n  "), StyledString.s("Character code: \\u" + Integer.toHexString(content.charAt(curIndex))).withStyle(new StyledCSS("errorable-sub-explanation"))), ImmutableList.of(new TextQuickFix("error.illegalCharacter.remove", invalidCharLocation, () -> new Pair<>("", StyledString.s("<remove>")))));
            
            curIndex += RawInputLocation.ONE;
        }
        @Recorded UnitExpression saved = saver.finish(removedCharacters.map(curIndex, curIndex));
        Pair<ArrayList<CaretPos>, ImmutableList<@CanonicalLocation Integer>> caretPositions = calculateCaretPos(chunks);
        
        if (requireConcrete)
        {
            @RawInputLocation int lastIndex = curIndex;
            try
            {
                saved.asUnit(unitManager);
            }
            catch (UnitLookupException e)
            {
                if (e.errorMessage != null || !e.quickFixes.isEmpty())
                {
                    saver.locationRecorder.addErrorAndFixes(new CanonicalSpan(CanonicalLocation.ZERO, removedCharacters.map(lastIndex)), e.errorMessage == null ? StyledString.s("") : e.errorMessage, Utility.mapListI(e.quickFixes, f -> new TextQuickFix(saver.locationRecorder.recorderFor(f.getReplacementTarget()), u -> u.save(SaveDestination.TO_EDITOR_FULL_NAME, true), f)));
                }
            }
        }

        return new LexerResult<>(saved, content, removedCharacters, false, ImmutableList.copyOf(caretPositions.getFirst()), ImmutableList.copyOf(caretPositions.getSecond()), StyledString.s(content), saver.getErrors(), saver.locationRecorder, makeCompletions(chunks, this::makeCompletions), new BitSet(), !saver.hasUnmatchedBrackets(), (i, n) -> ImmutableMap.<DisplayType, Pair<StyledString, ImmutableList<TextQuickFix>>>of());
    }
    
    private CodeCompletionContext makeCompletions(String stem, @CanonicalLocation int canonIndex, ChunkType curType, ChunkType preceding)
    {
        ImmutableList<SingleUnit> allDeclared = ImmutableList.sortedCopyOf(
            (u, v) -> u.getName().compareToIgnoreCase(v.getName()),
            unitManager.getAllDeclared());
                
        return new CodeCompletionContext(ImmutableList.of(
            new LexCompletionGroup(Utility.mapListI(allDeclared, u -> {
                int len = Utility.longestCommonStartIgnoringCase(u.getName(), 0, stem, 0);
                return new LexCompletion(canonIndex, len, u.getName()).withSideText(limit(u.getDescription()));
            }), null, 2),
            
            new LexCompletionGroup(Utility.mapListI(allDeclared, u -> {
                int len = Utility.longestCommonStartIgnoringCase(u.getDescription(), 0, stem, 0);
                return new LexCompletion(canonIndex, len, u.getName()) {
                    @Override
                    public boolean showFor(@CanonicalLocation int caretPos)
                    {
                        return caretPos > canonIndex + Utility.longestCommonStartIgnoringCase(u.getName(), 0, stem, 0) && super.showFor(caretPos);
                    }
                }.withSideText(limit(u.getDescription()));
            }), StyledString.s("Related"), 2)
        
        ));
    }

    private String limit(String description)
    {
        if (description.length() < 20)
            return description;
        else
            return description.substring(0, 19) + "\u2026";
    }
}
