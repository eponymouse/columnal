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

package xyz.columnal.gui;

import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import com.google.common.io.Files;
import com.google.common.primitives.Ints;
import xyz.columnal.log.Log;
import org.apache.commons.io.FileUtils;
import org.checkerframework.checker.nullness.qual.Nullable;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.adt.Pair;
import xyz.columnal.utility.Utility;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Objects;
import java.util.stream.Collectors;

@OnThread(Tag.Simulation)
public class UndoManager
{
    // Max number of backups for each file
    private static final int MAX_DETAILS = 20;
    private final HashFunction hashFunction = Hashing.goodFastHash(32);
    // From https://stackoverflow.com/questions/893977/java-how-to-find-out-whether-a-file-name-is-valids
    private static final int[] ILLEGAL_CHARACTERS = { '/', '\n', '\r', '\t', '\0', '\f', '`', '?', '*', '\\', '<', '>', '|', '\"', ':' };


    class SaveDetails
    {
        // Path of the backed up file, not the original which was backed up:
        private final File backupFile;
        private final Instant instant;
        private final @Nullable HashCode hash;

        public SaveDetails(File backupFile, Instant instant, @Nullable HashCode hash)
        {
            this.backupFile = backupFile;
            this.instant = instant;
            this.hash = hash;
        }

        public String _debug_detailsAndContent()
        {
            try
            {
                String content = FileUtils.readFileToString(backupFile, StandardCharsets.UTF_8);
                String indented = Arrays.stream(content.split(System.getProperty("line.separator")))
                        .filter(s -> !s.isEmpty())
                        .map(s -> "   " + s.trim())
                        .collect(Collectors.joining("\n"));
                return "@" + instant + "#" + hash + ":\n" + indented;
            }
            catch (IOException e)
            {
                e.printStackTrace();
                return "@" + instant + "#" + hash + ": " + e.getLocalizedMessage();
            }
        }
    }
    
    // Lists are held in chronological order:
    private final HashMap<File, ArrayList<SaveDetails>> backups = new HashMap<>();
    
    @OnThread(Tag.Any)
    public UndoManager()
    {
    }
    
    public void backupForUndo(File file, Instant saveTime)
    {
        @Nullable File undoPath = null;
        @Nullable HashCode hashCode = null;
        try
        {
            File f = new File(Utility.getUndoDirectory(),
                    "undo-" + munge(file) + "-" + saveTime.toEpochMilli()
                    );
            
            // Don't backup empty file:
            if (!file.exists() || file.length() == 0L)
                return;
            
            Files.copy(file, f);
            // Only record once it's copied:
            undoPath = f;
            
            // Hash last as we can survive if it fails:
            hashCode = hashContent(file);
        }
        catch (IOException e)
        {
            Log.log("Problem backing up", e);
        }
        
        if (undoPath != null)
        {
            ArrayList<SaveDetails> details = backups.merge(file, new ArrayList<>(), (old, blank) -> old);
            SaveDetails newSave = new SaveDetails(undoPath, saveTime, hashCode);
            if (!details.isEmpty() && Objects.equals(details.get(details.size() - 1).hash, hashCode))
            {
                // We always replace, as in rare case of hash collision, we will
                // at least keep most recent version:
                details.set(details.size() - 1, newSave);
            }
            else
            {
                details.add(newSave);
            }
            
            if (details.size() >= 2)
            {
                details.get(details.size() - 1).backupFile.deleteOnExit();
            }
            
            while (details.size() > MAX_DETAILS)
            {
                details.remove(0).backupFile.delete();
            }

            //Log.logStackTrace("Saving!");
            /*
            System.out.println("State after saving latest:");
            for (SaveDetails detail : details)
            {
                System.out.println(detail._debug_detailsAndContent());
            }
            */
        }
    }

    private HashCode hashContent(File file) throws IOException
    {
        return Files.asByteSource(file).hash(hashFunction);
    }

    private static String munge(File file)
    {
        int[] replaced = file.getAbsolutePath().codePoints()
            .map(i -> Ints.asList(ILLEGAL_CHARACTERS).contains(i) ? '_' : i).toArray();
        return new String(replaced, 0, replaced.length);
    }

    /**
     * Undoes last change and returns the content as a String.
     */
    public @Nullable String undo(File file)
    {
        ArrayList<SaveDetails> details = backups.get(file);
        if (details != null)
        {
            /* // For debugging:
            try
            {
                System.out.println("State at undo, cur #" + hashContent(file) + ":\n" + FileUtils.readFileToString(file, StandardCharsets.UTF_8) + "\n Undo possibilities:\n");
                for (SaveDetails detail : details)
                {
                    System.out.println(detail._debug_detailsAndContent());
                }
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
            */
            
            Pair<Integer, SaveDetails> latest = details.isEmpty() ? null : new Pair<>(details.size() - 1, details.get(details.size() - 1));
            
            if (latest != null)
            {
                try
                {
                    String content = FileUtils.readFileToString(latest.getSecond().backupFile, StandardCharsets.UTF_8);
                    // Important cast, to remove by index, not Integer object:
                    details.remove((int)latest.getFirst());
                    return content;
                }
                catch (IOException e)
                {
                    Log.log(e);
                    return null;
                }
            }
        }
        return null;
    }
}
