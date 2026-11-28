package com.bimob.app.ui.blueprint
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Canvas
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class BlueprintPalette(val id:String,val label:String,val bgPrimary:Color,val bgSurface:Color,val gridMinor:Color,val gridMajor:Color,val linePrimary:Color,val accentCyan:Color,val accentAmber:Color,val textPrimary:Color,val textMuted:Color,val editorBar:Color,val editorSurface:Color,val editorCanvas:Color,val editorGridMinor:Color,val editorGridMajor:Color,val editorAxis:Color,val editorWall:Color,val editorSelection:Color,val editorSnap:Color,val editorText:Color,val editorTextMuted:Color,val editorError:Color)
val ClassicBlueprint=BlueprintPalette("blue","BLUE",Color(0xFF0F233C),Color(0xFF142D4B),Color(0xFF1D3554),Color(0xFF2B4567),Color(0xFF6D88A8),Color(0xFF00B4D8),Color(0xFFFFB300),Color(0xFFE6F2FF),Color(0xFF86A4C2),Color(0xFF0F233C),Color(0xFF142D4B),Color(0xFF0F233C),Color(0xFF1D3554),Color(0xFF2B4567),Color(0xFF6D88A8),Color(0xFFE6F2FF),Color(0xFF00B4D8),Color(0xFF00E676),Color(0xFFE6F2FF),Color(0xFF86A4C2),Color(0xFFE53935)
val DarkCadGraphite=BlueprintPalette("dark","CAD",Color(0xFF1E1F22),Color(0xFF25282D),Color(0xFF30343A),Color(0xFF3A414A),Color(0xFF707A86),Color(0xFF00B4D8),Color(0xFFFFB300),Color(0xFFE6EDF3),Color(0xFF9BA7B5),Color(0xFF25282D),Color(0xFF25282D),Color(0xFF1E1F22),Color(0xFF30343A),Color(0xFF3A414A),Color(0xFF707A86),Color(0xFFE6EDF3),Color(0xFF00B4D8),Color(0xFF00E676),Color(0xFFE6EDF3),Color(0xFF9BA7B5),Color(0xFFE53935)
val LightDraftingPaper=BlueprintPalette("paper","PAPER",Color(0xFFF4F6F9),Color(0xFFFFFFFF),Color(0xFFE7ECF2),Color(0xFFD1D9E3),Color(0xFF2B4C7E),Color(0xFF0055FF),Color(0xFFD95D39),Color(0xFF172033),Color(0xFF66758A),Color(0xFFE8EDF3),Color(0xFFFFFFFF),Color(0xFFF4F6F9),Color(0xFFE7ECF2),Color(0xFFD1D9E3),Color(0xFF2B4C7E),Color(0xFF172033),Color(0xFF0055FF),Color(0xFF008A55),Color(0xFF172033),Color(0xFF66758A),Color(0xFFE53935))
val RevitCream=BlueprintPalette("cream","CREAM",Color(0xFFFAF9F6),Color(0xFFF0EEE8),Color(0xFFE7E3DC),Color(0xFFD2CCC2),Color(0xFF6D777F),Color(0xFF2D7FA3),Color(0xFFB7791F),Color(0xFF25282B),Color(0xFF77736C),Color(0xFFF0EEE8),Color(0xFFFFFFFF),Color(0xFFFAF9F6),Color(0xFFE7E3DC),Color(0xFFD2CCC2),Color(0xFF6D777F),Color(0xFF25282B),Color(0xFF2D7FA3),Color(0xFF2F8B60),Color(0xFF25282B),Color(0xFF77736C),Color(0xFFC62828))
val WarmParchment=BlueprintPalette("parchment","PARCHMENT",Color(0xFFF3EFE6),Color(0xFFE8E0D2),Color(0xFFE2D8C8),Color(0xFFCEC0AA),Color(0xFF78634D),Color(0xFF2D7196),Color(0xFFB66A24),Color(0xFF2D2924),Color(0xFF776B5C),Color(0xFFE8E0D2),Color(0xFFF8F4EB),Color(0xFFF3EFE6),Color(0xFFE2D8C8),Color(0xFFCEC0AA),Color(0xFF78634D),Color(0xFF2D2924),Color(0xFF2D7196),Color(0xFF2F8B60),Color(0xFF2D2924),Color(0xFF776B5C),Color(0xFFC62828))
val DarkSlate=BlueprintPalette("slate","SLATE",Color(0xFF262A30),Color(0xFF30363E),Color(0xFF353C45),Color(0xFF454E59),Color(0xFF8995A3),Color(0xFF00B4D8),Color(0xFFFFB300),Color(0xFFE7EDF4),Color(0xFF9BA7B5),Color(0xFF30363E),Color(0xFF262A30),Color(0xFF262A30),Color(0xFF353C45),Color(0xFF454E59),Color(0xFF8995A3),Color(0xFFE7EDF4),Color(0xFF00B4D8),Color(0xFF00E676),Color(0xFFE7EDF4),Color(0xFF9BA7B5),Color(0xFFE53935))

val LocalBlueprintPalette=staticCompositionLocalOf{ClassicBlueprint}
object BlueprintTheme {
 val palettes=listOf(RevitCream,DarkCadGraphite,ClassicBlueprint,WarmParchment,DarkSlate)
 val active=mutableStateOf(ClassicBlueprint)
 val BgPrimary get()=active.value.bgPrimary;val BgSurface get()=active.value.bgSurface
 val GridMinor get()=active.value.gridMinor;val GridMajor get()=active.value.gridMajor
 val LinePrimary get()=active.value.linePrimary;val AccentCyan get()=active.value.accentCyan
 val AccentAmber get()=active.value.accentAmber;val TextPrimary get()=active.value.textPrimary;val TextMuted get()=active.value.textMuted
 val EditorBar get()=active.value.editorBar;val EditorSurface get()=active.value.editorSurface;val EditorCanvas get()=active.value.editorCanvas
 val EditorGridMinor get()=active.value.editorGridMinor;val EditorGridMajor get()=active.value.editorGridMajor;val EditorAxis get()=active.value.editorAxis
 val EditorWall get()=active.value.editorWall;val EditorSelection get()=active.value.editorSelection;val EditorSnap get()=active.value.editorSnap
 val EditorText get()=active.value.editorText;val EditorTextMuted get()=active.value.editorTextMuted;val EditorError get()=active.value.editorError
 fun select(palette:BlueprintPalette){active.value=palette}
 fun next(){active.value=palettes[(palettes.indexOf(active.value)+1)%palettes.size]}
}
@Composable fun BlueprintThemeProvider(content:@Composable ()->Unit){CompositionLocalProvider(LocalBlueprintPalette provides BlueprintTheme.active.value){content()}}
@Composable fun ThemeButton(onChange:()->Unit){
 val p=LocalBlueprintPalette.current
 Row(verticalAlignment=Alignment.CenterVertically,modifier=Modifier.background(p.bgSurface).border(1.dp,p.linePrimary).clickable(onClick=onChange).padding(horizontal=5.dp,vertical=3.dp)){
  Text("[ТЕМА]",fontSize=9.sp,color=p.textPrimary,fontFamily=FontFamily.Monospace);Spacer(Modifier.width(4.dp))
  BlueprintTheme.palettes.forEach{palette->Canvas(Modifier.size(8.dp)){drawCircle(palette.accentCyan);if(BlueprintTheme.active.value==palette)drawCircle(p.accentAmber,style=androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))}}
 }
}
