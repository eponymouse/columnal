/*
 * Columnal: Safer, smoother data table processing.
 * Copyright (c) Neil Brown, 2016-2020, 2022.
 *
 * This file is part of Columnal.
 *
 * Columnal is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 *
 * Columnal is distributed in the hope that it will be useful, but WITHOUT 
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or 
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for 
 * more details.
 *
 * You should have received a copy of the GNU General Public License along 
 * with Columnal. If not, see <https://www.gnu.org/licenses/>.
 */

package xyz.columnal.rinterop;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import xyz.columnal.log.Log;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.SystemUtils;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import xyz.columnal.data.RecordSet;
import xyz.columnal.data.Settings;
import xyz.columnal.data.TableManager;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.rinterop.ConvertFromR.TableType;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.Utility;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;

public class RExecution
{
    // Uses $PATH by default:
    private static String rExecFromPath = "R";
    
    public static RValue runRExpression(String rExpression) throws UserException, InternalException
    {
        return runRExpression(rExpression, ImmutableList.of(), ImmutableMap.of());
    }
    
    public static RValue runRExpression(String rExpression, ImmutableList<String> packages, ImmutableMap<String, RecordSet> tablesToPass) throws UserException, InternalException
    {
        Settings settings = TableManager.getSettings();
        String rExec = Utility.onNullable(settings.pathToRExecutable, f -> f.getAbsolutePath());
        if (rExec == null)
        {            
            try
            {
                Runtime.getRuntime().exec(new String[]{"R", "--version"}).waitFor();
                rExec = "R";
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
                        rExec = local;
                    }
                    catch (IOException e2)
                    {
                        rExec = null;
                    }
                    catch (InterruptedException e2)
                    {
                        // Hit and hope...
                    }
                }
                else
                    rExec = null;
            }
            catch (InterruptedException e)
            {
                // Hit and hope...
            }
        }
        
        if (rExec == null)
        {
            throw new UserException("R not found on your system; set the R path in the settings");
        }
        
        try (TemporaryFileHandler rdsFile = new TemporaryFileHandler())
        {
            File rLibsDir = null;
            if (settings.useColumnalRLibs)
            {
                rLibsDir = new File(Utility.getStorageDirectory(), "Rlibs");
                if (!rLibsDir.exists() && !rLibsDir.mkdir())
                {
                    throw new IOException("Could not create local R libs directory: " + rLibsDir.getAbsolutePath());
                }
            }
            
            Process p = Runtime.getRuntime().exec(new String[]{rExec, "--vanilla", "--slave"});
            PrintStream cmdStream = new PrintStream(p.getOutputStream());
            
            for (String pkg : Utility.prependToList("tibble", packages))
            {
                StringBuilder require = new StringBuilder();
                require.append("require(").append(RUtility.escapeString(pkg, true));
                if (rLibsDir != null)
                {
                    require.append(", lib.loc=c(").append(RUtility.escapeString(rLibsDir.getAbsolutePath(), true)).append(", .libPaths())");
                }
                require.append(")");
                StringBuilder reqOrInstall = new StringBuilder();
                reqOrInstall.append("if (!").append(require).append(") { install.packages(").append(RUtility.escapeString(pkg, true)).append(", repos=\"https://cloud.r-project.org/\"");
                if (rLibsDir != null)
                {
                    reqOrInstall.append(", lib=c(").append(RUtility.escapeString(rLibsDir.getAbsolutePath(), true)).append(")");
                }
                reqOrInstall.append(");").append(require).append(";}");

                Log.debug("Sending:\n" + reqOrInstall.toString());
                cmdStream.println(reqOrInstall.toString());
            }

            for (Entry<String, RecordSet> entry : tablesToPass.entrySet())
            {
                File tableFile = rdsFile.addRDSFile("table");
                RValue rTable = ConvertToR.convertTableToR(entry.getValue(), TableType.TIBBLE);
                //System.out.println(RData.prettyPrint(rTable));
                RWrite.writeRData(tableFile, rTable);
                String read = entry.getKey() + " <- readRDS(" + RUtility.escapeString(tableFile.getAbsolutePath(), true) + ")";
                cmdStream.println(read);
            }
            
            String[] lines = Utility.splitLines(rExpression);
            File outputFile = rdsFile.addRDSFile("output");
            lines[lines.length - 1] = "saveRDS(" + lines[lines.length - 1] + ", file=" + RUtility.escapeString(outputFile.getAbsolutePath(), true) + ")";
            
            for (String line : lines)
            {
                System.out.println(line);
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
