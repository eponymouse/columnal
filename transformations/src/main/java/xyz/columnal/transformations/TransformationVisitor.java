package xyz.columnal.transformations;

public interface TransformationVisitor<T>
{
    T aggregate(Aggregate aggregate);
    T calculate(Calculate calculate);
    T check(Check check);
    T concatenate(Concatenate concatenate);
    T filter(Filter filter);
    T hideColumns(HideColumns hideColumns);
    T join(Join join);
    T manualEdit(ManualEdit manualEdit);
    T sort(Sort sort);
    T runR(RTransformation rTransformation);
}
