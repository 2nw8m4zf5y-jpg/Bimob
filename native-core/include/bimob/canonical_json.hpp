#pragma once
#include "bimob/model.hpp"
#include <string>
namespace bimob {
/** Canonical project snapshot serializer matching Kotlin CanonicalProjectJson schema. */
[[nodiscard]] std::string canonicalProjectJson(const Project&);
}
