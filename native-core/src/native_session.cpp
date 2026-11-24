#include "bimob/native_session.hpp"
#include "bimob/canonical_json.hpp"
#include <algorithm>
#include <map>
#include <memory>
#include <mutex>
#include <regex>
#include <stdexcept>
namespace bimob::bridge {
namespace {
struct Session {
 Project project; CommandBus bus; std::string gesture;
 Session():project{ModelId{"native-project"},"Native Engine Session",CowVector<Wall>{},CowVector<Level>{},CowVector<WallJoin>{},CowVector<Opening>{},CowVector<CatalogInstance>{}},bus(project){}
};
std::mutex registryMutex;std::map<std::uint64_t,std::unique_ptr<Session>> sessions;std::uint64_t nextHandle=1;
std::string quote(const std::string&s){std::string o="\"";for(char c:s){if(c=='"'||c=='\\')o+='\\';if(c=='\n')o+="\\n";else if(c=='\r')o+="\\r";else o+=c;}return o+"\"";}
std::string field(const std::string&j,const std::string&k){std::regex r("\""+k+"\""+R"REGEX(\s*:\s*"((?:\\.|[^"])*)")REGEX");std::smatch m;if(!std::regex_search(j,m,r))return {};return m[1].str();}
double number(const std::string&j,const std::string&k,double fallback=0){std::regex r("\""+k+"\"\\s*:\\s*(-?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?)");std::smatch m;if(!std::regex_search(j,m,r))return fallback;return std::stod(m[1].str());}
std::vector<std::string> objectsInArray(const std::string&j,const std::string&key){auto pos=j.find("\""+key+"\"");if(pos==std::string::npos)return {};pos=j.find('[',pos);if(pos==std::string::npos)return {};std::vector<std::string>out;bool inString=false,escape=false;int depth=0;std::size_t start=0;for(std::size_t i=pos+1;i<j.size();++i){char c=j[i];if(inString){if(escape)escape=false;else if(c=='\\')escape=true;else if(c=='"')inString=false;continue;}if(c=='"'){inString=true;continue;}if(c=='{'){if(depth++==0)start=i;}else if(c=='}'&&depth>0){if(--depth==0)out.push_back(j.substr(start,i-start+1));}else if(c==']'&&depth==0)break;}return out;}
std::string errorJson(const std::string&msg){return "{\"ok\":false,\"error\":"+quote(msg)+"}";}
std::string response(Session&s,bool ok,const std::string&err={}){auto rev=s.bus.snapshot();return "{\"ok\":"+std::string(ok?"true":"false")+",\"revision\":"+std::to_string(rev->number)+",\"snapshot\":"+canonicalProjectJson(*rev->project)+(err.empty()?"":",\"error\":"+quote(err))+"}";}
Mutation parseMutation(const std::string&j){
 const auto type=field(j,"type");ModelId id{field(j,"id")};
 if(type=="CreateLevel")return commands::createLevel(Level{id,field(j,"name"),Meters{number(j,"elevationMeters")},Meters{number(j,"heightMeters",3)}});
 if(type=="CreateWall"){Wall w{id,WallAxis{{Meters{number(j,"startX")},Meters{number(j,"startY")}},{Meters{number(j,"endX")},Meters{number(j,"endY")}}},Meters{number(j,"heightMeters",2.7)},Meters{number(j,"thicknessMeters",.2)},ModelId{field(j,"levelId")}};return commands::createWall(w);}
 if(type=="MoveWall")return commands::moveWall(id,Point2{Meters{number(j,"deltaX")},Meters{number(j,"deltaY")}});
 if(type=="ResizeWall")return commands::resizeWall(id,Meters{number(j,"lengthMeters")});
 if(type=="DeleteWall")return commands::deleteWall(id);
 if(type=="CreateOpening"){Opening o{id,ModelId{field(j,"hostWallId")},OpeningKind::Generic,Meters{number(j,"offsetAlongWallMeters")},Meters{number(j,"widthMeters")},Meters{number(j,"heightMeters")},Meters{number(j,"sillHeightMeters")}};auto kind=field(j,"openingKind");if(kind=="DOOR")o.kind=OpeningKind::Door;else if(kind=="WINDOW")o.kind=OpeningKind::Window;return commands::createOpening(o);}
 if(type=="UpdateOpening")return commands::batch({commands::moveOpening(id,Meters{number(j,"offsetAlongWallMeters")},Meters{number(j,"sillHeightMeters")}),commands::resizeOpening(id,Meters{number(j,"widthMeters")},Meters{number(j,"heightMeters")})});
 if(type=="DeleteOpening")return commands::deleteOpening(id);
 if(type=="CreateCatalogInstance"){CatalogInstance c{};c.id=id;c.assetId=field(j,"assetId");c.position={Meters{number(j,"x")},Meters{number(j,"y")}};c.rotationRadians=number(j,"rotationRadians");c.levelId=ModelId{field(j,"levelId")};c.hostWallId=ModelId{field(j,"hostWallId")};c.targetWidth=Meters{number(j,"widthMeters",1)};c.targetDepth=Meters{number(j,"depthMeters",1)};c.targetHeight=Meters{number(j,"heightMeters",1)};c.category=field(j,"category");c.scheduleTag=field(j,"scheduleTag");return commands::createCatalogInstance(c);}
 if(type=="UpdateCatalogInstance")return commands::batch({commands::moveCatalogInstance(id,Point2{Meters{number(j,"x")},Meters{number(j,"y")}}),commands::resizeCatalogInstance(id,Meters{number(j,"widthMeters")},Meters{number(j,"depthMeters")},Meters{number(j,"heightMeters")})});
 if(type=="DeleteCatalogInstance")return commands::deleteCatalogInstance(id);
 if(type=="Batch"){std::vector<Mutation>ops;for(const auto&o:objectsInArray(j,"commands"))ops.push_back(parseMutation(o));return commands::batch(std::move(ops));}
 throw std::invalid_argument("Unsupported command type: "+type);
}
}
std::uint64_t createSession(){std::lock_guard<std::mutex>g(registryMutex);if(nextHandle==0)throw std::overflow_error("Native handle space exhausted");auto h=nextHandle++;sessions.emplace(h,std::make_unique<Session>());return h;}
bool destroySession(std::uint64_t h)noexcept{if(!h)return false;std::lock_guard<std::mutex>g(registryMutex);return sessions.erase(h)==1;}
bool beginGesture(std::uint64_t h,const std::string&id){std::lock_guard<std::mutex>g(registryMutex);auto it=sessions.find(h);if(it==sessions.end()||id.empty()||!it->second->gesture.empty())return false;it->second->gesture=id;return true;}
bool commitGesture(std::uint64_t h,const std::string&id){std::lock_guard<std::mutex>g(registryMutex);auto it=sessions.find(h);if(it==sessions.end()||it->second->gesture!=id)return false;it->second->gesture.clear();return true;}
bool cancelGesture(std::uint64_t h,const std::string&id){std::lock_guard<std::mutex>g(registryMutex);auto it=sessions.find(h);if(it==sessions.end()||it->second->gesture!=id)return false;auto r=it->second->bus.undo();it->second->gesture.clear();return r.ok();}
std::string exportCanonicalSnapshot(std::uint64_t h){std::lock_guard<std::mutex>g(registryMutex);auto it=sessions.find(h);if(it==sessions.end())return errorJson("Invalid native handle");return canonicalProjectJson(*it->second->bus.snapshot()->project);}
std::string dispatchCommandJson(std::uint64_t h,const std::string&j){std::lock_guard<std::mutex>g(registryMutex);auto it=sessions.find(h);if(it==sessions.end())return errorJson("Invalid native handle");auto&s=*it->second;try{auto type=field(j,"type");if(type=="Undo"){auto r=s.bus.undo();return response(s,r.ok(),r.ok()?"":r.error()->message);}if(type=="Redo"){auto r=s.bus.redo();return response(s,r.ok(),r.ok()?"":r.error()->message);}if(type=="beginGesture")return response(s,!s.gesture.empty()?false:(s.gesture=field(j,"gestureId"),!s.gesture.empty()),"");if(type=="commitGesture"){bool ok=!s.gesture.empty()&&s.gesture==field(j,"gestureId");if(ok)s.gesture.clear();return response(s,ok,ok?"":"Gesture mismatch");}if(type=="cancelGesture"){bool ok=!s.gesture.empty()&&s.gesture==field(j,"gestureId");if(ok){auto r=s.bus.undo();s.gesture.clear();return response(s,r.ok(),r.ok()?"":r.error()->message);}return response(s,false,"Gesture mismatch");}auto mutation=parseMutation(j);auto r=s.bus.execute(mutation,{},s.gesture.empty()?std::optional<std::string>{}:std::optional<std::string>{s.gesture});return response(s,r.ok(),r.ok()?"":r.error()->message);}catch(const std::exception&e){return response(s,false,e.what());}}
}
