package test.gen;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataTypeUtility;
import test.data.GeneratedTextFile;
import test.gen.GenTypeAndValueGen.TypeAndValueGen;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Utility;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Created by neil on 26/10/2016.
 */
public class GenFile extends Generator<GeneratedTextFile>
{
    public GenFile()
    {
        super(GeneratedTextFile.class);
    }

    @Override
    @SuppressWarnings("nullness")
    @OnThread(value = Tag.Simulation, ignoreParent = true)
    public @Nullable GeneratedTextFile generate(SourceOfRandomness rnd, GenerationStatus generationStatus)
    {
        try
        {
            File file = File.createTempFile("aaa", "bbb");
            file.deleteOnExit();

            // We include a multibyte delimiter to stress test:
            String sep = rnd.choose(Arrays.asList(",", ";", "\t", new String(new int[] {0x1F806}, 0, 1)));
            String quot = rnd.choose(Arrays.asList("\"", "\'", new String(new int[] {0x1F962}, 0, 1)));
            
            Charset charset = rnd.<@NonNull Charset>choose(Charset.availableCharsets().values().stream().filter(c ->
                !c.displayName().contains("JIS") &&
                !c.displayName().contains("2022") &&
                !c.displayName().contains("IBM") &&
                !c.displayName().contains("COMPOUND_TEXT") &&
                c.newEncoder().canEncode(sep) && c.newEncoder().canEncode(quot)
            ).collect(Collectors.<Charset>toList()));

            BufferedWriter w = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), charset));

            int columnCount = rnd.nextInt(1, 6);
            int lineCount = rnd.nextInt(0, 10000);

            TypeAndValueGen[] columnTypes = new TypeAndValueGen[columnCount];
            GenTypeAndValueGen typeGen = new GenTypeAndValueGen(true);
            ArrayList<ImmutableList.Builder<@Value Object>> columnValues = new ArrayList<>();
            for (int i = 0; i < columnCount; i++)
            {
                columnTypes[i] = typeGen.generate(rnd, generationStatus);
                columnValues.add(ImmutableList.builderWithExpectedSize(lineCount));
            }

            
            for (int line = 0; line < lineCount; line++)
            {
                for (int column = 0; column < columnTypes.length; column++)
                {
                    @Value Object value = columnTypes[column].makeValue();
                    // We don't want quoting and escaping of strings, so treat them different
                    String string;
                    if (columnTypes[column].getType().isText())
                    {
                        string = (String)value;

                        if (!charset.newEncoder().canEncode(string))
                        {
                            string = "" + rnd.nextDouble();
                            value = DataTypeUtility.value(string); 
                        }
                    }
                    else
                    {
                        string = DataTypeUtility.valueToString(columnTypes[column].getType(), value, null);
                    }

                    boolean quote = string.contains(sep) || string.contains(quot) || rnd.nextInt(3) == 1;

                    if (quote)
                    {
                        string = quot + string.replaceAll(quot, quot + quot) + quot;
                    }

                    w.write((column > 0 ? sep : "") + string);
                    columnValues.get(column).add(value);
                }
                w.write("\n");
            }

            w.close();
            
            return new GeneratedTextFile(file, charset, lineCount, sep, quot,
                Arrays.stream(columnTypes).map(t -> t.getType()).collect(ImmutableList.toImmutableList()),
                    Utility.<Builder<@Value Object>, ImmutableList<@Value Object>>mapListI(columnValues, Builder::build));
        }
        catch (Exception e)
        {
            e.printStackTrace();
            return null;
        }
    }
}
