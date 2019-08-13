package records.data;

import org.checkerframework.dataflow.qual.Pure;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.util.Random;

/**
 * A several-letter always-capitalised save tag, to help distinguish
 * tables in the saved files and prevent diff from seeing common lines
 * in different tables.
 */
public class SaveTag
{
    private final String tag;

    public SaveTag(String tag)
    {
        this.tag = tag;
    }

    public static SaveTag generateRandom()
    {
        int index = new Random().nextInt(26 * 26 * 26);
        char[] cs = new char[3];
        for (int i = 0; i < cs.length; i++)
        {
            cs[i] = (char)((index % 26) + 'A');
            index = index / 26;
        }
        return new SaveTag(new String(cs));
    }

    @OnThread(Tag.Any)
    @Pure
    public final String getTag()
    {
        return tag;
    }
}
