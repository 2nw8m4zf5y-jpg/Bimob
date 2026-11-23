#include "bimob/engine.hpp"
#include <algorithm>
#include <cmath>
#include <limits>
#include <map>
#include <queue>
#include <set>
#include <tuple>
namespace bimob {
namespace {
Error budgetError(std::string name,std::size_t actual,std::size_t limit,std::string node={}){return{ErrorCode::EngineLimitExceeded,"ENGINE_LIMIT_EXCEEDED: "+name+" actual="+std::to_string(actual)+" limit="+std::to_string(limit),std::move(node),std::move(name),actual,limit};}
std::vector<NodeId> sortedUnique(std::vector<NodeId> v){std::sort(v.begin(),v.end());v.erase(std::unique(v.begin(),v.end()),v.end());return v;}
using Adjacency=std::map<NodeId,std::vector<NodeId>>;
}
bool DependencyGraph::hasNode(const NodeId&n)const{return std::binary_search(nodes.begin(),nodes.end(),n);}
bool DependencyGraph::hasEdge(const DependencyEdge&e)const{return std::binary_search(edges.begin(),edges.end(),e,[](const auto&a,const auto&b){return std::tie(a.source,a.dependent)<std::tie(b.source,b.dependent);});}
EvaluatorRegistry EvaluatorRegistry::withOverride(const NodeId&id,Evaluator e)const{auto copy=*this;copy.overrides_[id]=std::move(e);return copy;}
std::optional<Evaluator> EvaluatorRegistry::get(const NodeId&id)const{if(auto it=overrides_.find(id);it!=overrides_.end())return it->second;if(auto it=base_.find(id);it!=base_.end())return it->second;return{};}
ModelRevision::ModelRevision(std::uint64_t n,std::shared_ptr<const Project>p,std::shared_ptr<const DependencyGraph>g,std::shared_ptr<const DerivedMap>d,std::shared_ptr<const std::vector<std::shared_ptr<const Wall>>> entities):number(n),project(std::move(p)),graph(std::move(g)),derived(std::move(d)),wallEntities(entities?std::move(entities):std::make_shared<const std::vector<std::shared_ptr<const Wall>>>([&]{std::vector<std::shared_ptr<const Wall>>v;v.reserve(project->walls.size());for(const auto&w:project->walls)v.push_back(std::make_shared<const Wall>(w));return v;}())){}
Result<DependencyGraph> admitEdges(const DependencyGraph&base,const std::vector<DependencyEdge>&batch,const EngineLimits&limits){
 auto nodes=base.nodes;auto edges=base.edges;for(const auto&e:batch){nodes.push_back(e.source);nodes.push_back(e.dependent);edges.push_back(e);}nodes=sortedUnique(std::move(nodes));
 std::sort(edges.begin(),edges.end(),[](const auto&a,const auto&b){return std::tie(a.source,a.dependent)<std::tie(b.source,b.dependent);});edges.erase(std::unique(edges.begin(),edges.end()),edges.end());
 if(nodes.size()>limits.graph_nodes)return Result<DependencyGraph>::failure(budgetError("graph_nodes",nodes.size(),limits.graph_nodes));
 if(edges.size()>limits.graph_edges)return Result<DependencyGraph>::failure(budgetError("graph_edges",edges.size(),limits.graph_edges));
 Adjacency out,in;for(const auto&n:nodes){out[n];in[n];}for(const auto&e:edges){out[e.source].push_back(e.dependent);in[e.dependent].push_back(e.source);}
 for(auto&[key,v]:out){(void)key;v=sortedUnique(std::move(v));}for(auto&[key,v]:in){(void)key;v=sortedUnique(std::move(v));}
 std::map<NodeId,std::size_t>degree,depth;std::queue<NodeId>ready;for(const auto&n:nodes){degree[n]=in[n].size();depth[n]=1;if(degree[n]==0)ready.push(n);}std::size_t processed=0,maxDepth=0;NodeId maxNode;
 while(!ready.empty()){auto n=ready.front();ready.pop();++processed;if(depth[n]>maxDepth){maxDepth=depth[n];maxNode=n;}for(const auto&child:out[n]){depth[child]=std::max(depth[child],depth[n]+1);if(--degree[child]==0)ready.push(child);}}
 if(processed!=nodes.size())return Result<DependencyGraph>::failure({ErrorCode::DependencyCycle,"Dependency cycle detected (deterministic Kahn residual)",nodes.empty()?std::string{}:nodes.front(),{},0,0});
 if(maxDepth>limits.dependency_depth)return Result<DependencyGraph>::failure(budgetError("dependency_depth",maxDepth,limits.dependency_depth,maxNode));
 return Result<DependencyGraph>::success({std::move(nodes),std::move(edges)});
}
Result<EvaluationReport> recompute(const DependencyGraph&g,const std::vector<NodeId>&seeds,const Project&project,const DerivedMap&initial,const EvaluatorRegistry&registry,const EngineLimits&limits){
 if(g.nodes.size()>limits.graph_nodes)return Result<EvaluationReport>::failure(budgetError("graph_nodes",g.nodes.size(),limits.graph_nodes));
 if(g.edges.size()>limits.graph_edges)return Result<EvaluationReport>::failure(budgetError("graph_edges",g.edges.size(),limits.graph_edges));
 Adjacency out,in;for(const auto&n:g.nodes){out[n];in[n];}for(const auto&e:g.edges){out[e.source].push_back(e.dependent);in[e.dependent].push_back(e.source);}
 for(auto&[key,v]:out){(void)key;v=sortedUnique(std::move(v));}for(auto&[key,v]:in){(void)key;v=sortedUnique(std::move(v));}
 std::set<NodeId>dirty;std::queue<NodeId>q;for(auto seed:sortedUnique(seeds)){if(dirty.insert(seed).second){if(dirty.size()>limits.dirty_nodes)return Result<EvaluationReport>::failure(budgetError("dirty_nodes",dirty.size(),limits.dirty_nodes,seed));q.push(seed);}}
 while(!q.empty()){auto n=q.front();q.pop();const auto fan=out[n].size();if(fan>limits.fan_out)return Result<EvaluationReport>::failure(budgetError("fan_out",fan,limits.fan_out,n));for(const auto&child:out[n])if(dirty.insert(child).second){if(dirty.size()>limits.dirty_nodes)return Result<EvaluationReport>::failure(budgetError("dirty_nodes",dirty.size(),limits.dirty_nodes,child));q.push(child);}}
 std::map<NodeId,std::size_t>degree;for(const auto&n:dirty){degree[n]=0;for(const auto&s:in[n])if(dirty.contains(s))++degree[n];}
 std::queue<NodeId>ready;for(const auto&[n,d]:degree)if(d==0)ready.push(n);std::vector<NodeId>order;
 while(!ready.empty()){auto n=ready.front();ready.pop();order.push_back(n);for(const auto&c:out[n])if(dirty.contains(c)&&--degree[c]==0)ready.push(c);}
 if(order.size()!=dirty.size())return Result<EvaluationReport>::failure({ErrorCode::DependencyCycle,"Dirty subgraph has cycle",{}, {},0,0});
 DerivedMap staged=initial;for(const auto&n:dirty)staged.erase(n);std::size_t evaluations=0;
 for(const auto&n:order){auto evaluator=registry.get(n);if(!evaluator)continue;if(++evaluations>limits.evaluations)return Result<EvaluationReport>::failure(budgetError("evaluations",evaluations,limits.evaluations,n));DerivedMap direct;for(const auto&s:in[n])if(auto it=staged.find(s);it!=staged.end())direct.emplace(it->first,it->second);
  Result<DerivedValue> value=Result<DerivedValue>::failure({ErrorCode::EvaluationFailed,"Evaluator threw",n,{},0,0});
  try{value=(*evaluator)(project,direct);}catch(const std::exception&ex){return Result<EvaluationReport>::failure({ErrorCode::EvaluationFailed,"Evaluator exception at "+n+": "+ex.what(),n,{},0,0});}catch(...){return Result<EvaluationReport>::failure({ErrorCode::EvaluationFailed,"Evaluator exception at "+n,n,{},0,0});}
  if(!value.ok()){return Result<EvaluationReport>::failure({ErrorCode::EvaluationFailed,"Evaluator failed at node "+n+": "+value.error()->message,n,{},0,0});}\n  staged[n]=*value.value();
 }
 return Result<EvaluationReport>::success({std::move(staged),std::move(order),evaluations});
}
CommandBus::CommandBus(Project p,EngineLimits l,EvaluatorRegistry e):limits_(l),evaluators_(std::move(e)){auto issues=validate(p);if(!issues.empty())initializationError_=Error{ErrorCode::ValidationFailed,issues.front().message,issues.front().elementId,{},0,0};current_=std::make_shared<ModelRevision>(0,std::make_shared<const Project>(std::move(p)),std::make_shared<const DependencyGraph>(),std::make_shared<const DerivedMap>());}
std::shared_ptr<const ModelRevision>CommandBus::snapshot()const{return current_;}
Result<Commit>CommandBus::execute(const Mutation&m,std::optional<std::uint64_t>expected,std::optional<std::string>gesture){if(initializationError_)return Result<Commit>::failure(*initializationError_);if(expected&&*expected!=current_->number)return Result<Commit>::failure({ErrorCode::StaleRevision,"Stale revision",{}, {},0,0});Result<Project>candidate=Result<Project>::failure({ErrorCode::CommandFailed,"Mutation failed",{}, {},0,0});try{candidate=m(*current_->project);}catch(const std::exception&ex){return Result<Commit>::failure({ErrorCode::CommandFailed,ex.what(),{}, {},0,0});}catch(...){return Result<Commit>::failure({ErrorCode::CommandFailed,"Mutation exception",{}, {},0,0});}if(!candidate.ok())return Result<Commit>::failure(*candidate.error());return commitCandidate(std::move(*candidate.value()),current_->graph,*current_->derived,{}, {},std::move(gesture));}
Result<Commit>CommandBus::transact(const Mutation&m,const std::vector<DependencyEdge>&edges,const std::vector<NodeId>&dirty,const std::map<NodeId,Evaluator>&overrides,std::optional<std::string>gesture){if(initializationError_)return Result<Commit>::failure(*initializationError_);Result<Project>candidate=Result<Project>::failure({ErrorCode::CommandFailed,"Mutation failed",{}, {},0,0});try{candidate=m(*current_->project);}catch(const std::exception&ex){return Result<Commit>::failure({ErrorCode::CommandFailed,ex.what(),{}, {},0,0});}catch(...){return Result<Commit>::failure({ErrorCode::CommandFailed,"Mutation exception",{}, {},0,0});}if(!candidate.ok())return Result<Commit>::failure(*candidate.error());auto graph=admitEdges(*current_->graph,edges,limits_);if(!graph.ok())return Result<Commit>::failure(*graph.error());return commitCandidate(std::move(*candidate.value()),std::make_shared<const DependencyGraph>(*graph.value()),*current_->derived,dirty,overrides,std::move(gesture));}
Result<Commit>CommandBus::commitCandidate(Project p,std::shared_ptr<const DependencyGraph>g,DerivedMap values,std::vector<NodeId>dirty,std::map<NodeId,Evaluator>overrides,std::optional<std::string>gesture){
 auto issues=validate(p);if(!issues.empty())return Result<Commit>::failure({ErrorCode::ValidationFailed,issues.front().message,issues.front().elementId,{},0,0});EvaluatorRegistry local=evaluators_;for(auto&[id,e]:overrides)local=local.withOverride(id,e);auto evaluated=recompute(*g,dirty,p,values,local,limits_);if(!evaluated.ok())return Result<Commit>::failure(*evaluated.error());if(current_->number==std::numeric_limits<std::uint64_t>::max())return Result<Commit>::failure({ErrorCode::RevisionOverflow,"Revision overflow",{}, {},0,0});
 auto projectPtr=std::make_shared<const Project>(std::move(p));std::map<std::string,std::shared_ptr<const Wall>>prior;for(const auto&wp:*current_->wallEntities)prior[wp->id.value]=wp;std::vector<std::shared_ptr<const Wall>>entities;entities.reserve(projectPtr->walls.size());for(const auto&w:projectPtr->walls){auto it=prior.find(w.id.value);if(it!=prior.end()&&*it->second==w)entities.push_back(it->second);else entities.push_back(std::make_shared<const Wall>(w));}
 auto next=std::make_shared<ModelRevision>(current_->number+1,std::move(projectPtr),std::move(g),std::make_shared<const DerivedMap>(std::move(evaluated.value()->values)),std::make_shared<const std::vector<std::shared_ptr<const Wall>>>(std::move(entities)));
 const bool coalesce=gesture&&lastGesture_&&*gesture==*lastGesture_&&!undo_.empty();if(!coalesce)undo_.push_back(current_);redo_.clear();current_=next;lastGesture_=gesture;return Result<Commit>::success({current_,coalesce});
}
Result<std::shared_ptr<const ModelRevision>>CommandBus::undo(){if(undo_.empty())return Result<std::shared_ptr<const ModelRevision>>::failure({ErrorCode::HistoryEmpty,"Undo history empty",{}, {},0,0});redo_.push_back(current_);current_=undo_.back();undo_.pop_back();lastGesture_.reset();return Result<std::shared_ptr<const ModelRevision>>::success(current_);}
Result<std::shared_ptr<const ModelRevision>>CommandBus::redo(){if(redo_.empty())return Result<std::shared_ptr<const ModelRevision>>::failure({ErrorCode::HistoryEmpty,"Redo history empty",{}, {},0,0});undo_.push_back(current_);current_=redo_.back();redo_.pop_back();lastGesture_.reset();return Result<std::shared_ptr<const ModelRevision>>::success(current_);}
std::size_t CommandBus::undoDepth()const noexcept{return undo_.size();}std::size_t CommandBus::redoDepth()const noexcept{return redo_.size();}
} // namespace bimob
