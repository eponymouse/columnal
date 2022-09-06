package records.rinterop;

import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.HashMap;

/**
 * This is really just a single int read from R, but it has all the helper methods for interrogating the different parts.
 */
final class TypeHeader
{
    private final int headerBits;
    
    public TypeHeader(DataInputStream d, HashMap<Integer, String> atoms) throws IOException, InternalException, UserException
    {
        headerBits = d.readInt();
    }

    @Nullable RValue readAttributes(DataInputStream d, HashMap<Integer, String> atoms) throws IOException, UserException, InternalException
    {
        final @Nullable RValue attr;
        if (hasAttributes())
        {
            // Also read trailing attributes:
            attr = RRead.readItem(d, atoms);
        }
        else
        {
            attr = null;
        }
        return attr;
    }

    @Nullable RValue readTag(DataInputStream d, HashMap<Integer, String> atoms) throws IOException, UserException, InternalException
    {
        final @Nullable RValue tag;
        if (hasTag())
        {
            // Also read trailing attributes:
            tag = RRead.readItem(d, atoms);
        }
        else
        {
            tag = null;
        }
        return tag;
    }

    public int getType()
    {
        return headerBits & 0xFF;
    }
    
    public boolean hasAttributes()
    {
        return (headerBits & 0x200) != 0;
    }

    public boolean hasTag()
    {
        return (headerBits & 0x400) != 0;
    }

    public boolean isObject()
    {
        return (headerBits & 0x100) != 0;
    }
    
    public int getReference(DataInputStream d) throws IOException
    {
        int ref = headerBits >>> 8;
        if (ref == 0)
            return d.readInt();
        else
            return ref;
    }
}
