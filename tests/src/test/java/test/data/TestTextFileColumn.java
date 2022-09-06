package test.data;

import annotation.identifier.qual.ExpressionIdentifier;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import org.junit.runner.RunWith;
import records.data.ColumnId;
import records.data.DataTestUtil;
import records.data.KnownLengthRecordSet;
import records.data.RecordSet;
import records.data.TextFileColumn;
import records.data.datatype.DataType;
import records.data.datatype.DataType.DataTypeVisitor;
import records.data.datatype.DataType.DateTimeInfo;
import records.data.datatype.DataType.TagType;
import records.data.datatype.NumberInfo;
import records.data.datatype.TypeId;
import records.data.unit.Unit;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import test.gen.GenFile;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.Either;
import xyz.columnal.utility.IdentifierUtility;
import xyz.columnal.utility.SimulationFunction;
import xyz.columnal.utility.Utility;
import xyz.columnal.utility.Utility.ReadState;

import java.io.IOException;

@RunWith(JUnitQuickcheck.class)
public class TestTextFileColumn
{
    @Property(trials=10)
    @OnThread(Tag.Simulation)
    public void testTextFileColumn(@From(GenFile.class) GeneratedTextFile generatedTextFile) throws UserException, InternalException
    {
        KnownLengthRecordSet recordSet = new KnownLengthRecordSet(
                Utility.mapListExI_Index(generatedTextFile.getColumnTypes(), (i, t) -> makeColumn(generatedTextFile, i, t)), generatedTextFile.getLineCount());

        for (int column = 0; column < generatedTextFile.getColumnCount(); column++)
        {
            for (int line = 0; line < generatedTextFile.getLineCount(); line++)
            {
                DataTestUtil.assertValueEqual("Col " + column + " index " + line, generatedTextFile.getExpectedValue(column, line), recordSet.getColumns().get(column).getType().getCollapsed(line));
            }
        }
    }
    
    private SimulationFunction<RecordSet, TextFileColumn> makeColumn(GeneratedTextFile f, int index, DataType dataType) throws UserException, InternalException
    {
        try
        {
            ColumnId columnName = new ColumnId(IdentifierUtility.identNum("C", index));
            ReadState readState = new ReadState(f.getFile(), f.getCharset(), 0);
            return rs -> dataType.apply(new DataTypeVisitor<TextFileColumn>()
            {
                @Override
                public TextFileColumn number(NumberInfo numberInfo) throws InternalException, UserException
                {
                    return TextFileColumn.numericColumn(rs, readState, f.getSeparator(), f.getQuote(), columnName, index, f.getColumnCount(), numberInfo, s -> s);
                }

                @Override
                public TextFileColumn text() throws InternalException, UserException
                {
                    return TextFileColumn.stringColumn(rs, readState, f.getSeparator(), f.getQuote(), columnName, index, f.getColumnCount());
                }

                @Override
                public TextFileColumn date(DateTimeInfo dateTimeInfo) throws InternalException, UserException
                {
                    return TextFileColumn.dateColumn(rs, readState, f.getSeparator(), f.getQuote(), columnName, index, f.getColumnCount(), dateTimeInfo, (String s) -> {
                        try
                        {
                            return Either.right(dateTimeInfo.fromParsed(dateTimeInfo.getStrictFormatter().parse(s)));
                        }
                        catch (InternalException e)
                        {
                            throw new RuntimeException(e);
                        }
                    });
                }

                @Override
                public TextFileColumn bool() throws InternalException, UserException
                {
                    throw new InternalException("bool");
                }

                @Override
                public TextFileColumn tagged(TypeId typeName, ImmutableList<Either<Unit, DataType>> typeVars, ImmutableList<TagType<DataType>> tags) throws InternalException, UserException
                {
                    throw new InternalException("tagged");
                }

                @Override
                public TextFileColumn record(ImmutableMap<@ExpressionIdentifier String, DataType> fields) throws InternalException, UserException
                {
                    throw new InternalException("record");
                }

                @Override
                public TextFileColumn array(DataType inner) throws InternalException, UserException
                {
                    throw new InternalException("array");
                }
            });
        }
        catch (IOException e)
        {
            throw new UserException("IO", e);
        }
    }
}
