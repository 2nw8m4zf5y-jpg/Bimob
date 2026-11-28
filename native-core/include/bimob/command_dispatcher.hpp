#pragma once
#include <cstdint>
#include <cstddef>
#include <deque>
#include <functional>
#include <mutex>
#include <string>
#include <unordered_map>
namespace nagaplas::transactions{enum class ResultCode{Applied,Duplicate,RevisionMismatch,IdempotencyConflict,InvalidRequest,ApplyFailed};struct CommandResult{ResultCode code{ResultCode::InvalidRequest};bool is_duplicate{};uint64_t scene_revision{};std::string state,message;};using ApplyCommand=std::function<bool(const std::string&,const std::string&,std::string&)>;class CommandDispatcher{public:explicit CommandDispatcher(size_t limit=1024);CommandResult dispatch(const std::string&,const std::string&,const std::string&,const ApplyCommand&,uint64_t expected_revision=UINT64_MAX);uint64_t revision()const;std::string state()const;void reset(std::string state={});private:struct Entry{std::string id;uint64_t hash;CommandResult result;};static uint64_t hash(const std::string&)noexcept;size_t limit_;mutable std::mutex m_;uint64_t rev_{};std::string state_;std::unordered_map<std::string,std::deque<Entry>>cache_;};}