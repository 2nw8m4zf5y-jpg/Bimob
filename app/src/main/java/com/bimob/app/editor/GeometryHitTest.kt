package com.bimob.app.editor

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.IntSize
import kotlin.math.*

/** Hit testing is performed in screen pixels so target sizes remain stable at every camera zoom. */
internal fun hitTestTypedGeometry(
    screen: Offset, geometry: TypedGeometry, canvas: IntSize, zoom: Float, pan: Offset, radiusPx: Float = 24f
): EditorSelection? {
    if (!screen.x.isFinite() || !screen.y.isFinite() || !zoom.isFinite() || zoom <= 0f || radiusPx <= 0f) return null
    val size=Size(canvas.width.toFloat(),canvas.height.toFloat())
    fun point(v:Vec2)=worldToScreen(WorldPoint(v.x,v.y),size,zoom,pan)
    fun distanceSegment(p:Offset,a:Offset,b:Offset):Float {
        val dx=b.x-a.x;val dy=b.y-a.y;val l=dx*dx+dy*dy
        val t=if(l<=1e-12f)0f else (((p.x-a.x)*dx+(p.y-a.y)*dy)/l).coerceIn(0f,1f)
        return hypot(p.x-a.x-t*dx,p.y-a.y-t*dy)
    }
    fun corners(item:CatalogItem):List<Vec2>{
        val hw=item.width/2;val hd=item.depth/2;val angle=item.rotationRadians
        return listOf(-hw to -hd,hw to -hd,hw to hd,-hw to hd).map{(x,y)->Vec2(item.center.x+x*cos(angle)-y*sin(angle),item.center.y+x*sin(angle)+y*cos(angle))}
    }
    fun polygonHit(vertices:List<Vec2>):Boolean{
        val pts=vertices.map(::point);var inside=false
        for(i in pts.indices){val a=pts[i];val b=pts[(i+1)%pts.size]
            if(distanceSegment(screen,a,b)<=radiusPx)return true
            if((a.y>screen.y)!=(b.y>screen.y)&&screen.x<(b.x-a.x)*(screen.y-a.y)/(b.y-a.y)+a.x)inside=!inside
        };return inside
    }
    geometry.catalog.asReversed().firstOrNull{polygonHit(corners(it))}?.let{return EditorSelection(EditorElementKind.CATALOG,it.id)}
    geometry.openings.asReversed().firstOrNull{item->
        val host=geometry.walls.firstOrNull{it.id==item.hostWallId}?:return@firstOrNull false
        val dx=host.axisEnd.x-host.axisStart.x;val dy=host.axisEnd.y-host.axisStart.y;val len=hypot(dx,dy);if(len<=1e-9)return@firstOrNull false
        val ux=dx/len;val uy=dy/len;val half=item.width/2
        distanceSegment(screen,point(Vec2(item.center.x-ux*half,item.center.y-uy*half)),point(Vec2(item.center.x+ux*half,item.center.y+uy*half)))<=radiusPx
    }?.let{return EditorSelection(EditorElementKind.OPENING,it.id)}
    geometry.walls.asReversed().firstOrNull{distanceSegment(screen,point(it.axisStart),point(it.axisEnd))<=radiusPx}
        ?.let{return EditorSelection(EditorElementKind.WALL,it.id)}
    return null
}
