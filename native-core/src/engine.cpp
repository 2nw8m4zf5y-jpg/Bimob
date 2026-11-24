#include "bimob/engine.hpp"
#include <algorithm>
#include <cmath>
#include <exception>
#include <limits>
#include <queue>
#include <tuple>
#include <unordered_map>
namespace bimob {
namespace {
Error budgetError(std::string name,std::size_t actual,std::size_t limit,std::string node={}){return{ErrorCode::EngineLimitExceeded,"ENGINE_LIMIT_EXCEEDED: "+name+" actual="+std::to_string(actual)+" limit="+std::to_string(limit),std::move(node),std::move(name),actual,limit};}
std::vector<NodeId> sortedUnique(std::vector<NodeId> values){std::sort(values.begin(),values.end());values.erase(std::unique(values.begin(),values.end()),values.end());return values;}
struct IndexedGraph {
 std::vector<NodeId> keys;
 std::unordered_map<NodeId,std::size_t> index; // lookup only; never iterated, so it cannot affect scheduler order
 std::vector<std::vector<std::size_t>> outgoing,incoming;
};
IndexedGraph indexGraph(const DependencyGraph&graph,const std::vector<NodeId>&extra={}){
 IndexedGraph x;x.keys=graph.nodes;x.keys.insert(x.keys.end(),extra.begin(),extra.end());x.keys=sortedUnique(std::move(x.keys));
 x.index.reserve(x.keys.size());for(std::size_t i=0;i<x.keys.size();++i)x.index.emplace(x.keys[i],i);
 x.outgoing.resize(x.keys.size());x.incoming.resize(x.keys.size());
 for(const auto&e:graph.edges){const auto s=x.index.at(e.source),d=x.index.at(e.dependent);x.outgoing[s].push_back(d);x.incoming[d].push_back(s);}
 // Canonically sorted edges produce stable adjacency; hash table is lookup-only.
 return x;
}
std::vector<NodeId> deterministicCycle(const IndexedGraph&x){
 const std::size_t n=x.keys.size();std::vector<unsigned char>color(n,0);std::vector<std::size_t>parent(n,n);
 for(std::size_t root=0;root<n;++root){if(color[root])continue;color[root]=1;std::vector<std::pair<std::size_t,std::size_t>>stack{{root,0}};
  while(!stack.empty()){auto&frame=stack.back();const auto node=frame.first;if(frame.second>=x.outgoing[node].size()){color[node]=2;stack.pop_back();continue;}const auto child=x.outgoing[node][frame.second++];if(color[child]==0){parent[child]=node;color[child]=1;stack.emplace_back(child,0);continue;}if(color[child]==1){std::vector<std::size_t>path{node};auto cursor=node;while(cursor!=child){cursor=parent[cursor];if(cursor==n)break;path.push_back(cursor);}if(cursor==child){std::reverse(path.begin(),path.end());path.push_back(child);std::vector<NodeId>result;for(auto index:path)result.push_back(x.keys[index]);return result;}}
  }
 }
 return {};
}
std::string cycleText(const std::vector<NodeId>&path){std::string text;for(const auto&node:path){if(!text.empty())text+=" -> ";text+=node;}return text;}
}
bool DependencyGraph::hasNode(const NodeId&n)const{return std::binary_search(nodes.begin(),nodes.end(),n);}
bool DependencyGraph::hasEdge(const DependencyEdge&e)const{return std::binary_search(edges.begin(),edges.end(),e,[](const auto&a,const auto&b){return std::tie(a.source,a.dependent)<std::tie(b.source,b.dependent);});}
EvaluatorRegistry EvaluatorRegistry::withOverride(const NodeId&id,Evaluator e)const{auto copy=*this;copy.overrides_[id]=std::move(e);return copy;}
std::optional<Evaluator> EvaluatorRegistry::get(const NodeId&id)const{if(auto it=overrides_.find(id);it!=overrides_.end())return it->second;if(auto it=base_.find(id);it!=base_.end())return it->second;return{};}
ModelRevision::ModelRevision(std::uint64_t n,std::shared_ptr<const Project>p,std::shared_ptr<const DependencyGraph>g,std::shared_ptr<const DerivedMap>d,std::shared_ptr<const std::vector<std::shared_ptr<const Wall>>>entities):number(n),project(std::move(p)),graph(std::move(g)),derived(std::move(d)),wallEntities(entities?std::move(entities):std::make_shared<const std::vector<std::shared_ptr<const Wall>>>([&]{std::vector<std::shared_ptr<const Wall>>v;v.reserve(project->walls.size());for(const auto&w:project->walls)v.push_back(std::make_shared<const Wall>(w));return v;}())){}
Result<DependencyGraph> admitEdges(const DependencyGraph&base,const std::vector<DependencyEdge>&batch,const EngineLimits&limits){
 auto nodes=base.nodes;auto edges=base.edges;for(const auto&e:batch){nodes.push_back(e.source);nodes.push_back(e.dependent);edges.push_back(e);}nodes=sortedUnique(std::move(nodes));
 std::sort(edges.begin(),edges.end(),[](const auto&a,const auto&b){return std::tie(a.source,a.dependent)<std::tie(b.source,b.dependent);});edges.erase(std::unique(edges.begin(),edges.end()),edges.end());
 if(nodes.size()>limits.graph_nodes)return Result<DependencyGraph>::failure(budgetError("graph_nodes",nodes.size(),limits.graph_nodes));
 if(edges.size()>limits.graph_edges)return Result<DependencyGraph>::failure(budgetError("graph_edges",edges.size(),limits.graph_edges));
 DependencyGraph candidate{nodes,edges};auto x=indexGraph(candidate);std::vector<std::size_t>degree(nodes.size(),0),depth(nodes.size(),1);std::queue<std::size_t>ready;
 for(std::size_t i=0;i<nodes.size();++i){degree[i]=x.incoming[i].size();if(degree[i]==0)ready.push(i);}
 std::size_t processed=0,maxDepth=0;
 while(!ready.empty()){auto n=ready.front();ready.pop();++processed;if(depth[n]>maxDepth)maxDepth=depth[n];for(auto child:x.outgoing[n]){depth[child]=std::max(depth[child],depth[n]+1);if(--degree[child]==0)ready.push(child);}}
 if(processed!=nodes.size()){auto path=deterministicCycle(x);return Result<DependencyGraph>::failure({ErrorCode::DependencyCycle,"Dependency cycle detected: "+cycleText(path),path.empty()?std::string{}:path.front(),{},0,0});}
 if(maxDepth>limits.dependency_depth)return Result<DependencyGraph>::failure(budgetError("dependency_depth",maxDepth,limits.dependency_depth));
 return Result<DependencyGraph>::success(std::move(candidate));
}
Result<EvaluationReport> recompute(const DependencyGraph&g,const std::vector<NodeId>&seeds,const Project&project,const DerivedMap&initial,const EvaluatorRegistry&registry,const EngineLimits&limits,const CancellationToken*cancellation){
 if(g.nodes.size()>limits.graph_nodes)return Result<EvaluationReport>::failure(budgetError("graph_nodes",g.nodes.size(),limits.graph_nodes));
 if(g.edges.size()>limits.graph_edges)return Result<EvaluationReport>::failure(budgetError("graph_edges",g.edges.size(),limits.graph_edges));
 auto x=indexGraph(g,seeds);std::vector<bool>dirty(x.keys.size(),false);std::queue<std::size_t>q;std::size_t dirtyCount=0;
 for(const auto&s:sortedUnique(seeds)){auto i=x.index.at(s);if(!dirty[i]){dirty[i]=true;++dirtyCount;if(dirtyCount>limits.dirty_nodes)return Result<EvaluationReport>::failure(budgetError("dirty_nodes",dirtyCount,limits.dirty_nodes,s));q.push(i);}}
 while(!q.empty()){if(cancellation&&cancellation->isCancelled())return Result<EvaluationReport>::failure({ErrorCode::Cancelled,"Dependency recompute cancelled",{}, {},0,0});auto n=q.front();q.pop();auto fan=x.outgoing[n].size();if(fan>limits.fan_out)return Result<EvaluationReport>::failure(budgetError("fan_out",fan,limits.fan_out,x.keys[n]));for(auto child:x.outgoing[n])if(!dirty[child]){dirty[child]=true;++dirtyCount;if(dirtyCount>limits.dirty_nodes)return Result<EvaluationReport>::failure(budgetError("dirty_nodes",dirtyCount,limits.dirty_nodes,x.keys[child]));q.push(child);}}
 std::vector<std::size_t>degree(x.keys.size(),0);for(std::size_t n=0;n<x.keys.size();++n)if(dirty[n])for(auto source:x.incoming[n])if(dirty[source])++degree[n];
 std::queue<std::size_t>ready;for(std::size_t i=0;i<x.keys.size();++i)if(dirty[i]&&degree[i]==0)ready.push(i);std::vector<NodeId>order;order.reserve(dirtyCount);
 while(!ready.empty()){if(cancellation&&cancellation->isCancelled())return Result<EvaluationReport>::failure({ErrorCode::Cancelled,"Dependency scheduling cancelled",{}, {},0,0});auto n=ready.front();ready.pop();order.push_back(x.keys[n]);for(auto child:x.outgoing[n])if(dirty[child]&&--degree[child]==0)ready.push(child);}
 if(order.size()!=dirtyCount)return Result<EvaluationReport>::failure({ErrorCode::DependencyCycle,"Dirty subgraph has cycle",{}, {},0,0});
 DerivedMap staged=initial;for(const auto&node:order)staged.erase(node);std::size_t evaluations=0;
 for(const auto&node:order){if(cancellation&&cancellation->isCancelled())return Result<EvaluationReport>::failure({ErrorCode::Cancelled,"Evaluation cancelled",node,{},0,0});auto evaluator=registry.get(node);if(!evaluator)continue;if(++evaluations>limits.evaluations)return Result<EvaluationReport>::failure(budgetError("evaluations",evaluations,limits.evaluations,node));DerivedMap direct;for(auto source:x.incoming[x.index.at(node)]){const auto&key=x.keys[source];if(auto it=staged.find(key);it!=staged.end())direct.emplace(it->first,it->second);}
  Result<DerivedValue> value=Result<DerivedValue>::failure({ErrorCode::EvaluationFailed,"Evaluator threw",node,{},0,0});
  try{value=(*evaluator)(project,direct);}catch(const std::exception&ex){return Result<EvaluationReport>::failure({ErrorCode::EvaluationFailed,"Evaluator exception at "+node+": "+ex.what(),node,{},0,0});}catch(...){return Result<EvaluationReport>::failure({ErrorCode::EvaluationFailed,"Evaluator exception at "+node,node,{},0,0});}
  if(!value.ok()){return Result<EvaluationReport>::failure({ErrorCode::EvaluationFailed,"Evaluator failed at node "+node+": "+value.error()->message,node,{},0,0});}
  staged[node]=*value.value();
 }
 return Result<EvaluationReport>::success({std::move(staged),std::move(order),evaluations});
}
namespace {
std::vector<NodeId> dirtyForProjectChange(const Project&before,const Project&after);
Result<DependencyGraph> syncJoinGraph(const DependencyGraph&current,const Project&p,const EngineLimits&limits);
}
CommandBus::CommandBus(Project p,EngineLimits l,EvaluatorRegistry e,HistoryLimits history):limits_(l),evaluators_(std::move(e)),historyLimits_(history){auto issues=validate(p);if(!issues.empty())initializationError_=Error{ErrorCode::ValidationFailed,issues.front().message,issues.front().elementId,{},0,0};current_=std::make_shared<ModelRevision>(0,std::make_shared<const Project>(std::move(p)),std::make_shared<const DependencyGraph>(),std::make_shared<const DerivedMap>());}
std::shared_ptr<const ModelRevision>CommandBus::snapshot()const{std::lock_guard lock(mutex_);return current_;}
Result<Commit>CommandBus::execute(const Mutation&m,std::optional<std::uint64_t>expected,std::optional<std::string>gesture,const CancellationToken*cancellation){std::lock_guard lock(mutex_);if(initializationError_)return Result<Commit>::failure(*initializationError_);if(cancellation&&cancellation->isCancelled())return Result<Commit>::failure({ErrorCode::Cancelled,"Command cancelled before mutation",{}, {},0,0});if(expected&&*expected!=current_->number)return Result<Commit>::failure({ErrorCode::StaleRevision,"Stale revision",{}, {},0,0});Result<Project>candidate=Result<Project>::failure({ErrorCode::CommandFailed,"Mutation failed",{}, {},0,0});try{candidate=m(*current_->project);}catch(const std::exception&ex){return Result<Commit>::failure({ErrorCode::CommandFailed,ex.what(),{}, {},0,0});}catch(...){return Result<Commit>::failure({ErrorCode::CommandFailed,"Mutation exception",{}, {},0,0});}if(!candidate.ok())return Result<Commit>::failure(*candidate.error());if(cancellation&&cancellation->isCancelled())return Result<Commit>::failure({ErrorCode::Cancelled,"Command cancelled before commit",{}, {},0,0});auto dirty=dirtyForProjectChange(*current_->project,*candidate.value());auto graph=syncJoinGraph(*current_->graph,*candidate.value(),limits_);if(!graph.ok())return Result<Commit>::failure(*graph.error());return commitCandidate(std::move(*candidate.value()),std::make_shared<const DependencyGraph>(*graph.value()),*current_->derived,std::move(dirty),{},std::move(gesture),cancellation);}
Result<Commit>CommandBus::transact(const Mutation&m,const std::vector<DependencyEdge>&edges,const std::vector<NodeId>&dirty,const std::map<NodeId,Evaluator>&overrides,std::optional<std::string>gesture,const CancellationToken*cancellation){std::lock_guard lock(mutex_);if(initializationError_)return Result<Commit>::failure(*initializationError_);if(cancellation&&cancellation->isCancelled())return Result<Commit>::failure({ErrorCode::Cancelled,"Transaction cancelled before mutation",{}, {},0,0});Result<Project>candidate=Result<Project>::failure({ErrorCode::CommandFailed,"Mutation failed",{}, {},0,0});try{candidate=m(*current_->project);}catch(const std::exception&ex){return Result<Commit>::failure({ErrorCode::CommandFailed,ex.what(),{}, {},0,0});}catch(...){return Result<Commit>::failure({ErrorCode::CommandFailed,"Mutation exception",{}, {},0,0});}if(!candidate.ok())return Result<Commit>::failure(*candidate.error());if(cancellation&&cancellation->isCancelled())return Result<Commit>::failure({ErrorCode::Cancelled,"Transaction cancelled before recompute",{}, {},0,0});auto synchronized=syncJoinGraph(*current_->graph,*candidate.value(),limits_);if(!synchronized.ok())return Result<Commit>::failure(*synchronized.error());auto graph=admitEdges(*synchronized.value(),edges,limits_);if(!graph.ok())return Result<Commit>::failure(*graph.error());auto derivedDirty=dirtyForProjectChange(*current_->project,*candidate.value());derivedDirty.insert(derivedDirty.end(),dirty.begin(),dirty.end());derivedDirty=sortedUnique(std::move(derivedDirty));return commitCandidate(std::move(*candidate.value()),std::make_shared<const DependencyGraph>(*graph.value()),*current_->derived,std::move(derivedDirty),overrides,std::move(gesture),cancellation);}
Result<Commit>CommandBus::commitCandidate(Project p,std::shared_ptr<const DependencyGraph>g,DerivedMap values,std::vector<NodeId>dirty,std::map<NodeId,Evaluator>overrides,std::optional<std::string>gesture,const CancellationToken*cancellation){
 auto issues=validate(p);if(!issues.empty())return Result<Commit>::failure({ErrorCode::ValidationFailed,issues.front().message,issues.front().elementId,{},0,0});EvaluatorRegistry local=evaluators_;for(auto&[id,e]:overrides)local=local.withOverride(id,e);if(cancellation&&cancellation->isCancelled())return Result<Commit>::failure({ErrorCode::Cancelled,"Commit cancelled before recomputation",{}, {},0,0});auto evaluated=recompute(*g,dirty,p,values,local,limits_,cancellation);if(!evaluated.ok())return Result<Commit>::failure(*evaluated.error());if(current_->number==std::numeric_limits<std::uint64_t>::max())return Result<Commit>::failure({ErrorCode::RevisionOverflow,"Revision overflow",{}, {},0,0});
 auto projectPtr=std::make_shared<const Project>(std::move(p));std::map<std::string,std::shared_ptr<const Wall>>prior;for(const auto&wp:*current_->wallEntities)prior[wp->id.value]=wp;std::vector<std::shared_ptr<const Wall>>entities;entities.reserve(projectPtr->walls.size());for(const auto&w:projectPtr->walls){auto it=prior.find(w.id.value);if(it!=prior.end()&&*it->second==w)entities.push_back(it->second);else entities.push_back(std::make_shared<const Wall>(w));}
 auto next=std::make_shared<ModelRevision>(current_->number+1,std::move(projectPtr),std::move(g),std::make_shared<const DerivedMap>(std::move(evaluated.value()->values)),std::make_shared<const std::vector<std::shared_ptr<const Wall>>>(std::move(entities)));
 const bool coalesce=gesture&&lastGesture_&&*gesture==*lastGesture_&&!undo_.empty();if(!coalesce){undo_.push_back(current_);while(undo_.size()>historyLimits_.max_steps)undo_.erase(undo_.begin());}redo_.clear();current_=next;lastGesture_=gesture;return Result<Commit>::success({current_,coalesce});
}
Result<std::shared_ptr<const ModelRevision>>CommandBus::undo(){std::lock_guard lock(mutex_);if(undo_.empty())return Result<std::shared_ptr<const ModelRevision>>::failure({ErrorCode::HistoryEmpty,"Undo history empty",{}, {},0,0});redo_.push_back(current_);current_=undo_.back();undo_.pop_back();lastGesture_.reset();return Result<std::shared_ptr<const ModelRevision>>::success(current_);}
Result<std::shared_ptr<const ModelRevision>>CommandBus::redo(){std::lock_guard lock(mutex_);if(redo_.empty())return Result<std::shared_ptr<const ModelRevision>>::failure({ErrorCode::HistoryEmpty,"Redo history empty",{}, {},0,0});undo_.push_back(current_);while(undo_.size()>historyLimits_.max_steps)undo_.erase(undo_.begin());current_=redo_.back();redo_.pop_back();lastGesture_.reset();return Result<std::shared_ptr<const ModelRevision>>::success(current_);}
std::size_t CommandBus::undoDepth()const{std::lock_guard lock(mutex_);return undo_.size();}std::size_t CommandBus::redoDepth()const{std::lock_guard lock(mutex_);return redo_.size();}
Result<Project> deleteWallCascade(const Project&source,const ModelId&wallId){
 Project candidate=source;auto wall=std::find_if(candidate.walls.begin(),candidate.walls.end(),[&](const Wall&w){return w.id==wallId;});if(wall==candidate.walls.end())return Result<Project>::failure({ErrorCode::InvalidArgument,"Wall to delete not found",wallId.value,{},0,0});
 candidate.walls.erase(std::remove_if(candidate.walls.begin(),candidate.walls.end(),[&](const Wall&w){return w.id==wallId;}),candidate.walls.end());
 candidate.openings.erase(std::remove_if(candidate.openings.begin(),candidate.openings.end(),[&](const Opening&o){return o.hostWallId==wallId;}),candidate.openings.end());
 candidate.catalogInstances.erase(std::remove_if(candidate.catalogInstances.begin(),candidate.catalogInstances.end(),[&](const CatalogInstance&i){return i.hostWallId==wallId;}),candidate.catalogInstances.end());
 candidate.joins.erase(std::remove_if(candidate.joins.begin(),candidate.joins.end(),[&](const WallJoin&j){return j.wallA==wallId||j.wallB==wallId;}),candidate.joins.end());
 return Result<Project>::success(std::move(candidate));
}
Result<Project> deleteLevelCascade(const Project&source,const ModelId&levelId){
 Project candidate=source;auto level=std::find_if(candidate.levels.begin(),candidate.levels.end(),[&](const Level&l){return l.id==levelId;});if(level==candidate.levels.end())return Result<Project>::failure({ErrorCode::InvalidArgument,"Level to delete not found",levelId.value,{},0,0});
 std::vector<ModelId> hostedWalls;for(const auto&w:candidate.walls)if(w.levelId==levelId)hostedWalls.push_back(w.id);
 for(const auto&id:hostedWalls){auto deleted=deleteWallCascade(candidate,id);if(!deleted.ok())return deleted;candidate=std::move(*deleted.value());}
 candidate.catalogInstances.erase(std::remove_if(candidate.catalogInstances.begin(),candidate.catalogInstances.end(),[&](const CatalogInstance&i){return i.levelId==levelId;}),candidate.catalogInstances.end());
 candidate.levels.erase(std::remove_if(candidate.levels.begin(),candidate.levels.end(),[&](const Level&l){return l.id==levelId;}),candidate.levels.end());
 return Result<Project>::success(std::move(candidate));
}
Result<WallJoinGeometry> solveWallJoinGeometry(const Project&project,const WallJoin&join){
 auto find=[&](const ModelId&id)->const Wall*{auto i=std::find_if(project.walls.begin(),project.walls.end(),[&](const Wall&w){return w.id==id;});return i==project.walls.end()?nullptr:&*i;};
 const Wall*a=find(join.wallA),*b=find(join.wallB);if(!a||!b||!join.joint.finite()||!a->axis.valid()||!b->axis.valid())return Result<WallJoinGeometry>::failure({ErrorCode::InvalidArgument,"Invalid WallJoin geometry input",join.id.value,{},0,0});
 auto info=[&](const Wall&w){double dx=w.axis.end.x.value-w.axis.start.x.value,dy=w.axis.end.y.value-w.axis.start.y.value,len=std::hypot(dx,dy),ux=dx/len,uy=dy/len,nx=-uy,ny=ux,px=join.joint.x.value-w.axis.start.x.value,py=join.joint.y.value-w.axis.start.y.value,t=(px*dx+py*dy)/(len*len),dist=std::abs(px*uy-py*ux);return std::array<double,6>{ux,uy,nx,ny,t,dist};};
 const auto A=info(*a),B=info(*b);constexpr double tol=1e-6;if(A[5]>tol||B[5]>tol||A[4]<-tol||A[4]>1+tol||B[4]<-tol||B[4]>1+tol)return Result<WallJoinGeometry>::failure({ErrorCode::InvalidArgument,"Join point is outside finite axes",join.id.value,{},0,0});
 bool ea=A[4]<=tol||A[4]>=1-tol,eb=B[4]<=tol||B[4]>=1-tol;auto topology=ea&&eb?WallJoinTopology::L:(ea!=eb?WallJoinTopology::T:WallJoinTopology::X);double angle=std::acos(std::clamp(A[0]*B[0]+A[1]*B[1],-1.0,1.0))*180/std::acos(-1.0),den=A[0]*B[1]-A[1]*B[0];if(std::abs(den)<1e-10)return Result<WallJoinGeometry>::failure({ErrorCode::InvalidArgument,"Parallel joined axes",join.id.value,{},0,0});
 std::array<Point2,4>v{};size_t k=0;for(double sa:{-1.,1.})for(double sb:{-1.,1.}){double ax=join.joint.x.value+A[2]*a->thickness.value*.5*sa,ay=join.joint.y.value+A[3]*a->thickness.value*.5*sa,bx=join.joint.x.value+B[2]*b->thickness.value*.5*sb,by=join.joint.y.value+B[3]*b->thickness.value*.5*sb,t=((bx-ax)*B[1]-(by-ay)*B[0])/den;v[k++]={Meters{ax+t*A[0]},Meters{ay+t*A[1]}};}
 double cx=0,cy=0;for(auto&p:v){cx+=p.x.value;cy+=p.y.value;}cx/=4;cy/=4;std::sort(v.begin(),v.end(),[&](const Point2&x,const Point2&y){return std::atan2(x.y.value-cy,x.x.value-cx)<std::atan2(y.y.value-cy,y.x.value-cx);});double area=0;for(size_t i=0;i<4;i++){auto&p=v[i];auto&q=v[(i+1)%4];area+=p.x.value*q.y.value-q.x.value*p.y.value;}area=std::abs(area)*.5;if(!std::isfinite(area)||area<=1e-12)return Result<WallJoinGeometry>::failure({ErrorCode::EvaluationFailed,"Degenerate join footprint",join.id.value,{},0,0});return Result<WallJoinGeometry>::success({topology,angle,v});
}

Result<Project> solveWallJoin(const Project&source,const ModelId&movedWall,bool movedStart,Point2 newJoint){
 if(!newJoint.finite())return Result<Project>::failure({ErrorCode::InvalidArgument,"WallJoin point must be finite",movedWall.value,{},0,0});
 Project candidate=source;auto moved=std::find_if(candidate.walls.begin(),candidate.walls.end(),[&](const Wall&w){return w.id==movedWall;});
 if(moved==candidate.walls.end())return Result<Project>::failure({ErrorCode::InvalidArgument,"Moved wall not found",movedWall.value,{},0,0});
 (movedStart?moved->axis.start:moved->axis.end)=newJoint;
 for(auto&join:candidate.joins){bool isA=join.wallA==movedWall&&join.endA==!movedStart;bool isB=join.wallB==movedWall&&join.endB==!movedStart;if(!isA&&!isB)continue;join.joint=newJoint;ModelId otherId=isA?join.wallB:join.wallA;bool otherStart=isA?!join.endB:!join.endA;auto other=std::find_if(candidate.walls.begin(),candidate.walls.end(),[&](const Wall&w){return w.id==otherId;});if(other==candidate.walls.end())return Result<Project>::failure({ErrorCode::ValidationFailed,"WallJoin references missing adjacent wall",otherId.value,{},0,0});(otherStart?other->axis.start:other->axis.end)=newJoint;}
 auto issues=validate(candidate);if(!issues.empty())return Result<Project>::failure({ErrorCode::ValidationFailed,issues.front().message,issues.front().elementId,{},0,0});return Result<Project>::success(std::move(candidate));
}


namespace {
std::string wallNode(const ModelId&id){return "wall:"+id.value+":geometry";}
std::string openingNode(const ModelId&id){return "opening:"+id.value+":cut";}
std::string levelNode(const ModelId&id){return "level:"+id.value+":elevation";}
std::string catalogNode(const ModelId&id,const char*property){return "entity:"+id.value+":property:"+property;}
std::vector<NodeId> dirtyForProjectChange(const Project&before,const Project&after){
 std::vector<NodeId> seeds;
 auto wallChanged=[&](const ModelId&id){auto a=std::find_if(before.walls.begin(),before.walls.end(),[&](const Wall&w){return w.id==id;});auto b=std::find_if(after.walls.begin(),after.walls.end(),[&](const Wall&w){return w.id==id;});return a==before.walls.end()||b==after.walls.end()||!(*a==*b);};
 for(const auto&w:before.walls)if(wallChanged(w.id))seeds.push_back(wallNode(w.id));
 for(const auto&w:after.walls)if(std::none_of(before.walls.begin(),before.walls.end(),[&](const Wall&x){return x.id==w.id;}))seeds.push_back(wallNode(w.id));
 auto levelChanged=[&](const ModelId&id){auto a=std::find_if(before.levels.begin(),before.levels.end(),[&](const Level&l){return l.id==id;});auto b=std::find_if(after.levels.begin(),after.levels.end(),[&](const Level&l){return l.id==id;});return a==before.levels.end()||b==after.levels.end()||!(*a==*b);};
 for(const auto&l:before.levels)if(levelChanged(l.id))seeds.push_back(levelNode(l.id));
 for(const auto&l:after.levels)if(std::none_of(before.levels.begin(),before.levels.end(),[&](const Level&x){return x.id==l.id;}))seeds.push_back(levelNode(l.id));
 auto openingChanged=[&](const ModelId&id){auto a=std::find_if(before.openings.begin(),before.openings.end(),[&](const Opening&o){return o.id==id;});auto b=std::find_if(after.openings.begin(),after.openings.end(),[&](const Opening&o){return o.id==id;});return a==before.openings.end()||b==after.openings.end()||!(*a==*b);};
 for(const auto&o:before.openings)if(openingChanged(o.id))seeds.push_back(openingNode(o.id));
 for(const auto&o:after.openings)if(std::none_of(before.openings.begin(),before.openings.end(),[&](const Opening&x){return x.id==o.id;}))seeds.push_back(openingNode(o.id));
 auto joinChanged=[&](const ModelId&id){auto a=std::find_if(before.joins.begin(),before.joins.end(),[&](const WallJoin&j){return j.id==id;});auto b=std::find_if(after.joins.begin(),after.joins.end(),[&](const WallJoin&j){return j.id==id;});return a==before.joins.end()||b==after.joins.end()||!(*a==*b);};
 for(const auto&j:before.joins)if(joinChanged(j.id)){seeds.push_back("join:"+j.id.value+":geometry");seeds.push_back(wallNode(j.wallA));seeds.push_back(wallNode(j.wallB));}
 for(const auto&j:after.joins)if(std::none_of(before.joins.begin(),before.joins.end(),[&](const WallJoin&x){return x.id==j.id;})){seeds.push_back("join:"+j.id.value+":geometry");seeds.push_back(wallNode(j.wallA));seeds.push_back(wallNode(j.wallB));}
 auto catalogChanged=[&](const ModelId&id){auto a=std::find_if(before.catalogInstances.begin(),before.catalogInstances.end(),[&](const CatalogInstance&i){return i.id==id;});auto b=std::find_if(after.catalogInstances.begin(),after.catalogInstances.end(),[&](const CatalogInstance&i){return i.id==id;});return a==before.catalogInstances.end()||b==after.catalogInstances.end()||!(*a==*b);};
 for(const auto&i:before.catalogInstances)if(catalogChanged(i.id)){seeds.push_back(catalogNode(i.id,"POSITION"));seeds.push_back(catalogNode(i.id,"DIMENSIONS"));seeds.push_back(catalogNode(i.id,"HOST"));}
 for(const auto&i:after.catalogInstances)if(std::none_of(before.catalogInstances.begin(),before.catalogInstances.end(),[&](const CatalogInstance&x){return x.id==i.id;})){seeds.push_back(catalogNode(i.id,"POSITION"));seeds.push_back(catalogNode(i.id,"DIMENSIONS"));seeds.push_back(catalogNode(i.id,"HOST"));}
 return sortedUnique(std::move(seeds));
}
std::vector<DependencyEdge> wallJoinDependencyEdges(const Project&p){
 std::vector<DependencyEdge> edges;
 for(const auto&j:p.joins){const auto joinNode="join:"+j.id.value+":geometry";edges.push_back({wallNode(j.wallA),joinNode});edges.push_back({wallNode(j.wallB),joinNode});for(const auto&o:p.openings)if(o.hostWallId==j.wallA||o.hostWallId==j.wallB)edges.push_back({joinNode,openingNode(o.id)});for(const auto&i:p.catalogInstances)if(i.hostWallId==j.wallA||i.hostWallId==j.wallB)edges.push_back({joinNode,catalogNode(i.id,"HOST")});}
 return edges;
}
Result<DependencyGraph> syncJoinGraph(const DependencyGraph&current,const Project&p,const EngineLimits&limits){
 std::vector<DependencyEdge> retained;for(const auto&e:current.edges)if(e.source.rfind("join:",0)!=0&&e.dependent.rfind("join:",0)!=0)retained.push_back(e);
 DependencyGraph base{sortedUnique([&]{std::vector<NodeId> n;for(const auto&e:retained){n.push_back(e.source);n.push_back(e.dependent);}return n;}()),retained};
 return admitEdges(base,wallJoinDependencyEdges(p),limits);
}
}

namespace commands {
namespace {
Error missing(const ModelId& id,const char* kind){return{ErrorCode::InvalidArgument,std::string(kind)+" not found",id.value,{},0,0};}
Point2 add(Point2 a,Point2 b){return Point2{Meters{a.x.value+b.x.value},Meters{a.y.value+b.y.value}};}
}
Mutation createLevel(Level value){return [value=std::move(value)](const Project&p){auto c=p;if(std::any_of(c.levels.begin(),c.levels.end(),[&](const Level&x){return x.id==value.id;}))return Result<Project>::failure({ErrorCode::InvalidArgument,"Level already exists",value.id.value,{},0,0});c.levels.push_back(value);return Result<Project>::success(std::move(c));};}
Mutation moveLevel(ModelId id,Meters elevation){return [id=std::move(id),elevation](const Project&p){auto c=p;auto it=std::find_if(c.levels.begin(),c.levels.end(),[&](const Level&x){return x.id==id;});if(it==c.levels.end())return Result<Project>::failure(missing(id,"Level"));it->elevation=elevation;return Result<Project>::success(std::move(c));};}
Mutation resizeLevel(ModelId id,Meters height){return [id=std::move(id),height](const Project&p){auto c=p;auto it=std::find_if(c.levels.begin(),c.levels.end(),[&](const Level&x){return x.id==id;});if(it==c.levels.end())return Result<Project>::failure(missing(id,"Level"));it->height=height;return Result<Project>::success(std::move(c));};}
Mutation deleteLevel(ModelId id){return [id=std::move(id)](const Project&p){return deleteLevelCascade(p,id);};}
Mutation createWall(Wall value){return [value=std::move(value)](const Project&p){auto c=p;if(std::any_of(c.walls.begin(),c.walls.end(),[&](const Wall&x){return x.id==value.id;}))return Result<Project>::failure({ErrorCode::InvalidArgument,"Wall already exists",value.id.value,{},0,0});c.walls.push_back(value);return Result<Project>::success(std::move(c));};}
Mutation moveWall(ModelId id,Point2 delta){return [id=std::move(id),delta](const Project&p){if(!delta.finite())return Result<Project>::failure({ErrorCode::InvalidArgument,"Move delta must be finite",id.value,{},0,0});auto c=p;auto it=std::find_if(c.walls.begin(),c.walls.end(),[&](const Wall&w){return w.id==id;});if(it==c.walls.end())return Result<Project>::failure(missing(id,"Wall"));it->axis.start=add(it->axis.start,delta);it->axis.end=add(it->axis.end,delta);auto a=solveWallJoin(c,id,true,it->axis.start);if(!a.ok())return a;return solveWallJoin(*a.value(),id,false,it->axis.end);};}
Mutation moveWallEndpoint(ModelId id,bool movedStart,Point2 joint){return [id=std::move(id),movedStart,joint](const Project&p){return solveWallJoin(p,id,movedStart,joint);};}
Mutation resizeWall(ModelId id,Meters length){return [id=std::move(id),length](const Project&p){if(!std::isfinite(length.value)||length.value<kMinWallLength)return Result<Project>::failure({ErrorCode::InvalidArgument,"Wall length must be finite and >= kMinWallLength",id.value,{},0,0});auto it=std::find_if(p.walls.begin(),p.walls.end(),[&](const Wall&w){return w.id==id;});if(it==p.walls.end())return Result<Project>::failure(missing(id,"Wall"));const auto dx=it->axis.end.x.value-it->axis.start.x.value,dy=it->axis.end.y.value-it->axis.start.y.value;const auto current=it->axis.length().value;if(current<kMinWallLength)return Result<Project>::failure({ErrorCode::ValidationFailed,"Cannot resize invalid wall",id.value,{},0,0});Point2 end{Meters{it->axis.start.x.value+dx/current*length.value},Meters{it->axis.start.y.value+dy/current*length.value}};return solveWallJoin(p,id,false,end);};}
Mutation deleteWall(ModelId id){return [id=std::move(id)](const Project&p){return deleteWallCascade(p,id);};}
Mutation createOpening(Opening value){return [value=std::move(value)](const Project&p){auto c=p;if(std::any_of(c.openings.begin(),c.openings.end(),[&](const Opening&x){return x.id==value.id;}))return Result<Project>::failure({ErrorCode::InvalidArgument,"Opening already exists",value.id.value,{},0,0});c.openings.push_back(value);return Result<Project>::success(std::move(c));};}
Mutation moveOpening(ModelId id,Meters offset,Meters sill){return [id=std::move(id),offset,sill](const Project&p){auto c=p;auto it=std::find_if(c.openings.begin(),c.openings.end(),[&](const Opening&o){return o.id==id;});if(it==c.openings.end())return Result<Project>::failure(missing(id,"Opening"));it->offsetAlongWall=offset;it->sillHeight=sill;return Result<Project>::success(std::move(c));};}
Mutation resizeOpening(ModelId id,Meters width,Meters height){return [id=std::move(id),width,height](const Project&p){auto c=p;auto it=std::find_if(c.openings.begin(),c.openings.end(),[&](const Opening&o){return o.id==id;});if(it==c.openings.end())return Result<Project>::failure(missing(id,"Opening"));it->width=width;it->height=height;return Result<Project>::success(std::move(c));};}
Mutation deleteOpening(ModelId id){return [id=std::move(id)](const Project&p){auto c=p;auto it=std::find_if(c.openings.begin(),c.openings.end(),[&](const Opening&o){return o.id==id;});if(it==c.openings.end())return Result<Project>::failure(missing(id,"Opening"));c.openings.erase(it);return Result<Project>::success(std::move(c));};}
Mutation createCatalogInstance(CatalogInstance value){return [value=std::move(value)](const Project&p){auto c=p;if(std::any_of(c.catalogInstances.begin(),c.catalogInstances.end(),[&](const CatalogInstance&x){return x.id==value.id;}))return Result<Project>::failure({ErrorCode::InvalidArgument,"Catalog instance already exists",value.id.value,{},0,0});c.catalogInstances.push_back(value);return Result<Project>::success(std::move(c));};}
Mutation moveCatalogInstance(ModelId id,Point2 position){return [id=std::move(id),position](const Project&p){if(!position.finite())return Result<Project>::failure({ErrorCode::InvalidArgument,"Catalog position must be finite",id.value,{},0,0});auto c=p;auto it=std::find_if(c.catalogInstances.begin(),c.catalogInstances.end(),[&](const CatalogInstance&x){return x.id==id;});if(it==c.catalogInstances.end())return Result<Project>::failure(missing(id,"Catalog instance"));it->position=position;return Result<Project>::success(std::move(c));};}
Mutation resizeCatalogInstance(ModelId id,Meters width,Meters depth,Meters height){return [id=std::move(id),width,depth,height](const Project&p){auto c=p;auto it=std::find_if(c.catalogInstances.begin(),c.catalogInstances.end(),[&](const CatalogInstance&x){return x.id==id;});if(it==c.catalogInstances.end())return Result<Project>::failure(missing(id,"Catalog instance"));it->targetWidth=width;it->targetDepth=depth;it->targetHeight=height;return Result<Project>::success(std::move(c));};}
Mutation rotateCatalogInstance(ModelId id,double radians){return [id=std::move(id),radians](const Project&p){if(!std::isfinite(radians))return Result<Project>::failure({ErrorCode::InvalidArgument,"Catalog rotation must be finite",id.value,{},0,0});auto c=p;auto it=std::find_if(c.catalogInstances.begin(),c.catalogInstances.end(),[&](const CatalogInstance&x){return x.id==id;});if(it==c.catalogInstances.end())return Result<Project>::failure(missing(id,"Catalog instance"));it->rotationRadians=radians;return Result<Project>::success(std::move(c));};}
Mutation deleteCatalogInstance(ModelId id){return [id=std::move(id)](const Project&p){auto c=p;auto it=std::find_if(c.catalogInstances.begin(),c.catalogInstances.end(),[&](const CatalogInstance&x){return x.id==id;});if(it==c.catalogInstances.end())return Result<Project>::failure(missing(id,"Catalog instance"));c.catalogInstances.erase(it);return Result<Project>::success(std::move(c));};}
Mutation batch(std::vector<Mutation> mutations){return [mutations=std::move(mutations)](const Project&p){Project candidate=p;for(const auto&mutation:mutations){if(!mutation)return Result<Project>::failure({ErrorCode::InvalidArgument,"Batch contains an empty command",{}, {},0,0});auto staged=mutation(candidate);if(!staged.ok())return Result<Project>::failure(*staged.error());candidate=std::move(*staged.value());}return Result<Project>::success(std::move(candidate));};}
} // namespace commands

} // namespace bimob
