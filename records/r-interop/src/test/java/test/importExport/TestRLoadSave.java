package test.importExport;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.junit.Test;
import records.data.ColumnId;
import records.data.DataTestUtil;
import records.data.RecordSet;
import records.data.datatype.DataType;
import records.data.datatype.DataTypeUtility;
import records.data.datatype.TypeId;
import records.data.datatype.TypeManager;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import records.rinterop.RData;
import records.rinterop.RData.RValue;
import records.rinterop.RData.SpecificRVisitor;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;
import utility.TaggedValue;

import java.io.File;
import java.math.BigDecimal;
import java.net.URL;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

public class TestRLoadSave
{
    @Test
    public void testImportRData() throws Exception
    {
        @SuppressWarnings("nullness")
        @NonNull URL resource = getClass().getClassLoader().getResource("iris.rds");
        RValue loaded = RData.readRData(new File(resource.toURI()));
        System.out.println(RData.prettyPrint(loaded));
        TypeManager typeManager = new TypeManager(UnitManager._test_blank());
        RecordSet rs = RData.convertRToTable(typeManager, loaded);
                
        assertEquals(ImmutableList.of(new ColumnId("Sepal Length"), new ColumnId("Sepal Width"), new ColumnId("Petal Length"), new ColumnId("Petal Width"), new ColumnId("Species")), rs.getColumnIds());
        
        DataTestUtil.assertValueListEqual("Row 0", ImmutableList.of(d("5.1"), d("3.5"), d("1.4"), d("0.2"), typeManager.lookupTag("setosa 3", "setosa").getRight("Tag not found").makeTag(null)), DataTestUtil.getRowVals(rs, 0));

        DataTestUtil.assertValueListEqual("Row 149", ImmutableList.of(d("5.9"), d("3.0"), d("5.1"), d("1.8"), typeManager.lookupTag("setosa 3", "virginica").getRight("Tag not found").makeTag(null)), DataTestUtil.getRowVals(rs, 149));
    }

    private static @Value BigDecimal d(String s)
    {
        // We go through double to replicate the R value exactly
        return DataTypeUtility.value(new BigDecimal(Double.parseDouble(s)));
    }


}
