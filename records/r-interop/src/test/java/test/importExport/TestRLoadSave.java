package test.importExport;

import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.junit.Test;
import records.data.ColumnId;
import records.data.RecordSet;
import records.data.datatype.TypeManager;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import records.rinterop.RData;
import records.rinterop.RData.RValue;
import records.rinterop.RData.SpecificRVisitor;

import java.io.File;
import java.math.BigDecimal;
import java.net.URL;

import static org.junit.Assert.assertEquals;

public class TestRLoadSave
{
    @Test
    public void testImportRData() throws Exception
    {
        @SuppressWarnings("nullness")
        @NonNull URL resource = getClass().getClassLoader().getResource("iris.rds");
        RValue loaded = RData.readRData(new File(resource.toURI()));
        RecordSet rs = RData.convertRToTable(new TypeManager(UnitManager._test_blank()), loaded);
                
        assertEquals(ImmutableList.of(new ColumnId("Sepal Length"), new ColumnId("Sepal Width"), new ColumnId("Petal Length"), new ColumnId("Petal Width"), new ColumnId("Species")), rs.getColumnIds());
    }
}
