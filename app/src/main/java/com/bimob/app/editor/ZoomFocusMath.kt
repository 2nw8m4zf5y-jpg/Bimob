package com.bimob.app.editor

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size

internal data class FocusZoomCamera(val zoom: Float, val panX: Float, val panY: Float)

internal fun screenToWorldFocus(screen: Offset, size: Size, zoom: Float, panX: Float, panY: Float): Offset =
 Offset((screen.x-size.width/2f-panX)/zoom,(screen.y-size.height/2f-panY)/zoom)

internal fun zoomAroundCentroid(oldCentroid: Offset, centroid: Offset, zoomFactor: Float, size: Size, camera: FocusZoomCamera): FocusZoomCamera {
 if(!zoomFactor.isFinite()||zoomFactor<=0f||camera.zoom<=0f||size.width<=0f||size.height<=0f)return camera
 val focus=screenToWorldFocus(oldCentroid,size,camera.zoom,camera.panX,camera.panY)
 val newZoom=(camera.zoom*zoomFactor).coerceIn(12f,240f)
 val panX=(centroid.x-size.width/2f-focus.x*newZoom).coerceIn(-100000f,100000f)
 val panY=(centroid.y-size.height/2f-focus.y*newZoom).coerceIn(-100000f,100000f)
 return FocusZoomCamera(newZoom,panX,panY)
}
internal fun worldToScreenFocus(world: Offset,size: Size,camera:FocusZoomCamera)=Offset(size.width/2f+camera.panX+world.x*camera.zoom,size.height/2f+camera.panY+world.y*camera.zoom)
