package com.bimob.app.editor
import kotlin.math.cos
import kotlin.math.sin
internal enum class CatalogGrip { WIDTH_POSITIVE, WIDTH_NEGATIVE, DEPTH_POSITIVE, DEPTH_NEGATIVE }
internal data class CatalogGripPreview(val center: Vec2, val width: Double, val depth: Double)
internal fun catalogGripAxis(item: CatalogItem, grip: CatalogGrip): Vec2 { val a=item.rotationRadians;return if(grip==CatalogGrip.WIDTH_POSITIVE||grip==CatalogGrip.WIDTH_NEGATIVE)Vec2(cos(a),sin(a))else Vec2(-sin(a),cos(a)) }
internal fun catalogGripSign(grip: CatalogGrip)=if(grip==CatalogGrip.WIDTH_POSITIVE||grip==CatalogGrip.DEPTH_POSITIVE)1.0 else -1.0
internal fun catalogGripDimension(item: CatalogItem,grip:CatalogGrip)=if(grip==CatalogGrip.WIDTH_POSITIVE||grip==CatalogGrip.WIDTH_NEGATIVE)item.width else item.depth
internal fun catalogGripPoint(item:CatalogItem,grip:CatalogGrip):Vec2{val a=catalogGripAxis(item,grip);val s=catalogGripSign(grip);val d=catalogGripDimension(item,grip);return Vec2(item.center.x+a.x*s*d/2,item.center.y+a.y*s*d/2)}
internal fun catalogGripFixedPoint(item:CatalogItem,grip:CatalogGrip):Vec2{val a=catalogGripAxis(item,grip);val s=catalogGripSign(grip);val d=catalogGripDimension(item,grip);return Vec2(item.center.x-a.x*s*d/2,item.center.y-a.y*s*d/2)}
internal fun resizeCatalogGrip(item:CatalogItem,grip:CatalogGrip,point:Vec2,minSize:Double=.05,maxSize:Double=50.0):CatalogGripPreview?{
 if(!listOf(item.center.x,item.center.y,item.rotationRadians,item.width,item.depth,point.x,point.y,minSize,maxSize).all{it.isFinite()}||minSize<=0||maxSize<minSize||item.width<=0||item.depth<=0)return null
 val a=catalogGripAxis(item,grip);val s=catalogGripSign(grip);val fixed=catalogGripFixedPoint(item,grip);val projected=((point.x-fixed.x)*a.x+(point.y-fixed.y)*a.y)*s
 val old=catalogGripDimension(item,grip);val n=projected.coerceIn(minSize,maxSize);val delta=n-old;val center=Vec2(item.center.x+a.x*s*delta/2,item.center.y+a.y*s*delta/2)
 return if(grip==CatalogGrip.WIDTH_POSITIVE||grip==CatalogGrip.WIDTH_NEGATIVE)CatalogGripPreview(center,n,item.depth)else CatalogGripPreview(center,item.width,n)
}