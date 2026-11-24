#include "bimob/native_session.hpp"
#include "bimob/canonical_json.hpp"
#include "bimob/json_value.hpp"
#include <algorithm>
#include <map>
#include <memory>
#include <mutex>
#include <regex>
#include <stdexcept>
#include <set>
#include <array>
#include <cstring>
#include <limits>
namespace bimob::bridge {
namespace {
struct Session {
 Project project; CommandBus bus; std::string gesture; bool gestureHasChanges=false; std::uint64_t generation=0;
 explicit Session(Project initial):project(std::move(initial)),bus(project){}
};
std::mutex registryMutex;std::map<std::uint64_t,std::shared_ptr<Session>> sessions;std::uint64_t nextHandle=1;
std::string quote(const std::string&s){std::string o="\"";for(char c:s){if(c=='"'||c=='\\')o+='\\';if(c=='
')o+="\
";else if(c=='\r')o+="\\r";else o+=c;}return o+"\"";}
std::string field(const std::string&j,const std::string&k){std::regex r("\""+k+"\""+R"REGEX(\s*:\s*"((?:\\.|[^"])*)")REGEX");std::smatch m;if(!std::regex_search(j,m,r))return {};return m[1].str();}
double number(const std::string&j,const std::string&k,double fallback=0){std::regex r("\""+k+"\"\\s*:\\s*(-?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?)");std::smatch m;if(!std::regex_search(j,m,r))return fallback;return std::stod(m[1].str());}
std::vector<std::string> objectsInArray(const std::string&j,const std::string&key){auto pos=j.find("rotationRadians");if(pos==std::string::npos)return {};pos=j.find('[',pos);if(pos==std::string::npos)return {};std::vector<std::string>out;bool inString=false,escape=false;int depth=0;std::size_t start=0;for(std::size_t i=pos+1;i<j.size();++i){char c=j[i];if(inString){if(escape)escape=false;else if(c=='\\')escape=true;else if(c=='"')inString=false;continue;}if(c=='"'){inString=true;continue;}if(c=='{'){if(depth++==0)start=i;}else if(c=='}'&&depth>0){if(--depth==0)out.push_back(j.substr(start,i-start+1));}else if(c==']'&&depth==0)break;}return out;}
std::string errorJson(const std::string&msg){return "{\"ok\":false,\"error\":"+quote(msg)+"}";}
std::string response(Session&s,bool ok,const std::string&err={}){auto rev=s.bus.snapshot();return "{\"ok\":"+std::string(ok?"true":"false")+",\"revision\":"+std::to_string(rev->number)+",\"canUndo\":"+(s.bus.undoDepth()>0?"true":"false")+",\"canRedo\":"+(s.bus.redoDepth()>0?"true":"false")+",\"snapshot\":"+canonicalProjectJson(*rev->project)+(err.empty()?"":",\"error\":"+quote(err))+"}";}
Mutation parseMutation(const std::string&j){
 const auto type=field(j,"type");ModelId id{field(j,"id")};
 if(type=="CreateLevel")return commands::createLevel(Level{id,field(j,"name"),Meters{number(j,"elevationMeters")},Meters{number(j,"heightMeters",3)}});
 if(type=="CreateWall"){const ModelId levelId{field(j,"levelId")};if(!levelId.valid())throw std::invalid_argument("CreateWall requires an active level.");const double sx=number(j,"startX"),sy=number(j,"startY"),ex=number(j,"endX"),ey=number(j,"endY");if(!std::isfinite(sx)||!std::isfinite(sy)||!std::isfinite(ex)||!std::isfinite(ey))throw std::invalid_argument("Wall coordinates must be finite.");const double length=std::hypot(ex-sx,ey-sy);if(!std::isfinite(length)||length<0.05)throw std::invalid_argument("Wall length must be at least 0.05 metres.");Wall w{id,WallAxis{{Meters{sx},Meters{sy}},{Meters{ex},Meters{ey}}},Meters{number(j,"heightMeters",2.7)},Meters{number(j,"thicknessMeters",.2)},levelId};return commands::createWall(w);}
 if(type=="MoveWall")return commands::moveWall(id,Point2{Meters{number(j,"deltaX")},Meters{number(j,"deltaY")}});
 if(type=="ResizeWall")return commands::resizeWall(id,Meters{number(j,"lengthMeters")});
 if(type=="RotateWall"){
  return commands::batch({
   commands::moveWallEndpoint(id,true,Point2{Meters{number(j,"startX")},Meters{number(j,"startY")}}),
   commands::moveWallEndpoint(id,false,Point2{Meters{number(j,"endX")},Meters{number(j,"endY")}})
  });
 }
 if(type=="DeleteWall")return commands::deleteWall(id);
 if(type=="CreateOpening"){Opening o{id,ModelId{field(j,"hostWallId")},OpeningKind::Generic,Meters{number(j,"offsetAlongWallMeters")},Meters{number(j,"widthMeters")},Meters{number(j,"heightMeters")},Meters{number(j,"sillHeightMeters")}};auto kind=field(j,"openingKind");if(kind=="DOOR")o.kind=OpeningKind::Door;else if(kind=="WINDOW")o.kind=OpeningKind::Window;return commands::createOpening(o);}
 if(type=="UpdateOpening")return commands::batch({commands::moveOpening(id,Meters{number(j,"offsetAlongWallMeters")},Meters{number(j,"sillHeightMeters")}),commands::resizeOpening(id,Meters{number(j,"widthMeters")},Meters{number(j,"heightMeters")})});
 if(type=="DeleteOpening")return commands::deleteOpening(id);
 if(type=="CreateCatalogInstance"){CatalogInstance c{};c.id=id;c.assetId=field(j,"assetId");c.position={Meters{number(j,"x")},Meters{number(j,"y")}};c.rotationRadians=number(j,"rotationRadians");c.levelId=ModelId{field(j,"levelId")};c.hostWallId=ModelId{field(j,"hostWallId")};c.targetWidth=Meters{number(j,"widthMeters",1)};c.targetDepth=Meters{number(j,"depthMeters",1)};c.targetHeight=Meters{number(j,"heightMeters",1)};c.widthBounds={Meters{0.001},Meters{1000}};c.depthBounds={Meters{0.001},Meters{1000}};c.heightBounds={Meters{0.001},Meters{1000}};c.category=field(j,"category");c.scheduleTag=field(j,"scheduleTag");return commands::createCatalogInstance(c);}
 if(type=="UpdateCatalogInstance"){std::vector<Mutation>ops{commands::moveCatalogInstance(id,Point2{Meters{number(j,"x")},Meters{number(j,"y")}}),commands::resizeCatalogInstance(id,Meters{number(j,"widthMeters")},Meters{number(j,"depthMeters")},Meters{number(j,"heightMeters")})};if(j.find("rotationRadians")!=std::string::npos)ops.push_back(commands::rotateCatalogInstance(id,number(j,"rotationRadians")));return commands::batch(std::move(ops));}
 if(type=="DeleteCatalogInstance")return commands::deleteCatalogInstance(id);
 if(type=="Batch"){std::vector<Mutation>ops;for(const auto&o:objectsInArray(j,"commands"))ops.push_back(parseMutation(o));return commands::batch(std::move(ops));}
 throw std::invalid_argument("Unsupported command type: "+type);
}

struct RenderJoin {std::string id;WallJoin join;};
std::vector<RenderJoin> collectRenderJoins(const Project&p){
 std::vector<RenderJoin>out;std::set<std::pair<std::string,std::string>>known;
 for(const auto&j:p.joins){auto a=j.wallA.value,b=j.wallB.value;if(b<a)std::swap(a,b);known.emplace(a,b);out.push_back({j.id.value,j});}
 constexpr double eps=1e-7;
 for(std::size_t i=0;i<p.walls.size();++i)for(std::size_t k=i+1;k<p.walls.size();++k){
  const auto&a=p.walls[i];const auto&b=p.walls[k];auto ids=std::pair{a.id.value,b.id.value};if(ids.second<ids.first)std::swap(ids.first,ids.second);if(known.contains(ids))continue;
  const double ax=a.axis.start.x.value,ay=a.axis.start.y.value,adx=a.axis.end.x.value-ax,ady=a.axis.end.y.value-ay;
  const double bx=b.axis.start.x.value,by=b.axis.start.y.value,bdx=b.axis.end.x.value-bx,bdy=b.axis.end.y.value-by;
  const double den=adx*bdy-ady*bdx;if(std::abs(den)<1e-12)continue;
  const double t=((bx-ax)*bdy-(by-ay)*bdx)/den,u=((bx-ax)*ady-(by-ay)*adx)/den;
  if(t<-eps||t>1+eps||u<-eps||u>1+eps)continue;
  Point2 pt{Meters{ax+t*adx},Meters{ay+t*ady}};
  WallJoin j{ModelId{"auto:"+ids.first+":"+ids.second},a.id,t>=1-eps,b.id,u>=1-eps,pt};
  out.push_back({j.id.value,j});known.emplace(ids);
 }
 std::sort(out.begin(),out.end(),[](const auto&a,const auto&b){return a.id<b.id;});return out;
}
void appendU32(std::vector<std::uint8_t>&o,std::uint32_t v){for(unsigned i=0;i<4;++i)o.push_back(static_cast<std::uint8_t>(v>>(8*i)));}
void appendU64(std::vector<std::uint8_t>&o,std::uint64_t v){for(unsigned i=0;i<8;++i)o.push_back(static_cast<std::uint8_t>(v>>(8*i)));}
void appendString(std::vector<std::uint8_t>&o,const std::string&s){if(s.size()>std::numeric_limits<std::uint32_t>::max())throw std::runtime_error("Render geometry ID exceeds encoding limit");appendU32(o,static_cast<std::uint32_t>(s.size()));o.insert(o.end(),s.begin(),s.end());}
void appendF32(std::vector<std::uint8_t>&o,double v){if(!std::isfinite(v)||std::abs(v)>std::numeric_limits<float>::max())throw std::runtime_error("Render geometry contains non-finite/out-of-range value");float f=static_cast<float>(v);std::uint32_t bits;std::memcpy(&bits,&f,sizeof(bits));appendU32(o,bits);}

std::array<Point2,4> wallFootprint(const Wall&w,const Project&p,const std::vector<RenderJoin>&joins){
 double dx=w.axis.end.x.value-w.axis.start.x.value,dy=w.axis.end.y.value-w.axis.start.y.value,len=std::hypot(dx,dy);if(!std::isfinite(len)||len<0.05)throw std::runtime_error("Wall footprint requires a finite axis of at least 0.05 metres");double ux=dx/len,uy=dy/len,nx=-uy,ny=ux,half=w.thickness.value*.5;
 std::array<Point2,4>v{{{Meters{w.axis.start.x.value+nx*half},Meters{w.axis.start.y.value+ny*half}},{Meters{w.axis.end.x.value+nx*half},Meters{w.axis.end.y.value+ny*half}},{Meters{w.axis.end.x.value-nx*half},Meters{w.axis.end.y.value-ny*half}},{Meters{w.axis.start.x.value-nx*half},Meters{w.axis.start.y.value-ny*half}}}};
 for(const auto&r:joins){
  const bool isA=r.join.wallA==w.id,isB=r.join.wallB==w.id;if(!isA&&!isB)continue;
  const double jx=r.join.joint.x.value-w.axis.start.x.value,jy=r.join.joint.y.value-w.axis.start.y.value,projection=(jx*dx+jy*dy)/(len*len);const bool atEnd=projection>=1-1e-7;if(projection>1e-7&&projection<1-1e-7)continue;
  const Wall*other=nullptr;for(const auto&candidate:p.walls)if(candidate.id==(isA?r.join.wallB:r.join.wallA)){other=&candidate;break;}if(!other)continue;
  const double odx=other->axis.end.x.value-other->axis.start.x.value,ody=other->axis.end.y.value-other->axis.start.y.value,olen=std::hypot(odx,ody);if(olen<=kMinWallLength)continue;
  const double oux=odx/olen,ouy=ody/olen,onx=-ouy,ony=oux,den=ux*ouy-uy*oux;if(std::abs(den)<1e-12)continue;
  for(int side:{-1,1}){
   const double ownOff=side*half,otherSide=-side*(den>0?1:-1),otherOff=otherSide*other->thickness.value*.5;
   const double px=r.join.joint.x.value+nx*ownOff,py=r.join.joint.y.value+ny*ownOff;
   const double qx=r.join.joint.x.value+onx*otherOff,qy=r.join.joint.y.value+ony*otherOff;
   const double t=((qx-px)*ouy-(qy-py)*oux)/den;Point2 m{Meters{px+t*ux},Meters{py+t*uy}};
   const std::size_t index=atEnd?(side>0?1:2):(side>0?0:3);v[index]=m;
  }
 }
 return v;
}

const Level* levelById(const Project&p,const ModelId&id){auto it=std::find_if(p.levels.begin(),p.levels.end(),[&](const Level&l){return l.id==id;});return it==p.levels.end()?nullptr:&*it;}
std::vector<std::uint8_t> geometryBytes(const ModelRevision&rev){
 const Project&p=*rev.project;auto joins=collectRenderJoins(p);std::vector<std::uint8_t>o;o.reserve(24+p.walls.size()*32+joins.size()*32+p.openings.size()*32+p.catalogInstances.size()*32);
 o.insert(o.end(),{'B','G','R','1'});appendU32(o,1);appendU64(o,rev.number);appendU32(o,static_cast<std::uint32_t>(p.walls.size()));appendU32(o,static_cast<std::uint32_t>(joins.size()));appendU32(o,static_cast<std::uint32_t>(p.openings.size()));appendU32(o,static_cast<std::uint32_t>(p.catalogInstances.size()));
 std::vector<const Wall*>walls;for(const auto&w:p.walls)walls.push_back(&w);std::sort(walls.begin(),walls.end(),[](auto*a,auto*b){return a->id.value<b->id.value;});
 for(const Wall*w:walls){if(!w->axis.valid())throw std::runtime_error("Invalid wall length in geometry export");auto v=wallFootprint(*w,p,joins);for(auto q:v){appendF32(o,q.x.value);appendF32(o,q.y.value);}auto lv=levelById(p,w->levelId);appendF32(o,lv?lv->elevation.value:0);appendF32(o,w->height.value);}
 for(const auto&j:joins){auto g=solveWallJoinGeometry(p,j.join);if(!g.ok())throw std::runtime_error("WallJoin geometry failed: "+g.error()->message);for(const auto&q:g.value()->vertices){appendF32(o,q.x.value);appendF32(o,q.y.value);}double h=0;for(const auto&w:p.walls)if(w.id==j.join.wallA||w.id==j.join.wallB)h=std::max(h,w.height.value);appendF32(o,h);}
 std::vector<const Opening*>openings;for(const auto&x:p.openings)openings.push_back(&x);std::sort(openings.begin(),openings.end(),[](auto*a,auto*b){return a->id.value<b->id.value;});
 for(const Opening*x:openings){auto w=std::find_if(p.walls.begin(),p.walls.end(),[&](const Wall&a){return a.id==x->hostWallId;});if(w==p.walls.end())throw std::runtime_error("Opening references missing wall");double len=w->axis.length().value,ux=(w->axis.end.x.value-w->axis.start.x.value)/len,uy=(w->axis.end.y.value-w->axis.start.y.value)/len,center=x->offsetAlongWall.value+x->width.value*.5;auto lv=levelById(p,w->levelId);appendF32(o,w->axis.start.x.value+ux*center);appendF32(o,w->axis.start.y.value+uy*center);appendF32(o,x->offsetAlongWall.value);appendF32(o,x->width.value);appendF32(o,x->height.value);appendF32(o,x->sillHeight.value);appendF32(o,lv?lv->elevation.value:0);appendF32(o,len);}
 std::vector<const CatalogInstance*>catalog;for(const auto&x:p.catalogInstances)catalog.push_back(&x);std::sort(catalog.begin(),catalog.end(),[](auto*a,auto*b){return a->id.value<b->id.value;});
 for(const auto*x:catalog){auto lv=levelById(p,x->levelId);appendF32(o,x->position.x.value);appendF32(o,x->position.y.value);appendF32(o,x->rotationRadians);appendF32(o,x->targetWidth.value);appendF32(o,x->targetDepth.value);appendF32(o,x->targetHeight.value);appendF32(o,lv?lv->elevation.value:0);appendF32(o,x->hostWallId.valid()?1:0);}
 // ID tables follow numeric payload in section order and match the deterministic record sorting.
 for(const auto*w:walls)appendString(o,w->id.value);
 for(const auto&j:joins)appendString(o,j.id);
 for(const auto*x:openings)appendString(o,x->id.value);
 for(const auto*x:catalog)appendString(o,x->id.value);
 return o;
}

}

namespace {
const json::Value& req(const json::Value&v,const char*k){auto*p=v.get(k);if(!p)throw std::invalid_argument(std::string("Missing field: ")+k);return *p;}
std::string js(const json::Value&v,const char*k){auto*p=req(v,k).string();if(!p)throw std::invalid_argument(std::string("Expected string: ")+k);return *p;}
double jn(const json::Value&v,const char*k){auto*p=req(v,k).number();if(!p||!std::isfinite(*p))throw std::invalid_argument(std::string("Expected number: ")+k);return *p;}
bool jb(const json::Value&v,const char*k){auto*p=req(v,k).boolean();if(!p)throw std::invalid_argument(std::string("Expected boolean: ")+k);return *p;}
ModelId ji(const json::Value&v,const char*k){return ModelId{js(v,k)};}
ModelId jni(const json::Value&v,const char*k){const auto&x=req(v,k);if(std::holds_alternative<std::nullptr_t>(x.data))return {};auto*p=x.string();if(!p)throw std::invalid_argument("Expected nullable ID");return ModelId{*p};}
Point2 jp(const json::Value&v,const char*k){const auto&x=req(v,k);return {Meters{jn(x,"xMeters")},Meters{jn(x,"yMeters")}};}
DimensionBounds jbounds(const json::Value&v,const char*k){const auto&x=req(v,k);return {Meters{jn(x,"min")},Meters{jn(x,"max")}};}
Project parseSnapshot(const std::string&s){
 auto root=json::parse(s);if(!root.object()||jn(root,"schemaVersion")!=1)throw std::invalid_argument("Unsupported canonical schema");
 Project p{ji(root,"projectId"),js(root,"name"),{},{},{},{},{}};
 auto*ls=req(root,"levels").array();auto*joins=req(root,"joins").array();auto*es=req(root,"elements").array();
 if(!ls||!joins||!es||ls->size()>10000||joins->size()>100000||es->size()>250000)throw std::invalid_argument("Invalid snapshot collections or entity budget");
 for(const auto&v:*ls)p.levels.push_back({ji(v,"id"),js(v,"name"),Meters{jn(v,"elevationMeters")},Meters{jn(v,"heightMeters")}});
 for(const auto&v:*joins)p.joins.push_back({ji(v,"id"),ji(v,"wallA"),jb(v,"endA"),ji(v,"wallB"),jb(v,"endB"),jp(v,"point")});
 for(const auto&v:*es){auto k=js(v,"kind");
 if(k=="wall"){const auto&a=req(v,"axis");p.walls.push_back({ji(v,"id"),{jp(a,"start"),jp(a,"end")},Meters{jn(v,"heightMeters")},Meters{jn(v,"thicknessMeters")},jni(v,"levelId")});}
 else if(k=="opening"){auto t=js(v,"openingKind");OpeningKind kind=t=="DOOR"?OpeningKind::Door:t=="WINDOW"?OpeningKind::Window:t=="GENERIC"?OpeningKind::Generic:throw std::invalid_argument("Invalid opening kind");p.openings.push_back({ji(v,"id"),ji(v,"hostWallId"),kind,Meters{jn(v,"offsetAlongWallMeters")},Meters{jn(v,"widthMeters")},Meters{jn(v,"heightMeters")},Meters{jn(v,"sillHeightMeters")}});}
 else if(k=="catalogInstance"){CatalogInstance c{};c.id=ji(v,"id");c.assetId=js(v,"assetId");c.position=jp(v,"position");c.rotationRadians=jn(v,"rotationRadians");c.levelId=jni(v,"levelId");c.hostWallId=jni(v,"hostWallId");c.targetWidth=Meters{jn(v,"targetWidthMeters")};c.targetDepth=Meters{jn(v,"targetDepthMeters")};c.targetHeight=Meters{jn(v,"targetHeightMeters")};auto m=js(v,"scalingMode");c.scalingMode=m=="FREE_STRETCH"?ScalingMode::FreeStretch:m=="UNIFORM"?ScalingMode::Uniform:m=="PRESETS"?ScalingMode::Presets:throw std::invalid_argument("Invalid scaling mode");c.widthBounds=jbounds(v,"widthBounds");c.depthBounds=jbounds(v,"depthBounds");c.heightBounds=jbounds(v,"heightBounds");c.category=js(v,"category");c.scheduleTag=js(v,"scheduleTag");p.catalogInstances.push_back(std::move(c));}
 else throw std::invalid_argument("Unknown element kind");}
 auto errors=validate(p);if(!errors.empty())throw std::invalid_argument(errors.front().message);
 for(const auto&j:p.joins){auto r=solveWallJoinGeometry(p,j);if(!r.ok())throw std::invalid_argument(r.error()->message);}
 return p;
}
}

std::uint64_t createSession(){std::lock_guard<std::mutex>g(registryMutex);if(nextHandle==0)throw std::overflow_error("Native handle space exhausted");auto h=nextHandle++;sessions.emplace(h,std::make_shared<Session>(Project{ModelId{"native-project"},"Native Engine Session",CowVector<Wall>{},CowVector<Level>{},CowVector<WallJoin>{},CowVector<Opening>{},CowVector<CatalogInstance>{}}));return h;}
bool destroySession(std::uint64_t h)noexcept{if(!h)return false;std::lock_guard<std::mutex>g(registryMutex);return sessions.erase(h)==1;}
bool beginGesture(std::uint64_t h,const std::string&id){std::lock_guard<std::mutex>g(registryMutex);auto it=sessions.find(h);if(it==sessions.end()||id.empty()||!it->second->gesture.empty())return false;it->second->gesture=id;it->second->gestureHasChanges=false;if(it->second->generation<std::numeric_limits<std::uint64_t>::max())++it->second->generation;return true;}
bool commitGesture(std::uint64_t h,const std::string&id){std::lock_guard<std::mutex>g(registryMutex);auto it=sessions.find(h);if(it==sessions.end()||it->second->gesture!=id)return false;it->second->gesture.clear();it->second->gestureHasChanges=false;if(it->second->generation<std::numeric_limits<std::uint64_t>::max())++it->second->generation;return true;}
bool cancelGesture(std::uint64_t h,const std::string&id){std::lock_guard<std::mutex>g(registryMutex);auto it=sessions.find(h);if(it==sessions.end()||it->second->gesture!=id)return false;auto r=it->second->gestureHasChanges?it->second->bus.undo():bimob::Result<std::shared_ptr<const bimob::ModelRevision>>::success(it->second->bus.snapshot());it->second->gesture.clear();it->second->gestureHasChanges=false;if(it->second->generation<std::numeric_limits<std::uint64_t>::max())++it->second->generation;return r.ok();}
std::string exportCanonicalSnapshot(std::uint64_t h){std::lock_guard<std::mutex>g(registryMutex);auto it=sessions.find(h);if(it==sessions.end())return errorJson("Invalid native handle");return canonicalProjectJson(*it->second->bus.snapshot()->project);}
std::string importCanonicalSnapshot(std::uint64_t h,const std::string&source){
 std::shared_ptr<Session> current;std::uint64_t observedGeneration=0;
 {std::lock_guard<std::mutex>g(registryMutex);auto it=sessions.find(h);if(it==sessions.end())return errorJson("Invalid native handle");current=it->second;if(!current->gesture.empty())return errorJson("Active gesture prevents project replacement");observedGeneration=current->generation;}
 try{auto candidate=std::make_shared<Session>(parseSnapshot(source));(void)candidate->bus.snapshot();
  std::lock_guard<std::mutex>g(registryMutex);auto it=sessions.find(h);
  if(it==sessions.end()||it->second!=current)return errorJson("Session was removed or replaced during import");
  if(current->generation!=observedGeneration)return errorJson("Session changed during import; retry with a fresh snapshot");
  if(!current->gesture.empty())return errorJson("Active gesture prevents project replacement");
  if(observedGeneration==std::numeric_limits<std::uint64_t>::max())return errorJson("Session generation exhausted");
  candidate->generation=observedGeneration+1;it->second.swap(candidate);return "{\"ok\":true}";
 }catch(const std::exception&e){return errorJson(e.what());}
}
std::vector<std::uint8_t> exportRenderGeometry(std::uint64_t h){std::lock_guard<std::mutex>g(registryMutex);auto it=sessions.find(h);if(it==sessions.end())return {};return geometryBytes(*it->second->bus.snapshot());}
std::string dispatchCommandJson(std::uint64_t h,const std::string&j){std::lock_guard<std::mutex>g(registryMutex);auto it=sessions.find(h);if(it==sessions.end())return errorJson("Invalid native handle");auto&s=*it->second;try{auto parsed=json::parse(j);if(!parsed.object())return response(s,false,"Command JSON must be an object");auto type=field(j,"type");if(type=="CreateWall"){const ModelId requestedLevel{field(j,"levelId")};const auto project=s.bus.snapshot()->project;if(!requestedLevel.valid()||std::none_of(project->levels.begin(),project->levels.end(),[&](const Level&level){return level.id==requestedLevel;}))return response(s,false,"CreateWall references a missing active level.");}if(type=="HistoryState")return response(s,true);if(type=="Undo"||type=="Redo"){if(!s.gesture.empty())return response(s,false,"History operations are not allowed during an active gesture");auto r=type=="Undo"?s.bus.undo():s.bus.redo();return response(s,r.ok(),r.ok()?"":r.error()->message);}if(type=="beginGesture"){bool ok=s.gesture.empty()&&!field(j,"gestureId").empty();if(ok){s.gesture=field(j,"gestureId");s.gestureHasChanges=false;if(s.generation<std::numeric_limits<std::uint64_t>::max())++s.generation;}return response(s,ok,ok?"":"Gesture already active or ID empty");}if(type=="commitGesture"){bool ok=!s.gesture.empty()&&s.gesture==field(j,"gestureId");if(ok){s.gesture.clear();s.gestureHasChanges=false;if(s.generation<std::numeric_limits<std::uint64_t>::max())++s.generation;}return response(s,ok,ok?"":"Gesture mismatch");}if(type=="cancelGesture"){bool ok=!s.gesture.empty()&&s.gesture==field(j,"gestureId");if(ok){auto r=s.gestureHasChanges?s.bus.undo():bimob::Result<std::shared_ptr<const bimob::ModelRevision>>::success(s.bus.snapshot());s.gesture.clear();s.gestureHasChanges=false;if(s.generation<std::numeric_limits<std::uint64_t>::max())++s.generation;return response(s,r.ok(),r.ok()?"":r.error()->message);}return response(s,false,"Gesture mismatch");}auto mutation=parseMutation(j);auto before=s.bus.snapshot()->number;auto r=s.bus.execute(mutation,{},s.gesture.empty()?std::optional<std::string>{}:std::optional<std::string>{s.gesture});if(r.ok()&&s.bus.snapshot()->number!=before){if(!s.gesture.empty())s.gestureHasChanges=true;if(s.generation<std::numeric_limits<std::uint64_t>::max())++s.generation;}return response(s,r.ok(),r.ok()?"":r.error()->message);}catch(const std::exception&e){return response(s,false,e.what());}}
}
