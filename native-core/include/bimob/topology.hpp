#pragma once
#include <cstdint>
#include <deque>
#include <memory>
#include <mutex>
#include <optional>
#include <vector>
#include <cstddef>
namespace bimob {
struct Node final { std::uint64_t id{}; double x{}; double y{}; std::size_t degree{}; };
struct WallSegment final { std::uint64_t id{}; std::uint64_t startNodeId{}; std::uint64_t endNodeId{}; double thickness{}; };
struct TopologySnapshot final {
 const std::vector<Node> nodes;
 const std::vector<WallSegment> segments;
 const std::size_t undoDepth;
 const std::size_t redoDepth;
 TopologySnapshot(std::vector<Node>,std::vector<WallSegment>,std::size_t,std::size_t);
};
class CommandDispatcher final {
public:
 explicit CommandDispatcher(double nodeSnapRadius=0.05);
 std::uint64_t addNode(double x,double y);
 std::uint64_t addWallSegment(std::uint64_t startNodeId,std::uint64_t endNodeId,double thickness);
 bool undo();
 bool redo();
 std::shared_ptr<const TopologySnapshot> snapshot() const;
 void closeAndReset();
 [[nodiscard]] double nodeSnapRadius() const noexcept { return nodeSnapRadius_; }
private:
 struct Command {
  enum class Kind { Node, Segment } kind{};
  Node node{};
  WallSegment segment{};
 };
 std::uint64_t enqueueAndRun(Command);
 bool undoLocked();
 bool redoLocked();
 void removeIsolatedNodeLocked(std::uint64_t);
 mutable std::mutex mutex_;
 std::deque<Command> queue_;
 std::vector<Node> nodes_;
 std::vector<WallSegment> segments_;
 std::vector<Command> undo_;
 std::vector<Command> redo_;
 std::uint64_t nextNodeId_{1},nextSegmentId_{1};
 double nodeSnapRadius_;
};
} // namespace bimob
