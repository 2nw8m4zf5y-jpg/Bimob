#include <algorithm>
#include <type_traits>
#include "bimob/model.hpp"
#include <cassert>
#include <limits>
#include <string>
using namespace bimob;
namespace {
Wall wall(std::string id = "wall-1") { return Wall{ModelId{std::move(id)}, WallAxis{Point2{Meters{0},Meters{0}},Point2{Meters{3},Meters{4}}},Meters{2.7},Meters{0.2}}; }
Project project() { return Project{ModelId{"project-1"},"Test",{wall()}}; }
void metricAndValidModel() { assert(wall().axis.length().value==5.0); assert(wall().valid()); assert(validate(project()).empty()); }
void invalidAxes() {
 auto p=project(); p.walls[0].axis.end=p.walls[0].axis.start; auto issues=validate(p); assert(issues.front().code==ValidationCode::ZeroLengthWall);
 p=project(); p.walls[0].axis.start.x.value=std::numeric_limits<double>::infinity(); issues=validate(p); assert(std::any_of(issues.begin(),issues.end(),[](auto& i){return i.code==ValidationCode::NonFiniteCoordinate;}));
 p=project(); p.walls[0].axis.start.x.value=-std::numeric_limits<double>::max(); p.walls[0].axis.end.x.value=std::numeric_limits<double>::max(); issues=validate(p); assert(std::any_of(issues.begin(),issues.end(),[](auto& i){return i.code==ValidationCode::NonFiniteCoordinate;}));
}
void duplicateAndBadThickness() { auto p=project(); p.walls.push_back(wall()); p.walls[1].thickness.value=-.1; auto issues=validate(p); assert(std::any_of(issues.begin(),issues.end(),[](auto& i){return i.code==ValidationCode::DuplicateWallId;})); assert(std::any_of(issues.begin(),issues.end(),[](auto& i){return i.code==ValidationCode::InvalidWallThickness;})); }
void snapshotCopy() { auto p=project(); ModelSnapshot snap(7,p); p.name="mutated"; p.walls.clear(); assert(snap.revision()==7); assert(snap.project().name=="Test"); assert(snap.project().walls.size()==1); static_assert(std::is_same_v<decltype(snap.project()),const Project&>); }
void invalidMetadata() { Project p{ModelId{""},"",{}}; assert(validate(p).size()==2); }
}
int main(){metricAndValidModel();invalidAxes();duplicateAndBadThickness();snapshotCopy();invalidMetadata();}
