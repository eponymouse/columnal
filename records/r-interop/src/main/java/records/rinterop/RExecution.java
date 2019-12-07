package records.rinterop;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.apache.commons.io.IOUtils;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import records.data.RecordSet;
import records.error.InternalException;
import records.error.UserException;
import records.rinterop.ConvertFromR.TableType;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Utility;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Map.Entry;

public class RExecution
{
    // null means not checked yet
    private static @MonotonicNonNull Boolean isRAvailable;
    
    public static RValue runRExpression(String rExpression) throws UserException, InternalException
    {
        return runRExpression(rExpression, ImmutableList.of(), ImmutableMap.of());
    }
    
    public static RValue runRExpression(String rExpression, ImmutableList<String> packages, ImmutableMap<String, RecordSet> tablesToPass) throws UserException, InternalException
    {
        if (isRAvailable == null)
        {
            try
            {
                Runtime.getRuntime().exec(new String[]{"R", "--version"}).waitFor();
                isRAvailable = true;
            }
            catch (IOException e)
            {
                isRAvailable = false;
            }
            catch (InterruptedException e)
            {
                // Hit and hope...
            }
        }
        
        if (isRAvailable != null && isRAvailable == false)
        {
            throw new UserException("R not found on your system; R must be installed and in your PATH");
        }
        
        try (TemporaryFileHandler rdsFile = new TemporaryFileHandler())
        {
            Process p = Runtime.getRuntime().exec(new String[]{"R", "--vanilla", "--slave"});
            PrintStream cmdStream = new PrintStream(p.getOutputStream());
            
            for (String pkg : Utility.prependToList("tibble", packages))
            {
                cmdStream.println("require(\"" + pkg + "\") || install.packages(\"" + pkg + "\", repos=\"https://cloud.r-project.org/\")");
            }

            for (Entry<String, RecordSet> entry : tablesToPass.entrySet())
            {
                File tableFile = rdsFile.addRDSFile("table");
                RValue rTable = ConvertToR.convertTableToR(entry.getValue(), TableType.TIBBLE);
                //System.out.println(RData.prettyPrint(rTable));
                RWrite.writeRData(tableFile, rTable);
                String read = entry.getKey() + " <- readRDS(\"" + escape(tableFile.getAbsolutePath()) + "\")";
                cmdStream.println(read);
            }
            
            String[] lines = Utility.splitLines(rExpression);
            File outputFile = rdsFile.addRDSFile("output");
            lines[lines.length - 1] = "saveRDS(" + lines[lines.length - 1] + ", file=\"" + escape(outputFile.getAbsolutePath()) + "\")";
            
            for (String line : lines)
            {
                //System.out.println(line);
                cmdStream.println(line);
            }
            cmdStream.println("quit();");
            cmdStream.flush();
            StringWriter stdout = new StringWriter();
            IOUtils.copy(p.getInputStream(), stdout, StandardCharsets.UTF_8);
            StringWriter stderr = new StringWriter();
            IOUtils.copy(p.getErrorStream(), stderr, StandardCharsets.UTF_8);
            int exitCode = p.waitFor();
            if (exitCode != 0)
                throw new UserException("Exit code from running R (" + exitCode + ") indicates error.  Output was:\n" + stdout.toString() + "\nError was:\n" + stderr.toString());
            return RRead.readRData(outputFile);
        }
        catch (IOException | InterruptedException e)
        {
            throw new UserException("Problem running R", e);
        }
    }
    
    private static String escape(String original)
    {
        return original.replace("\\", "\\\\");
    }

    private static class TemporaryFileHandler implements AutoCloseable
    {
        private final ArrayList<File> files = new ArrayList<>();

        public TemporaryFileHandler()
        {
        }
        
        public File addRDSFile(String name) throws IOException
        {
            File f = File.createTempFile(name, "rds");
            files.add(f);
            return f;
        }

        @Override
        @OnThread(Tag.Any)
        public void close()
        {
            for (File f : files)
            {
                f.delete();
            }
        }
    }
}
