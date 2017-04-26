package test;

import com.pholser.junit.quickcheck.random.SourceOfRandomness;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.util.stream.Collectors;

/**
 * Created by neil on 26/10/2016.
 */
public class TestTextFile
{
    private final File file;
    private final Charset charset;
    private final int lineCount;

    public TestTextFile(SourceOfRandomness rnd) throws IOException
    {
        charset = rnd.choose(Charset.availableCharsets().values().stream().filter(c -> !c.displayName().contains("JIS") && !c.displayName().contains("2022") && !c.displayName().contains("IBM")).collect(Collectors.<Charset>toList()));
        file = File.createTempFile("aaa", "bbb");
        file.deleteOnExit();

        BufferedWriter w = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), charset));

        //TODO write columns not just junk
        lineCount = rnd.nextInt(0, 100000);
        for (int i = 0; i < lineCount; i++)
            w.write(String.valueOf(rnd.nextLong()) + "\n");

        w.close();
    }

    public File getFile()
    {
        return file;
    }

    public int getLineCount()
    {
        return lineCount;
    }

    @Override
    public String toString()
    {
        return "TestTextFile{" +
            "lineCount=" + lineCount +
            '}';
    }

    public Charset getCharset()
    {
        return charset;
    }
}
