#pragma once
#include <atomic>
#include <functional>
#include <string>
#include "llama.h"

extern std::atomic<bool> cancelled;
class OfflineEngine {
 public:
  explicit OfflineEngine(const std::string &path);
  ~OfflineEngine();
  std::string generate(const std::string &prompt,
                       const std::function<void(const std::string &)> &progress);
 private:
  llama_model *model_ = nullptr;
};
