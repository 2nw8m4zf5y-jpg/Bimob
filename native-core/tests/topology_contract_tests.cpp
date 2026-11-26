#include "bimob/topology.hpp"
#include <cassert>
#include <algorithm>
#include <stdexcept>
#include <cmath>
#include <thread>
#include <vector>
using namespace bimob;
int main(){
 CommandDispatcher d(.1);
 auto n1=d.addNode(0,0), n2=d.addNode(1,0), n3=d.addNode(1,1);
 assert(n1!=n2&&n2!=n3);
 assert(d.addNode(.05,.04)==n1); // radius snapping reuses existing id
 auto s1=d.addWallSegment(n1,n2,.2),s2=d.addWallSegment(n2,n3,.2);
 auto snap=d.snapshot();assert(snap->nodes.size()==3&&snap->segments.size()==2);
 assert(snap->nodes[0].degree==1&&snap->nodes[1].degree==2&&snap->nodes[2].degree==1);
 assert(d.undo());snap=d.snapshot();assert(snap->segments.size()==1&&snap->nodes[1].degree==1&&snap->nodes[2].degree==0);
 assert(d.undo());snap=d.snapshot();assert(snap->segments.empty()&&snap->nodes[0].degree==0&&snap->nodes[1].degree==0);
 assert(d.undo());snap=d.snapshot();assert(snap->nodes.size()==2); // isolated n3 removed by its own undo
 assert(d.redo());assert(d.snapshot()->nodes.size()==3);
 assert(d.redo());assert(d.snapshot()->segments.size()==1);
 assert(d.redo());snap=d.snapshot();assert(snap->segments.size()==2&&snap->nodes[1].degree==2);
 auto first=snap;d.addNode(5,5);assert(first->nodes.size()==3); // immutable render snapshot
 auto n4=d.snapshot()->nodes.back().id;assert(d.undo());assert(std::none_of(d.snapshot()->nodes.begin(),d.snapshot()->nodes.end(),[&](auto n){return n.id==n4;}));
 bool bad=false;try{d.addWallSegment(n1,n1,.2);}catch(const std::invalid_argument&){bad=true;}assert(bad);
 CommandDispatcher concurrent;std::vector<std::thread> workers;for(int i=0;i<8;i++)workers.emplace_back([&]{for(int j=0;j<100;j++)concurrent.addNode(j*2.0,0);});for(auto&t:workers)t.join();assert(concurrent.snapshot()->nodes.size()==800);
 concurrent.closeAndReset();assert(concurrent.snapshot()->nodes.empty()&&concurrent.snapshot()->segments.empty()&&concurrent.snapshot()->undoDepth==0);
 (void)s1;(void)s2;
}
