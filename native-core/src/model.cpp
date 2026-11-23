#include "bimob/model.hpp"
#include <algorithm>
#include <cmath>
namespace bimob {
bool ModelId::valid() const noexcept { return !value.empty() && value.find_first_of("\r\n\t") == std::string::npos; }
bool Point2::finite() const noexcept { return std::isfinite(x.value) && std::isfinite(y.value); }
Meters WallAxis::length() const noexcept { return Meters{std::hypot(end.x.value-start.x.value,end.y.value-start.y.value)}; }
bool WallAxis::valid() const noexcept { return start.finite()&&end.finite()&&std::isfinite(length().value)&&length().value>=kMinWallLength; }
bool Wall::valid() const noexcept { return id.valid()&&axis.valid()&&std::isfinite(height.value)&&height.value>kCoordinateEpsilon&&std::isfinite(thickness.value)&&thickness.value>kCoordinateEpsilon; }
std::vector<ValidationIssue> validate(const Project&p){
 std::vector<ValidationIssue> issues;
 if(!p.id.valid())issues.push_back({ValidationCode::InvalidProjectId,{},"Project ID is invalid."});
 if(p.name.empty())issues.push_back({ValidationCode::EmptyProjectName,{},"Project name must not be empty."});
 std::vector<std::string> ids;ids.reserve(p.walls.size());
 for(const auto&w:p.walls){const auto&id=w.id.value;
  if(!w.id.valid())issues.push_back({ValidationCode::InvalidWallId,id,"Wall ID is invalid."});
  ids.push_back(id);
  if(!w.axis.start.finite()||!w.axis.end.finite()||!std::isfinite(w.axis.length().value))issues.push_back({ValidationCode::NonFiniteCoordinate,id,"Wall coordinates and length must be finite."});
  else if(w.axis.length().value<kMinWallLength)issues.push_back({ValidationCode::ZeroLengthWall,id,"Wall axis is below minimum length."});
  if(!std::isfinite(w.height.value)||w.height.value<=kCoordinateEpsilon)issues.push_back({ValidationCode::InvalidWallHeight,id,"Wall height must be finite and positive."});
  if(!std::isfinite(w.thickness.value)||w.thickness.value<=kCoordinateEpsilon)issues.push_back({ValidationCode::InvalidWallThickness,id,"Wall thickness must be finite and positive."});
 }
 std::sort(ids.begin(),ids.end());for(std::size_t i=1;i<ids.size();++i)if(ids[i]==ids[i-1])issues.push_back({ValidationCode::DuplicateWallId,ids[i],"Wall ID must be unique."});
 return issues;
}
} // namespace bimob
