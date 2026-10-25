#include "c/engine.h"
#include "c/engine_internal.h"
#include "c/error_reporter_internal.h"
#include "nlohmann/json.hpp"
#include "runtime/conversation/model_data_processor/model_data_processor_factory.h"
#include "runtime/conversation/model_data_processor/model_data_processor.h"
#include "runtime/engine/engine.h"
#include "runtime/engine/engine_settings.h"
#include "runtime/engine/io_types.h"

// App-specific C entry point: preprocess only this turn, then use the existing
// Session. No Conversation, transcript list, checkpoints or prompt replay.
extern "C" LITERT_LM_C_API_EXPORT LiteRtLmResponses*
ghost_session_generate_multimodal(LiteRtLmEngine* engine, LiteRtLmSession* session,
                                  const char* text, const char* image_path,
                                  const char* audio_path) {
  using namespace litert::lm;
  auto error = [](const absl::Status& status) -> LiteRtLmResponses* {
    litert::lm::c::SetLastError(status);
    return nullptr;
  };
  if (!engine || !engine->engine || !session || !session->session || !text)
    return error(absl::InvalidArgumentError("Invalid multimodal request."));
  auto config = CreateDataProcessorConfigFromLlmModelType(
      session->session->GetSessionConfig().GetLlmModelType());
  if (!config.ok()) return error(config.status());
  auto processor = CreateModelDataProcessor(*config, std::nullopt,
      &engine->engine->GetTokenizer(),
      session->session->GetSessionConfig().GetStopTokenIds(), false);
  if (!processor.ok()) return error(processor.status());
  auto content = nlohmann::ordered_json::array();
  std::string prompt = "<|turn>user\n";
  if (image_path && image_path[0]) {
    content.push_back({{"type", "image"}, {"path", image_path}});
    prompt += "\n\n<|image|>\n\n";
  }
  if (audio_path && audio_path[0]) {
    content.push_back({{"type", "audio"}, {"path", audio_path}});
    prompt += "<|audio|>";
  }
  content.push_back({{"type", "text"}, {"text", text}});
  prompt += text;
  prompt += "<turn|>\n<|turn>model\n";
  auto messages = nlohmann::ordered_json::array();
  messages.push_back({{"role", "user"}, {"content", std::move(content)}});
  auto inputs = (*processor)->ToInputDataVector(prompt, messages, std::monostate{});
  if (!inputs.ok()) return error(inputs.status());
  auto response = session->session->GenerateContent(std::move(*inputs));
  if (!response.ok()) return error(response.status());
  return new LiteRtLmResponses{std::move(*response)};
}

extern "C" LITERT_LM_C_API_EXPORT LiteRtLmResponses*
ghost_session_debug(LiteRtLmSession* session, const char* request) {
  if(!session||!session->session) {
    litert::lm::c::SetLastError(absl::InvalidArgumentError("No active session."));
    return nullptr;
  }
  auto snapshot=session->session->GetGhostDebugJson(request);
  if(!snapshot.ok()){litert::lm::c::SetLastError(snapshot.status());return nullptr;}
  return new LiteRtLmResponses{litert::lm::Responses(litert::lm::TaskState::kDone, std::vector<std::string>{*snapshot})};
}
