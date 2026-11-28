package com.bimob.app.ui.workspace
import org.junit.Assert.*
import org.junit.Test
class NagaplasWorkspaceLayoutTest{
 @Test fun gridAddressRoundTrip(){assertEquals("A1",NagaplasWorkspaceLayout.address(1));assertEquals("CZ1",NagaplasWorkspaceLayout.address(104));assertEquals("A2",NagaplasWorkspaceLayout.address(105));assertEquals("BK17",NagaplasWorkspaceLayout.address(NagaplasWorkspaceLayout.id("BK17")));assertEquals("CZ48",NagaplasWorkspaceLayout.address(4992))}
 @Test fun excelColumnLabels(){assertEquals("Z",NagaplasWorkspaceLayout.columnName(26));assertEquals("AA",NagaplasWorkspaceLayout.columnName(27));assertEquals("AZ",NagaplasWorkspaceLayout.columnName(52));assertEquals("BA",NagaplasWorkspaceLayout.columnName(53));assertEquals("CZ",NagaplasWorkspaceLayout.columnName(104));assertEquals(103,NagaplasWorkspaceLayout.columnIndex("CZ"))}
 @Test fun headerCoordinates(){assertEquals("A1:L3",NagaplasWorkspaceLayout.header.first().rect.address);assertEquals("AW1:BC3",NagaplasWorkspaceLayout.header.first{it.code=="Q0007"}.rect.address);assertEquals("BY1:CE3",NagaplasWorkspaceLayout.header.first{it.code=="Q0011"}.rect.address);assertEquals("CT1:CZ3",NagaplasWorkspaceLayout.header.first{it.code=="Q0014"}.rect.address)}
 @Test fun 34ToolSlotsAndRemainder(){assertEquals(34,NagaplasWorkspaceLayout.tools.size);assertEquals("A4:C6",NagaplasWorkspaceLayout.tools.first().rect.address);assertEquals("CV4:CX6",NagaplasWorkspaceLayout.tools.last().rect.address);assertEquals("CY4:CZ6",NagaplasWorkspaceLayout.toolRemainder.rect.address)}
 @Test fun panelAndCanvasCoordinates(){assertEquals("A7:L7",NagaplasWorkspaceLayout.sidePanels[0].rect.address);assertEquals("CO7:CZ7",NagaplasWorkspaceLayout.sidePanels[1].rect.address);assertEquals("A8:L45",NagaplasWorkspaceLayout.sidePanels[2].rect.address);assertEquals("CO8:CZ45",NagaplasWorkspaceLayout.sidePanels[3].rect.address);assertEquals("A46:CZ48",NagaplasWorkspaceLayout.sidePanels[4].rect.address);assertEquals("M7:CN45",NagaplasWorkspaceLayout.canvas.address)}
 @Test fun regionsNoCollision(){assertTrue(NagaplasWorkspaceLayout.overlaps().toString(),NagaplasWorkspaceLayout.overlaps().isEmpty())}
 @Test(expected=IllegalArgumentException::class) fun invalidAddressRejected(){NagaplasWorkspaceLayout.id("DA1")}
 @Test(expected=IllegalArgumentException::class) fun invalidRowRejected(){NagaplasWorkspaceLayout.id("A49")}
}
