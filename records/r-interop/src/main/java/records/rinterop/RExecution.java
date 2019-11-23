package records.rinterop;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import log.Log;
import org.apache.commons.io.IOUtils;
import records.data.RecordSet;
import records.error.InternalException;
import records.error.UserException;
import records.rinterop.RData.RValue;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Map;
import java.util.Map.Entry;

public class RExecution
{
    public static RValue runRExpression(String rExpression) throws UserException, InternalException
    {
        return runRExpression(rExpression, ImmutableList.of(), ImmutableMap.of());
    }
    
    public static RValue runRExpression(String rExpression, ImmutableList<String> packages, ImmutableMap<String, RecordSet> tablesToPass) throws UserException, InternalException
    {
        try (TemporaryFileHandler rdsFile = new TemporaryFileHandler())
        {
            Process p = Runtime.getRuntime().exec(new String[]{"R", "--vanilla", "--slave"});
            PrintStream cmdStream = new PrintStream(p.getOutputStream());

            for (Entry<String, RecordSet> entry : tablesToPass.entrySet())
            {
                File tableFile = rdsFile.addRDSFile("table");
                RValue rTable = RData.convertTableToR(entry.getValue());
                System.out.println(RData.prettyPrint(rTable));
                RData.writeRData(tableFile, rTable);
                String read = entry.getKey() + " <- readRDS(\"" + tableFile.getAbsolutePath() + "\")";
                System.out.println(read);
                cmdStream.println(read);
            }
            
            String[] lines = rExpression.split("\\r?\\n");
            File outputFile = rdsFile.addRDSFile("output");
            lines[lines.length - 1] = "saveRDS(" + lines[lines.length - 1] + ", file=\"" + outputFile.getAbsolutePath() + "\")";
            
            for (String line : lines)
            {
                System.out.println(line);
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
            return RData.readRData(outputFile);
        }
        catch (IOException | InterruptedException e)
        {
            throw new UserException("Problem running R", e);
        }
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
        public void close()
        {
            for (File f : files)
            {
                f.delete();
            }
        }
    }
}
