package test.gen;

import test.gen.GenDataType.GenTaggedType;

/**
 * Created by neil on 05/06/2017.
 */
public class GenTaggedTypeAndValueGen extends GenTypeAndValueGen
{
    public GenDataType typeGenerator()
    {
        return new GenTaggedType();
    }
}
