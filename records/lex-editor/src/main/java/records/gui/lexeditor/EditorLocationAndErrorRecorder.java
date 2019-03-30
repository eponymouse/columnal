package records.gui.lexeditor;

import annotation.recorded.qual.Recorded;
import annotation.units.SourceLocation;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import log.Log;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.TableAndColumnRenames;
import records.error.InternalException;
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
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.Utility;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;

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
    public static final class Span
    {
        // Start is inclusive, end is exclusive
        public final @SourceLocation int start;
        public final @SourceLocation int end;
        
        public Span(@SourceLocation int start, @SourceLocation int end)
        {
            this.start = start;
            this.end = end;
        }

        public static Span fromTo(Span start, Span end)
        {
            return new Span(start.start, end.end);
        }
        
        @SuppressWarnings("units")
        public static final Span START = new Span(0, 0);

        public boolean contains(@SourceLocation int position)
        {
            return start <= position && position <= end;
        }

        @Override
        public String toString()
        {
            return "[" + start + "->" + end + "]";
        }
    }
    
    // We use IdentityHashMap because we want to distinguish between multiple duplicate sub-expressions,
    // e.g. in the expression 2 + abs(2), we want to assign any error to the right 2.  Because of this
    // we use identity hash map, and we cannot use Either (which would break this property).  So two maps it is:
    private final IdentityHashMap<Expression, Span> expressionDisplayers = new IdentityHashMap<>();
    private final IdentityHashMap<UnitExpression, Span> unitDisplayers = new IdentityHashMap<>();
    private final IdentityHashMap<TypeExpression, Span> typeDisplayers = new IdentityHashMap<>();
    private final IdentityHashMap<Expression, Either<TypeConcretisationError, TypeExp>> types = new IdentityHashMap<>();

    private static interface UnresolvedErrorDetails
    {
        public ErrorDetails resolve() throws InternalException;
    }
    
    public static class ErrorDetails
    {
        public final Span location;
        public final StyledString error;
        public final ImmutableList<TextQuickFix> quickFixes;

        public ErrorDetails(Span location, StyledString error, ImmutableList<TextQuickFix> quickFixes)
        {
            this.location = location;
            this.error = error;
            this.quickFixes = quickFixes;
        }
    }
    
    private final ArrayList<UnresolvedErrorDetails> errorsToShow = new ArrayList<>();
    
    public EditorLocationAndErrorRecorder()
    {
    }

    // Non-generic version that avoids type checker arguments.
    public @NonNull @Recorded Expression record(Span location,  @NonNull Expression e)
    {
        return this.<Expression>recordG(location, e);
    }
    
    // Generic version that lets you return a particular expression
    @SuppressWarnings({"initialization", "unchecked", "recorded"})
    public <EXPRESSION extends Expression> @NonNull @Recorded EXPRESSION recordG(Span location,  @NonNull EXPRESSION e)
    {
        expressionDisplayers.put(e, location);
        return e;
    }

    @SuppressWarnings({"initialization", "recorded"})
    public <UNIT_EXPRESSION extends UnitExpression> @NonNull @Recorded UNIT_EXPRESSION recordUnit(Span location, @NonNull UNIT_EXPRESSION e)
    {
        unitDisplayers.put(e, location);
        return e;
    }

    @SuppressWarnings({"initialization", "recorded"})
    public <TYPE_EXPRESSION extends TypeExpression> @NonNull @Recorded TYPE_EXPRESSION recordType(Span location, @NonNull TYPE_EXPRESSION e)
    {
        typeDisplayers.put(e, location);
        return e;
    }

    private void showUnresolvedError(Expression e, @Nullable StyledString error, ImmutableList<QuickFix<Expression>> quickFixes)
    {
        errorsToShow.add(() -> {
            @Nullable Span resolvedLocation = expressionDisplayers.get(e);
            if (resolvedLocation != null)
            {
                return new ErrorDetails(resolvedLocation, error == null ? StyledString.s("") : error, Utility.mapListI(quickFixes, q -> new TextQuickFix(expressionDisplayers.get(q.getReplacementTarget()), exp -> exp.save(false, BracketedStatus.MISC, new TableAndColumnRenames(ImmutableMap.of())), q)));
            }
            else
            {
                throw new InternalException("Could not resolve location for expression: " + e);
            }
        });
    }
    
    public void addErrorAndFixes(Span showFor, StyledString error, ImmutableList<TextQuickFix> quickFixes)
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
                            errorDisplayer.addErrorAndFixes(err.getErrorText(), ExpressionEditorUtil.quickFixesForTypeError(typeManager, expression, err.getSuggestedTypeFix()));
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
            }

            @SuppressWarnings("unchecked")
            @Override
            public <EXPRESSION extends StyledShowable> void recordQuickFixes(EXPRESSION src, List<QuickFix<EXPRESSION>> quickFixes)
            {
                if (src instanceof Expression && !quickFixes.isEmpty())
                {
                    EditorLocationAndErrorRecorder.this.showUnresolvedError((Expression) src, null, ImmutableList.copyOf((List<QuickFix<Expression>>)(List)quickFixes));
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
    public Span recorderFor(@Recorded Expression expression)
    {
        return expressionDisplayers.get(expression);
    }

    @SuppressWarnings("nullness")
    public Span recorderFor(@Recorded TypeExpression expression)
    {
        return typeDisplayers.get(expression);
    }

    @SuppressWarnings("nullness")
    public Span recorderFor(@Recorded UnitExpression expression)
    {
        return unitDisplayers.get(expression);
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
}
