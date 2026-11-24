#include "bimob/native_session.hpp"
#include <cassert>
#include <string>
int main(){using namespace bimob::bridge;auto h=createSession();assert(h);assert(exportCanonicalSnapshot(h).find("Native Engine Session")!=std::string::npos);
 auto call=[&](const char*j){return dispatchCommandJson(h,j);};
 assert(call(R"({"type":"CreateLevel","id":"L1","name":"Ground","elevationMeters":0,"heightMeters":3})").find("\"ok\":true")!=std::string::npos);
 assert(call(R"({"type":"CreateWall","id":"W1","levelId":"L1","startX":0,"startY":0,"endX":4,"endY":0,"heightMeters":3,"thicknessMeters":0.2})").find("\"ok\":true")!=std::string::npos);
 assert(call(R"({"type":"CreateOpening","id":"O1","hostWallId":"W1","openingKind":"DOOR","offsetAlongWallMeters":0.5,"widthMeters":0.9,"heightMeters":2.1,"sillHeightMeters":0})").find("\"ok\":true")!=std::string::npos);
 assert(call(R"({"type":"UpdateOpening","id":"O1","offsetAlongWallMeters":1,"widthMeters":1,"heightMeters":2.2,"sillHeightMeters":0})").find("\"ok\":true")!=std::string::npos);
 assert(call(R"({"type":"MoveWall","id":"W1","deltaX":1,"deltaY":0})").find("\"ok\":true")!=std::string::npos);
 assert(call(R"({"type":"ResizeWall","id":"W1","lengthMeters":5})").find("\"ok\":true")!=std::string::npos);
 assert(call(R"({"type":"CreateCatalogInstance","id":"C1","assetId":"asset","levelId":"L1","x":0,"y":0,"widthMeters":1,"depthMeters":1,"heightMeters":1})").find("\"ok\":true")!=std::string::npos);
 assert(call(R"({"type":"UpdateCatalogInstance","id":"C1","x":2,"y":1,"widthMeters":2,"depthMeters":1,"heightMeters":1})").find("\"ok\":true")!=std::string::npos);
 assert(call(R"({"type":"Batch","commands":[{"type":"MoveWall","id":"W1","deltaX":1,"deltaY":0},{"type":"ResizeWall","id":"W1","lengthMeters":6}]})").find("\"ok\":true")!=std::string::npos);
 assert(beginGesture(h,"g1"));assert(call(R"({"type":"MoveWall","id":"W1","deltaX":1,"deltaY":0})").find("\"ok\":true")!=std::string::npos);assert(call(R"({"type":"MoveWall","id":"W1","deltaX":1,"deltaY":0})").find("\"ok\":true")!=std::string::npos);assert(commitGesture(h,"g1"));assert(call(R"({"type":"Undo"})").find("\"ok\":true")!=std::string::npos);assert(call(R"({"type":"Redo"})").find("\"ok\":true")!=std::string::npos);
 auto beforeEmptyCancel=exportCanonicalSnapshot(h);assert(beginGesture(h,"empty"));assert(cancelGesture(h,"empty"));assert(exportCanonicalSnapshot(h)==beforeEmptyCancel);
 auto beforeEditCancel=exportCanonicalSnapshot(h);assert(beginGesture(h,"cancel-edit"));assert(call(R"({"type":"MoveWall","id":"W1","deltaX":3,"deltaY":0})").find("\"ok\":true")!=std::string::npos);assert(cancelGesture(h,"cancel-edit"));assert(exportCanonicalSnapshot(h)==beforeEditCancel);
 assert(call(R"({"type":"DeleteWall","id":"W1"})").find("\"ok\":true")!=std::string::npos);auto snap=exportCanonicalSnapshot(h);assert(snap.find("\"id\":\"W1\"")==std::string::npos&&snap.find("\"id\":\"O1\"")==std::string::npos);
 assert(destroySession(h));assert(!destroySession(h));assert(exportCanonicalSnapshot(h).find("Invalid native handle")!=std::string::npos);return 0;}
