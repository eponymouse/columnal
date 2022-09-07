package records.transformations.expression;

import annotation.units.CanonicalLocation;
import org.checkerframework.checker.nullness.qual.Nullable;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.util.Objects;

// A semantic error matches an expression which may span multiple children.
@OnThread(Tag.Any)
public final class CanonicalSpan implements Comparable<CanonicalSpan>
{
    // Start is inclusive char index, end is exclusive char index
    public final @CanonicalLocation int start;
    public final @CanonicalLocation int end;
    
    public CanonicalSpan(@CanonicalLocation int start, @CanonicalLocation int end)
    {
        this.start = start;
        this.end = end;
    }

    public static CanonicalSpan fromTo(CanonicalSpan start, CanonicalSpan end)
    {
        if (start.start <= end.end)
            return new CanonicalSpan(start.start, end.end);
        else
            return new CanonicalSpan(start.start, start.start);
    }
    
    public CanonicalSpan offsetBy(@CanonicalLocation int offsetBy)
    {
        return new CanonicalSpan(start + offsetBy, end + offsetBy);
    }
  
    @SuppressWarnings("units")
    public static final CanonicalSpan START = new CanonicalSpan(0, 0);
    
    // Even though end is typically exclusive, this checks
    // if <= end because for errors etc we still want to display
    // if we touch the extremity.
    public boolean touches(@CanonicalLocation int position)
    {
        return start <= position && position <= end;
    }

    @Override
    public String toString()
    {
        return "[" + start + "->" + end + "]";
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CanonicalSpan span = (CanonicalSpan) o;
        return start == span.start &&
                end == span.end;
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(start, end);
    }

    @Override
    public int compareTo(CanonicalSpan o)
    {
        int c = Integer.compare(start, o.start);
        if (c != 0)
            return c;
        else
            return Integer.compare(end, o.end);
    }

    public CanonicalSpan lhs()
    {
        return new CanonicalSpan(start, start);
    }

    public CanonicalSpan rhs()
    {
        return new CanonicalSpan(end, end);
    }
}
