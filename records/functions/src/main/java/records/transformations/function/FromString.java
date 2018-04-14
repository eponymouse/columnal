package records.transformations.function;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataType;
import records.data.datatype.DataType.DataTypeVisitorEx;
import records.data.datatype.DataType.DateTimeInfo;
import records.data.datatype.DataType.TagType;
import records.data.datatype.DataTypeUtility;
import records.data.datatype.NumberInfo;
import records.data.datatype.TypeId;
import records.error.InternalException;
import records.error.UserException;
import records.types.MutVar;
import records.types.TypeExp;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;
import utility.TaggedValue;
import utility.Utility;
import utility.Utility.ListEx;
import utility.Utility.ListExList;
import utility.ValueFunction;

import java.text.ParsePosition;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

public class FromString extends FunctionDefinition
{
    public FromString()
    {
        super("from text", "from.string.mini", typeManager -> new FunctionTypes(typeManager,
            new MutVar(null),
            TypeExp.text(null)
        ) {
            @Override
            protected ValueFunction makeInstanceAfterTypeCheck() throws UserException, InternalException
            {
                return new Instance(returnType.toConcreteType(typeManager).eitherEx(err -> {throw new UserException(err.getErrorText().toPlain());}, x -> x));
            }
        });
    }

    public static FunctionGroup group()
    {
        return new FunctionGroup("from.string.short", new FromString());
    }

    private static class Instance extends ValueFunction
    {
        private final DataType type;

        public Instance(DataType type)
        {
            this.type = type;
        }


        @Override
        public @Value Object call(@Value Object param) throws UserException, InternalException
        {
            return convertFromString(type, new StringView(param.toString()));
        }

        // The StringView gets modified as we process it.
        @OnThread(Tag.Simulation)
        private @Value Object convertFromString(DataType type, StringView src) throws InternalException, UserException
        {
            return type.apply(new DataTypeVisitorEx<@Value Object, UserException>()
            {
                @Override
                @OnThread(Tag.Simulation)
                public @Value Object number(NumberInfo numberInfo) throws InternalException, UserException
                {
                    // First, we need to consume the chars for the number:
                    StringBuilder s = new StringBuilder();
                    if (src.tryRead("+"))
                    {
                        s.append("+");
                    }
                    else if (src.tryRead("-"))
                    {
                        s.append("-");
                    }
                    s.append(src.consumeNumbers());
                    if (src.tryRead("."))
                    {
                        s.append(".").append(src.consumeNumbers());
                    }
                    
                    return DataTypeUtility.value(Utility.parseNumber(s.toString()));
                }

                @Override
                @OnThread(Tag.Simulation)
                public @Value Object text() throws InternalException, UserException
                {
                    if (!src.tryRead("\""))
                        throw new UserException("Expected start of string but found: " + src.snippet());
                    // TODO deal with escaped quotes
                    @Nullable String content = src.readUntil('\"');
                    if (content != null)
                        return DataTypeUtility.value(content);
                    else
                        throw new UserException("Could not find end of string");
                }

                @Override
                @OnThread(Tag.Simulation)
                public @Value Object date(DateTimeInfo dateTimeInfo) throws InternalException, UserException
                {
                    DateTimeFormatter formatter = dateTimeInfo.getFormatter();
                    try
                    {
                        src.skipSpaces();
                        ParsePosition position = new ParsePosition(src.charStart);
                        TemporalAccessor temporalAccessor = formatter.parse(src.original, position);
                        src.charStart = position.getIndex();
                        return DataTypeUtility.value(dateTimeInfo, temporalAccessor);
                    }
                    catch (DateTimeParseException e)
                    {
                        throw new UserException("Expected date/time value but found: " + src.snippet());
                    }
                }

                @Override
                @OnThread(Tag.Simulation)
                public @Value Object bool() throws InternalException, UserException
                {
                    if (src.tryRead("true"))
                        return DataTypeUtility.value(true);
                    else if (src.tryRead("false"))
                        return DataTypeUtility.value(false);
                    else
                        throw new UserException("Expected boolean but found: " + src.snippet());
                }

                @Override
                @OnThread(Tag.Simulation)
                public @Value Object tagged(TypeId typeName, ImmutableList<DataType> typeVars, ImmutableList<TagType<DataType>> tags) throws InternalException, UserException
                {
                    ArrayList<Pair<Integer, TagType<DataType>>> indexedTags = new ArrayList<>();
                    for (int i = 0; i < tags.size(); i++)
                    {
                        indexedTags.add(new Pair<>(i, tags.get(i)));
                    }
                    // Need to check longest tags first, otherwise we may consume a short tag
                    // which is a prefix of a longer tag's name.  Compare by negative length
                    // to put longest first:
                    Collections.sort(indexedTags, Comparator.comparing(p -> -p.getSecond().getName().length()));

                    for (Pair<Integer, TagType<DataType>> indexedTag : indexedTags)
                    {
                        if (src.tryRead(indexedTag.getSecond().getName()))
                        {
                            // Found it!
                            if (indexedTag.getSecond().getInner() != null)
                            {
                                return new TaggedValue(indexedTag.getFirst(), convertFromString(indexedTag.getSecond().getInner(), src));
                            }
                            else
                            {
                                return new TaggedValue(indexedTag.getFirst(), null);
                            }
                        }
                    }
                    throw new UserException("Looking for tags but found unrecognised tag: " + src.snippet());
                }

                @Override
                @OnThread(Tag.Simulation)
                public @Value Object tuple(ImmutableList<DataType> inner) throws InternalException, UserException
                {
                    if (!src.tryRead("("))
                        throw new UserException("Expected tuple, but no opening round bracket, instead found: " + src.snippet());

                    
                    @Value Object items @Value [] = DataTypeUtility.value(new @Value Object[inner.size()]);
                    for (int i = 0; i < inner.size(); i++)
                    {
                        if (i > 0)
                        {
                            // Must be comma if not first item:
                            if (!src.tryRead(","))
                                throw new UserException("Expected comma after tuple item but found: " + src.snippet());
                        }
                        items[i] = convertFromString(inner.get(i), src);
                    }
                    if (!src.tryRead(")"))
                        throw new UserException("Expected round bracket at end of tuple but found: " + src.snippet());
                    return items;
                }

                @Override
                @OnThread(Tag.Simulation)
                public @Value Object array(@Nullable DataType inner) throws InternalException, UserException
                {
                    if (!src.tryRead("["))
                        throw new UserException("Expected list, but no opening square bracket, instead found: " + src.snippet());
                    
                    if (src.tryRead("]"))
                        return ListEx.empty();
                    if (inner == null)
                        throw new UserException("Expected empty list but found data items: " + src.snippet());

                    ArrayList<@Value Object> items = new ArrayList<>();
                    while (!src.tryRead("]"))
                    {
                        if (!items.isEmpty())
                        {
                            // Must be comma if not first item:
                            if (!src.tryRead(","))
                                throw new UserException("Expected comma after list item but found " + src.snippet());
                        }
                        items.add(convertFromString(inner, src));
                    }
                    return new ListExList(items);
                }
            });
        }
    }
    
    // Keeps track of a trailing substring of a string.  Saves memory compared to copying
    // the substrings over and over.  The data is immutable, the position is mutable.
    public static class StringView
    {
        private final String original;
        private int charStart;
        
        public StringView(String s)
        {
            this.original = s;
            this.charStart = 0;
        }
        
        public StringView(StringView stringView)
        {
            this.original = stringView.original;
            this.charStart = stringView.charStart;
        }
        
        // Tries to read the given literal, having skipped any spaces at current position.
        // If found, the string is consumed and true is returned. If not found, the spaces
        // are still consumed, and false is returned.
        public boolean tryRead(String literal)
        {
            skipSpaces();
            if (original.regionMatches(charStart, literal, 0, literal.length()))
            {
                charStart += literal.length();
                return true;
            }
            return false;
        }

        private void skipSpaces()
        {
            // Don't try and get clever recurse to call tryRead, because it calls us!
            while (charStart < original.length() && original.charAt(charStart) == ' ')
                charStart += 1;
        }

        public String snippet()
        {
            StringBuilder s = new StringBuilder();
            // Add prefix:
            s.append("\"" + original.substring(Math.max(0, charStart - 20), charStart) + ">>>");
            return s.append(original.substring(charStart, Math.min(charStart + 20, original.length())) + "\"").toString();
        }

        // Reads up until that character, and also consumes that character
        // Returns null if end of string is found first
        public @Nullable String readUntil(char c)
        {
            int start = charStart;
            while (charStart < original.length() && original.charAt(charStart) != c)
            {
                charStart += 1;
            }
            if (charStart >= original.length())
                return null;
            // End is exclusive, but then add one to consume it:
            return original.substring(start, charStart++);
        }

        // Doesn't skip spaces!
        public String consumeNumbers()
        {
            int start = charStart;
            while (charStart < original.length() && Character.isDigit(original.charAt(charStart)))
                charStart += 1;
            return original.substring(start, charStart);
        }
    }
}
