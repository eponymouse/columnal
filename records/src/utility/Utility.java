package utility;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.checkerframework.checker.nullness.qual.NonNull;

/**
 * Created by neil on 20/10/2016.
 */
public class Utility
{
    public static <R,T> List<@NonNull R> mapList(List<@NonNull T> list, Function<@NonNull T, @NonNull R> func)
    {
        ArrayList<@NonNull R> r = new ArrayList<>(list.size());
        for (T t : list)
            r.add(func.apply(t));
        return r;
    }

    // From http://stackoverflow.com/questions/453018/number-of-lines-in-a-file-in-java
    public static int countLines(File filename) throws IOException
    {
        InputStream is = new BufferedInputStream(new FileInputStream(filename));
        try {
            byte[] c = new byte[1024];
            int count = 0;
            int readChars = 0;
            boolean empty = true;
            while ((readChars = is.read(c)) != -1) {
                empty = false;
                for (int i = 0; i < readChars; ++i) {
                    if (c[i] == '\n') {
                        ++count;
                    }
                }
            }
            return (count == 0 && !empty) ? 1 : count;
        } finally {
            is.close();
        }
    }

    public static int countIn(String small, String large)
    {
        int total = 0;
        for (int last = large.indexOf(small); last != -1; last = large.indexOf(small, last + 1))
        {
            total += 1;
        }
        return total;
    }

    public static double variance(List<? extends Number> src)
    {
        if (src.isEmpty())
            return 0;
        double mean = src.stream().mapToDouble(Number::doubleValue).summaryStatistics().getAverage();
        return src.stream().mapToDouble(Number::doubleValue).map(x -> (x - mean) * (x - mean)).sum();
    }
}
