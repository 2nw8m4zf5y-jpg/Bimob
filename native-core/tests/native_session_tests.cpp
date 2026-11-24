#include "bimob/native_session.hpp"
#include <cassert>
#include <string>
#include <cstdint>
#include <cstring>
#include <cmath>
#include <algorithm>
int main(){using namespace bimob::bridge;auto h=createSession();assert(h);assert(exportCanonicalSnapshot(h).find("Native Engine Session")!=std::string::npos);
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
 auto wc=readU32(16),jc=readU32(20),oc=readU32(24),cc=readU32(28);assert(wc==2&&jc==1&&oc==1&&cc==1);
 assert(geometry.size()>=32ULL+wc*40ULL+jc*36ULL+oc*32ULL+cc*32ULL);
 auto idOffset=32ULL+wc*40ULL+jc*36ULL+oc*32ULL+cc*32ULL;
 auto readId=[&](){auto n=readU32(idOffset);idOffset+=4;assert(n<=geometry.size()-idOffset);std::string id(reinterpret_cast<const char*>(geometry.data()+idOffset),n);idOffset+=n;return id;};
 assert(readId()=="W1"&&readId()=="W2");assert(readId()=="auto:W1:W2");assert(readId()=="O1");assert(readId()=="C1");assert(idOffset==geometry.size());
 auto readF=[&](std::size_t offset){auto bits=readU32(offset);float v;std::memcpy(&v,&bits,sizeof(v));return v;};
 for(std::size_t i=32;i<32+wc*40;i+=4)assert(std::isfinite(readF(i)));
 assert(std::abs(readF(32+8)-4.1f)<1e-4f&&std::abs(readF(32+9*4)-0.1f)<1e-4f);
 assert(std::abs(readF(32+4*4)-3.9f)<1e-4f&&std::abs(readF(32+5*4)+0.1f)<1e-4f);
 const std::size_t joinOff=32+wc*40;for(int i=0;i<9;i++)assert(std::isfinite(readF(joinOff+i*4)));assert(readF(joinOff+32)==3.0f);
 const std::size_t openingOff=joinOff+jc*36;assert(readF(openingOff+12)==0.9f&&readF(openingOff+16)==2.1f);
 const std::size_t catalogOff=openingOff+oc*32;assert(readF(catalogOff)==2.0f&&readF(catalogOff+12)==1.0f);
 
 assert(call(R"({"type":"UpdateOpening","id":"O1","offsetAlongWallMeters":1,"widthMeters":1,"heightMeters":2.2,"sillHeightMeters":0})").find("\"ok\":true")!=std::string::npos);
 assert(call(R"({"type":"MoveWall","id":"W1","deltaX":1,"deltaY":0})").find("\"ok\":true")!=std::string::npos);
 assert(call(R"({"type":"ResizeWall","id":"W1","lengthMeters":5})").find("\"ok\":true")!=std::string::npos);
 assert(call(R"({"type":"CreateCatalogInstance","id":"C2","assetId":"asset","levelId":"L1","x":0,"y":0,"widthMeters":1,"depthMeters":1,"heightMeters":1})").find("\"ok\":true")!=std::string::npos);
 assert(call(R"({"type":"UpdateCatalogInstance","id":"C2","x":2,"y":1,"widthMeters":2,"depthMeters":1,"heightMeters":1})").find("\"ok\":true")!=std::string::npos);
 assert(call(R"({"type":"Batch","commands":[{"type":"MoveWall","id":"W1","deltaX":1,"deltaY":0},{"type":"ResizeWall","id":"W1","lengthMeters":6}]})").find("\"ok\":true")!=std::string::npos);
 assert(beginGesture(h,"g1"));assert(call(R"({"type":"MoveWall","id":"W1","deltaX":1,"deltaY":0})").find("\"ok\":true")!=std::string::npos);assert(call(R"({"type":"MoveWall","id":"W1","deltaX":1,"deltaY":0})").find("\"ok\":true")!=std::string::npos);assert(commitGesture(h,"g1"));assert(call(R"({"type":"Undo"})").find("\"ok\":true")!=std::string::npos);assert(call(R"({"type":"Redo"})").find("\"ok\":true")!=std::string::npos);
 auto beforeEmptyCancel=exportCanonicalSnapshot(h);assert(beginGesture(h,"empty"));assert(cancelGesture(h,"empty"));assert(exportCanonicalSnapshot(h)==beforeEmptyCancel);
 auto beforeEditCancel=exportCanonicalSnapshot(h);assert(beginGesture(h,"cancel-edit"));assert(call(R"({"type":"MoveWall","id":"W1","deltaX":3,"deltaY":0})").find("\"ok\":true")!=std::string::npos);assert(cancelGesture(h,"cancel-edit"));assert(exportCanonicalSnapshot(h)==beforeEditCancel);
 assert(call(R"({"type":"DeleteWall","id":"W1"})").find("\"ok\":true")!=std::string::npos);auto snap=exportCanonicalSnapshot(h);assert(snap.find("\"id\":\"W1\"")==std::string::npos&&snap.find("\"id\":\"O1\"")==std::string::npos);
 auto stable=exportCanonicalSnapshot(h);assert(call(R"({"type":"MoveWall","id":"W1","deltaX":0,"deltaY":0} trailing)").find("\"ok\":false")!=std::string::npos);assert(exportCanonicalSnapshot(h)==stable);assert(call(R"([{"type":"Undo"}])").find("\"ok\":false")!=std::string::npos);assert(call(R"({"type":"Undo","type":"Redo"})").find("\"ok\":false")!=std::string::npos);assert(call(R"({"type":"MoveWall","id":"W1","deltaX":1e999,"deltaY":0})").find("\"ok\":false")!=std::string::npos);assert(exportCanonicalSnapshot(h)==stable);
 assert(destroySession(h));assert(!destroySession(h));assert(exportCanonicalSnapshot(h).find("Invalid native handle")!=std::string::npos);return 0;}
