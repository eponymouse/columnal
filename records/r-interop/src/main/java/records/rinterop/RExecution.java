package records.rinterop;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import log.Log;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.SystemUtils;
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
    // Uses $PATH by default:
    private static String rExec = "R";
    
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
                // Bit of a quick hack, especially for Mac where PATH is not taken from terminal:
                if (SystemUtils.IS_OS_MAC_OSX || SystemUtils.IS_OS_LINUX)
                {
                    try
                    {
                        String local = "/usr/local/bin/R";
                        Runtime.getRuntime().exec(new String[]{local, "--version"}).waitFor();
                        isRAvailable = true;
                        rExec = local;
                    }
                    catch (IOException e2)
                    {
                        isRAvailable = false;
                    }
                    catch (InterruptedException e2)
                    {
                        // Hit and hope...
                    }
                }
                else
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
            Process p = Runtime.getRuntime().exec(new String[]{rExec, "--vanilla", "--slave"});
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
            CopyStreamThread copyOut = new CopyStreamThread(p.getInputStream());
            CopyStreamThread copyErr = new CopyStreamThread(p.getErrorStream());
            try
            {
                copyOut.start();
                copyErr.start();
                if (!p.waitFor(15, TimeUnit.SECONDS))
                    throw new UserException("R process took too long to complete, giving up.");
                if (p.exitValue() != 0)
                    throw new UserException("Exit code from running R (" + p.exitValue() + ") indicates error.  Output was:\n" + copyOut.destination.toString() + "\nError was:\n" + copyErr.destination.toString());
                return RRead.readRData(outputFile);
            }
            finally
            {
                p.destroyForcibly();
                copyOut.interrupt();
                copyErr.interrupt();
            }
        }
        catch (IOException | InterruptedException | IllegalThreadStateException e)
        {
            throw new UserException("Problem running R", e);
        }
    }
    
    private static class CopyStreamThread extends Thread
    {
        private final StringWriter destination = new StringWriter();
        private final InputStream source;

        private CopyStreamThread(InputStream source)
        {
            this.source = source;
        }

        @Override
        public void run()
        {
            try
            {
                IOUtils.copy(source, destination, StandardCharsets.UTF_8);
            }
            catch (IOException e)
            {
                Log.log(e);
            }
            /*
            catch (InterruptedException e)
            {
                // Just finish...
            }
             */
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
