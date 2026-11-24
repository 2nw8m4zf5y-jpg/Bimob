#include "bimob/native_session.hpp"
#include "bimob/canonical_json.hpp"
#include <cmath>
#include <map>
#include <memory>
#include <mutex>
#include <regex>
#include <sstream>
namespace bimob::bridge {
namespace {
struct Session { Project project; CommandBus bus; Session():project{ModelId{"native-project"},"Native Engine Session",CowVector<Wall>{},CowVector<Level>{},CowVector<WallJoin>{},CowVector<Opening>{},CowVector<CatalogInstance>{}},bus(project){} };
std::mutex registryMutex;std::map<std::uint64_t,std::unique_ptr<Session>> sessions;std::uint64_t nextHandle=1;
std::string quote(const std::string&s){std::string o="\"";for(char c:s){if(c=='"'||c=='\\')o+='\\';if(c=='\n')o+="\\n";else if(c=='\r')o+="\\r";else o+=c;}return o+"\"";}
std::string field(const std::string&j,const std::string&k){std::regex r("\""+k+"\""+R"REGEX(\s*:\s*"((?:\\.|[^"])*)")REGEX");std::smatch m;if(!std::regex_search(j,m,r))return {};return m[1].str();}
double number(const std::string&j,const std::string&k,double fallback=0){std::regex r("\""+k+"\"\\s*:\\s*(-?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?)");std::smatch m;if(!std::regex_search(j,m,r))return fallback;return std::stod(m[1].str());}
std::string errorJson(const std::string&msg){return "{\"ok\":false,\"error\":"+quote(msg)+"}";}
std::string response(Session&s,bool ok,const std::string&err={}){auto rev=s.bus.snapshot();return "{\"ok\":"+std::string(ok?"true":"false")+",\"revision\":"+std::to_string(rev->number)+",\"snapshot\":"+canonicalProjectJson(*rev->project)+(err.empty()?"":",\"error\":"+quote(err))+"}";}
Mutation parseMutation(const std::string&j){
 const auto type=field(j,"type");
 if(type=="CreateLevel")return commands::createLevel(Level{ModelId{field(j,"id")},field(j,"name"),Meters{number(j,"elevationMeters")},Meters{number(j,"heightMeters",3)}});
 if(type=="CreateWall"){Wall w{ModelId{field(j,"id")},WallAxis{{Meters{number(j,"startX")},Meters{number(j,"startY")}},{Meters{number(j,"endX")},Meters{number(j,"endY")}}},Meters{number(j,"heightMeters",2.7)},Meters{number(j,"thicknessMeters",.2)},ModelId{field(j,"levelId")}};return commands::createWall(w);}
 if(type=="CreateOpening"){Opening o{ModelId{field(j,"id")},ModelId{field(j,"hostWallId")},OpeningKind::Generic,Meters{number(j,"offsetAlongWallMeters")},Meters{number(j,"widthMeters")},Meters{number(j,"heightMeters")},Meters{number(j,"sillHeightMeters")}};const auto kind=field(j,"openingKind");if(kind=="DOOR")o.kind=OpeningKind::Door;else if(kind=="WINDOW")o.kind=OpeningKind::Window;return commands::createOpening(o);}
 if(type=="Undo"||type=="Redo")return {};
 throw std::invalid_argument("Unsupported command type: "+type);
}
}
std::uint64_t createSession(){std::lock_guard<std::mutex>g(registryMutex);if(nextHandle==0)throw std::overflow_error("Native handle space exhausted");auto h=nextHandle++;sessions.emplace(h,std::make_unique<Session>());return h;}
bool destroySession(std::uint64_t h)noexcept{if(!h)return false;std::lock_guard<std::mutex>g(registryMutex);return sessions.erase(h)==1;}
std::string exportCanonicalSnapshot(std::uint64_t h){std::lock_guard<std::mutex>g(registryMutex);auto it=sessions.find(h);if(it==sessions.end())return errorJson("Invalid native handle");return canonicalProjectJson(*it->second->bus.snapshot()->project);}
std::string dispatchCommandJson(std::uint64_t h,const std::string&j){std::lock_guard<std::mutex>g(registryMutex);auto it=sessions.find(h);if(it==sessions.end())return errorJson("Invalid native handle");auto&s=*it->second;try{auto type=field(j,"type");if(type=="Undo"){auto r=s.bus.undo();return response(s,r.ok(),r.ok()?"":r.error()->message);}if(type=="Redo"){auto r=s.bus.redo();return response(s,r.ok(),r.ok()?"":r.error()->message);}auto mutation=parseMutation(j);auto r=s.bus.execute(mutation);return response(s,r.ok(),r.ok()?"":r.error()->message);}catch(const std::exception&e){return response(s,false,e.what());}}
}
