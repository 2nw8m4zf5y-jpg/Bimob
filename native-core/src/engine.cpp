#include "bimob/engine.hpp"
#include <algorithm>
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
 std::size_t processed=0,maxDepth=0,maxIndex=0;
 while(!ready.empty()){auto n=ready.front();ready.pop();++processed;if(depth[n]>maxDepth){maxDepth=depth[n];maxIndex=n;}for(auto child:x.outgoing[n]){depth[child]=std::max(depth[child],depth[n]+1);if(--degree[child]==0)ready.push(child);}}
 if(processed!=nodes.size())return Result<DependencyGraph>::failure({ErrorCode::DependencyCycle,"Dependency cycle detected (deterministic Kahn residual)",nodes.empty()?std::string{}:nodes.front(),{},0,0});
 if(maxDepth>limits.dependency_depth)return Result<DependencyGraph>::failure(budgetError("dependency_depth",maxDepth,limits.dependency_depth,nodes[maxIndex]));
 return Result<DependencyGraph>::success(std::move(candidate));
}
Result<EvaluationReport> recompute(const DependencyGraph&g,const std::vector<NodeId>&seeds,const Project&project,const DerivedMap&initial,const EvaluatorRegistry&registry,const EngineLimits&limits){
 if(g.nodes.size()>limits.graph_nodes)return Result<EvaluationReport>::failure(budgetError("graph_nodes",g.nodes.size(),limits.graph_nodes));
 if(g.edges.size()>limits.graph_edges)return Result<EvaluationReport>::failure(budgetError("graph_edges",g.edges.size(),limits.graph_edges));
 auto x=indexGraph(g,seeds);std::vector<bool>dirty(x.keys.size(),false);std::queue<std::size_t>q;std::size_t dirtyCount=0;
 for(const auto&s:sortedUnique(seeds)){auto i=x.index.at(s);if(!dirty[i]){dirty[i]=true;++dirtyCount;if(dirtyCount>limits.dirty_nodes)return Result<EvaluationReport>::failure(budgetError("dirty_nodes",dirtyCount,limits.dirty_nodes,s));q.push(i);}}
 while(!q.empty()){auto n=q.front();q.pop();auto fan=x.outgoing[n].size();if(fan>limits.fan_out)return Result<EvaluationReport>::failure(budgetError("fan_out",fan,limits.fan_out,x.keys[n]));for(auto child:x.outgoing[n])if(!dirty[child]){dirty[child]=true;++dirtyCount;if(dirtyCount>limits.dirty_nodes)return Result<EvaluationReport>::failure(budgetError("dirty_nodes",dirtyCount,limits.dirty_nodes,x.keys[child]));q.push(child);}}
 std::vector<std::size_t>degree(x.keys.size(),0);for(std::size_t n=0;n<x.keys.size();++n)if(dirty[n])for(auto source:x.incoming[n])if(dirty[source])++degree[n];
 std::queue<std::size_t>ready;for(std::size_t i=0;i<x.keys.size();++i)if(dirty[i]&&degree[i]==0)ready.push(i);std::vector<NodeId>order;order.reserve(dirtyCount);
 while(!ready.empty()){auto n=ready.front();ready.pop();order.push_back(x.keys[n]);for(auto child:x.outgoing[n])if(dirty[child]&&--degree[child]==0)ready.push(child);}
 if(order.size()!=dirtyCount)return Result<EvaluationReport>::failure({ErrorCode::DependencyCycle,"Dirty subgraph has cycle",{}, {},0,0});
 DerivedMap staged=initial;for(const auto&node:order)staged.erase(node);std::size_t evaluations=0;
 for(const auto&node:order){auto evaluator=registry.get(node);if(!evaluator)continue;if(++evaluations>limits.evaluations)return Result<EvaluationReport>::failure(budgetError("evaluations",evaluations,limits.evaluations,node));DerivedMap direct;for(auto source:x.incoming[x.index.at(node)]){const auto&key=x.keys[source];if(auto it=staged.find(key);it!=staged.end())direct.emplace(it->first,it->second);}
  Result<DerivedValue> value=Result<DerivedValue>::failure({ErrorCode::EvaluationFailed,"Evaluator threw",node,{},0,0});
  try{value=(*evaluator)(project,direct);}catch(const std::exception&ex){return Result<EvaluationReport>::failure({ErrorCode::EvaluationFailed,"Evaluator exception at "+node+": "+ex.what(),node,{},0,0});}catch(...){return Result<EvaluationReport>::failure({ErrorCode::EvaluationFailed,"Evaluator exception at "+node,node,{},0,0});}
  if(!value.ok()){return Result<EvaluationReport>::failure({ErrorCode::EvaluationFailed,"Evaluator failed at node "+node+": "+value.error()->message,node,{},0,0});}
  staged[node]=*value.value();
 }
 return Result<EvaluationReport>::success({std::move(staged),std::move(order),evaluations});
}
CommandBus::CommandBus(Project p,EngineLimits l,EvaluatorRegistry e):limits_(l),evaluators_(std::move(e)){auto issues=validate(p);if(!issues.empty())initializationError_=Error{ErrorCode::ValidationFailed,issues.front().message,issues.front().elementId,{},0,0};current_=std::make_shared<ModelRevision>(0,std::make_shared<const Project>(std::move(p)),std::make_shared<const DependencyGraph>(),std::make_shared<const DerivedMap>());}
std::shared_ptr<const ModelRevision>CommandBus::snapshot()const{std::lock_guard lock(mutex_);return current_;}
Result<Commit>CommandBus::execute(const Mutation&m,std::optional<std::uint64_t>expected,std::optional<std::string>gesture){std::lock_guard lock(mutex_);if(initializationError_)return Result<Commit>::failure(*initializationError_);if(expected&&*expected!=current_->number)return Result<Commit>::failure({ErrorCode::StaleRevision,"Stale revision",{}, {},0,0});Result<Project>candidate=Result<Project>::failure({ErrorCode::CommandFailed,"Mutation failed",{}, {},0,0});try{candidate=m(*current_->project);}catch(const std::exception&ex){return Result<Commit>::failure({ErrorCode::CommandFailed,ex.what(),{}, {},0,0});}catch(...){return Result<Commit>::failure({ErrorCode::CommandFailed,"Mutation exception",{}, {},0,0});}if(!candidate.ok())return Result<Commit>::failure(*candidate.error());return commitCandidate(std::move(*candidate.value()),current_->graph,*current_->derived,{}, {},std::move(gesture));}
Result<Commit>CommandBus::transact(const Mutation&m,const std::vector<DependencyEdge>&edges,const std::vector<NodeId>&dirty,const std::map<NodeId,Evaluator>&overrides,std::optional<std::string>gesture){std::lock_guard lock(mutex_);if(initializationError_)return Result<Commit>::failure(*initializationError_);Result<Project>candidate=Result<Project>::failure({ErrorCode::CommandFailed,"Mutation failed",{}, {},0,0});try{candidate=m(*current_->project);}catch(const std::exception&ex){return Result<Commit>::failure({ErrorCode::CommandFailed,ex.what(),{}, {},0,0});}catch(...){return Result<Commit>::failure({ErrorCode::CommandFailed,"Mutation exception",{}, {},0,0});}if(!candidate.ok())return Result<Commit>::failure(*candidate.error());auto graph=admitEdges(*current_->graph,edges,limits_);if(!graph.ok())return Result<Commit>::failure(*graph.error());return commitCandidate(std::move(*candidate.value()),std::make_shared<const DependencyGraph>(*graph.value()),*current_->derived,dirty,overrides,std::move(gesture));}
Result<Commit>CommandBus::commitCandidate(Project p,std::shared_ptr<const DependencyGraph>g,DerivedMap values,std::vector<NodeId>dirty,std::map<NodeId,Evaluator>overrides,std::optional<std::string>gesture){
 auto issues=validate(p);if(!issues.empty())return Result<Commit>::failure({ErrorCode::ValidationFailed,issues.front().message,issues.front().elementId,{},0,0});EvaluatorRegistry local=evaluators_;for(auto&[id,e]:overrides)local=local.withOverride(id,e);auto evaluated=recompute(*g,dirty,p,values,local,limits_);if(!evaluated.ok())return Result<Commit>::failure(*evaluated.error());if(current_->number==std::numeric_limits<std::uint64_t>::max())return Result<Commit>::failure({ErrorCode::RevisionOverflow,"Revision overflow",{}, {},0,0});
 auto projectPtr=std::make_shared<const Project>(std::move(p));std::map<std::string,std::shared_ptr<const Wall>>prior;for(const auto&wp:*current_->wallEntities)prior[wp->id.value]=wp;std::vector<std::shared_ptr<const Wall>>entities;entities.reserve(projectPtr->walls.size());for(const auto&w:projectPtr->walls){auto it=prior.find(w.id.value);if(it!=prior.end()&&*it->second==w)entities.push_back(it->second);else entities.push_back(std::make_shared<const Wall>(w));}
 auto next=std::make_shared<ModelRevision>(current_->number+1,std::move(projectPtr),std::move(g),std::make_shared<const DerivedMap>(std::move(evaluated.value()->values)),std::make_shared<const std::vector<std::shared_ptr<const Wall>>>(std::move(entities)));
 const bool coalesce=gesture&&lastGesture_&&*gesture==*lastGesture_&&!undo_.empty();if(!coalesce)undo_.push_back(current_);redo_.clear();current_=next;lastGesture_=gesture;return Result<Commit>::success({current_,coalesce});
}
Result<std::shared_ptr<const ModelRevision>>CommandBus::undo(){std::lock_guard lock(mutex_);if(undo_.empty())return Result<std::shared_ptr<const ModelRevision>>::failure({ErrorCode::HistoryEmpty,"Undo history empty",{}, {},0,0});redo_.push_back(current_);current_=undo_.back();undo_.pop_back();lastGesture_.reset();return Result<std::shared_ptr<const ModelRevision>>::success(current_);}
Result<std::shared_ptr<const ModelRevision>>CommandBus::redo(){std::lock_guard lock(mutex_);if(redo_.empty())return Result<std::shared_ptr<const ModelRevision>>::failure({ErrorCode::HistoryEmpty,"Redo history empty",{}, {},0,0});undo_.push_back(current_);current_=redo_.back();redo_.pop_back();lastGesture_.reset();return Result<std::shared_ptr<const ModelRevision>>::success(current_);}
std::size_t CommandBus::undoDepth()const{std::lock_guard lock(mutex_);return undo_.size();}std::size_t CommandBus::redoDepth()const{std::lock_guard lock(mutex_);return redo_.size();}
} // namespace bimob
