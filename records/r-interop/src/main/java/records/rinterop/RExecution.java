package records.rinterop;

import com.google.common.collect.ImmutableList;
import org.apache.commons.io.IOUtils;
import records.error.InternalException;
import records.error.UserException;
import records.rinterop.RData.RValue;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;

public class RExecution
{
    public static RValue runRExpression(String rExpression) throws UserException, InternalException
    {
        return runRExpression(rExpression, ImmutableList.of());
    }
    
    public static RValue runRExpression(String rExpression, ImmutableList<String> packages) throws UserException, InternalException
    {
        try (TemporaryFileHandler rdsFile = new TemporaryFileHandler("output", "rds"))
        {
            Process p = Runtime.getRuntime().exec(new String[]{"R", "--vanilla", "--slave"});
            String[] lines = rExpression.split("\\r?\\n");
            lines[lines.length - 1] = "saveRDS(" + lines[lines.length - 1] + ", file=\"" + rdsFile.getFile().getAbsolutePath() + "\")";
            PrintStream cmdStream = new PrintStream(p.getOutputStream());
            for (String line : lines)
            {
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
            return RData.readRData(rdsFile.getFile());
        }
        catch (IOException | InterruptedException e)
        {
            throw new UserException("Problem running R", e);
        }
    }

    private static class TemporaryFileHandler implements AutoCloseable
    {
        private File file;

        public TemporaryFileHandler(String prefix, String suffix) throws IOException
        {
            this.file = File.createTempFile(prefix, suffix);
        }

        public File getFile()
        {
            return file;
        }

        @Override
        public void close() throws IOException
        {
            file.delete();
        }
    }
}
