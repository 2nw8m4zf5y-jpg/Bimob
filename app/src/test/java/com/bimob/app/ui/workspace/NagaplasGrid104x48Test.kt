package com.bimob.app.ui.workspace
import org.junit.Assert.*
import org.junit.Test
class NagaplasGrid104x48Test{
 @Test fun countAndCorners(){assertEquals(4992,NagaplasGrid104x48.CELL_COUNT);assertEquals(NagaplasGrid104x48.Cell(0,0),NagaplasGrid104x48.cell(1));assertEquals(NagaplasGrid104x48.Cell(103,47),NagaplasGrid104x48.cell(4992))}
 @Test fun rowMajorMapping(){assertEquals(NagaplasGrid104x48.Cell(103,0),NagaplasGrid104x48.cell(104));assertEquals(NagaplasGrid104x48.Cell(0,1),NagaplasGrid104x48.cell(105));assertEquals(NagaplasGrid104x48.Cell(10,1),NagaplasGrid104x48.cell(115));assertEquals(NagaplasGrid104x48.Cell(86,6),NagaplasGrid104x48.cell(711))}
 @Test fun regionNormalization(){val del=NagaplasGrid104x48.staticRegions.first{it.name=="Удалить"};assertEquals(listOf(NagaplasGrid104x48.Rect(76,0,82,2)),del.rectangles);val logo=NagaplasGrid104x48.staticRegions.first{it.name=="Логотип"};assertEquals(listOf(NagaplasGrid104x48.Rect(0,0,6,0)),logo.rectangles);assertTrue(NagaplasGrid104x48.staticRegions.filter{it.name.startsWith("Слот")}.all{it.rectangles.single().width==3&&it.rectangles.single().height==3})}
 @Test fun staticRegionsDontCollide(){assertTrue(NagaplasGrid104x48.validateNoOverlap(NagaplasGrid104x48.staticRegions).isEmpty())}
 @Test fun pixelBounds(){val px=NagaplasGrid104x48.toPixels(NagaplasGrid104x48.Rect(0,0,6,0),1040f,480f);assertEquals(0f,px.left,.001f);assertEquals(0f,px.top,.001f);assertEquals(70f,px.right,.001f);assertEquals(10f,px.bottom,.001f)}
 @Test(expected=IllegalArgumentException::class) fun rejectsZero(){NagaplasGrid104x48.cell(0)}
 @Test(expected=IllegalArgumentException::class) fun rejectsOverflow(){NagaplasGrid104x48.cell(4993)}
}
