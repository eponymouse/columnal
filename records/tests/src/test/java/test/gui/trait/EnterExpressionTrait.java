package test.gui.trait;

import annotation.identifier.qual.ExpressionIdentifier;
import annotation.qual.Value;
import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableList;
import javafx.scene.input.KeyCode;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.testfx.api.FxRobotInterface;
import records.data.ColumnId;
import records.data.TableId;
import records.data.datatype.DataType.DateTimeInfo.DateTimeType;
import records.data.datatype.TypeManager;
import records.data.datatype.TypeManager.TagInfo;
import records.error.InternalException;
import records.grammar.ExpressionLexer;
import records.transformations.expression.*;
import records.transformations.expression.AddSubtractExpression.AddSubtractOp;
import records.transformations.expression.ComparisonExpression.ComparisonOperator;
import records.transformations.expression.DefineExpression.DefineItem;
import records.transformations.expression.DefineExpression.Definition;
import records.transformations.expression.Expression.SaveDestination;
import records.transformations.expression.MatchExpression.MatchClause;
import records.transformations.expression.MatchExpression.Pattern;
import records.transformations.expression.function.StandardFunctionDefinition;
import records.transformations.expression.type.TypeExpression;
import records.transformations.expression.visitor.ExpressionVisitorFlat;
import records.transformations.expression.visitor.ExpressionVisitorStream;
import styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.Pair;
import utility.UnitType;
import utility.Utility;

import java.time.temporal.TemporalAccessor;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

@SuppressWarnings("recorded")
public interface EnterExpressionTrait extends FxRobotInterface, EnterTypeTrait, FocusOwnerTrait, AutoCompleteTrait
{
    static final int DELAY = 1;
    
    public static enum EntryBracketStatus
    {
        // e.g. when a function call argument
        DIRECTLY_ROUND_BRACKETED,
        // e.g. the top-level condition in @if ... @then
        SURROUNDED_BY_KEYWORDS,
        // e.g. an argument of plus
        SUB_EXPRESSION
    }
    
    @OnThread(Tag.Any)
    public default void enterExpression(TypeManager typeManager, Expression expression, EntryBracketStatus bracketedStatus, Random r)
    {
        expression.visit(new ExpressionVisitorFlat<UnitType>()
        {
            @Override
            protected UnitType makeDef(Expression expression)
            {
                throw new RuntimeException("Unsupported expression: " + expression.getClass());
                //return UnitType.UNIT;
            }

            @Override
            public UnitType record(RecordExpression self, ImmutableList<Pair<@ExpressionIdentifier String, @Recorded Expression>> members)
            {
                if (bracketedStatus != EntryBracketStatus.DIRECTLY_ROUND_BRACKETED)
                {
                    write("(");
                    push(KeyCode.DELETE);
                }
                for (int i = 0; i < members.size(); i++)
                {
                    if (i > 0)
                        write(",");
                    write(members.get(i).getFirst()  + ": ");
                    enterExpression(typeManager, members.get(i).getSecond(), EntryBracketStatus.SUB_EXPRESSION, r);

                }
                if (bracketedStatus != EntryBracketStatus.DIRECTLY_ROUND_BRACKETED)
                    write(")");
                return UnitType.UNIT;
            }

            @Override
            public UnitType list(ArrayExpression self, ImmutableList<@Recorded Expression> members)
            {
                write("[");
                push(KeyCode.DELETE);
                for (int i = 0; i < members.size(); i++)
                {
                    if (i > 0)
                        write(",");
                    enterExpression(typeManager, members.get(i), EntryBracketStatus.SUB_EXPRESSION, r);

                }
                write("]");
                return UnitType.UNIT;
            }

            @Override
            public UnitType litText(StringLiteral self, String value)
            {
                write('"');
                push(KeyCode.DELETE);
                write(((StringLiteral)expression).editString().replaceAll("\"", "^q"));
                write('"');
                return UnitType.UNIT;
            }

            @Override
            public UnitType litNumber(NumericLiteral self, @Value Number value, @Nullable UnitExpression unit)
            {
                write(self.editString());
                if (unit != null)
                {
                    write("{");
                    push(KeyCode.DELETE);
                    try
                    {
                        enterUnit(unit, r);
                    }
                    catch (InternalException e)
                    {
                        throw new RuntimeException(e);
                    }
                    write("}");
                }
                return UnitType.UNIT;
            }

            @Override
            public UnitType litTemporal(TemporalLiteral self, DateTimeType literalType, String content, Either<StyledString, TemporalAccessor> value)
            {
                write(self.toString(), DELAY);
                // Delete trailing curly and other:
                int opened = (int)expression.toString().codePoints().filter(ch -> ch == '{' || ch == '[' || ch == '(').count();
                for (int i = 0; i < opened; i++)
                {
                    push(KeyCode.DELETE);
                }
                return UnitType.UNIT;
            }

            @Override
            public UnitType litBoolean(BooleanLiteral self, @Value Boolean value)
            {
                write(self.toString(), DELAY);
                return UnitType.UNIT;
            }

            @Override
            public UnitType litType(TypeLiteralExpression self, TypeExpression type)
            {
                write("type{", DELAY);
                push(KeyCode.DELETE);
                try
                {
                    enterType(self.getType(), r);
                }
                catch (InternalException e)
                {
                    throw new RuntimeException(e);
                }
                write("}");
                return UnitType.UNIT;
            }

            @Override
            public UnitType litUnit(UnitLiteralExpression self, @Recorded UnitExpression unitExpression)
            {
                write("unit{", DELAY);
                push(KeyCode.DELETE);
                write(self.getUnit().save(SaveDestination.EDITOR, true));
                write("}");
                return UnitType.UNIT;
            }

            @Override
            public UnitType call(CallExpression self, @Recorded Expression callTarget, ImmutableList<@Recorded Expression> arguments)
            {
                enterExpression(typeManager, callTarget, EntryBracketStatus.SUB_EXPRESSION, r);
                write("(");
                // Delete closing bracket:
                push(KeyCode.DELETE);
                for (int i = 0; i < arguments.size(); i++)
                {
                    if (i > 0)
                        write(",");
                    enterExpression(typeManager, arguments.get(i), EntryBracketStatus.DIRECTLY_ROUND_BRACKETED, r);
                }
                write(")");
                return UnitType.UNIT;
            }

            /*
            @Override
            public UnitType standardFunction(StandardFunction self, StandardFunctionDefinition functionDefinition)
            {
                String name = functionDefinition.getName();
                if (r.nextBoolean())
                {
                    write(name, DELAY);
                    if (r.nextBoolean())
                    {
                        scrollLexAutoCompleteToOption(name + "()");
                        push(KeyCode.ENTER);
                        // Get rid of brackets; if in a call expression, we will add them again:
                        push(KeyCode.BACK_SPACE);
                        push(KeyCode.DELETE);
                    }
                }
                else
                {
                    write(name.substring(0, 1 + r.nextInt(name.length() - 1)));
                    scrollLexAutoCompleteToOption(name + "()");
                    push(KeyCode.ENTER);
                    // Get rid of brackets; if in a call expression, we will add them again:
                    push(KeyCode.BACK_SPACE);
                    push(KeyCode.DELETE);
                }
                return UnitType.UNIT;
            }

            @Override
            public UnitType constructor(ConstructorExpression tag, Either<String, TagInfo> tagInfo)
            {
                String tagName = tag.getName();
                boolean multipleTagsOfThatName = typeManager.ambiguousTagName(tagName);

                if (multipleTagsOfThatName && tag.getTypeName() != null)
                    tagName = tag.getTypeName().getRaw() + "\\" + tagName;
                write(tagName, DELAY);
                if (r.nextBoolean())
                {
                    scrollLexAutoCompleteToOption(tagName + (tag._test_hasInner() ? "()" : ""));
                    push(KeyCode.ENTER);
                    if (tag._test_hasInner())
                    {
                        // Get rid of brackets; if in a call expression, we will add them again:
                        push(KeyCode.BACK_SPACE);
                        push(KeyCode.DELETE);
                    }
                }
                return UnitType.UNIT;
            }
             */

            @Override
            public UnitType match(MatchExpression self, @Recorded Expression expression, ImmutableList<MatchClause> clauses)
            {
                write(Utility.literal(ExpressionLexer.VOCABULARY, ExpressionLexer.MATCH), DELAY);
                enterExpression(typeManager, expression, EntryBracketStatus.SURROUNDED_BY_KEYWORDS, r);
                for (MatchClause matchClause : clauses)
                {
                    write(Utility.literal(ExpressionLexer.VOCABULARY, ExpressionLexer.CASE), DELAY);
                    for (int i = 0; i < matchClause.getPatterns().size(); i++)
                    {
                        if (i > 0)
                            write(Utility.literal(ExpressionLexer.VOCABULARY, ExpressionLexer.ORCASE), DELAY);
                        Pattern pattern = matchClause.getPatterns().get(i);
                        enterExpression(typeManager, pattern.getPattern(), EntryBracketStatus.SURROUNDED_BY_KEYWORDS, r);
                        @Nullable Expression guard = pattern.getGuard();
                        if (guard != null)
                        {
                            write(Utility.literal(ExpressionLexer.VOCABULARY, ExpressionLexer.CASEGUARD), DELAY);
                            enterExpression(typeManager, guard, EntryBracketStatus.SURROUNDED_BY_KEYWORDS, r);
                        }
                    }
                    write(Utility.literal(ExpressionLexer.VOCABULARY, ExpressionLexer.THEN), DELAY);
                    enterExpression(typeManager, matchClause.getOutcome(), EntryBracketStatus.SURROUNDED_BY_KEYWORDS, r);
                }
                // To finish whole match expression:
                write(Utility.literal(ExpressionLexer.VOCABULARY, ExpressionLexer.ENDMATCH), DELAY);
                return UnitType.UNIT;
            }

            @Override
            public UnitType ifThenElse(IfThenElseExpression self, @Recorded Expression condition, @Recorded Expression thenExpression, @Recorded Expression elseExpression)
            {
                write(Utility.literal(ExpressionLexer.VOCABULARY, ExpressionLexer.IF), DELAY);
                enterExpression(typeManager, condition, EntryBracketStatus.SURROUNDED_BY_KEYWORDS, r);
                write(Utility.literal(ExpressionLexer.VOCABULARY, ExpressionLexer.THEN), DELAY);
                enterExpression(typeManager, thenExpression, EntryBracketStatus.SURROUNDED_BY_KEYWORDS, r);
                write(Utility.literal(ExpressionLexer.VOCABULARY, ExpressionLexer.ELSE), DELAY);
                enterExpression(typeManager, elseExpression, EntryBracketStatus.SURROUNDED_BY_KEYWORDS, r);
                write(Utility.literal(ExpressionLexer.VOCABULARY, ExpressionLexer.ENDIF), DELAY);
                return UnitType.UNIT;
            }

            @Override
            public UnitType matchAnything(MatchAnythingExpression self)
            {
                write("_");
                return UnitType.UNIT;
            }

            @Override
            public UnitType implicitLambdaArg(ImplicitLambdaArg self)
            {
                write("?");
                return UnitType.UNIT;
            }

            @Override
            public UnitType ident(IdentExpression self, @Nullable @ExpressionIdentifier String namespace, ImmutableList<@ExpressionIdentifier String> idents, boolean isVariable)
            {
                if (namespace != null)
                {
                    write(namespace + "\\\\", DELAY);
                }
                write(idents.stream().collect(Collectors.joining("\\")), DELAY);
                
                return UnitType.UNIT;
            }

            @Override
            public UnitType invalidIdent(InvalidIdentExpression self, String text)
            {
                write(text, DELAY);
                return UnitType.UNIT;
            }

            @Override
            public UnitType invalidOps(InvalidOperatorExpression self, ImmutableList<@Recorded Expression> items)
            {
                for (Expression e : items)
                {
                    enterExpression(typeManager, e, EntryBracketStatus.SUB_EXPRESSION, r);
                }
                return UnitType.UNIT;
            }

            @Override
            public UnitType define(DefineExpression self, ImmutableList<DefineItem> defines, @Recorded Expression body)
            {
                write("@define");
                boolean first = true;
                for (DefineItem define : defines)
                {
                    if (!first)
                        write(",");
                    first = false;
                    define.typeOrDefinition.either_(x -> enterExpression(typeManager, x, EntryBracketStatus.SURROUNDED_BY_KEYWORDS, r),
                            x -> {
                                enterExpression(typeManager, x.lhsPattern, EntryBracketStatus.SUB_EXPRESSION, r);
                                write("=");
                                enterExpression(typeManager, x.rhsValue, EntryBracketStatus.SUB_EXPRESSION, r);
                            });
                }
                write("@then");
                enterExpression(typeManager, body, EntryBracketStatus.SURROUNDED_BY_KEYWORDS, r);
                write("@enddefine");
                return UnitType.UNIT;
            }
            
            private UnitType enterNary(NaryOpExpression n)
            {
                if (bracketedStatus == EntryBracketStatus.SUB_EXPRESSION)
                {
                    write("(");
                    push(KeyCode.DELETE);
                }
                for (int i = 0; i < n.getChildren().size(); i++)
                {
                    enterExpression(typeManager, n.getChildren().get(i), EntryBracketStatus.SUB_EXPRESSION, r);
                    if (i < n.getChildren().size() - 1)
                    {
                        write(n._test_getOperatorEntry(i));
                        if (n._test_getOperatorEntry(i).equals("-") || n._test_getOperatorEntry(i).equals("+") || r.nextBoolean())
                            write(" ");
                    }
                }
                if (bracketedStatus == EntryBracketStatus.SUB_EXPRESSION)
                    write(")");
                return UnitType.UNIT;
            }

            @Override
            public UnitType addSubtract(AddSubtractExpression self, ImmutableList<@Recorded Expression> expressions, ImmutableList<AddSubtractOp> ops)
            {
                return enterNary(self);
            }

            @Override
            public UnitType and(AndExpression self, ImmutableList<@Recorded Expression> expressions)
            {
                return enterNary(self);
            }

            @Override
            public UnitType or(OrExpression self, ImmutableList<@Recorded Expression> expressions)
            {
                return enterNary(self);
            }

            @Override
            public UnitType comparison(ComparisonExpression self, ImmutableList<@Recorded Expression> expressions, ImmutableList<ComparisonOperator> operators)
            {
                return enterNary(self);
            }

            @Override
            public UnitType concatText(StringConcatExpression self, ImmutableList<@Recorded Expression> expressions)
            {
                return enterNary(self);
            }

            @Override
            public UnitType multiply(TimesExpression self, ImmutableList<@Recorded Expression> expressions)
            {
                return enterNary(self);
            }
            
            private UnitType enterBinary(BinaryOpExpression b)
            {
                if (bracketedStatus == EntryBracketStatus.SUB_EXPRESSION)
                {
                    write("(");
                    push(KeyCode.DELETE);
                }
                enterExpression(typeManager, b.getLHS(), EntryBracketStatus.SUB_EXPRESSION, r);
                write(b._test_getOperatorEntry());
                if (b._test_getOperatorEntry().equals("-") || b._test_getOperatorEntry().equals("+") || r.nextBoolean())
                    write(" ");
                enterExpression(typeManager, b.getRHS(), EntryBracketStatus.SUB_EXPRESSION, r);
                if (bracketedStatus == EntryBracketStatus.SUB_EXPRESSION)
                    write(")");
                return UnitType.UNIT;
            }

            @Override
            public UnitType notEqual(NotEqualExpression self, @Recorded Expression lhs, @Recorded Expression rhs)
            {
                return enterBinary(self);
            }

            @Override
            public UnitType divide(DivideExpression self, @Recorded Expression lhs, @Recorded Expression rhs)
            {
                return enterBinary(self);
            }

            @Override
            public UnitType plusMinus(PlusMinusPatternExpression self, @Recorded Expression lhs, @Recorded Expression rhs)
            {
                return enterBinary(self);
            }

            @Override
            public UnitType raise(RaiseExpression self, @Recorded Expression lhs, @Recorded Expression rhs)
            {
                return enterBinary(self);
            }

            @Override
            public UnitType hasType(@Recorded HasTypeExpression self, @Recorded Expression lhsVar, @Recorded Expression rhsType)
            {
                enterExpression(typeManager, lhsVar, EntryBracketStatus.SUB_EXPRESSION, r);
                write("::");
                enterExpression(typeManager, rhsType, EntryBracketStatus.SUB_EXPRESSION, r);
                return UnitType.UNIT;
            }

            @Override
            public UnitType field(FieldAccessExpression self, Expression lhsRecord, Expression fieldName)
            {
                if (bracketedStatus == EntryBracketStatus.SUB_EXPRESSION)
                {
                    write("(");
                    push(KeyCode.DELETE);
                }
                enterExpression(typeManager, lhsRecord, EntryBracketStatus.SUB_EXPRESSION, r);
                write("#");
                enterExpression(typeManager, fieldName, EntryBracketStatus.SUB_EXPRESSION, r);
                if (bracketedStatus == EntryBracketStatus.SUB_EXPRESSION)
                    write(")");
                return UnitType.UNIT;
            }

            @Override
            public UnitType equal(EqualExpression self, ImmutableList<@Recorded Expression> expressions, boolean lastIsPattern)
            {
                return enterNary(self);
            }

            @Override
            public UnitType lambda(LambdaExpression self, ImmutableList<@Recorded Expression> parameters, @Recorded Expression body)
            {
                write("@function ");
                for (int i = 0; i < parameters.size(); i++)
                {
                    if (i > 0)
                        write(", ");
                    enterExpression(typeManager, parameters.get(i), EntryBracketStatus.SUB_EXPRESSION, r);
                }
                write (" @then ");
                enterExpression(typeManager, body, EntryBracketStatus.SURROUNDED_BY_KEYWORDS, r);
                write("@endfunction");
                return UnitType.UNIT;
            }
        });
        
        // TODO add randomness to entry methods (e.g. selection from auto-complete
    }
}
