package com.bimob.app.ui.workspace
/** Canonical 104x48 NAGAPLAS cell grid, 1-based row-major. */
internal object NagaplasGrid104x48 {
 const val COLUMNS=104; const val ROWS=48; const val CELL_COUNT=COLUMNS*ROWS
 data class Cell(val column:Int,val row:Int){init{require(column in 0 until COLUMNS);require(row in 0 until ROWS)};val id:Int get()=row*COLUMNS+column+1}
 data class Rect(val left:Int,val top:Int,val right:Int,val bottom:Int){init{require(left<=right&&top<=bottom&&left>=0&&top>=0&&right<COLUMNS&&bottom<ROWS)};val width get()=right-left+1;val height get()=bottom-top+1;val cells:Set<Int> get()=buildSet{for(r in top..bottom)for(c in left..right)add(Cell(c,r).id)}}
 data class Region(val name:String,val cellIds:Set<Int>){init{require(name.isNotBlank()&&cellIds.isNotEmpty()&&cellIds.all{it in 1..CELL_COUNT})};val rectangles:List<Rect> get()=normalize(cellIds)}
 fun cell(id:Int):Cell{require(id in 1..CELL_COUNT);val z=id-1;return Cell(z%COLUMNS,z/COLUMNS)}
 fun ids(first:Int,last:Int):Set<Int>{require(first<=last&&first in 1..CELL_COUNT&&last in 1..CELL_COUNT);return(first..last).toSet()}
 fun region(name:String,vararg ranges:IntRange)=Region(name,ranges.flatMap{ids(it.first,it.last)}.toSet())
 /** Convert horizontal runs to rectangles, vertically merging identical runs. */
 fun normalize(ids:Set<Int>):List<Rect>{
  require(ids.all{it in 1..CELL_COUNT})
  val runs=mutableListOf<Rect>()
  for(row in 0 until ROWS){val cols=ids.map(::cell).filter{it.row==row}.map{it.column}.sorted();if(cols.isEmpty())continue
   var start=cols.first();var end=start
   for(col in cols.drop(1)){if(col==end+1)end=col else{runs+=Rect(start,row,end,row);start=col;end=col}}
   runs+=Rect(start,row,end,row)
  }
  val merged=mutableListOf<Rect>()
  for(run in runs){val i=merged.indexOfLast{it.left==run.left&&it.right==run.right&&it.bottom==run.top-1};if(i>=0){val p=merged[i];merged[i]=Rect(p.left,p.top,p.right,run.bottom)}else merged+=run}
  return merged.sortedWith(compareBy<Rect>{it.top}.thenBy{it.left})
 }
 data class PixelRect(val left:Float,val top:Float,val right:Float,val bottom:Float)
 fun toPixels(rect:Rect,width:Float,height:Float):PixelRect{require(width.isFinite()&&height.isFinite()&&width>0&&height>0);val cw=width/COLUMNS;val ch=height/ROWS;return PixelRect(rect.left*cw,rect.top*ch,(rect.right+1)*cw,(rect.bottom+1)*ch)}
 val staticRegions=listOf(
  region("Логотип",1..7),region("Текущий проект",45..60),
  region("Удалить",77..83,181..187,285..291),region("Вперед",84..90,188..194,292..298),
  region("Отмена",91..97,195..201,299..305),region("Тема",98..104,202..208,306..312),
  region("Главный",105..110,209..214),region("Архитектура",111..115,215..219),
  region("Инструменты",116..120,220..224),region("Материалы",121..125,225..229),
  region("Слот 1",313..315,417..419,521..523),region("Слот 2",316..318,420..422,524..526),
  region("Слот 3",319..321,423..425,527..529),region("Слот 4",322..324,426..428,530..532),
  region("Слот 5",325..327,429..431,533..535),region("Информация — шапка",711..728)
 )
 fun validateNoOverlap(regions:List<Region>):List<String>{val owners=mutableMapOf<Int,String>();val conflicts=mutableListOf<String>();regions.forEach{r->r.cellIds.forEach{id->val prior=owners.putIfAbsent(id,r.name);if(prior!=null)conflicts+="cell $id: $prior <> ${r.name}"}};return conflicts}
}
