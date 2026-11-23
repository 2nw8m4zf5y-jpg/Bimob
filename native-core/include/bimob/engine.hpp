#pragma once
#include "bimob/model.hpp"
#include <atomic>
#include <functional>
#include <map>
#include <memory>
#include <mutex>
#include <optional>
#include <string>
#include <variant>
#include <vector>
namespace bimob {
enum class ErrorCode { InvalidArgument, ValidationFailed, StaleRevision, Cancelled, CommandFailed, DependencyCycle, DependencyDepthExceeded, EngineLimitExceeded, EvaluationFailed, HistoryEmpty, RevisionOverflow };
struct CancellationToken final { std::atomic<bool> cancelled{false}; void cancel() noexcept { cancelled.store(true,std::memory_order_relaxed); } [[nodiscard]] bool isCancelled() const noexcept { return cancelled.load(std::memory_order_relaxed); } };
struct HistoryLimits final { std::size_t max_steps{100}; };
struct Error { ErrorCode code; std::string message; std::string nodeId; std::string budget; std::size_t actual{}; std::size_t limit{}; };
template<class T> class Result final {
public:
 static Result success(T value){return Result(std::move(value));}
 static Result failure(Error error){return Result(std::move(error));}
 [[nodiscard]] bool ok()const noexcept{return std::holds_alternative<T>(value_);}
 [[nodiscard]] const T* value()const noexcept{return std::get_if<T>(&value_);}
 [[nodiscard]] T* value()noexcept{return std::get_if<T>(&value_);}
 [[nodiscard]] const Error* error()const noexcept{return std::get_if<Error>(&value_);}
private:
 explicit Result(T value):value_(std::move(value)){}
 explicit Result(Error error):value_(std::move(error)){}
 std::variant<T,Error> value_;
};
using NodeId=std::string;
struct DependencyEdge {NodeId source;NodeId dependent;friend bool operator==(const DependencyEdge&,const DependencyEdge&)=default;};
struct DerivedValue {std::variant<double,bool,std::string,std::vector<double>> data;friend bool operator==(const DerivedValue&,const DerivedValue&)=default;};
using DerivedMap=std::map<NodeId,DerivedValue>;
using Evaluator=std::function<Result<DerivedValue>(const Project&,const DerivedMap& directInputs)>;
struct EngineLimits {std::size_t dirty_nodes{100000};std::size_t evaluations{1000000};std::size_t fan_out{10000};std::size_t graph_nodes{250000};std::size_t graph_edges{1000000};std::size_t dependency_depth{4096};};
struct DependencyGraph {std::vector<NodeId> nodes;std::vector<DependencyEdge> edges;[[nodiscard]] bool hasNode(const NodeId&)const;[[nodiscard]] bool hasEdge(const DependencyEdge&)const;};
struct EvaluationReport {DerivedMap values;std::vector<NodeId> order;std::size_t evaluations{};};
class EvaluatorRegistry final {
public:
 EvaluatorRegistry()=default;
 explicit EvaluatorRegistry(std::map<NodeId,Evaluator> evaluators):base_(std::move(evaluators)){}
 [[nodiscard]] EvaluatorRegistry withOverride(const NodeId&,Evaluator)const;
 [[nodiscard]] std::optional<Evaluator> get(const NodeId&)const;
private:std::map<NodeId,Evaluator> base_;std::map<NodeId,Evaluator> overrides_;
};
struct ModelRevision final {
 const std::uint64_t number;
 const std::shared_ptr<const Project> project;
 const std::shared_ptr<const DependencyGraph> graph;
 const std::shared_ptr<const DerivedMap> derived;
 const std::shared_ptr<const std::vector<std::shared_ptr<const Wall>>> wallEntities;
 ModelRevision(std::uint64_t,std::shared_ptr<const Project>,std::shared_ptr<const DependencyGraph>,std::shared_ptr<const DerivedMap>,std::shared_ptr<const std::vector<std::shared_ptr<const Wall>>> = {});
};
using Mutation=std::function<Result<Project>(const Project&)>;
struct Commit {std::shared_ptr<const ModelRevision> revision;bool coalesced{};};
class CommandBus final {
public:
 explicit CommandBus(Project initial,EngineLimits limits={},EvaluatorRegistry evaluators={},HistoryLimits historyLimits={});
 [[nodiscard]] std::shared_ptr<const ModelRevision> snapshot()const;
 Result<Commit> execute(const Mutation&,std::optional<std::uint64_t> expectedRevision={},std::optional<std::string> gestureId={},const CancellationToken* cancellation=nullptr);
 Result<std::shared_ptr<const ModelRevision>> undo();
 Result<std::shared_ptr<const ModelRevision>> redo();
 Result<Commit> transact(const Mutation&,const std::vector<DependencyEdge>& addEdges,const std::vector<NodeId>& dirty,const std::map<NodeId,Evaluator>& overrides={},std::optional<std::string> gestureId={},const CancellationToken* cancellation=nullptr);
 [[nodiscard]] std::size_t undoDepth()const;
 [[nodiscard]] std::size_t redoDepth()const;
private:
 Result<Commit> commitCandidate(Project,std::shared_ptr<const DependencyGraph>,DerivedMap,std::vector<NodeId>,std::map<NodeId,Evaluator>,std::optional<std::string>,const CancellationToken* cancellation=nullptr);
 mutable std::mutex mutex_;EngineLimits limits_;EvaluatorRegistry evaluators_;std::shared_ptr<const ModelRevision> current_;std::optional<Error> initializationError_;HistoryLimits historyLimits_;
 std::vector<std::shared_ptr<const ModelRevision>> undo_;std::vector<std::shared_ptr<const ModelRevision>> redo_;std::optional<std::string> lastGesture_;
};
Result<Project> deleteWallCascade(const Project&,const ModelId& wallId);
Result<Project> deleteLevelCascade(const Project&,const ModelId& levelId);
Result<Project> solveWallJoin(const Project&,const ModelId& movedWall,bool movedStart,Point2 newJoint);
Result<DependencyGraph> admitEdges(const DependencyGraph&,const std::vector<DependencyEdge>&,const EngineLimits&);
Result<EvaluationReport> recompute(const DependencyGraph&,const std::vector<NodeId>& seeds,const Project&,const DerivedMap&,const EvaluatorRegistry&,const EngineLimits&,const CancellationToken* cancellation=nullptr);

/** Typed native BIM mutation factories. Every mutation is staged against an immutable source
 * Project and validated before CommandBus publishes a revision. */
namespace commands {
Mutation createLevel(Level value);
Mutation moveLevel(ModelId id, Meters elevation);
Mutation resizeLevel(ModelId id, Meters height);
Mutation deleteLevel(ModelId id);
Mutation createWall(Wall value);
Mutation moveWall(ModelId id, Point2 delta);
Mutation moveWallEndpoint(ModelId id, bool movedStart, Point2 joint);
Mutation resizeWall(ModelId id, Meters length);
Mutation deleteWall(ModelId id);
Mutation createOpening(Opening value);
Mutation moveOpening(ModelId id, Meters offsetAlongWall, Meters sillHeight);
Mutation resizeOpening(ModelId id, Meters width, Meters height);
Mutation deleteOpening(ModelId id);
Mutation createCatalogInstance(CatalogInstance value);
Mutation moveCatalogInstance(ModelId id, Point2 position);
Mutation resizeCatalogInstance(ModelId id, Meters width, Meters depth, Meters height);
Mutation deleteCatalogInstance(ModelId id);
/** Stages child mutations in sequence; CommandBus commits the returned candidate once. */
Mutation batch(std::vector<Mutation> mutations);
} // namespace commands

} // namespace bimob
