package records.gui.lexeditor;

import annotation.recorded.qual.Recorded;
import annotation.units.CanonicalLocation;
import annotation.units.DisplayLocation;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import log.Log;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.TableAndColumnRenames;
import records.error.InternalException;
import records.gui.lexeditor.TopLevelEditor.DisplayType;
import records.transformations.expression.BracketedStatus;
import records.transformations.expression.ErrorAndTypeRecorder;
import records.transformations.expression.Expression;
import records.transformations.expression.QuickFix;
import records.transformations.expression.UnitExpression;
import records.transformations.expression.type.TypeExpression;
import records.typeExp.TypeConcretisationError;
import records.typeExp.TypeExp;
import styled.StyledShowable;
import styled.StyledString;
import styled.StyledString.Builder;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.Pair;
import utility.Utility;

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
    // A semantic error matches an expression which may span multiple children.
    @OnThread(Tag.Any)
    public static final class CanonicalSpan implements Comparable<CanonicalSpan>
    {
        // Start is inclusive char index, end is exclusive char index
        public final @CanonicalLocation int start;
        public final @CanonicalLocation int end;
        
        public CanonicalSpan(@CanonicalLocation int start, @CanonicalLocation int end)
        {
            this.start = start;
            this.end = end;
        }

        public static CanonicalSpan fromTo(CanonicalSpan start, CanonicalSpan end)
        {
            return new CanonicalSpan(start.start, end.end);
        }
        
        public CanonicalSpan offsetBy(@CanonicalLocation int offsetBy)
        {
            return new CanonicalSpan(start + offsetBy, end + offsetBy);
        }
      
        @SuppressWarnings("units")
        public static final CanonicalSpan START = new CanonicalSpan(0, 0);
        
        // Even though end is typically exclusive, this checks
        // if <= end because for errors etc we still want to display
        // if we touch the extremity.
        public boolean touches(@CanonicalLocation int position)
        {
            return start <= position && position <= end;
        }

        @Override
        public String toString()
        {
            return "[" + start + "->" + end + "]";
        }

        @Override
        public boolean equals(@Nullable Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            CanonicalSpan span = (CanonicalSpan) o;
            return start == span.start &&
                    end == span.end;
        }

        @Override
        public int hashCode()
        {
            return Objects.hash(start, end);
        }

        @Override
        public int compareTo(CanonicalSpan o)
        {
            int c = Integer.compare(start, o.start);
            if (c != 0)
                return c;
            else
                return Integer.compare(end, o.end);
        }

        public CanonicalSpan lhs()
        {
            return new CanonicalSpan(start, start);
        }

        public CanonicalSpan rhs()
        {
            return new CanonicalSpan(end, end);
        }
    }
    
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
    private final ArrayList<Pair<CanonicalSpan, StyledString>> entryPrompts = new ArrayList<>();
    private final IdentityHashMap<Expression, StyledString> information = new IdentityHashMap<>();

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
    
    public EditorLocationAndErrorRecorder()
    {
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
                return new ErrorDetails(resolvedLocation, error == null ? StyledString.s("") : error, Utility.mapListI(quickFixes, q -> new TextQuickFix(positions.get(q.getReplacementTarget()), exp -> exp.save(false, BracketedStatus.DONT_NEED_BRACKETS, new TableAndColumnRenames(ImmutableMap.of())), q)));
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
                return new ErrorDetails(resolvedLocation, error == null ? StyledString.s("") : error, Utility.mapListI(quickFixes, q -> new TextQuickFix(positions.get(q.getReplacementTarget()), exp -> exp.save(false, new TableAndColumnRenames(ImmutableMap.of())), q)));
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
                return new ErrorDetails(resolvedLocation, error == null ? StyledString.s("") : error, Utility.mapListI(quickFixes, q -> new TextQuickFix(positions.get(q.getReplacementTarget()), exp -> exp.save(false, false), q)));
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
            public <EXPRESSION> void recordInformation(EXPRESSION src, StyledString info)
            {
                if (src instanceof Expression)
                    information.put((Expression)src, info);
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


    public ImmutableMap<DisplayType, StyledString> getDisplayFor(@CanonicalLocation int canonIndex)
    {
        ArrayList<Pair<StyledString, CanonicalSpan>> relevantPrompts = new ArrayList<>();
        ArrayList<Pair<StyledString, CanonicalSpan>> relevantInformation = new ArrayList<>();
        for (Entry<Object, CanonicalSpan> expLocation : positions.entrySet())
        {
            if (expLocation.getValue().touches(canonIndex))
            {
                StyledString info = information.get(expLocation.getKey());
                if (info != null)
                    relevantInformation.add(new Pair<>(info, expLocation.getValue()));
            }
        }
        for (Pair<CanonicalSpan, StyledString> entryPrompt : entryPrompts)
        {
            if (entryPrompt.getFirst().touches(canonIndex))
                relevantPrompts.add(new Pair<>(entryPrompt.getSecond(), entryPrompt.getFirst()));
        }

        Collections.<Pair<StyledString, CanonicalSpan>>sort(relevantPrompts, Comparator.<Pair<StyledString, CanonicalSpan>, Integer>comparing(p -> p.getSecond().start));
        Collections.<Pair<StyledString, CanonicalSpan>>sort(relevantInformation, Comparator.<Pair<StyledString, CanonicalSpan>, Integer>comparing(p -> p.getSecond().start));

        EnumMap<DisplayType, StyledString> combined = new EnumMap<DisplayType, StyledString>(DisplayType.class);
        for (Pair<StyledString, CanonicalSpan> prompt : relevantPrompts)
        {
            combined.merge(DisplayType.PROMPT, prompt.getFirst(), (a, b) -> StyledString.intercalate(StyledString.s("\n"), ImmutableList.of(a, b)));
        }
        for (Pair<StyledString, CanonicalSpan> info : relevantInformation)
        {
            combined.merge(DisplayType.INFORMATION, info.getFirst(), (a, b) -> StyledString.intercalate(StyledString.s("\n"), ImmutableList.of(a, b)));
        }
        
        return ImmutableMap.copyOf(combined);
    }

    public <EXPRESSION> void recordEntryPromptG(@Recorded EXPRESSION expression, StyledString prompt)
    {
        if (expression instanceof Expression)
            recordEntryPrompt((Expression)expression, prompt);
    }

    public void recordEntryPrompt(@Recorded Expression expression, StyledString prompt)
    {
        CanonicalSpan canonicalSpan = positions.get(expression);
        if (canonicalSpan != null)
            entryPrompts.add(new Pair<>(canonicalSpan, prompt));
    }

    public void recordEntryPrompt(CanonicalSpan canonicalSpan, StyledString prompt)
    {
        entryPrompts.add(new Pair<>(canonicalSpan, prompt));
    }
}
