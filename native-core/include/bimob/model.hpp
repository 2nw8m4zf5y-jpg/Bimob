#pragma once
#include <cstdint>
#include <string>
#include <vector>
namespace bimob {
inline constexpr double kCoordinateEpsilon = 1e-9;
inline constexpr double kMinWallLength = 1e-6;
struct ModelId final { std::string value; [[nodiscard]] bool valid() const noexcept; friend bool operator==(const ModelId&, const ModelId&) = default; };
struct Meters final { double value{}; friend bool operator==(const Meters&, const Meters&) = default; };
struct Point2 final { Meters x; Meters y; [[nodiscard]] bool finite() const noexcept; friend bool operator==(const Point2&, const Point2&) = default; };
struct WallAxis final { Point2 start; Point2 end; [[nodiscard]] Meters length() const noexcept; [[nodiscard]] bool valid() const noexcept; friend bool operator==(const WallAxis&, const WallAxis&) = default; };
struct Level final { ModelId id; std::string name; Meters elevation; Meters height; friend bool operator==(const Level&, const Level&) = default; };
struct Wall final { ModelId id; WallAxis axis; Meters height; Meters thickness; ModelId levelId; [[nodiscard]] bool valid() const noexcept; friend bool operator==(const Wall&, const Wall&) = default; };
struct WallJoin final { ModelId id; ModelId wallA; bool endA{}; ModelId wallB; bool endB{}; Point2 joint; friend bool operator==(const WallJoin&, const WallJoin&) = default; };
enum class OpeningKind : std::uint8_t { Door, Window, Generic };
struct Opening final { ModelId id; ModelId hostWallId; OpeningKind kind{OpeningKind::Generic}; Meters offsetAlongWall; Meters width; Meters height; Meters sillHeight; friend bool operator==(const Opening&, const Opening&) = default; };
enum class ScalingMode : std::uint8_t { FreeStretch, Uniform, Presets };
struct DimensionBounds final { Meters min; Meters max; };
struct CatalogInstance final { ModelId id; std::string assetId; Point2 position; double rotationRadians{}; ModelId levelId; ModelId hostWallId; Meters targetWidth; Meters targetDepth; Meters targetHeight; ScalingMode scalingMode{ScalingMode::FreeStretch}; DimensionBounds widthBounds; DimensionBounds depthBounds; DimensionBounds heightBounds; std::string category; std::string scheduleTag; friend bool operator==(const CatalogInstance&, const CatalogInstance&) = default; };
struct Project final { ModelId id; std::string name; std::vector<Wall> walls; std::vector<Level> levels; std::vector<WallJoin> joins; std::vector<Opening> openings; std::vector<CatalogInstance> catalogInstances; };
enum class ValidationCode : std::uint8_t { InvalidProjectId, EmptyProjectName, InvalidWallId, DuplicateWallId, NonFiniteCoordinate, ZeroLengthWall, InvalidWallHeight, InvalidWallThickness, InvalidLevel, DuplicateLevelId, UnknownWallLevel, InvalidOpening, DuplicateOpeningId, OpeningOutOfBounds, InvalidCatalogInstance, DuplicateCatalogInstanceId, UnknownCatalogLevel, UnknownCatalogHost, InvalidWallJoin, DuplicateWallJoinId };
struct ValidationIssue { ValidationCode code; std::string elementId; std::string message; };
[[nodiscard]] std::vector<ValidationIssue> validate(const Project& project);
} // namespace bimob