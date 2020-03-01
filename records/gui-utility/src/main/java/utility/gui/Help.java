package utility.gui;

import annotation.help.qual.HelpKey;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableList;
import log.Log;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.grammar.GrammarUtility;
import utility.Pair;
import utility.ResourceUtility;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ExecutionException;

/**
 * Class for loading help information
 */
// package-visible
class Help
{
    // Maps file names (minus extension) to
    // help item names to help info
    private static LoadingCache<Pair<String, String>, HelpInfo> helpCache = CacheBuilder.newBuilder().build(
        new CacheLoader<Pair<String, String>, HelpInfo>()
        {
            @Override
            public HelpInfo load(Pair<String, String> s) throws Exception
            {
                return loadFile(s.getFirst(), s.getSecond());
            }
        }
    );

    @SuppressWarnings("i18n") // Because we assert that the loaded XML is localized
    private static HelpInfo loadFile(String fileStem, String id)
    {
        ResourceBundle resourceBundle = ResourceBundle.getBundle(fileStem);

        return new HelpInfo(resourceBundle.getString(id + ".title"), resourceBundle.getString(id), ImmutableList.copyOf(GrammarUtility.collapseSpaces(resourceBundle.getString(id + ".full")).split("£££££")));
    }

    static class HelpInfo
    {
        final @Localized String title;
        final @Localized String shortText;
        final List<@Localized String> fullParas;

        private HelpInfo(@Localized String title, @Localized String shortText, List<@Localized String> fullParas)
        {
            this.title = title;
            this.shortText = shortText;
            this.fullParas = fullParas;
        }
    }

    static @Nullable HelpInfo getHelpInfo(@HelpKey String helpKey)
    {
        String[] rootAndEntry = helpKey.split("/");
        try
        {
            return helpCache.get(new Pair<>(rootAndEntry[0], rootAndEntry[1]));
        }
        catch (ExecutionException e)
        {
            Log.log(e);
            return null;
        }
    }
}
