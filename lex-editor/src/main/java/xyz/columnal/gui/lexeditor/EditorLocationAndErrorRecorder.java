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
import annotation.units.CanonicalLocation;
import annotation.units.DisplayLocation;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import javafx.scene.Node;
import xyz.columnal.log.Log;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.id.TableAndColumnRenames;
import xyz.columnal.data.datatype.TypeManager;
import xyz.columnal.error.InternalException;
import xyz.columnal.gui.lexeditor.TopLevelEditor.DisplayType;
import xyz.columnal.gui.lexeditor.completion.InsertListener;
import xyz.columnal.transformations.expression.BracketedStatus;
import xyz.columnal.transformations.expression.CanonicalSpan;
import xyz.columnal.transformations.expression.ErrorAndTypeRecorder;
import xyz.columnal.transformations.expression.Expression;
import xyz.columnal.transformations.expression.Expression.SaveDestination;
import xyz.columnal.transformations.expression.QuickFix;
import xyz.columnal.transformations.expression.UnitExpression;
import xyz.columnal.transformations.expression.type.TypeExpression;
import xyz.columnal.typeExp.NumTypeExp;
import xyz.columnal.typeExp.TypeConcretisationError;
import xyz.columnal.typeExp.TypeCons;
import xyz.columnal.typeExp.TypeExp;
import xyz.columnal.typeExp.TypeExp.TypeError;
import xyz.columnal.styled.StyledShowable;
import xyz.columnal.styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.adt.Either;
import xyz.columnal.utility.function.fx.FXPlatformFunction;
import xyz.columnal.utility.adt.Pair;
import xyz.columnal.utility.Utility;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;

/**
 * This class keeps track of mapping expressions back to their source location in a graphical editor.
 * 
 * It is uncoupled from actually recording the error, which is handled in the {@link ErrorAndTypeRecorder} class.
 * 
 * For displaying semantic errors (which occur on a particular expression)
 */
public class EditorLocationAndErrorRecorder
{

    public static final class DisplaySpan
    {
        public final @DisplayLocation int start;
        public final @DisplayLocation int end;

        public DisplaySpan(@DisplayLocation int start, @DisplayLocation int end)
        {
            this.start = start;
            this.end = end;
        }

        // Even though end is typically exclusive, this checks
        // if <= end because for errors etc we still want to display
        // if we touch the extremity.
        public boolean touches(@DisplayLocation int position)
        {
            return start <= position && position <= end;
        }

        // Used in testing:
        @Override
        public boolean equals(@Nullable Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            DisplaySpan that = (DisplaySpan) o;
            return start == that.start &&
                    end == that.end;
        }

        @Override
        public int hashCode()
        {
            return Objects.hash(start, end);
        }

        @Override
        public String toString()
        {
            return start + "->" + end;
        }
    }
    
    // We use IdentityHashMap because we want to distinguish between multiple duplicate sub-expressions,
    // e.g. in the expression 2 + abs(2), we want to assign any error to the right 2.  Because of this
    // we use identity hash map, and we cannot use Either (which would break this property).  So two maps it is:
    private final IdentityHashMap<@Recorded Object, CanonicalSpan> positions = new IdentityHashMap<>();
    private final IdentityHashMap<Expression, Either<TypeConcretisationError, TypeExp>> types = new IdentityHashMap<>();
    // Function takes node that documentation should appear to the right of
    private final ArrayList<Pair<CanonicalSpan, FXPlatformFunction<Node, StyledString>>> entryPrompts = new ArrayList<>();
    private final IdentityHashMap<Expression, Pair<StyledString, ImmutableList<TextQuickFix>>> information = new IdentityHashMap<>();
    // Used by DocWindow
    private final InsertListener insertListener;
    private final TypeManager typeManager;

    private static interface UnresolvedErrorDetails
    {
        public ErrorDetails resolve() throws InternalException;
    }
    
    public static class ErrorDetails
    {
        public final CanonicalSpan location;
        // Mutable for ease of processing:
        public @MonotonicNonNull DisplaySpan displayLocation;
        public final StyledString error;
        public final ImmutableList<TextQuickFix> quickFixes;
        // Note -- mutable field.
        public boolean caretHasLeftSinceEdit;

        public ErrorDetails(CanonicalSpan location, StyledString error, ImmutableList<TextQuickFix> quickFixes)
        {
            this.location = location;
            this.error = error;
            this.quickFixes = quickFixes;
        }

        public ErrorDetails offsetBy(@CanonicalLocation int caretPosOffset, @DisplayLocation int displayCaretPosOffset)
        {
            ErrorDetails r = new ErrorDetails(location.offsetBy(caretPosOffset), error, Utility.mapListI(quickFixes, f -> f.offsetBy(caretPosOffset)));
            r.caretHasLeftSinceEdit = caretHasLeftSinceEdit;
            if (displayLocation != null)
                r.displayLocation = new DisplaySpan(displayLocation.start + displayCaretPosOffset, displayLocation.end + displayCaretPosOffset);
            return r;
        }

        // Useful for debugging:
        @Override
        public String toString()
        {
            return "ErrorDetails{" +
                    "location=" + location +
                    "//" + displayLocation +
                    ", error=" + error +
                    '}';
        }
    }
    
    private final ArrayList<UnresolvedErrorDetails> errorsToShow = new ArrayList<>();
    
    public EditorLocationAndErrorRecorder(TypeManager typeManager, InsertListener insertListener)
    {
        this.typeManager = typeManager;
        this.insertListener = insertListener;
    }

    @SuppressWarnings("recorded")
    public <EXPRESSION> @NonNull @Recorded EXPRESSION record(CanonicalSpan location,  @NonNull EXPRESSION e)
    {
        if (positions.containsKey(e))
            Log.logStackTrace("Double position record for: " + e);
        positions.put(e, location);
        return e;
    }

    private void showUnresolvedError(Expression e, @Nullable StyledString error, ImmutableList<QuickFix<Expression>> quickFixes)
    {
        errorsToShow.add(() -> {
            @Nullable CanonicalSpan resolvedLocation = positions.get(e);
            if (resolvedLocation != null)
            {
                return new ErrorDetails(resolvedLocation, error == null ? StyledString.s("") : error, Utility.mapListInt(quickFixes, q -> {
                    CanonicalSpan replPos = positions.get(q.getReplacementTarget());
                    if (replPos == null)
                        throw new InternalException("Could not resolve location for expression: " + q.getReplacementTarget());
                    return new TextQuickFix(replPos, exp -> exp.save(SaveDestination.TO_EDITOR_FULL_NAME, BracketedStatus.DONT_NEED_BRACKETS, new TableAndColumnRenames(ImmutableMap.of())), q);
                }));
            }
            else
            {
                throw new InternalException("Could not resolve location for expression: " + e);
            }
        });
    }

    private void showUnresolvedError(TypeExpression e, @Nullable StyledString error, ImmutableList<QuickFix<TypeExpression>> quickFixes)
    {
        errorsToShow.add(() -> {
            @Nullable CanonicalSpan resolvedLocation = positions.get(e);
            if (resolvedLocation != null)
            {
                return new ErrorDetails(resolvedLocation, error == null ? StyledString.s("") : error, Utility.mapListInt(quickFixes, q -> {
                    CanonicalSpan replPos = positions.get(q.getReplacementTarget());
                    if (replPos == null)
                        throw new InternalException("Could not resolve location for expression: " + q.getReplacementTarget());
                    return new TextQuickFix(replPos, exp -> exp.save(SaveDestination.TO_EDITOR_FULL_NAME, new TableAndColumnRenames(ImmutableMap.of())), q);
                }));
            }
            else
            {
                throw new InternalException("Could not resolve location for type expression: " + e);
            }
        });
    }

    private void showUnresolvedError(UnitExpression e, @Nullable StyledString error, ImmutableList<QuickFix<UnitExpression>> quickFixes)
    {
        errorsToShow.add(() -> {
            @Nullable CanonicalSpan resolvedLocation = positions.get(e);
            if (resolvedLocation != null)
            {
                return new ErrorDetails(resolvedLocation, error == null ? StyledString.s("") : error, Utility.mapListInt(quickFixes, q -> {
                    CanonicalSpan replPos = positions.get(q.getReplacementTarget());
                    if (replPos == null)
                        throw new InternalException("Could not resolve location for expression: " + q.getReplacementTarget());
                    return new TextQuickFix(replPos, exp -> exp.save(SaveDestination.TO_EDITOR_FULL_NAME, false), q);
                }));
            }
            else
            {
                throw new InternalException("Could not resolve location for type expression: " + e);
            }
        });
    }
    
    public void addErrorAndFixes(CanonicalSpan showFor, StyledString error, ImmutableList<TextQuickFix> quickFixes)
    {
        errorsToShow.add(() -> new ErrorDetails(showFor, error, quickFixes));
    }
    
    /*
    public void showAllTypes(TypeManager typeManager)
    {
        expressionDisplayers.forEach(((expression, errorDisplayer) -> {
            Either<TypeConcretisationError, TypeExp> typeDetails = types.get(expression);
            //Log.debug("Showing " + expression + " item " + typeDetails + " showing error: " + errorDisplayer.isShowingError());
            if (typeDetails != null)
            {
                try
                {
                    typeDetails.flatMapEx(typeExp -> typeExp.toConcreteType(typeManager).mapEx(dataType -> dataType.toDisplay(false)))
                        .either_(err -> {
                            errorDisplayer.showType("");
                            errorDisplayer.addErrorAndFixes(err.getErrorText(), ExpressionUtil.quickFixesForTypeError(typeManager, expression, err.getSuggestedTypeFix()));
                        }, display -> errorDisplayer.showType(display));
                }
                catch (InternalException | UserException e)
                {
                    errorDisplayer.addErrorAndFixes(StyledString.s(e.getLocalizedMessage()), Collections.emptyList());
                }
            }
            else
            {
                errorDisplayer.showType("");
            }
            
        }));
    }
    */

    public void recordType(Expression src, Either<TypeConcretisationError, TypeExp> errorOrType)
    {
        types.put(src, errorOrType);
    }
    
    @OnThread(Tag.Any)
    public ErrorAndTypeRecorder getRecorder()
    {
        return new ErrorAndTypeRecorder()
        {
            @Override
            public <EXPRESSION> void recordError(EXPRESSION src, StyledString error)
            {
                if (src instanceof Expression)
                {
                    EditorLocationAndErrorRecorder.this.showUnresolvedError((Expression) src, error, ImmutableList.of());
                }
                else if (src instanceof TypeExpression)
                {
                    EditorLocationAndErrorRecorder.this.showUnresolvedError((TypeExpression) src, error, ImmutableList.of());
                }
                else if (src instanceof UnitExpression)
                {
                    EditorLocationAndErrorRecorder.this.showUnresolvedError((UnitExpression) src, error, ImmutableList.of());
                }
            }

            @Override
            public <EXPRESSION extends StyledShowable> void recordInformation(@Recorded EXPRESSION src, Pair<StyledString, @Nullable QuickFix<EXPRESSION>> info)
            {
                if (src instanceof Expression)
                    information.put((Expression)src, info.mapSecond(qf -> qf == null ? ImmutableList.<TextQuickFix>of() : ImmutableList.<TextQuickFix>of(new <EXPRESSION>TextQuickFix(recorderFor(src), e -> e.toStyledString().toPlain(), qf))));
            }

            @SuppressWarnings("unchecked")
            @Override
            public <EXPRESSION extends StyledShowable> void recordQuickFixes(EXPRESSION src, List<QuickFix<EXPRESSION>> quickFixes)
            {
                if (src instanceof Expression && !quickFixes.isEmpty())
                {
                    EditorLocationAndErrorRecorder.this.showUnresolvedError((Expression) src, null, ImmutableList.copyOf((List<QuickFix<Expression>>)(List)quickFixes));
                }
                else if (src instanceof TypeExpression && !quickFixes.isEmpty())
                {
                    EditorLocationAndErrorRecorder.this.showUnresolvedError((TypeExpression) src, null, ImmutableList.copyOf((List<QuickFix<TypeExpression>>)(List)quickFixes));
                }
                else if (src instanceof UnitExpression && !quickFixes.isEmpty())
                {
                    EditorLocationAndErrorRecorder.this.showUnresolvedError((UnitExpression) src, null, ImmutableList.copyOf((List<QuickFix<UnitExpression>>)(List)quickFixes));
                }
            }

            @Override
            @SuppressWarnings("recorded")
            public @Recorded @NonNull TypeExp recordTypeNN(Expression expression, @NonNull TypeExp typeExp)
            {
                EditorLocationAndErrorRecorder.this.recordType(expression, Either.right(typeExp));
                return typeExp;
            }

            @Override
            public @Nullable TypeExp recordError(@Recorded Expression src, Either<@Nullable TypeError, TypeExp> errorOrType)
            {
                return errorOrType.<@Nullable TypeExp>either(err -> {
                    if (err != null)
                    {
                        
                        if (err.getCouldNotUnify().stream().anyMatch(this::isOptional) && !err.getCouldNotUnify().stream().allMatch(this::isOptional))
                            showUnresolvedError(src, err.getMessage(), ImmutableList.of(new QuickFix<Expression>(StyledString.s("Show guide for Optional type"), ImmutableList.<String>of(), src, (typeManager, sceneProp) -> {
                                try
                                {
                                    new DocWindow("Optional", "guide-optional.html", null, insertListener, sceneProp).show();
                                }
                                catch (InternalException e)
                                {
                                    Log.log(e);
                                }
                                return null;
                            })));
                        // If they're all numbers and there was an error, must be units problem:
                        else if (err.getCouldNotUnify().stream().allMatch(this::isNumber))
                            showUnresolvedError(src, err.getMessage(), ImmutableList.of(new QuickFix<Expression>(StyledString.s("Show units guide"), ImmutableList.<String>of(), src, (typeManager, sceneProp) -> {
                                try
                                {
                                    new DocWindow("Units", "guide-units.html", null, insertListener, sceneProp).show();
                                }
                                catch (InternalException e)
                                {
                                    Log.log(e);
                                }
                                return null;
                            })));
                        else
                            recordError(src, err.getMessage());
                    }
                    return null;
                }, val -> val);
            }

            private boolean isNumber(TypeExp typeExp)
            {
                return typeExp instanceof NumTypeExp;
            }

            private boolean isOptional(TypeExp typeExp)
            {
                return typeExp instanceof TypeCons && ((TypeCons)typeExp).name.equals("Optional");
            }
        };
    }

    @SuppressWarnings("nullness")
    public CanonicalSpan recorderFor(@Recorded Object expression)
    {
        return positions.get(expression);
    }

    public void addNestedError(ErrorDetails nestedError, @CanonicalLocation int caretPosOffset, @DisplayLocation int displayCaretPosOffset)
    {
        errorsToShow.add(() -> nestedError.offsetBy(caretPosOffset, displayCaretPosOffset));
    }
    
    public void addNestedLocations(EditorLocationAndErrorRecorder nested, @CanonicalLocation int caretPosOffset)
    {
        nested.positions.forEach((e, s) -> positions.put(e, s.offsetBy(caretPosOffset)));
    }
    
    public ImmutableList<ErrorDetails> getErrors()
    {
        ImmutableList.Builder<ErrorDetails> r = ImmutableList.builderWithExpectedSize(errorsToShow.size());

        for (UnresolvedErrorDetails unresolvedErrorDetails : errorsToShow)
        {
            try
            {
                r.add(unresolvedErrorDetails.resolve());
            }
            catch (InternalException e)
            {
                Log.log(e);
            }
        }
        
        return r.build();
    }


    public ImmutableMap<DisplayType, Pair<StyledString, ImmutableList<TextQuickFix>>> getDisplayFor(@CanonicalLocation int canonIndex, Node toRightOf)
    {
        ArrayList<Pair<StyledString, CanonicalSpan>> relevantPrompts = new ArrayList<>();
        ArrayList<Pair<Pair<StyledString, ImmutableList<TextQuickFix>>, CanonicalSpan>> relevantInformation = new ArrayList<>();
        for (Entry<Object, CanonicalSpan> expLocation : positions.entrySet())
        {
            if (expLocation.getValue().touches(canonIndex))
            {
                Pair<StyledString, ImmutableList<TextQuickFix>> info = information.get(expLocation.getKey());
                if (info != null)
                    relevantInformation.add(new Pair<>(info, expLocation.getValue()));
            }
        }
        for (Pair<CanonicalSpan, FXPlatformFunction<Node, StyledString>> entryPrompt : entryPrompts)
        {
            if (entryPrompt.getFirst().touches(canonIndex))
                relevantPrompts.add(new Pair<>(entryPrompt.getSecond().apply(toRightOf), entryPrompt.getFirst()));
        }

        Collections.<Pair<StyledString, CanonicalSpan>>sort(relevantPrompts, Comparator.<Pair<StyledString, CanonicalSpan>, Integer>comparing(p -> p.getSecond().start));
        Collections.<Pair<Pair<StyledString, ImmutableList<TextQuickFix>>, CanonicalSpan>>sort(relevantInformation, Comparator.<Pair<Pair<StyledString, ImmutableList<TextQuickFix>>, CanonicalSpan>, Integer>comparing(p -> p.getSecond().start));

        EnumMap<DisplayType, Pair<StyledString, ImmutableList<TextQuickFix>>> combined = new EnumMap<DisplayType, Pair<StyledString, ImmutableList<TextQuickFix>>>(DisplayType.class);
        for (Pair<StyledString, CanonicalSpan> prompt : relevantPrompts)
        {
            combined.merge(DisplayType.PROMPT, new Pair<>(prompt.getFirst(), ImmutableList.of()), (a, b) -> new Pair<>(StyledString.intercalate(StyledString.s("\n"), ImmutableList.of(a.getFirst(), b.getFirst())), Utility.concatI(a.getSecond(), b.getSecond())));
        }
        for (Pair<Pair<StyledString, ImmutableList<TextQuickFix>>, CanonicalSpan> info : relevantInformation)
        {
            combined.merge(DisplayType.INFORMATION, info.getFirst(), (a, b) -> new Pair<>(StyledString.intercalate(StyledString.s("\n"), ImmutableList.of(a.getFirst(), b.getFirst())), Utility.concatI(a.getSecond(), b.getSecond())));
        }
        
        return ImmutableMap.copyOf(combined);
    }

    public <EXPRESSION> void recordEntryPromptG(@Recorded EXPRESSION expression, FXPlatformFunction<Node, StyledString> prompt)
    {
        if (expression instanceof Expression)
            recordEntryPrompt((Expression)expression, prompt);
    }

    public void recordEntryPrompt(@Recorded Expression expression, FXPlatformFunction<Node, StyledString> prompt)
    {
        CanonicalSpan canonicalSpan = positions.get(expression);
        if (canonicalSpan != null)
            entryPrompts.add(new Pair<>(canonicalSpan, prompt));
    }

    public void recordEntryPrompt(CanonicalSpan canonicalSpan, FXPlatformFunction<Node, StyledString> prompt)
    {
        entryPrompts.add(new Pair<>(canonicalSpan, prompt));
    }

    public InsertListener getInsertListener()
    {
        return insertListener;
    }
}
