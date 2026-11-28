package com.bimob.app.ui.workspace

/** Canonical workspace map transcribed from the Excel register. Coordinates are 1-based A..CZ and 1..48. */
internal object NagaplasWorkspaceLayout {
 const val COLUMNS=104; const val ROWS=48; const val CELL_COUNT=4992
 data class Rect(val left:Int,val top:Int,val right:Int,val bottom:Int){
  init{require(left in 0 until COLUMNS&&right in left until COLUMNS);require(top in 0 until ROWS&&bottom in top until ROWS)}
  val width get()=right-left+1;val height get()=bottom-top+1
  val ids:Set<Int> get()=buildSet{for(r in top..bottom)for(c in left..right)add(r*COLUMNS+c+1)}
  val address get()="${columnName(left+1)}${top+1}:${columnName(right+1)}${bottom+1}"
 }
 data class Region(val code:String,val name:String,val rect:Rect)
 fun columnName(number:Int):String{require(number in 1..COLUMNS);var n=number;var out="";while(n>0){val rem=(n-1)%26;out=('A'+rem)+out;n=(n-1)/26};return out}
 fun columnIndex(name:String):Int{require(name.isNotBlank());var v=0;name.uppercase().forEach{ch->require(ch in 'A'..'Z');v=v*26+(ch-'A'+1)};require(v in 1..COLUMNS);return v-1}
 fun address(id:Int):String{require(id in 1..CELL_COUNT);val z=id-1;return "${columnName(z%COLUMNS+1)}${z/COLUMNS+1}"}
 fun id(address:String):Int{val m=Regex("^([A-Z]+)([1-9][0-9]*)$",RegexOption.IGNORE_CASE).matchEntire(address.trim())?:error("Invalid cell address: $address");val c=columnIndex(m.groupValues[1]);val r=m.groupValues[2].toInt();require(r in 1..ROWS);return(r-1)*COLUMNS+c+1}
 private fun rect(code:String,name:String,left:String,top:Int,right:String,bottom:Int)=Region(code,name,Rect(columnIndex(left),top-1,columnIndex(right),bottom-1))
 val header=listOf(
  rect("Q0001","Логотип и главное меню","A",1,"L",3),rect("Q0002","Резерв","M",1,"T",3),
  rect("Q0003","Быстрая кнопка 1","U",1,"AA",3),rect("Q0004","Быстрая кнопка 2","AB",1,"AH",3),
  rect("Q0005","Быстрая кнопка 3","AI",1,"AO",3),rect("Q0006","Быстрая кнопка 4","AP",1,"AV",3),
  rect("Q0007","Имя проекта","AW",1,"BC",3),rect("Q0008","Быстрая кнопка 5","BD",1,"BJ",3),
  rect("Q0009","Быстрая кнопка 6","BK",1,"BQ",3),rect("Q0010","Быстрая кнопка 7","BR",1,"BX",3),
  rect("Q0011","Удалить","BY",1,"CE",3),rect("Q0012","Повтор","CF",1,"CL",3),
  rect("Q0013","Отмена","CM",1,"CS",3),rect("Q0014","Темы","CT",1,"CZ",3)
 )
 val tools=(0 until 34).map{i->Region("Q${(15+i).toString().padStart(4,'0')}","Инструмент ${i+1}",Rect(i*3,3,i*3+2,5))}
 val toolRemainder=rect("Q0049","Резерв инструментов","CY",4,"CZ",6)
 val sidePanels=listOf(
  rect("Q0050","Заголовок левой панели","A",7,"L",7),rect("Q0051","Заголовок правой панели","CO",7,"CZ",7),
  rect("Q0052","Левая панель","A",8,"L",45),rect("Q0053","Правая панель","CO",8,"CZ",45),
  rect("Q0054","Статусная панель","A",46,"CZ",48))
 val canvas=Rect(columnIndex("M"),6,columnIndex("CN"),44)
 val allRegions=header+tools+listOf(toolRemainder)+sidePanels
 fun overlaps():List<String>{val owners=mutableMapOf<Int,String>();val conflicts=mutableListOf<String>();allRegions.forEach{r->r.rect.ids.forEach{id->val prior=owners.putIfAbsent(id,r.code);if(prior!=null)conflicts+="${address(id)}: $prior / ${r.code}"}};return conflicts}
}
