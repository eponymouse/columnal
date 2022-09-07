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

package xyz.columnal.grammar;

/**
 * Each overall save version corresponds to:
 *  - an expression parser version
 *  - a data parser version
 *  - a main parser version
 *  
 * The overall version must be incremented when any of those sub-versions increments, but it's not required that every version increases in tandem.
 * The history is:
 *  - OverallVersion.ONE: ExpressionVersion.ONE, DataVersion.ONE, MainVersion.ONE
 *  - OverallVersion.TWO: ExpressionVersion.ONE, DataVersion.TWO, MainVersion.TWO
 *  - OverallVersion.THREE: ExpressionVersion.TWO, DataVersion.TWO, MainVersion.TWO
 */
public class Versions
{
    // All versions must in ascending numeric order
    
    public static enum OverallVersion
    {
        ONE, TWO, THREE;

        public static OverallVersion latest()
        {
            return OverallVersion.values()[OverallVersion.values().length - 1];
        }
        
        public int asNumber()
        {
            return ordinal() + 1;
        }
    }
    
    public static enum ExpressionVersion
    {
        ONE, TWO;
        
        public static ExpressionVersion latest()
        {
            return ExpressionVersion.values()[ExpressionVersion.values().length - 1];
        }
    }
    
    public static enum DataVersion
    {
        ONE, TWO;
    }
    
    public static enum MainVersion
    {
        ONE, TWO;
    }
    
    public static ExpressionVersion getExpressionVersion(OverallVersion overallVersion)
    {
        switch (overallVersion)
        {
            case ONE: case TWO:
                return ExpressionVersion.ONE;
            case THREE:
            default:
                return ExpressionVersion.TWO;
        }
    }
    
    public static DataVersion getDataVersion(OverallVersion overallVersion)
    {
        switch (overallVersion)
        {
            case ONE:
                return DataVersion.ONE;
            case TWO:
            case THREE:
            default:
                return DataVersion.TWO;
        }
    }

    public static MainVersion getMainVersion(OverallVersion overallVersion)
    {
        switch (overallVersion)
        {
            case ONE:
                return MainVersion.ONE;
            case TWO:
            case THREE:
            default:
                return MainVersion.TWO;
        }
    }
}
