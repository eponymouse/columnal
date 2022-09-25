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

package test.gen;

import com.google.common.collect.ImmutableList;
import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import xyz.columnal.gui.lexeditor.ExpressionLexer.Keyword;
import xyz.columnal.gui.lexeditor.ExpressionLexer.Op;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.util.Arrays;
import java.util.function.Supplier;

public class GenInvalidExpressionSource extends Generator<String>
{
    public GenInvalidExpressionSource()
    {
        super(String.class);
    }

    @Override
    @OnThread(value = Tag.FXPlatform, ignoreParent = true)
    public String generate(SourceOfRandomness random, GenerationStatus status)
    {
        ImmutableList<Supplier<String>> tokenMakers = ImmutableList.of(
            ts(random, Arrays.stream(Keyword.values()).map(o -> o.getContent()).toArray(String[]::new)),
            ts(random, Arrays.stream(Op.values()).filter(o -> o != Op.ADD).map(o -> o.getContent()).toArray(String[]::new)),
            ts(random, "a", "z"),
            ts(random, "1", "9", "1.2")
        );
        
        int totalTokens = random.nextInt(20);
        StringBuilder r = new StringBuilder();
        for (int i = 0; i < totalTokens; i++)
        {
            r.append(tokenMakers.get(random.nextInt(tokenMakers.size())).get());
        }
        
        return r.toString();
    }
    
    private static Supplier<String> ts(SourceOfRandomness r, String... options)
    {
        return () -> options[r.nextInt(options.length)];
    }
}
