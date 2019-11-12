package records.rinterop;

import com.google.common.collect.ImmutableList;
import records.error.InternalException;
import records.error.UserException;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.DeflaterInputStream;
import java.util.zip.GZIPInputStream;

public class RData
{
    private static class V2Header
    {
        private final byte header[];
        private final int formatVersion;
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
    
    public static RValue readRData(File rFilePath) throws IOException, InternalException, UserException
    {
        InputStream rds = openInputStream(rFilePath);
        DataInputStream d = new DataInputStream(rds);
        byte[] header = new byte[5];
        d.mark(10);
        d.readFully(header);
        boolean rData = false;
        if (header[0] == 'R' && header[1] == 'D' && header[2] == 'X' && header[4] == '\n')
        {
            rData = true;
            throw new InternalException("RData not yet supported");
        }
        else
        {
            d.reset();
        }
        
        V2Header v2Header = new V2Header(d);
        
        if (v2Header.formatVersion == 3)
        {
            String encoding = readLenString(d);
        }

        return readItem(d);
    }

    private static RValue readItem(DataInputStream d) throws IOException, UserException
    {
        int objHeader = d.readInt();

        int type = objHeader & 0xFF;
        switch (type)
        {
            case 14: // Floating point
            {
                int vecLen = d.readInt();
                double[] values = new double[vecLen];
                for (int i = 0; i < vecLen; i++)
                {
                    values[i] = d.readDouble();
                }
                return new RValue()
                {
                    @Override
                    public <T> T visit(RVisitor<T> visitor) throws InternalException, UserException
                    {
                        return visitor.visitDoubleList(values);
                    }
                };
            }
            case 19: // Generic vector
            {
                int vecLen = d.readInt();
                ImmutableList.Builder<RValue> valueBuilder = ImmutableList.builderWithExpectedSize(vecLen);
                for (int i = 0; i < vecLen; i++)
                {
                    valueBuilder.add(readItem(d));
                }
                ImmutableList<RValue> values = valueBuilder.build();
                return new RValue()
                {
                    @Override
                    public <T> T visit(RVisitor<T> visitor) throws InternalException, UserException
                    {
                        return visitor.visitGenericList(values);
                    }
                };
            }
            case 238: // ALTREP_SXP
            {
                RValue info = readItem(d);
                RValue state = readItem(d);
                RValue attr = readItem(d);
                // TODO
            }
            default:
                throw new UserException("Unsupported R object type (identifier: " + type + ")");
        }
    }

    private static String readLenString(DataInputStream d) throws IOException
    {
        int len = d.readInt();
        byte chars[] = new byte[len];
        d.readFully(chars);
        return new String(chars, StandardCharsets.US_ASCII);
    }

    private static BufferedInputStream openInputStream(File rFilePath) throws IOException, UserException
    {
        byte[] firstBytes = new byte[10];
        new DataInputStream(new FileInputStream(rFilePath)).readFully(firstBytes);
        if (firstBytes[0] == 0x1F && Byte.toUnsignedInt(firstBytes[1]) == 0x8B)
        {
            return new BufferedInputStream(new GZIPInputStream(new FileInputStream(rFilePath))); 
        }
        throw new UserException("Unrecognised file format");
    }
    
    public static interface RVisitor<T>
    {
        public T visitDoubleList(double[] values) throws InternalException, UserException;
        public T visitGenericList(ImmutableList<RValue> values) throws InternalException, UserException;
    }
    
    public static interface RValue
    {
        public <T> T visit(RVisitor<T> visitor) throws InternalException, UserException;
    }
}
