#pragma once
#include <cstdint>
#include <string>
#include <utility>
#include <vector>
namespace bimob {
struct ModelId final { std::string value; [[nodiscard]] bool valid() const noexcept; friend bool operator==(const ModelId&, const ModelId&) = default; };
struct Meters final { double value{}; friend bool operator==(const Meters&, const Meters&) = default; };
struct Point2 final { Meters x; Meters y; [[nodiscard]] bool finite() const noexcept; friend bool operator==(const Point2&, const Point2&) = default; };
struct WallAxis final { Point2 start; Point2 end; [[nodiscard]] Meters length() const noexcept; [[nodiscard]] bool valid() const noexcept; friend bool operator==(const WallAxis&, const WallAxis&) = default; };
struct Wall final { ModelId id; WallAxis axis; Meters height; Meters thickness; [[nodiscard]] bool valid() const noexcept; friend bool operator==(const Wall&, const Wall&) = default; };
struct Project final { ModelId id; std::string name; std::vector<Wall> walls; };
enum class ValidationCode : std::uint8_t { InvalidProjectId, EmptyProjectName, InvalidWallId, DuplicateWallId, NonFiniteCoordinate, ZeroLengthWall, InvalidWallHeight, InvalidWallThickness };
struct ValidationIssue final { ValidationCode code; std::string elementId; std::string message; };
[[nodiscard]] std::vector<ValidationIssue> validate(const Project& project);
class ModelSnapshot final {
public:
    ModelSnapshot(std::uint64_t revision, Project project);
    [[nodiscard]] std::uint64_t revision() const noexcept;
    [[nodiscard]] const Project& project() const noexcept;
private:
    std::uint64_t revision_;
    Project project_;
};
} // namespace bimob
