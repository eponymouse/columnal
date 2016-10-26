import com.pholser.junit.quickcheck.random.SourceOfRandomness;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

/**
 * Created by neil on 26/10/2016.
 */
public class TestTextFile
{
    private File file;
    private final int lineCount;

    public TestTextFile(SourceOfRandomness rnd) throws IOException
    {
        file = File.createTempFile("aaa", "bbb");
        file.deleteOnExit();

        BufferedWriter w = new BufferedWriter(new FileWriter(file));

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
}
