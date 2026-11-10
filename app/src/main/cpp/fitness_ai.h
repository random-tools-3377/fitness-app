// The app's own AI code (0.18.0): loads a model with llama.cpp (and its media part, for photos
// and speech, with mtmd), and answers one prompt at a time. Plain C++ with no Android code, so
// tools/ai-check builds and runs the same file on a Linux machine.
#pragma once

#include <cstddef>
#include <string>

namespace fitai {

struct Model;

// Loads the compute backends: from [lib_dir] on Android, where each CPU variant is its own file;
// from the usual places when [lib_dir] is null.
void init(const char * lib_dir);

// Loads a model, and its media part when [mmproj_path] isn't null or empty. Null on failure.
Model * load(const char * model_path, const char * mmproj_path, int n_ctx, int n_threads);

void unload(Model * m);

// The chat template's text for a system and a user message, ready for the model's answer. The
// user message holds the media marker where a photo or a recording goes.
std::string format(Model * m, const std::string & system, const std::string & user);

// The marker that stands for a photo or a recording in a prompt.
const char * media_marker();

// Answers [prompt] (already formatted), greedily, held to [grammar] (GBNF) when it isn't empty,
// with at most [max_tokens]. [media] is a photo (JPEG/PNG) or a recording (WAV) for the marker in
// the prompt, or null. [stop] is checked between tokens; when it is set, or after [max_seconds],
// the answer so far is returned, so an answer can never hang. On failure, returns an empty
// string and sets [error].
std::string answer(Model * m, const std::string & prompt, const std::string & grammar, int max_tokens,
                   const unsigned char * media, size_t media_len, const volatile bool * stop, std::string & error,
                   int max_seconds = 120);

}  // namespace fitai
