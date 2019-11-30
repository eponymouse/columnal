package records.rinterop;

import java.io.DataInputStream;
import java.io.IOException;

final class V2Header
{
    private final byte header[];
    final int formatVersion;
    private final int writerVersion;
    private final int readerVersion;
    
    public V2Header(DataInputStream d) throws IOException
    {
        header = new byte[2];
        d.readFully(header);
        formatVersion = d.readInt();
        writerVersion = d.readInt();
        readerVersion = d.readInt();
    }
}
