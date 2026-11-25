#include "bimob/native_session.hpp"
#include <cassert>
#include <string>
#include <cstdint>
#include <cstring>
#include <cmath>
#include <algorithm>
#include <thread>
#include <atomic>
int main(){using namespace bimob::bridge;
 auto fresh=createSession();assert(fresh);
 auto freshCall=[&](const char*j){return dispatchCommandJson(fresh,j);};
 assert(freshCall(R"({"type":"CreateWall","id":"W0","levelId":"","startX":0,"startY":0,"endX":2,"endY":0,"heightMeters":2.7,"thicknessMeters":0.2})").find("\"ok\":false")!=std::string::npos);
 assert(freshCall(R"({"type":"CreateWall","id":"Wunknown","levelId":"missing","startX":0,"startY":0,"endX":2,"endY":0,"heightMeters":2.7,"thicknessMeters":0.2})").find("\"ok\":false")!=std::string::npos);
 assert(freshCall(R"({"type":"CreateLevel","id":"LF","name":"Ground","elevationMeters":0,"heightMeters":3})").find("\"ok\":true")!=std::string::npos);
 assert(freshCall(R"({"type":"CreateWall","id":"Wnan","levelId":"LF","startX":1e999,"startY":0,"endX":2,"endY":0,"heightMeters":2.7,"thicknessMeters":0.2})").find("\"ok\":false")!=std::string::npos);
 assert(freshCall(R"({"type":"CreateWall","id":"Wshort","levelId":"LF","startX":0,"startY":0,"endX":0.01,"endY":0,"heightMeters":2.7,"thicknessMeters":0.2})").find("\"ok\":false")!=std::string::npos);
 assert(freshCall(R"({"type":"CreateWall","id":"Wfirst","levelId":"LF","startX":0,"startY":0,"endX":3,"endY":0,"heightMeters":2.7,"thicknessMeters":0.2})").find("\"ok\":true")!=std::string::npos);
 auto firstGeometry=exportRenderGeometry(fresh);assert(firstGeometry.size()>=72&&std::memcmp(firstGeometry.data(),"BGR1",4)==0);
 auto firstU32=[&](std::size_t o){std::uint32_t v=0;for(int i=0;i<4;i++)v|=static_cast<std::uint32_t>(firstGeometry[o+i])<<(8*i);return v;};
 assert(firstU32(16)==1&&firstU32(20)==0&&firstU32(24)==0&&firstU32(28)==0);
 for(std::size_t i=32;i<72;i+=4){auto bits=firstU32(i);float value;std::memcpy(&value,&bits,sizeof(value));assert(std::isfinite(value));}
 assert(destroySession(fresh));
 auto h=createSession();assert(h);assert(exportCanonicalSnapshot(h).find("Native Engine Session")!=std::string::npos);
 auto call=[&](const char*j){return dispatchCommandJson(h,j);};
 assert(call(R"({"type":"CreateLevel","id":"L1","name":"Ground","elevationMeters":0,"heightMeters":3})").find("\"ok\":true")!=std::string::npos);
 assert(call(R"({"type":"CreateWall","id":"W1","levelId":"L1","startX":0,"startY":0,"endX":4,"endY":0,"heightMeters":3,"thicknessMeters":0.2})").find("\"ok\":true")!=std::string::npos);
 assert(call(R"({"type":"CreateOpening","id":"O1","hostWallId":"W1","openingKind":"DOOR","offsetAlongWallMeters":0.5,"widthMeters":0.9,"heightMeters":2.1,"sillHeightMeters":0})").find("\"ok\":true")!=std::string::npos);
 assert(call(R"({"type":"CreateWall","id":"W2","levelId":"L1","startX":4,"startY":0,"endX":4,"endY":3,"heightMeters":3,"thicknessMeters":0.2})") .find("ok")!=std::string::npos);
 assert(call(R"({"type":"CreateCatalogInstance","id":"C1","assetId":"asset","levelId":"L1","x":2,"y":1,"widthMeters":1,"depthMeters":0.5,"heightMeters":1.2})") .find("ok")!=std::string::npos);
 auto geometry=exportRenderGeometry(h);assert(geometry.size()>=32);assert(std::memcmp(geometry.data(),"BGR1",4)==0);
 auto readU32=[&](std::size_t offset){std::uint32_t v=0;for(int i=0;i<4;i++)v|=static_cast<std::uint32_t>(geometry[offset+i])<<(8*i);return v;};
 auto readU64=[&](std::size_t offset){std::uint64_t v=0;for(int i=0;i<8;i++)v|=static_cast<std::uint64_t>(geometry[offset+i])<<(8*i);return v;};
 assert(readU32(4)==1);assert(readU64(8)>=3);
 const auto exportRevision=readU64(8);assert(exportRenderGeometry(h)==geometry);
 auto wc=readU32(16),jc=readU32(20),oc=readU32(24),cc=readU32(28);assert(wc==2&&jc==1&&oc==1&&cc==1);
 assert(geometry.size()>=32ULL+wc*40ULL+jc*36ULL+oc*32ULL+cc*32ULL);
 auto idOffset=32ULL+wc*40ULL+jc*36ULL+oc*32ULL+cc*32ULL;
 auto readId=[&](){auto n=readU32(idOffset);idOffset+=4;assert(n<=geometry.size()-idOffset);std::string id(reinterpret_cast<const char*>(geometry.data()+idOffset),n);idOffset+=n;return id;};
 assert(readId()=="W1"&&readId()=="W2");assert(readId()=="auto:W1:W2");assert(readId()=="O1");assert(readId()=="C1");assert(idOffset==geometry.size());
 auto readF=[&](std::size_t offset){auto bits=readU32(offset);float v;std::memcpy(&v,&bits,sizeof(v));return v;};
 for(std::size_t i=32;i<32+wc*40;i+=4)assert(std::isfinite(readF(i)));
 assert(std::abs(readF(32+8)-3.9f)<1e-4f&&std::abs(readF(32+3*4)-0.1f)<1e-4f);
 assert(std::abs(readF(32+4*4)-4.1f)<1e-4f&&std::abs(readF(32+5*4)+0.1f)<1e-4f);
 assert(std::abs(readF(32+40)-3.9f)<1e-4f&&std::abs(readF(32+40+4)-0.1f)<1e-4f);
 assert(std::abs(readF(32+40+6*4)-4.1f)<1e-4f&&std::abs(readF(32+40+7*4)+0.1f)<1e-4f);
 const std::size_t joinOff=32+wc*40;for(int i=0;i<9;i++)assert(std::isfinite(readF(joinOff+i*4)));assert(readF(joinOff+32)==3.0f);
 const std::size_t openingOff=joinOff+jc*36;assert(readF(openingOff+12)==0.9f&&readF(openingOff+16)==2.1f);
 const std::size_t catalogOff=openingOff+oc*32;assert(readF(catalogOff)==2.0f&&readF(catalogOff+12)==1.0f);
 
 assert(call(R"({"type":"UpdateOpening","id":"O1","offsetAlongWallMeters":1,"widthMeters":1,"heightMeters":2.2,"sillHeightMeters":0})").find("\"ok\":true")!=std::string::npos);
 auto changedGeometry=exportRenderGeometry(h);assert(changedGeometry.size()>=32&&std::memcmp(changedGeometry.data(),"BGR1",4)==0);
 std::uint64_t changedRevision=0;for(int i=0;i<8;i++)changedRevision|=static_cast<std::uint64_t>(changedGeometry[8+i])<<(8*i);assert(changedRevision>exportRevision);
 assert(call(R"({"type":"MoveWall","id":"W1","deltaX":1,"deltaY":0})").find("\"ok\":true")!=std::string::npos);
 assert(call(R"({"type":"ResizeWall","id":"W1","lengthMeters":5})").find("\"ok\":true")!=std::string::npos);
 assert(call(R"({"type":"UpdateWallHeight","id":"W1","heightMeters":3.4})").find("\"ok\":true")!=std::string::npos);
 assert(exportCanonicalSnapshot(h).find("\"heightMeters\":3.4")!=std::string::npos);
 assert(call(R"({"type":"MoveLevel","id":"L1","elevationMeters":0.25})").find("\"ok\":true")!=std::string::npos);
 assert(exportCanonicalSnapshot(h).find("\"elevationMeters\":0.25")!=std::string::npos);
 assert(call(R"({"type":"CreateCatalogInstance","id":"C2","assetId":"asset","levelId":"L1","x":0,"y":0,"widthMeters":1,"depthMeters":1,"heightMeters":1})").find("\"ok\":true")!=std::string::npos);
 assert(call(R"({"type":"UpdateCatalogInstance","id":"C2","x":2,"y":1,"widthMeters":2,"depthMeters":1,"heightMeters":1})").find("\"ok\":true")!=std::string::npos);
 geometry=exportRenderGeometry(h);auto catalogResizeOff=32ULL+readU32(16)*40ULL+readU32(20)*36ULL+readU32(24)*32ULL+32ULL;
 assert(std::abs(readF(catalogResizeOff)-2.0f)<1e-5f&&std::abs(readF(catalogResizeOff+4)-1.0f)<1e-5f);
 assert(std::abs(readF(catalogResizeOff+12)-2.0f)<1e-5f&&std::abs(readF(catalogResizeOff+16)-1.0f)<1e-5f);
 assert(call(R"({"type":"Undo"})").find("\"ok\":true")!=std::string::npos);
 geometry=exportRenderGeometry(h);assert(std::abs(readF(catalogResizeOff)-0.0f)<1e-5f&&std::abs(readF(catalogResizeOff+4)-0.0f)<1e-5f);
 assert(std::abs(readF(catalogResizeOff+12)-1.0f)<1e-5f&&std::abs(readF(catalogResizeOff+16)-1.0f)<1e-5f);
 assert(call(R"({"type":"Redo"})").find("\"ok\":true")!=std::string::npos);
 geometry=exportRenderGeometry(h);assert(std::abs(readF(catalogResizeOff)-2.0f)<1e-5f&&std::abs(readF(catalogResizeOff+4)-1.0f)<1e-5f);
 assert(std::abs(readF(catalogResizeOff+12)-2.0f)<1e-5f&&std::abs(readF(catalogResizeOff+16)-1.0f)<1e-5f);
 assert(call(R"({"type":"Batch","commands":[{"type":"MoveWall","id":"W1","deltaX":1,"deltaY":0},{"type":"ResizeWall","id":"W1","lengthMeters":6}]})").find("\"ok\":true")!=std::string::npos);
 assert(call(R"({"type":"UpdateCatalogInstance","id":"C2","x":2,"y":1,"widthMeters":2,"depthMeters":1,"heightMeters":1,"rotationRadians":1.25})").find("\"ok\":true")!=std::string::npos);
 auto rotatedCatalog=exportRenderGeometry(h);auto rotatedCatalogOffset=32ULL+readU32(16)*40ULL+readU32(20)*36ULL+readU32(24)*32ULL;auto readRotatedF=[&](std::size_t offset){std::uint32_t bits=0;for(int i=0;i<4;i++)bits|=static_cast<std::uint32_t>(rotatedCatalog[offset+i])<<(8*i);float value;std::memcpy(&value,&bits,sizeof(value));return value;};assert(std::abs(readRotatedF(rotatedCatalogOffset+32+8)-1.25f)<1e-5f);
 assert(call(R"({"type":"CreateWall","id":"W9","levelId":"L1","startX":10,"startY":0,"endX":12,"endY":0,"heightMeters":3,"thicknessMeters":0.2})").find("\"ok\":true")!=std::string::npos);
 assert(call(R"({"type":"RotateWall","id":"W9","startX":10,"startY":0,"endX":10,"endY":2})").find("\"ok\":true")!=std::string::npos);
 assert(call(R"({"type":"Undo"})").find("\"ok\":true")!=std::string::npos);assert(call(R"({"type":"Redo"})").find("\"ok\":true")!=std::string::npos);

 assert(beginGesture(h,"g1"));assert(call(R"({"type":"MoveWall","id":"W1","deltaX":1,"deltaY":0})").find("\"ok\":true")!=std::string::npos);assert(call(R"({"type":"MoveWall","id":"W1","deltaX":1,"deltaY":0})").find("\"ok\":true")!=std::string::npos);assert(commitGesture(h,"g1"));assert(call(R"({"type":"Undo"})").find("\"ok\":true")!=std::string::npos);assert(call(R"({"type":"Redo"})").find("\"ok\":true")!=std::string::npos);
 auto beforeEmptyCancel=exportCanonicalSnapshot(h);assert(beginGesture(h,"empty"));assert(cancelGesture(h,"empty"));assert(exportCanonicalSnapshot(h)==beforeEmptyCancel);
 auto beforeEditCancel=exportCanonicalSnapshot(h);assert(beginGesture(h,"cancel-edit"));assert(call(R"({"type":"MoveWall","id":"W1","deltaX":3,"deltaY":0})").find("\"ok\":true")!=std::string::npos);assert(cancelGesture(h,"cancel-edit"));assert(exportCanonicalSnapshot(h)==beforeEditCancel);
 assert(call(R"({"type":"DeleteWall","id":"W1"})").find("\"ok\":true")!=std::string::npos);auto snap=exportCanonicalSnapshot(h);assert(snap.find("\"id\":\"W1\"")==std::string::npos&&snap.find("\"id\":\"O1\"")==std::string::npos);
 auto stable=exportCanonicalSnapshot(h);assert(call(R"({"type":"MoveWall","id":"W1","deltaX":0,"deltaY":0} trailing)").find("\"ok\":false")!=std::string::npos);assert(exportCanonicalSnapshot(h)==stable);assert(call(R"([{"type":"Undo"}])").find("\"ok\":false")!=std::string::npos);assert(call(R"({"type":"Undo","type":"Redo"})").find("\"ok\":false")!=std::string::npos);assert(call(R"({"type":"MoveWall","id":"W1","deltaX":1e999,"deltaY":0})").find("\"ok\":false")!=std::string::npos);assert(exportCanonicalSnapshot(h)==stable);

 assert(call(R"({"type":"CreateLevel","id":"L2","name":"Upper","elevationMeters":3,"heightMeters":2.8})").find("\"ok\":true")!=std::string::npos);
 assert(call(R"({"type":"CreateWall","id":"W3","levelId":"L2","startX":0,"startY":0,"endX":3,"endY":0,"heightMeters":2.8,"thicknessMeters":0.18})").find("\"ok\":true")!=std::string::npos);
 const auto canonical=exportCanonicalSnapshot(h);auto restored=createSession();assert(restored);
 assert(importCanonicalSnapshot(restored,canonical).find("\"ok\":true")!=std::string::npos);
 assert(exportCanonicalSnapshot(restored)==canonical);
 const auto beforeBad=exportCanonicalSnapshot(restored);
 assert(importCanonicalSnapshot(restored,R"({"schemaVersion":1,"projectId":"broken"})").find("\"ok\":false")!=std::string::npos);
 assert(exportCanonicalSnapshot(restored)==beforeBad);
 // Optimistic generation check: two imports based on the same session generation cannot both replace it.
 const auto concurrentJson=exportCanonicalSnapshot(h);std::atomic<int> successfulImports{0};
 std::thread t1([&]{if(importCanonicalSnapshot(h,concurrentJson).find("\"ok\":true")!=std::string::npos)++successfulImports;});
 std::thread t2([&]{if(importCanonicalSnapshot(h,concurrentJson).find("\"ok\":true")!=std::string::npos)++successfulImports;});t1.join();t2.join();assert(successfulImports==1);assert(exportCanonicalSnapshot(h)==concurrentJson);
 auto removedDuringImport=createSession();std::thread t3([&]{assert(importCanonicalSnapshot(removedDuringImport,concurrentJson).find("\"ok\":true")==std::string::npos);});assert(destroySession(removedDuringImport));t3.join();
 assert(destroySession(restored));
 assert(destroySession(h));assert(!destroySession(h));assert(exportCanonicalSnapshot(h).find("Invalid native handle")!=std::string::npos);return 0;}
