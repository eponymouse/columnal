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

import annotation.recorded.qual.Recorded;
import annotation.recorded.qual.UnknownIfRecorded;
import annotation.units.CanonicalLocation;
import annotation.units.DisplayLocation;
import com.google.common.collect.ImmutableList;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import org.checkerframework.checker.initialization.qual.Initialized;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.data.datatype.TypeManager;
import xyz.columnal.transformations.expression.CanonicalSpan;
import xyz.columnal.gui.lexeditor.EditorLocationAndErrorRecorder.ErrorDetails;
import xyz.columnal.gui.lexeditor.completion.InsertListener;
import xyz.columnal.transformations.expression.QuickFix;
import xyz.columnal.styled.StyledShowable;
import xyz.columnal.styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.adt.Either;
import xyz.columnal.utility.adt.Pair;
import xyz.columnal.utility.Utility;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Stack;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * 
 * @param <EXPRESSION> The actual expression items in the tree
 * @param <SAVER> A saver (the subclass of this class) for the expressions
 * @param <OP> Operators that go between operands in an expression
 * @param <KEYWORD> Keywords that can alter scope levels, either beginning or ending scopes
 * @param <BRACKET_CONTENT> The content of brackets.  For units this is EXPRESSION, for others it is list of expression and operators (which will be commas)
 */
public abstract class SaverBase<EXPRESSION extends StyledShowable, SAVER, OP extends ExpressionToken, KEYWORD extends ExpressionToken, BRACKET_CONTENT>
{
    /**
     * Gets all special keywords available in child operators,
     * e.g. "then", paired with their description.
     */
    //default ImmutableList<Pair<String, @Localized String>> operatorKeywords()
    //{
    //    return ImmutableList.of();
    //}

    @OnThread(Tag.Any)
    protected <OP> ImmutableList<Either<OP, @Recorded EXPRESSION>> interleave(ImmutableList<@Recorded EXPRESSION> expressions, ImmutableList<OP> ops)
    {
        ImmutableList.Builder<Either<OP, @Recorded EXPRESSION>> r = ImmutableList.builder();

        for (int i = 0; i < expressions.size(); i++)
        {
            r.add(Either.right(expressions.get(i)));
            if (i < ops.size())
                r.add(Either.left(ops.get(i)));
        }
        
        return r.build();
    }

    public boolean hasUnmatchedBrackets()
    {
        return hasUnmatchedBrackets;
    }

    public void addNestedErrors(ImmutableList<ErrorDetails> nestedErrors, @CanonicalLocation int caretPosOffset, @DisplayLocation int displayCaretPosOffset)
    {
        for (ErrorDetails nestedError : nestedErrors)
        {
            locationRecorder.addNestedError(nestedError, caretPosOffset, displayCaretPosOffset);
        }
        
    }

    public void addNestedLocations(EditorLocationAndErrorRecorder locationRecorder, @CanonicalLocation int caretPosOffset)
    {
        this.locationRecorder.addNestedLocations(locationRecorder, caretPosOffset);
    }

    /**
     * Can this direct child node declare a variable?  i.e. is it part of a pattern?
     */
    //boolean canDeclareVariable(EEDisplayNode chid);

    public static interface MakeNary<EXPRESSION extends StyledShowable, SAVER, OP, BRACKET_CONTENT>
    {
        // Only called if the list is valid (one more expression than operators, strictly interleaved
        public <R extends StyledShowable> @Nullable @Recorded R makeNary(ImmutableList<@Recorded EXPRESSION> expressions, List<Pair<OP, CanonicalSpan>> operators, BracketAndNodes<EXPRESSION, SAVER, BRACKET_CONTENT, R> bracketedStatus, EditorLocationAndErrorRecorder locationRecorder);
    }

    public static interface MakeNarySimple<EXPRESSION extends StyledShowable, SAVER, OP>
    {
        // Only called if the list is valid (one more expression than operators, strictly interleaved)
        public @Nullable EXPRESSION makeNary(ImmutableList<@Recorded EXPRESSION> expressions, List<Pair<OP, CanonicalSpan>> operators);
    }

    public static interface MakeBinary<EXPRESSION extends StyledShowable, SAVER>
    {
        public <R extends StyledShowable> @Recorded R makeBinary(@Recorded EXPRESSION lhs, CanonicalSpan opNode, @Recorded EXPRESSION rhs, BracketAndNodes<EXPRESSION, SAVER, ?, R> bracketedStatus, EditorLocationAndErrorRecorder locationRecorder);
    }

    public static interface MakeBinarySimple<EXPRESSION extends StyledShowable, SAVER>
    {
        public EXPRESSION makeBinary(@Recorded EXPRESSION lhs, CanonicalSpan opNode, @Recorded EXPRESSION rhs);
    }

    // One set of operators that can be used to make a particular expression
    protected class OperatorExpressionInfo
    {
        public final ImmutableList<OP> operators;
        public final Either<MakeNary<EXPRESSION, SAVER, OP, BRACKET_CONTENT>, MakeBinary<EXPRESSION, SAVER>> makeExpression;

        public OperatorExpressionInfo(ImmutableList<OP> operators, MakeNary<EXPRESSION, SAVER, OP, BRACKET_CONTENT> makeExpression)
        {
            this.operators = operators;
            this.makeExpression = Either.left(makeExpression);
        }

        public OperatorExpressionInfo(ImmutableList<OP> operators, MakeNarySimple<EXPRESSION, SAVER, OP> makeExpression)
        {
            this.operators = operators;
            this.makeExpression = Either.left(new MakeNary<EXPRESSION, SAVER, OP, BRACKET_CONTENT>()
            {
                @Override
                public <R extends StyledShowable> @Nullable @Recorded R makeNary(ImmutableList<@Recorded EXPRESSION> expressions, List<Pair<OP, CanonicalSpan>> operators, BracketAndNodes<EXPRESSION, SAVER, BRACKET_CONTENT, R> bracketAndNodes, EditorLocationAndErrorRecorder locationRecorder)
                {
                    @Nullable EXPRESSION expression = makeExpression.makeNary(expressions, operators);
                    if (expression == null)
                        return null;
                    else
                        return bracketAndNodes.applyBrackets.applySingle(record(bracketAndNodes.location, expression));
                }
            });
        }

        // I know it's a bit odd, but we distinguish the N-ary constructor from the binary constructor by 
        // making the operators a non-list here.  The only expression with multiple operators
        // is add-subtract, which is N-ary.  And you can't have a binary expression with multiple different operators...
        protected OperatorExpressionInfo(OP operator, MakeBinarySimple<EXPRESSION, SAVER> makeExpression)
        {
            this.operators = ImmutableList.of(operator);
            this.makeExpression = Either.right(new MakeBinary<EXPRESSION, SAVER>()
            {
                @Override
                public <R extends StyledShowable> @Recorded R makeBinary(@Recorded EXPRESSION lhs, CanonicalSpan opNode, @Recorded EXPRESSION rhs, BracketAndNodes<EXPRESSION, SAVER, ?, R> bracketedStatus, EditorLocationAndErrorRecorder locationRecorder)
                {
                    EXPRESSION unrecorded = makeExpression.makeBinary(lhs, opNode, rhs);
                    @Recorded EXPRESSION recorded = locationRecorder.record(new CanonicalSpan(locationRecorder.recorderFor(lhs).start, locationRecorder.recorderFor(rhs).end), unrecorded);
                    return bracketedStatus.applyBrackets.applySingle(recorded);
                }
            });
        }

        public boolean includes(@NonNull OP op)
        {
            return operators.contains(op);
        }

        public OperatorSection makeOperatorSection(EditorLocationAndErrorRecorder locationRecorder, int operatorSetPrecedence, OpAndNode initialOperator, int initialIndex)
        {
            return makeExpression.either(
                nAry -> new NaryOperatorSection(locationRecorder, operators, operatorSetPrecedence, nAry, initialIndex, initialOperator),
                binary -> new BinaryOperatorSection(locationRecorder, operators, operatorSetPrecedence, binary, initialIndex, initialOperator)
            );
        }
    }

    public abstract class OperatorSection
    {
        protected final EditorLocationAndErrorRecorder locationRecorder;
        protected final ImmutableList<OP> possibleOperators;
        // The ordering in the candidates list:
        public final int operatorSetPrecedence;

        protected OperatorSection(EditorLocationAndErrorRecorder locationRecorder, ImmutableList<OP> possibleOperators, int operatorSetPrecedence)
        {
            this.locationRecorder = locationRecorder;
            this.possibleOperators = possibleOperators;
            this.operatorSetPrecedence = operatorSetPrecedence;
        }

        /**
         * Attempts to add the operator to this section.  If it can be added, it is added, and true is returned.
         * If it can't be added, nothing is changed, and false is returned.
         */
        abstract boolean addOperator(@NonNull OpAndNode operator, int indexOfOperator);

        /**
         * Given the operators already added, makes an expression.  Will only use the indexes that pertain
         * to the operators that got added, you should pass the entire list of the expression args.
         */
        abstract <R extends StyledShowable> @Nullable @Recorded R makeExpression(ImmutableList<@Recorded EXPRESSION> expressions, BracketAndNodes<EXPRESSION, SAVER, BRACKET_CONTENT, R> brackets);

        abstract int getFirstOperandIndex();

        abstract int getLastOperandIndex();
        
        abstract <R extends StyledShowable> @Nullable @Recorded R makeExpressionReplaceLHS(@Recorded EXPRESSION lhs, ImmutableList<@Recorded EXPRESSION> allOriginalExps, BracketAndNodes<EXPRESSION, SAVER, BRACKET_CONTENT, R> brackets);
        abstract <R extends StyledShowable> @Nullable @Recorded R makeExpressionReplaceRHS(@Recorded EXPRESSION rhs, ImmutableList<@Recorded EXPRESSION> allOriginalExps, BracketAndNodes<EXPRESSION, SAVER, BRACKET_CONTENT, R> brackets);
    }

    protected final class BinaryOperatorSection extends OperatorSection
    {
        protected final MakeBinary<EXPRESSION, SAVER> makeExpression;
        private final int operatorIndex;
        private final OpAndNode operator;

        BinaryOperatorSection(EditorLocationAndErrorRecorder locationRecorder, ImmutableList<OP> operators, int candidatePrecedence, MakeBinary<EXPRESSION, SAVER> makeExpression, int initialIndex, OpAndNode operator)
        {
            super(locationRecorder, operators, candidatePrecedence);
            this.makeExpression = makeExpression;
            this.operatorIndex = initialIndex;
            this.operator = operator;
        }

        @Override
        boolean addOperator(OpAndNode operator, int indexOfOperator)
        {
            // Can never add another operator to a binary operator:
            return false;
        }

        @Override
        <R extends StyledShowable> @Recorded R makeExpression(ImmutableList<@Recorded EXPRESSION> expressions, BracketAndNodes<EXPRESSION, SAVER, BRACKET_CONTENT, R> brackets)
        {
            return makeBinary(expressions.get(operatorIndex), operator.sourceNode, expressions.get(operatorIndex + 1), brackets, locationRecorder);
        }

        protected <R extends StyledShowable> @Recorded R makeBinary(@Recorded EXPRESSION lhs, CanonicalSpan opNode, @Recorded EXPRESSION rhs, BracketAndNodes<EXPRESSION, SAVER, BRACKET_CONTENT, R> brackets, EditorLocationAndErrorRecorder locationRecorder)
        {
            return makeExpression.<R>makeBinary(lhs, opNode, rhs, brackets, locationRecorder);
        }

        @Override
        int getFirstOperandIndex()
        {
            return operatorIndex;
        }

        @Override
        int getLastOperandIndex()
        {
            return operatorIndex + 1;
        }

        @Override
        <R extends StyledShowable> @Recorded R makeExpressionReplaceLHS(@Recorded EXPRESSION lhs, ImmutableList<@Recorded EXPRESSION> expressions, BracketAndNodes<EXPRESSION, SAVER, BRACKET_CONTENT, R> brackets)
        {
            return makeBinary(lhs, operator.sourceNode, expressions.get(operatorIndex + 1), brackets, locationRecorder);
        }

        @Override
        <R extends StyledShowable> @Recorded R makeExpressionReplaceRHS(@Recorded EXPRESSION rhs, ImmutableList<@Recorded EXPRESSION> expressions, BracketAndNodes<EXPRESSION, SAVER, BRACKET_CONTENT, R> brackets)
        {
            return makeBinary(expressions.get(operatorIndex), operator.sourceNode, rhs, brackets, locationRecorder);
        }
    }

    protected final class NaryOperatorSection extends OperatorSection
    {
        protected final MakeNary<EXPRESSION, SAVER, OP, BRACKET_CONTENT> makeExpression;
        private final ArrayList<Pair<OP, CanonicalSpan>> actualOperators = new ArrayList<>();
        private final int startingOperatorIndexIncl;
        private int endingOperatorIndexIncl;

        NaryOperatorSection(EditorLocationAndErrorRecorder locationRecorder, ImmutableList<OP> operators, int candidatePrecedence, MakeNary<EXPRESSION, SAVER, OP, BRACKET_CONTENT> makeExpression, int initialIndex, OpAndNode initialOperator)
        {
            super(locationRecorder, operators, candidatePrecedence);
            this.makeExpression = makeExpression;
            this.startingOperatorIndexIncl = initialIndex;
            this.endingOperatorIndexIncl = initialIndex;
            this.actualOperators.add(new Pair<>(initialOperator.op, initialOperator.sourceNode));
        }

        @Override
        boolean addOperator(@NonNull OpAndNode operator, int indexOfOperator)
        {
            if (possibleOperators.contains(operator.op) && indexOfOperator == endingOperatorIndexIncl + 1)
            {
                endingOperatorIndexIncl = indexOfOperator;
                actualOperators.add(new Pair<>(operator.op, operator.sourceNode));
                return true;
            }
            else
            {
                return false;
            }
        }

        @Override
        <R extends StyledShowable> @Nullable @Recorded R makeExpression(ImmutableList<@Recorded EXPRESSION> expressions, BracketAndNodes<EXPRESSION, SAVER, BRACKET_CONTENT, R> brackets)
        {
            // Given a + b + c, if end operator is 1, we want to include operand index 2, hence we pass excl index 3,
            // so it's last operator-inclusive, plus 2.
            ImmutableList<@Recorded EXPRESSION> selected = expressions.subList(startingOperatorIndexIncl, endingOperatorIndexIncl + 2);
            return makeNary(selected, actualOperators, brackets);
        }

        @Override
        int getFirstOperandIndex()
        {
            return startingOperatorIndexIncl;
        }

        @Override
        int getLastOperandIndex()
        {
            return endingOperatorIndexIncl + 1;
        }

        @Override
        <R extends StyledShowable> @Nullable @Recorded R makeExpressionReplaceLHS(@Recorded EXPRESSION lhs, ImmutableList<@Recorded EXPRESSION> expressions, BracketAndNodes<EXPRESSION, SAVER, BRACKET_CONTENT, R> brackets)
        {
            ArrayList<@Recorded EXPRESSION> args = new ArrayList<>(expressions.subList(startingOperatorIndexIncl, endingOperatorIndexIncl + 2));
            args.set(0, lhs);
            return makeNary(ImmutableList.copyOf(args), actualOperators, brackets);
        }

        @Override
        <R extends StyledShowable> @Nullable @Recorded R makeExpressionReplaceRHS(@Recorded EXPRESSION rhs, ImmutableList<@Recorded EXPRESSION> expressions, BracketAndNodes<EXPRESSION, SAVER, BRACKET_CONTENT, R> brackets)
        {
            ArrayList<@Recorded EXPRESSION> args = new ArrayList<>(expressions.subList(startingOperatorIndexIncl, endingOperatorIndexIncl + 2));
            args.set(args.size() - 1, rhs);
            return makeNary(ImmutableList.copyOf(args), actualOperators, brackets);
        }

        /**
         * Uses this expression as the LHS, and a custom middle and RHS that must have matching operators.  Used
         * to join two NaryOpExpressions while bracketing an item in the middle.
         */

        public <R extends @NonNull StyledShowable> @Nullable @Recorded R makeExpressionMiddleMerge(@Recorded EXPRESSION middle, NaryOperatorSection rhs, List<@Recorded EXPRESSION> expressions, BracketAndNodes<EXPRESSION, SAVER, BRACKET_CONTENT, R> brackets)
        {
            List<@Recorded EXPRESSION> args = new ArrayList<>();
            // Add our args, minus the end one:
            args.addAll(expressions.subList(startingOperatorIndexIncl, endingOperatorIndexIncl + 1));
            // Add the middle:
            args.add(middle);
            // Add RHS, minus the start one:
            args.addAll(expressions.subList(rhs.startingOperatorIndexIncl + 1, rhs.endingOperatorIndexIncl + 2));
            return makeExpression.makeNary(ImmutableList.copyOf(args), Utility.concatI(actualOperators, rhs.actualOperators), brackets, locationRecorder);
        }

        protected <R extends StyledShowable> @Nullable @Recorded R makeNary(ImmutableList<@Recorded EXPRESSION> expressions, List<Pair<OP, CanonicalSpan>> operators, BracketAndNodes<EXPRESSION, SAVER, BRACKET_CONTENT, R> bracketedStatus)
        {
            return makeExpression.makeNary(expressions, operators, bracketedStatus, locationRecorder);
        }
    }

    /**
     * A function to give back the content of a scope being ended.
     */
    public static interface FetchContent<EXPRESSION, SAVER, BRACKET_CONTENT>
    {
        /**
         * 
         * @param bracketInfo The bracket context for the scope being ended.
         * @return The expression for the scope being ended.
         */
        <R extends StyledShowable> @Recorded R fetchContent(BracketAndNodes<EXPRESSION, SAVER, BRACKET_CONTENT, R> bracketInfo);
    }

    /**
     * A lamba-interface like class with a method to call when you encounter
     * a keyword that should terminate the current scope.
     * 
     * Cannot be an interface because it's not static because
     * it uses many type variables from the outer SaverBase.
     */
    protected abstract class Terminator
    {
        public final String terminatorDescription;

        public Terminator(String terminatorDescription)
        {
            this.terminatorDescription = terminatorDescription;
        }

        /**
         * 
         * @param makeContent A function which, given a BracketedStatus wrapped with error displayers,
         *                    will give you the expression content of the just-finished scope. 
         * @param terminator The keyword which is terminating the current scope.
         * @param keywordErrorDisplayer The error displayer for the keyword.
         * @return true if the keyword matched and was consumed (always true if forceConsume was true), false if it didn't match and wasn't saved.
         */
        public abstract boolean terminate(FetchContent<EXPRESSION, SAVER, BRACKET_CONTENT> makeContent, @Nullable KEYWORD terminator, CanonicalSpan keywordErrorDisplayer);
    }
    
    // Op is typically an enum so we can't identity-hash-map it to a node, hence this wrapper
    protected class OpAndNode
    {
        public final @NonNull OP op;
        public final CanonicalSpan sourceNode;

        public OpAndNode(@NonNull OP op, CanonicalSpan sourceNode)
        {
            this.op = op;
            this.sourceNode = sourceNode;
        }
    }

    protected class Scope
    {
        public final ArrayList<Either<@Recorded EXPRESSION, OpAndNode>> items;
        public final Terminator terminator;
        public final CanonicalSpan openingNode;

        public Scope(CanonicalSpan openingNode, Terminator terminator)
        {
            this.items = new ArrayList<>();
            this.terminator = terminator;
            this.openingNode = openingNode;
        }
    }

    /**
     * BracketedStatus, paired with start and end for error recording purposes only.
     * @param <EXPRESSION>
     * @param <SAVER>
     */
    public static class BracketAndNodes<EXPRESSION, SAVER, BRACKET_CONTENT, RESULT>
    {
        public final ApplyBrackets<BRACKET_CONTENT, EXPRESSION, RESULT> applyBrackets;
        public final CanonicalSpan location;
        private final ImmutableList<ApplyBrackets<BRACKET_CONTENT, EXPRESSION, RESULT>> alternateBrackets;


        public BracketAndNodes(ApplyBrackets<BRACKET_CONTENT, EXPRESSION, RESULT> applyBrackets, CanonicalSpan location, ImmutableList<ApplyBrackets<BRACKET_CONTENT, EXPRESSION, RESULT>> alternateBrackets)
        {
            this.applyBrackets = applyBrackets;
            this.location = location;
            this.alternateBrackets = alternateBrackets;
        }

        public ImmutableList<BracketAndNodes<EXPRESSION, SAVER, BRACKET_CONTENT, RESULT>> alternateBrackets()
        {
            return Utility.mapListI(alternateBrackets, apply -> new BracketAndNodes<>(apply, location, ImmutableList.of()));
        }
    }
    
    protected abstract BracketAndNodes<EXPRESSION, SAVER, BRACKET_CONTENT, EXPRESSION> expectSingle(@UnknownInitialization(Object.class)SaverBase<EXPRESSION, SAVER, OP, KEYWORD, BRACKET_CONTENT> this, EditorLocationAndErrorRecorder locationRecorder, CanonicalSpan location);
    
    public BracketAndNodes<EXPRESSION, SAVER, BRACKET_CONTENT, EXPRESSION> miscBrackets(CanonicalSpan location)
    {
        return expectSingle(locationRecorder, location);
    }
    
    public Function<CanonicalSpan, BracketAndNodes<EXPRESSION, SAVER, BRACKET_CONTENT, EXPRESSION>> miscBracketsFrom(CanonicalSpan start)
    {
        return end -> miscBrackets(CanonicalSpan.fromTo(start, end));
    }

    protected final Stack<Scope> currentScopes = new Stack<>();
    protected final EditorLocationAndErrorRecorder locationRecorder;
    protected boolean hasUnmatchedBrackets = false;
    
    protected SaverBase(TypeManager typeManager, InsertListener insertListener)
    {
        this.locationRecorder = new EditorLocationAndErrorRecorder(typeManager, insertListener);
        addTopLevelScope();
    }
    
    public void saveOperator(@NonNull OP operator, CanonicalSpan errorDisplayer)
    {
        currentScopes.peek().items.add(Either.right(new OpAndNode(operator, errorDisplayer)));
    }

    protected abstract @Recorded EXPRESSION makeInvalidOp(CanonicalSpan location, ImmutableList<Either<OpAndNode, @Recorded EXPRESSION>> items);

    public void addTopLevelScope(@UnknownInitialization(SaverBase.class)SaverBase<EXPRESSION, SAVER, OP, KEYWORD, BRACKET_CONTENT>this)
    {
        @SuppressWarnings("nullness") // Pending fix for Checker Framework #2052
        final @NonNull Stack<Scope> currentScopesFinal = this.currentScopes;
        currentScopesFinal.push(new Scope(CanonicalSpan.START, new Terminator("end")
        {
            @Override
            public boolean terminate(FetchContent<EXPRESSION, SAVER, BRACKET_CONTENT> makeContent, @Nullable KEYWORD terminator, CanonicalSpan keywordErrorDisplayer)
            {
                CanonicalSpan start = CanonicalSpan.START;
                CanonicalSpan end = keywordErrorDisplayer;
                locationRecorder.addErrorAndFixes(end, StyledString.concat(StyledString.s("Unexpected "), terminator == null ? StyledString.s("item") : terminator.toStyledString()), ImmutableList.of());
                @Initialized SaverBase<EXPRESSION, SAVER, OP, KEYWORD, BRACKET_CONTENT> thisSaver = Utility.later(SaverBase.this);
                currentScopesFinal.peek().items.add(Either.<@Recorded EXPRESSION, OpAndNode>left(makeContent.<EXPRESSION>fetchContent(expectSingle(locationRecorder, CanonicalSpan.fromTo(start, end)))));
                if (terminator != null)
                    currentScopesFinal.peek().items.add(Either.<@Recorded EXPRESSION, OpAndNode>left(thisSaver.<EXPRESSION>record(keywordErrorDisplayer, thisSaver.keywordToInvalid(terminator))));
                return true;
            }
        }));
    }

    protected final <R extends StyledShowable> @Recorded @NonNull R record(CanonicalSpan location, @NonNull R expression)
    {
        return locationRecorder.record(location, expression);
    }
    
    protected abstract CanonicalSpan recorderFor(@Recorded EXPRESSION expression);

    protected abstract EXPRESSION keywordToInvalid(KEYWORD keyword);

    protected abstract EXPRESSION opToInvalid(OP op);

    protected CanonicalSpan getLocationForEither(Either<@Recorded EXPRESSION, OpAndNode> item)
    {
        return item.either(e -> recorderFor(e), op -> op.sourceNode);
    }

    //innerContentLocation is after opening bracket, used for
    //errors about empty content items
    protected abstract <R extends StyledShowable> @Recorded R makeExpression(List<Either<@Recorded EXPRESSION, OpAndNode>> content, BracketAndNodes<EXPRESSION, SAVER, BRACKET_CONTENT, R> brackets, @CanonicalLocation int innerContentLocation, @Nullable String terminatorDescription);
    
    public final @Recorded EXPRESSION finish(CanonicalSpan errorDisplayer)
    {
        while (currentScopes.size() > 1)
        {
            // The error should be given by terminate when terminator is null
            Scope closed = currentScopes.pop();
            closed.terminator.terminate(new FetchContent<EXPRESSION, SAVER, BRACKET_CONTENT>()
            {
                @Override
                public <R extends StyledShowable> @Recorded R fetchContent(BracketAndNodes<EXPRESSION, SAVER, BRACKET_CONTENT, R> brackets)
                {
                    return makeExpression(closed.items, brackets, errorDisplayer.start, null);
                }
            }, null, errorDisplayer);
        }

        Scope closed = currentScopes.pop();
        BracketAndNodes<EXPRESSION, SAVER, BRACKET_CONTENT, EXPRESSION> brackets = expectSingle(locationRecorder, CanonicalSpan.fromTo(closed.openingNode, errorDisplayer));
        return makeExpression(closed.items, brackets, errorDisplayer.start, closed.terminator.terminatorDescription);
    }

    private CanonicalSpan lastNode(Scope closed)
    {
        if (!closed.items.isEmpty())
        {
            return closed.items.get(closed.items.size() - 1).either(e -> recorderFor(e), op -> op.sourceNode);
        }
        return closed.openingNode;
    }

    // Note: if we are copying to clipboard, callback will not be called
    public void saveOperand(EXPRESSION singleItem, CanonicalSpan location)
    {
        currentScopes.peek().items.add(Either.<@Recorded EXPRESSION, OpAndNode>left(this.<EXPRESSION>record(location, singleItem)));
    }

    protected CollectedItems processItems(List<Either<@Recorded EXPRESSION, OpAndNode>> content)
    {
        return new CollectedItems(content);
    }

    protected class CollectedItems
    {
        private class InvalidReason
        {
            private final CanonicalSpan location;
            private final StyledString error;
            private final ImmutableList<TextQuickFix> fixes;

            public InvalidReason(CanonicalSpan location, StyledString error, ImmutableList<TextQuickFix> fixes)
            {
                this.location = location;
                this.error = error;
                this.fixes = fixes;
            }
        }
        
        // This is like a boolean; empty means valid, non-empty means invalid
        // because of those errors.
        private ArrayList<InvalidReason> invalidReasons;
        private final ArrayList<Either<OpAndNode, @Recorded EXPRESSION>> invalid;
        private final ArrayList<@Recorded EXPRESSION> validOperands;
        private final ArrayList<OpAndNode> validOperators;

        public boolean isValid()
        {
            return invalidReasons.isEmpty();
        }

        public @Recorded EXPRESSION makeInvalid(CanonicalSpan location, Function<ImmutableList<@Recorded EXPRESSION>, EXPRESSION> makeInvalid)
        {
            @Recorded EXPRESSION expression = record(location, makeInvalid.apply(
                    Utility.<Either<OpAndNode, @Recorded EXPRESSION>, @Recorded EXPRESSION>mapListI(invalid, et -> et.<@Recorded EXPRESSION>either(o -> record(o.sourceNode, opToInvalid(o.op)), x -> x))
            ));
            for (InvalidReason invalidReason : invalidReasons)
            {
                locationRecorder.addErrorAndFixes(invalidReason.location, invalidReason.error, invalidReason.fixes);
            }
            return expression;
        }

        public ArrayList<@Recorded EXPRESSION> getValidOperands()
        {
            return validOperands;
        }

        public ArrayList<OpAndNode> getValidOperators()
        {
            return validOperators;
        }

        private CollectedItems(List<Either<@Recorded EXPRESSION, OpAndNode>> originalContent)
        {
            // Take a copy because we may modify it while processing unary operators:
            ArrayList<Either<@Recorded EXPRESSION, OpAndNode>> content = new ArrayList<>(originalContent);
            
            // Although it's duplication, we keep a list for if it turns out invalid, and two lists for if it is valid:
            // Valid means that operands interleave exactly with operators, and there is an operand at beginning and end.
            invalidReasons = new ArrayList<>();
            invalid = new ArrayList<>();
            validOperands = new ArrayList<>();
            validOperators = new ArrayList<>();

            SimpleBooleanProperty lastWasOperand = new SimpleBooleanProperty(false); // Think of it as an invisible empty prefix operator
            SimpleObjectProperty<@Nullable CanonicalSpan> lastNodeSpan = new SimpleObjectProperty<>(null);

            for (int i = 0; i < content.size(); i++)
            {
                Either<@Recorded EXPRESSION, OpAndNode> item = content.get(i);
                int iFinal = i;
                
                item.either_(expression -> {
                    invalid.add(Either.right(expression));
                    validOperands.add(expression);

                    if (lastWasOperand.get() && iFinal > 0)
                    {
                        CanonicalSpan last = lastNodeSpan.get();
                        CanonicalSpan start = last != null ? last : recorderFor(expression).lhs();
                        invalidReasons.add(new InvalidReason(CanonicalSpan.fromTo(start.rhs(), recorderFor(expression).lhs()), StyledString.s("Missing operator"), fixesForAdjacentOperands(validOperands.get(validOperands.size() - 2), expression)));
                    }
                    lastWasOperand.set(true);
                    lastNodeSpan.set(recorderFor(expression));
                }, op -> {
                    invalid.add(Either.left(op));
                    validOperators.add(op);

                    // There are two ways a unary operator can be valid as unary.
                    // One is that it's at the beginning of a group,
                    // the other is that it follows exactly one other operator

                    @Nullable Supplier<@Recorded EXPRESSION> canBeUnary = iFinal + 1 >= content.size() ? null :
                            content.get(iFinal + 1).<@Nullable Supplier<@Recorded EXPRESSION>> either((@Recorded EXPRESSION next) -> canBeUnary(op, next), anotherOp -> null);

                    if (!lastWasOperand.get())
                    {
                        if (canBeUnary != null)
                        {
                            // Scrap us, and modify next operand with the unary operator:
                            invalid.remove(invalid.size() - 1);
                            validOperators.remove(validOperators.size() - 1);
                            content.set(iFinal + 1, Either.left(canBeUnary.get()));
                        }
                        else
                        {
                            CanonicalSpan last = lastNodeSpan.get();
                            CanonicalSpan start = last != null ? last : op.sourceNode;
                            invalidReasons.add(new InvalidReason(CanonicalSpan.fromTo(start.rhs(),op.sourceNode.lhs()), StyledString.s("Missing item between operators"), ImmutableList.of()));
                        }
                    }
                    lastWasOperand.set(false);
                    lastNodeSpan.set(op.sourceNode);
                });
            }
            
            // Must end with operand:
            CanonicalSpan last = lastNodeSpan.get();
            if (!lastWasOperand.get() && last != null)
            {
                invalidReasons.add(new InvalidReason(last.rhs(), StyledString.s("Missing item after operator."), ImmutableList.of()));
            }
        }
    }
    
    // For overriding in child classes
    protected ImmutableList<TextQuickFix> fixesForAdjacentOperands(@Recorded EXPRESSION first, @Recorded EXPRESSION second)
    {
        return ImmutableList.of();
    }

    /**
     * If the operator followed by the operand can act as a unary operator,
     * return a supplier to make the combined item.  If not, return null.
     */
    protected abstract @Nullable Supplier<@Recorded EXPRESSION> canBeUnary(OpAndNode operator, @Recorded EXPRESSION followingOperand);

    public interface ApplyBrackets<BRACKET_CONTENT, EXPRESSION, RESULT>
    {
        // Return null if that content with these brackets is invalid
        public @Nullable @Recorded RESULT apply(@NonNull BRACKET_CONTENT items);
        
        public @NonNull @Recorded RESULT applySingle(@NonNull @Recorded EXPRESSION singleItem);
    }
    
    /**
     * If all operators are from the same {@link records.gui.expressioneditor.OperandOps.OperatorExpressionInfo}, returns a normal expression with those operators.
     * Otherwise, it returns an invalid operator expression (as specified by the lambda), AND if feasible, suggests
     * likely quick fixes based on the suggested priority ordering given by the list parameter's ordering.
     *
     * @param candidates The ordering of the outer list indicates likely bracketing priority, with items earlier
     *                   in the list more likely to be bracketed (thus earlier means binds tighter).  So for example,
     *                   plus will come earlier in the list than equals, because given "a + b = c", we're more likely
     *                   to want to bracket "(a + b) = c" than "a + (b = c)".
     */
    public <R extends StyledShowable> @Nullable @Recorded R makeExpressionWithOperators(
        ImmutableList<ImmutableList<OperatorExpressionInfo>> candidates, EditorLocationAndErrorRecorder locationRecorder,
        Function<ImmutableList<Either<@NonNull OpAndNode, @Recorded EXPRESSION>>, @Recorded R> makeInvalidOpExpression,
        ImmutableList<@Recorded EXPRESSION> expressionExps, ImmutableList<@NonNull OpAndNode> ops, BracketAndNodes<EXPRESSION, SAVER, BRACKET_CONTENT, R> brackets)
    {
        if (ops.size() != expressionExps.size() - 1)
        {
            // Other issues: we're missing an argument!
            return null;
        }

        if (ops.isEmpty())
        {
            // Given above, expressionExps must be size 1, so just use that list:
            return brackets.applyBrackets.applySingle(expressionExps.get(0));
        }

        // First, split it into sections based on cohesive parts that have the same operators:
        List<OperatorSection> operatorSections = new ArrayList<>();
        nextOp: for (int i = 0; i < ops.size(); i++)
        {
            if (operatorSections.isEmpty() || !operatorSections.get(operatorSections.size() - 1).addOperator(ops.get(i), i))
            {
                // Make new section:
                for (int candidateIndex = 0; candidateIndex < candidates.size(); candidateIndex++)
                {
                    for (OperatorExpressionInfo operatorExpressionInfo : candidates.get(candidateIndex))
                    {
                        if (operatorExpressionInfo.includes(ops.get(i).op))
                        {
                            operatorSections.add(operatorExpressionInfo.makeOperatorSection(locationRecorder, candidateIndex, ops.get(i), i));
                            continue nextOp;
                        }
                    }
                }
                // If we get here, it's an unrecognised operator, so return an invalid expression:
                return null;
            }
        }

        if (operatorSections.size() == 1)
        {
            // All operators are coherent with each other, can just return single expression:
            @Nullable @Recorded R single = operatorSections.get(0).makeExpression(expressionExps, brackets);
            if (single != null)
                return single;

            // Maybe with the possibility of different brackets?
            List<@Recorded R> possibles = new ArrayList<>();
            for (BracketAndNodes<EXPRESSION, SAVER, BRACKET_CONTENT, R> alternateBracket : brackets.alternateBrackets())
            {
                @Nullable @Recorded R possible = operatorSections.get(0).makeExpression(expressionExps, alternateBracket);
                if (possible != null)
                    possibles.add(possible);
            }
            if (!possibles.isEmpty())
            {
                @Recorded R invalidOpExpression = makeInvalidOpExpression.apply(interleave(expressionExps, ops));
                locationRecorder.getRecorder().recordError(invalidOpExpression, StyledString.s("Surrounding brackets required"));
                locationRecorder.getRecorder().<R>recordQuickFixes(invalidOpExpression, Utility.<@Recorded R, QuickFix<R>>mapList(possibles, fixed -> new QuickFix<R>("fix.bracketAs", invalidOpExpression, () -> fixed)));
                return invalidOpExpression;
            }
        }

        @Recorded R invalidOpExpression = makeInvalidOpExpression.apply(interleave(expressionExps, ops));
        locationRecorder.getRecorder().recordError(invalidOpExpression, StyledString.s("Mixed operators: brackets required"));

        if (operatorSections.size() == 3
            && operatorSections.get(0).possibleOperators.equals(operatorSections.get(2).possibleOperators)
            && operatorSections.get(0) instanceof SaverBase.NaryOperatorSection
            && operatorSections.get(2) instanceof SaverBase.NaryOperatorSection
            && operatorSections.get(1).operatorSetPrecedence <= operatorSections.get(0).operatorSetPrecedence
            )
        {
            // The sections either side match up, and the middle is same or lower precedence, so we can bracket
            // the middle and put it into one valid expression.  Hurrah!
            CanonicalSpan middleStart = recorderFor(expressionExps.get(operatorSections.get(1).getFirstOperandIndex()));
            CanonicalSpan middleEnd = recorderFor(expressionExps.get(operatorSections.get(1).getLastOperandIndex()));
            @SuppressWarnings("recorded")
            @Nullable @Recorded EXPRESSION middle = operatorSections.get(1).makeExpression(expressionExps, expectSingle(locationRecorder, CanonicalSpan.fromTo(middleStart, middleEnd)));
            if (middle != null)
            {
                @Nullable @Recorded R replacement = ((NaryOperatorSection) operatorSections.get(0)).makeExpressionMiddleMerge(
                    middle,
                    (NaryOperatorSection) operatorSections.get(2),
                    expressionExps, brackets
                );

                if (replacement != null)
                {
                    @SuppressWarnings("recorded") // Because the replaced version is immediately loaded again
                    @NonNull @Recorded R replacementFinal = replacement;
                    locationRecorder.getRecorder().<R>recordQuickFixes(invalidOpExpression, Collections.<QuickFix<R>>singletonList(
                        new QuickFix<R>("fix.bracketAs", invalidOpExpression, () -> replacementFinal)
                    ));
                }
            }
        }
        else
        {
            // We may be able to suggest some brackets
            Collections.<OperatorSection>sort(operatorSections, Comparator.<OperatorSection, Integer>comparing(os -> os.operatorSetPrecedence));
            int precedence = operatorSections.get(0).operatorSetPrecedence;

            for (int i = 0; i < operatorSections.size(); i++)
            {
                OperatorSection operatorSection = operatorSections.get(i);
                if (operatorSection.operatorSetPrecedence != precedence)
                    break;

                // We try all the bracketing states, preferring un-bracketed, for valid replacements:
                CanonicalSpan secStart = recorderFor(expressionExps.get(operatorSection.getFirstOperandIndex()));
                CanonicalSpan secEnd = recorderFor(expressionExps.get(operatorSection.getLastOperandIndex()));
                @SuppressWarnings("recorded")
                @Nullable @Recorded EXPRESSION sectionExpression = operatorSection.makeExpression(expressionExps, expectSingle(locationRecorder, CanonicalSpan.fromTo(secStart, secEnd)));
                if (sectionExpression == null)
                    continue;

                // The replacement if we just bracketed this section:
                @UnknownIfRecorded R replacement;
                // There's three possibilities.  One is that if there is one other section, or two that match each other,
                // we could make a valid expression.  Otherwise we're going to be invalid even with a bracket.
                if (operatorSections.size() == 2)
                {
                    // We need our new expression, plus the bits we're not including
                    if (operatorSection.getFirstOperandIndex() == 0)
                    {
                        replacement = operatorSections.get(1 - i).makeExpressionReplaceLHS(
                            sectionExpression,
                            expressionExps,
                            brackets
                        );
                    }
                    else
                    {
                        replacement = operatorSections.get(1 - i).makeExpressionReplaceRHS(
                            sectionExpression,
                            expressionExps,
                            brackets
                        );
                    }
                }
                //else if (operatorSections.size() == 3 && ...) -- Handled above
                else
                {
                    // Just have to make an invalid op expression, then:
                    ArrayList<@Recorded EXPRESSION> newExps = new ArrayList<>(expressionExps);
                    ArrayList<OpAndNode> newOps = new ArrayList<>(ops);

                    newExps.subList(operatorSection.getFirstOperandIndex(), operatorSection.getLastOperandIndex() + 1).clear();
                    newExps.add(operatorSection.getFirstOperandIndex(), sectionExpression);
                    newOps.subList(operatorSection.getFirstOperandIndex(), operatorSection.getLastOperandIndex()).clear();

                    replacement = makeInvalidOpExpression.apply(SaverBase.this.<OpAndNode>interleave(ImmutableList.<@Recorded EXPRESSION>copyOf(newExps), ImmutableList.<OpAndNode>copyOf(newOps)));
                }

                if (replacement != null)
                {
                    locationRecorder.getRecorder().recordQuickFixes(invalidOpExpression, Collections.<QuickFix<R>>singletonList(
                        new QuickFix<R>("fix.bracketAs", invalidOpExpression, () -> replacement)
                    ));
                }
            }
        }

        return invalidOpExpression;
    }

    // Expects a keyword matching closer.  If so, call the function with the current scope's expression, and you'll get back a final expression or a
    // terminator for a new scope, compiled using the scope content and given bracketed status
    public Terminator expect(ImmutableList<@NonNull KEYWORD> expected, Function<CanonicalSpan, BracketAndNodes<EXPRESSION, SAVER, BRACKET_CONTENT, EXPRESSION>> makeBrackets, BiFunction<@Recorded EXPRESSION, CanonicalSpan, Either<@Recorded EXPRESSION, Terminator>> onSuccessfulClose, Supplier<ImmutableList<@Recorded EXPRESSION>> prefixItemsOnFailedClose, @Nullable StyledString promptIfUnfinished, boolean isBracket)
    {
        return new Terminator(expected.get(0).getContent()) {
            @Override
            public boolean terminate(FetchContent<EXPRESSION, SAVER, BRACKET_CONTENT> makeContent, @Nullable KEYWORD terminator, CanonicalSpan keywordErrorDisplayer)
            {
                int termIndex = expected.indexOf(terminator);
                if (termIndex == 0)
                {
                    // All is well:
                    @Recorded EXPRESSION content = makeContent.fetchContent(makeBrackets.apply(keywordErrorDisplayer));
                    Either<@Recorded EXPRESSION, Terminator> result = onSuccessfulClose.apply(content, keywordErrorDisplayer);
                    result.either_(e -> currentScopes.peek().items.add(Either.left(e)), t -> currentScopes.push(new Scope(keywordErrorDisplayer, t)));
                    return true;
                }
                else
                {
                    if (isBracket)
                        hasUnmatchedBrackets = true;
                    
                    // Error!
                    ImmutableList<KEYWORD> toAdd = termIndex == -1 ? expected : expected.subList(0, termIndex);
                    StyledString toAddSS = toAdd.stream().map(s -> s.toStyledString()).collect(StyledString.joining("\u2026"));
                    TextQuickFix fix = new TextQuickFix(StyledString.s("Add missing item(s)"), ImmutableList.of(), keywordErrorDisplayer.lhs(), () -> new Pair<>(toAdd.stream().map(k -> k.getContent()).collect(Collectors.joining()), toAddSS));
                    locationRecorder.addErrorAndFixes(keywordErrorDisplayer, StyledString.concat(StyledString.s("Missing "), toAddSS, StyledString.s(" before "), terminator == null ? StyledString.s("end") : terminator.toStyledString()), ImmutableList.of(fix));
                    @Nullable CanonicalSpan start = currentScopes.peek().openingNode;
                    // Important to call makeContent before adding to scope on the next line:
                    ImmutableList.Builder<Either<OpAndNode, @Recorded EXPRESSION>> items = ImmutableList.builder();
                    items.addAll(Utility.<@Recorded EXPRESSION, Either<OpAndNode, @Recorded EXPRESSION>>mapListI(prefixItemsOnFailedClose.get(), (@Recorded EXPRESSION e) -> Either.<OpAndNode, @Recorded EXPRESSION>right(e)));
                    @Recorded EXPRESSION content = makeContent.fetchContent(unclosedBrackets(makeBrackets.apply(keywordErrorDisplayer.lhs())));
                    if (promptIfUnfinished != null)
                        locationRecorder.recordEntryPromptG(content, n -> promptIfUnfinished);
                    items.add(Either.right(content));
                    //if (terminator != null)
                        //items.add(Either.<OpAndNode, @Recorded EXPRESSION>right(SaverBase.this.<EXPRESSION>record(keywordErrorDisplayer, keywordToInvalid(terminator))));
                    ImmutableList<Either<OpAndNode, @Recorded EXPRESSION>> built = items.build();
                    @Recorded EXPRESSION invalid = makeInvalidOp(CanonicalSpan.fromTo(built.get(0).either(opAndNode -> opAndNode.sourceNode, e -> recorderFor(e)), keywordErrorDisplayer.lhs()), built);
                    currentScopes.peek().items.add(Either.left(invalid));
                    return false;
                }
            }};
    }

    protected abstract BracketAndNodes<EXPRESSION,SAVER,BRACKET_CONTENT, EXPRESSION> unclosedBrackets(BracketAndNodes<EXPRESSION,SAVER,BRACKET_CONTENT, EXPRESSION> closed);

    public ImmutableList<ErrorDetails> getErrors()
    {
        return locationRecorder.getErrors();
    }
}
