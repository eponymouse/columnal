package xyz.columnal.data;

import org.checkerframework.dataflow.qual.Pure;
import records.grammar.MainParser2;
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
    
    public SaveTag(MainParser2.DetailContext detailContext)
    {
        this(detailContext.DETAIL_BEGIN().getText().substring("@BEGIN".length()).trim());
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
