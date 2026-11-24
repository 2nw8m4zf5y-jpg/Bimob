#include "bimob/native_session.hpp"
#include <cassert>
#include <string>
int main(){using bimob::bridge::createSession;auto h=createSession();assert(h!=0);auto initial=bimob::bridge::exportCanonicalSnapshot(h);assert(initial.find("Native Engine Session")!=std::string::npos);
 auto level=bimob::bridge::dispatchCommandJson(h,R"({"type":"CreateLevel","id":"L1","name":"Ground","elevationMeters":0,"heightMeters":3})");assert(level.find("\"ok\":true")!=std::string::npos);
 auto wall=bimob::bridge::dispatchCommandJson(h,R"({"type":"CreateWall","id":"W1","levelId":"L1","startX":0,"startY":0,"endX":4,"endY":0,"heightMeters":3,"thicknessMeters":0.2})");assert(wall.find("\"ok\":true")!=std::string::npos);
 auto opening=bimob::bridge::dispatchCommandJson(h,R"({"type":"CreateOpening","id":"O1","hostWallId":"W1","openingKind":"DOOR","offsetAlongWallMeters":0.5,"widthMeters":0.9,"heightMeters":2.1,"sillHeightMeters":0})");assert(opening.find("\"ok\":true")!=std::string::npos);
 auto snap=bimob::bridge::exportCanonicalSnapshot(h);assert(snap.find("\"id\":\"W1\"")!=std::string::npos&&snap.find("\"id\":\"O1\"")!=std::string::npos);
 auto undo=bimob::bridge::dispatchCommandJson(h,R"({"type":"Undo"})");assert(undo.find("\"ok\":true")!=std::string::npos);snap=bimob::bridge::exportCanonicalSnapshot(h);assert(snap.find("\"id\":\"O1\"")==std::string::npos);
 assert(bimob::bridge::destroySession(h));assert(!bimob::bridge::destroySession(h));assert(bimob::bridge::exportCanonicalSnapshot(h).find("Invalid native handle")!=std::string::npos);assert(!bimob::bridge::destroySession(0));return 0;}
