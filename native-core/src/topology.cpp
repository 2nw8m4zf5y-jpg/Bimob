#include "bimob/topology.hpp"
#include <cmath>
#include <stdexcept>
#include <algorithm>
namespace bimob {
TopologySnapshot::TopologySnapshot(std::vector<Node> n,std::vector<WallSegment>s,std::size_t u,std::size_t r):nodes(std::move(n)),segments(std::move(s)),undoDepth(u),redoDepth(r){}
CommandDispatcher::CommandDispatcher(double radius):nodeSnapRadius_(radius){if(!std::isfinite(radius)||radius<0)throw std::invalid_argument("node snap radius must be finite and non-negative");}
std::uint64_t CommandDispatcher::addNode(double x,double y){
 if(!std::isfinite(x)||!std::isfinite(y))throw std::invalid_argument("node coordinates must be finite");
 std::lock_guard<std::mutex> lock(mutex_);
 const double r2=nodeSnapRadius_*nodeSnapRadius_;
 for(const auto&n:nodes_)if((n.x-x)*(n.x-x)+(n.y-y)*(n.y-y)<=r2)return n.id;
 Command c;c.kind=Command::Kind::Node;c.node=Node{nextNodeId_++,x,y,0};queue_.push_back(c);
 while(!queue_.empty()){auto q=queue_.front();queue_.pop_front();if(q.kind==Command::Kind::Node){nodes_.push_back(q.node);undo_.push_back(q);redo_.clear();}}
 return c.node.id;
}
std::uint64_t CommandDispatcher::addWallSegment(std::uint64_t a,std::uint64_t b,double thickness){
 if(a==b||!std::isfinite(thickness)||thickness<=0)throw std::invalid_argument("invalid wall segment");
 std::lock_guard<std::mutex> lock(mutex_);
 auto find=[&](std::uint64_t id){return std::find_if(nodes_.begin(),nodes_.end(),[&](const Node&n){return n.id==id;});};
 if(find(a)==nodes_.end()||find(b)==nodes_.end())throw std::invalid_argument("segment references missing node");
 Command c;c.kind=Command::Kind::Segment;c.segment=WallSegment{nextSegmentId_++,a,b,thickness};queue_.push_back(c);
 while(!queue_.empty()){auto q=queue_.front();queue_.pop_front();if(q.kind==Command::Kind::Segment){auto n1=find(q.segment.startNodeId),n2=find(q.segment.endNodeId);if(n1==nodes_.end()||n2==nodes_.end())throw std::logic_error("queued segment node disappeared");++n1->degree;++n2->degree;segments_.push_back(q.segment);undo_.push_back(q);redo_.clear();}}
 return c.segment.id;
}
void CommandDispatcher::removeIsolatedNodeLocked(std::uint64_t id){auto it=std::find_if(nodes_.begin(),nodes_.end(),[&](const Node&n){return n.id==id;});if(it!=nodes_.end()&&it->degree==0)nodes_.erase(it);}
bool CommandDispatcher::undoLocked(){if(undo_.empty())return false;Command c=undo_.back();undo_.pop_back();if(c.kind==Command::Kind::Segment){auto it=std::find_if(segments_.begin(),segments_.end(),[&](const WallSegment&s){return s.id==c.segment.id;});if(it==segments_.end())throw std::logic_error("undo segment missing");auto a=std::find_if(nodes_.begin(),nodes_.end(),[&](const Node&n){return n.id==it->startNodeId;});auto b=std::find_if(nodes_.begin(),nodes_.end(),[&](const Node&n){return n.id==it->endNodeId;});if(a==nodes_.end()||b==nodes_.end()||a->degree==0||b->degree==0)throw std::logic_error("degree invariant violated");--a->degree;--b->degree;segments_.erase(it);}else removeIsolatedNodeLocked(c.node.id);redo_.push_back(c);return true;}
bool CommandDispatcher::redoLocked(){if(redo_.empty())return false;Command c=redo_.back();redo_.pop_back();if(c.kind==Command::Kind::Node){if(std::none_of(nodes_.begin(),nodes_.end(),[&](const Node&n){return n.id==c.node.id;}))nodes_.push_back(c.node);}else{auto a=std::find_if(nodes_.begin(),nodes_.end(),[&](const Node&n){return n.id==c.segment.startNodeId;});auto b=std::find_if(nodes_.begin(),nodes_.end(),[&](const Node&n){return n.id==c.segment.endNodeId;});if(a==nodes_.end()||b==nodes_.end())throw std::logic_error("redo segment missing node");++a->degree;++b->degree;segments_.push_back(c.segment);}undo_.push_back(c);return true;}
bool CommandDispatcher::undo(){std::lock_guard<std::mutex>l(mutex_);return undoLocked();}
bool CommandDispatcher::redo(){std::lock_guard<std::mutex>l(mutex_);return redoLocked();}
std::shared_ptr<const TopologySnapshot> CommandDispatcher::snapshot()const{std::lock_guard<std::mutex>l(mutex_);return std::make_shared<const TopologySnapshot>(nodes_,segments_,undo_.size(),redo_.size());}
void CommandDispatcher::closeAndReset(){std::lock_guard<std::mutex>l(mutex_);queue_.clear();nodes_.clear();segments_.clear();undo_.clear();redo_.clear();nextNodeId_=1;nextSegmentId_=1;}
} // namespace bimob
