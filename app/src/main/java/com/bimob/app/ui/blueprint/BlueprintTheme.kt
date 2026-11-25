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

data class BlueprintPalette(val id:String,val label:String,val bgPrimary:Color,val bgSurface:Color,val gridMinor:Color,val gridMajor:Color,val linePrimary:Color,val accentCyan:Color,val accentAmber:Color,val textPrimary:Color,val textMuted:Color,val editorBar:Color,val editorSurface:Color,val editorCanvas:Color,val editorGridMinor:Color,val editorGridMajor:Color,val editorAxis:Color,val editorWall:Color,val editorSelection:Color,val editorSnap:Color,val editorText:Color,val editorTextMuted:Color)
val ClassicBlueprint=BlueprintPalette("blue","BLUE",Color(0xFF0B132B),Color(0xFF1C2541),Color(0xFF1C2541),Color(0xFF3A506B),Color(0xFF5BC0BE),Color(0xFF6FFFE9),Color(0xFFFF9F1C),Color(0xFFE8F4F8),Color(0xFF9BB4C8),Color(0xFF0B132B),Color(0xFF1C2541),Color(0xFF0B132B),Color(0xFF17213B),Color(0xFF253654),Color(0xFF5BC0BE),Color(0xFFE8F4F8),Color(0xFF6FFFE9),Color(0xFF6FFFE9),Color(0xFFE8F4F8),Color(0xFF9BB4C8))
val DarkCadGraphite=BlueprintPalette("dark","DARK",Color(0xFF121417),Color(0xFF1E2229),Color(0xFF20252C),Color(0xFF303741),Color(0xFF8A94A6),Color(0xFF00E5FF),Color(0xFFFFB300),Color(0xFFE6EDF3),Color(0xFF9BA7B5),Color(0xFF121417),Color(0xFF1E2229),Color(0xFF121417),Color(0xFF1B2026),Color(0xFF2A3038),Color(0xFF8A94A6),Color(0xFFE6EDF3),Color(0xFF00E5FF),Color(0xFF00E5FF),Color(0xFFE6EDF3),Color(0xFF9BA7B5))
val LightDraftingPaper=BlueprintPalette("paper","PAPER",Color(0xFFF4F6F9),Color(0xFFFFFFFF),Color(0xFFE7ECF2),Color(0xFFD1D9E3),Color(0xFF2B4C7E),Color(0xFF0055FF),Color(0xFFD95D39),Color(0xFF172033),Color(0xFF66758A),Color(0xFFE8EDF3),Color(0xFFFFFFFF),Color(0xFFF4F6F9),Color(0xFFE7ECF2),Color(0xFFD1D9E3),Color(0xFF2B4C7E),Color(0xFF172033),Color(0xFF0055FF),Color(0xFF008A55),Color(0xFF172033),Color(0xFF66758A))
val LocalBlueprintPalette=staticCompositionLocalOf{ClassicBlueprint}
object BlueprintTheme {
 val palettes=listOf(ClassicBlueprint,DarkCadGraphite,LightDraftingPaper)
 val active=mutableStateOf(ClassicBlueprint)
 val BgPrimary get()=active.value.bgPrimary;val BgSurface get()=active.value.bgSurface
 val GridMinor get()=active.value.gridMinor;val GridMajor get()=active.value.gridMajor
 val LinePrimary get()=active.value.linePrimary;val AccentCyan get()=active.value.accentCyan
 val AccentAmber get()=active.value.accentAmber;val TextPrimary get()=active.value.textPrimary;val TextMuted get()=active.value.textMuted
 val EditorBar get()=active.value.editorBar;val EditorSurface get()=active.value.editorSurface;val EditorCanvas get()=active.value.editorCanvas
 val EditorGridMinor get()=active.value.editorGridMinor;val EditorGridMajor get()=active.value.editorGridMajor;val EditorAxis get()=active.value.editorAxis
 val EditorWall get()=active.value.editorWall;val EditorSelection get()=active.value.editorSelection;val EditorSnap get()=active.value.editorSnap
 val EditorText get()=active.value.editorText;val EditorTextMuted get()=active.value.editorTextMuted
 fun next(){active.value=palettes[(palettes.indexOf(active.value)+1)%palettes.size]}
}
@Composable fun BlueprintThemeProvider(content:@Composable()->Unit){CompositionLocalProvider(LocalBlueprintPalette provides BlueprintTheme.active.value,content=content)}
@Composable fun ThemeButton(onChange:()->Unit){val p=LocalBlueprintPalette.current
 Row(verticalAlignment=Alignment.CenterVertically,modifier=Modifier.background(p.bgSurface).border(1.dp,p.linePrimary).clickable(onClick=onChange).padding(horizontal=5.dp,vertical=3.dp)){
  Text("[ТЕМА]",fontSize=9.sp,color=p.textPrimary,fontFamily=FontFamily.Monospace);Spacer(Modifier.width(4.dp))
  BlueprintTheme.palettes.forEach{palette->Canvas(Modifier.size(8.dp)){drawCircle(palette.accentCyan)}}
 }
}