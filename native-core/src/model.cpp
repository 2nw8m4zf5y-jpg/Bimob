#include "bimob/model.hpp"
#include <cmath>
#include <unordered_set>
namespace bimob {
bool ModelId::valid() const noexcept { return !value.empty() && value.find_first_of("\r\n\t") == std::string::npos; }
bool Point2::finite() const noexcept { return std::isfinite(x.value) && std::isfinite(y.value); }
Meters WallAxis::length() const noexcept { return Meters{std::hypot(end.x.value - start.x.value, end.y.value - start.y.value)}; }
bool WallAxis::valid() const noexcept { return start.finite() && end.finite() && std::isfinite(length().value) && length().value > 0.0; }
bool Wall::valid() const noexcept { return id.valid() && axis.valid() && std::isfinite(height.value) && height.value > 0.0 && std::isfinite(thickness.value) && thickness.value > 0.0; }
std::vector<ValidationIssue> validate(const Project& project) {
    std::vector<ValidationIssue> issues;
    if (!project.id.valid()) issues.push_back({ValidationCode::InvalidProjectId, {}, "Project ID must be non-empty and contain no control separators."});
    if (project.name.empty()) issues.push_back({ValidationCode::EmptyProjectName, {}, "Project name must not be empty."});
    std::unordered_set<std::string> wallIds; wallIds.reserve(project.walls.size());
    for (const auto& wall : project.walls) {
        const auto id = wall.id.value;
        if (!wall.id.valid()) issues.push_back({ValidationCode::InvalidWallId, id, "Wall ID is invalid."});
        else if (!wallIds.insert(id).second) issues.push_back({ValidationCode::DuplicateWallId, id, "Wall ID must be unique within a project."});
        if (!wall.axis.start.finite() || !wall.axis.end.finite() || !std::isfinite(wall.axis.length().value))
            issues.push_back({ValidationCode::NonFiniteCoordinate, id, "Wall coordinates and derived axis length must be finite."});
        else if (wall.axis.length().value <= 0.0) issues.push_back({ValidationCode::ZeroLengthWall, id, "Wall axis must have non-zero length."});
        if (!std::isfinite(wall.height.value) || wall.height.value <= 0.0) issues.push_back({ValidationCode::InvalidWallHeight, id, "Wall height must be finite and positive."});
        if (!std::isfinite(wall.thickness.value) || wall.thickness.value <= 0.0) issues.push_back({ValidationCode::InvalidWallThickness, id, "Wall thickness must be finite and positive."});
    }
    return issues;
}
ModelSnapshot::ModelSnapshot(std::uint64_t revision, Project project) : revision_(revision), project_(std::move(project)) {}
std::uint64_t ModelSnapshot::revision() const noexcept { return revision_; }
const Project& ModelSnapshot::project() const noexcept { return project_; }
} // namespace bimob
