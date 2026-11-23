#include "bimob/model.hpp"
#include <algorithm>
#include <cmath>
#include <set>
namespace bimob {
bool ModelId::valid() const noexcept { return !value.empty() && value.find_first_of("\\r\\n\\t") == std::string::npos; }
bool Point2::finite() const noexcept { return std::isfinite(x.value) && std::isfinite(y.value); }
Meters WallAxis::length() const noexcept { return Meters{std::hypot(end.x.value-start.x.value,end.y.value-start.y.value)}; }
bool WallAxis::valid() const noexcept { return start.finite()&&end.finite()&&std::isfinite(length().value)&&length().value>=kMinWallLength; }
bool Wall::valid() const noexcept { return id.valid()&&axis.valid()&&std::isfinite(height.value)&&height.value>kCoordinateEpsilon&&std::isfinite(thickness.value)&&thickness.value>kCoordinateEpsilon; }
namespace {
void issue(std::vector<ValidationIssue>&out,ValidationCode code,const std::string&id,const char*msg){out.push_back({code,id,msg});}
bool validBounds(const DimensionBounds&b){return std::isfinite(b.min.value)&&std::isfinite(b.max.value)&&b.min.value>0&&b.max.value>=b.min.value;}
bool inBounds(Meters value,const DimensionBounds&b){return validBounds(b)&&value.value>=b.min.value-kCoordinateEpsilon&&value.value<=b.max.value+kCoordinateEpsilon;}
}
std::vector<ValidationIssue> validate(const Project&p){
 std::vector<ValidationIssue> out;
 if(!p.id.valid())issue(out,ValidationCode::InvalidProjectId,"","Project ID is invalid.");
 if(p.name.empty())issue(out,ValidationCode::EmptyProjectName,"","Project name must not be empty.");
 std::set<std::string> wallIds,levelIds,openingIds,instanceIds,joinIds;
 for(const auto&l:p.levels){if(!l.id.valid()||l.name.empty()||!std::isfinite(l.elevation.value)||!std::isfinite(l.height.value)||l.height.value<=kCoordinateEpsilon)issue(out,ValidationCode::InvalidLevel,l.id.value,"Level ID/name/elevation/height is invalid.");if(!levelIds.insert(l.id.value).second)issue(out,ValidationCode::DuplicateLevelId,l.id.value,"Level ID must be unique.");}
 for(const auto&w:p.walls){const auto&id=w.id.value;if(!w.id.valid())issue(out,ValidationCode::InvalidWallId,id,"Wall ID is invalid.");if(!wallIds.insert(id).second)issue(out,ValidationCode::DuplicateWallId,id,"Wall ID must be unique.");if(!w.axis.start.finite()||!w.axis.end.finite()||!std::isfinite(w.axis.length().value))issue(out,ValidationCode::NonFiniteCoordinate,id,"Wall coordinates and length must be finite.");else if(w.axis.length().value<kMinWallLength)issue(out,ValidationCode::ZeroLengthWall,id,"Wall axis is below minimum length.");if(!std::isfinite(w.height.value)||w.height.value<=kCoordinateEpsilon)issue(out,ValidationCode::InvalidWallHeight,id,"Wall height must be finite and positive.");if(!std::isfinite(w.thickness.value)||w.thickness.value<=kCoordinateEpsilon)issue(out,ValidationCode::InvalidWallThickness,id,"Wall thickness must be finite and positive.");if(w.levelId.valid()&&!levelIds.contains(w.levelId.value))issue(out,ValidationCode::UnknownWallLevel,id,"Wall references an unknown level.");}
 for(const auto&o:p.openings){if(!o.id.valid()||!o.hostWallId.valid()||!std::isfinite(o.offsetAlongWall.value)||!std::isfinite(o.width.value)||!std::isfinite(o.height.value)||!std::isfinite(o.sillHeight.value)||o.offsetAlongWall.value<0||o.width.value<=0||o.height.value<=0||o.sillHeight.value<0)issue(out,ValidationCode::InvalidOpening,o.id.value,"Opening parameters must be finite and positive.");if(!openingIds.insert(o.id.value).second)issue(out,ValidationCode::DuplicateOpeningId,o.id.value,"Opening ID must be unique.");auto wall=std::find_if(p.walls.begin(),p.walls.end(),[&](const Wall&w){return w.id==o.hostWallId;});if(wall==p.walls.end())issue(out,ValidationCode::InvalidOpening,o.id.value,"Opening host wall does not exist.");else if(o.offsetAlongWall.value+o.width.value>wall->axis.length().value+kCoordinateEpsilon||o.sillHeight.value+o.height.value>wall->height.value+kCoordinateEpsilon)issue(out,ValidationCode::OpeningOutOfBounds,o.id.value,"Opening lies outside host wall bounds.");}
 for(const auto&i:p.catalogInstances){if(!i.id.valid()||i.assetId.empty()||!i.position.finite()||!std::isfinite(i.rotationRadians)||!inBounds(i.targetWidth,i.widthBounds)||!inBounds(i.targetDepth,i.depthBounds)||!inBounds(i.targetHeight,i.heightBounds))issue(out,ValidationCode::InvalidCatalogInstance,i.id.value,"Catalog instance or dimensions are invalid/outside bounds.");if(!instanceIds.insert(i.id.value).second)issue(out,ValidationCode::DuplicateCatalogInstanceId,i.id.value,"Catalog instance ID must be unique.");if(i.levelId.valid()&&!levelIds.contains(i.levelId.value))issue(out,ValidationCode::UnknownCatalogLevel,i.id.value,"Catalog instance references unknown level.");if(i.hostWallId.valid()&&!wallIds.contains(i.hostWallId.value))issue(out,ValidationCode::UnknownCatalogHost,i.id.value,"Catalog instance references unknown host wall.");}
 for(const auto&j:p.joins){if(!j.id.valid()||!j.joint.finite()||j.wallA==j.wallB||!wallIds.contains(j.wallA.value)||!wallIds.contains(j.wallB.value))issue(out,ValidationCode::InvalidWallJoin,j.id.value,"Wall join must reference two distinct valid walls and a finite joint.");if(!joinIds.insert(j.id.value).second)issue(out,ValidationCode::DuplicateWallJoinId,j.id.value,"WallJoin ID must be unique.");}
 return out;
}
} // namespace bimob