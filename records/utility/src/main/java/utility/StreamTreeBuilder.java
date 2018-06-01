package utility;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Builds a stream from a sequence of single items and nested streams.
 * @param <T>
 */
public class StreamTreeBuilder<T>
{
    private final ArrayList<Either<T, Stream<T>>> items = new ArrayList<>();
    
    public void add(T singleItem)
    {
        items.add(Either.left(singleItem));
    }
    
    public void addAll(List<T> multipleItems)
    {
        addAll(multipleItems.stream());
    }
    
    public void addAll(Stream<T> itemStream)
    {
        items.add(Either.right(itemStream));
    }
    
    public Stream<T> stream()
    {
        return items.stream().flatMap(t -> t.either(Stream::of, s -> s));
    }
}
