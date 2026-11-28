#pragma once
#include "bimob/engine.hpp"
#include <cstdint>
#include <string>
#include <vector>
namespace bimob::bridge {
std::uint64_t createSession();
bool destroySession(std::uint64_t handle) noexcept;
std::string dispatchCommandJson(std::uint64_t handle,const std::string& commandJson);
std::string exportCanonicalSnapshot(std::uint64_t handle);
std::string importCanonicalSnapshot(std::uint64_t handle,const std::string& snapshotJson);
/** Binary little-endian render geometry contract: magic BGR1, version, revision,
 * section counts, then Float32 records. All coordinates/dimensions are metres. */
std::vector<std::uint8_t> exportRenderGeometry(std::uint64_t handle);
bool beginGesture(std::uint64_t handle,const std::string& gestureId);
bool commitGesture(std::uint64_t handle,const std::string& gestureId);
bool cancelGesture(std::uint64_t handle,const std::string& gestureId);
}
