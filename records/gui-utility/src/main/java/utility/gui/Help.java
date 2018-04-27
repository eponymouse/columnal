package utility.gui;

import annotation.help.qual.HelpKey;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import log.Log;
import nu.xom.Builder;
import nu.xom.Document;
import nu.xom.Element;
import nu.xom.Elements;
import nu.xom.Nodes;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.grammar.GrammarUtility;

import java.io.FileNotFoundException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

/**
 * Class for loading help information
 */
// package-visible
class Help
{
    // Maps file names (minus extension) to
    // help item names to help info
    private static LoadingCache<String, Map<String, HelpInfo>> helpCache = CacheBuilder.newBuilder().build(
        new CacheLoader<String, Map<String, HelpInfo>>()
        {
            @Override
            public Map<String, HelpInfo> load(String s) throws Exception
            {
                return loadFile(s);
            }
        }
    );

    @SuppressWarnings("i18n") // Because we assert that the loaded XML is localized
    private static Map<String, HelpInfo> loadFile(String fileStem)
    {
        try
        {
            Map<String, HelpInfo> foundNodes = new HashMap<>();
            Builder builder = new Builder();
            @Nullable URL resource = Help.class.getResource("/" + fileStem + ".help");
            if (resource == null)
                throw new FileNotFoundException(fileStem + ".help");
            Document doc = builder.build(resource.toString());

            Nodes helpNodes = doc.query("//help");

            // We don't do many checks here because it should have been checked by XSD
            // already, and at worst, we just lack the tooltip by hitting the catch:
            for (int i = 0; i < helpNodes.size(); i++)
            {
                Element helpNode = (Element)helpNodes.get(i);
                String id = helpNode.getAttributeValue("id");
                String title = helpNode.getAttributeValue("title");
                @Localized String shortText = helpNode.getChildElements("short").get(0).getValue();
                Elements fullParas = helpNode.getChildElements("full").get(0).getChildElements("p");
                List<@Localized String> fullText = new ArrayList<>();
                for (int j = 0; j < fullParas.size(); j++)
                {
                    fullText.add(GrammarUtility.collapseSpaces(fullParas.get(j).getValue()));
                }
                foundNodes.put(id, new HelpInfo(title, shortText, fullText));
            }
            return foundNodes;
        }
        catch (Exception e)
        {
            Log.log(e);
            throw new RuntimeException(e);
        }
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
            return helpCache.get(rootAndEntry[0]).get(rootAndEntry[1]);
        }
        catch (ExecutionException e)
        {
            Log.log(e);
            return null;
        }
    }
}
