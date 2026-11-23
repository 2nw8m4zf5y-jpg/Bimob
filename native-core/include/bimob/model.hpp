#pragma once
#include <algorithm>
#include <cstdint>
#include <initializer_list>
#include <memory>
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
template<class T> class CowVector final {
 using Storage=std::vector<T>;
 std::shared_ptr<Storage> storage_;
 void detach(){if(!storage_.unique())storage_=std::make_shared<Storage>(*storage_);}
public:
 using iterator=typename Storage::iterator;using const_iterator=typename Storage::const_iterator;
 CowVector():storage_(std::make_shared<Storage>()){}
 CowVector(std::initializer_list<T> init):storage_(std::make_shared<Storage>(init)){}
 explicit CowVector(Storage value):storage_(std::make_shared<Storage>(std::move(value))){}
 [[nodiscard]] std::size_t size()const noexcept{return storage_->size();}
 [[nodiscard]] bool empty()const noexcept{return storage_->empty();}
 [[nodiscard]] long storageUseCount()const noexcept{return storage_.use_count();}
 [[nodiscard]] bool sharesStorageWith(const CowVector&other)const noexcept{return storage_==other.storage_;}
 const_iterator begin()const noexcept{return storage_->begin();}const_iterator end()const noexcept{return storage_->end();}
 iterator begin(){detach();return storage_->begin();}iterator end(){detach();return storage_->end();}
 const T&operator[](std::size_t i)const{return (*storage_)[i];}T&operator[](std::size_t i){detach();return (*storage_)[i];}
 const T&front()const{return storage_->front();}T&front(){detach();return storage_->front();}
 const T&back()const{return storage_->back();}T&back(){detach();return storage_->back();}
 void push_back(const T&v){detach();storage_->push_back(v);}void push_back(T&&v){detach();storage_->push_back(std::move(v));}
 iterator erase(iterator first,iterator last){detach();return storage_->erase(first,last);}
 iterator erase(iterator pos){detach();return storage_->erase(pos);}
 void clear(){detach();storage_->clear();}
 friend bool operator==(const CowVector&a,const CowVector&b){return *a.storage_==*b.storage_;}
};
struct Project final { ModelId id; std::string name; CowVector<Wall> walls; CowVector<Level> levels; CowVector<WallJoin> joins; CowVector<Opening> openings; CowVector<CatalogInstance> catalogInstances; };
enum class ValidationCode : std::uint8_t { InvalidProjectId, EmptyProjectName, InvalidWallId, DuplicateWallId, NonFiniteCoordinate, ZeroLengthWall, InvalidWallHeight, InvalidWallThickness, InvalidLevel, DuplicateLevelId, UnknownWallLevel, InvalidOpening, DuplicateOpeningId, OpeningOutOfBounds, InvalidCatalogInstance, DuplicateCatalogInstanceId, UnknownCatalogLevel, UnknownCatalogHost, InvalidWallJoin, DuplicateWallJoinId };
struct ValidationIssue { ValidationCode code; std::string elementId; std::string message; };
[[nodiscard]] std::vector<ValidationIssue> validate(const Project& project);
} // namespace bimob