// The AI check's tool, asking a model the same way the app does:
//   ai-check MODEL MMPROJ|- SYSTEM_FILE USER_FILE [GRAMMAR_FILE|-] [MEDIA_FILE|-] [MAX_TOKENS] [PREFIX_FILE|-]
// "{media}" in the user message is where the photo or recording goes. Prints the answer, and on
// the error stream how long loading and answering took.
#include <chrono>
#include <cstdio>
#include <cstdlib>
#include <fstream>
#include <sstream>
#include <string>

#include "fitness_ai.h"

static std::string slurp(const std::string & path) {
    if (path == "-" || path.empty()) return "";
    std::ifstream f(path, std::ios::binary);
    std::stringstream ss;
    ss << f.rdbuf();
    return ss.str();
}

int main(int argc, char ** argv) {
    if (argc < 5) {
        fprintf(stderr, "usage: ai-check MODEL MMPROJ SYSTEM USER [GRAMMAR] [MEDIA] [MAX] [PREFIX]\n");
        return 2;
    }
    std::string mmproj = argv[2];
    std::string system = slurp(argv[3]);
    std::string user = slurp(argv[4]);
    std::string grammar = argc > 5 ? slurp(argv[5]) : "";
    std::string media = argc > 6 ? slurp(argv[6]) : "";
    int max_tokens = argc > 7 ? atoi(argv[7]) : 200;
    std::string prefix = argc > 8 ? slurp(argv[8]) : "";
    fitai::init(nullptr);
    auto t0 = std::chrono::steady_clock::now();
    fitai::Model * m = fitai::load(argv[1], mmproj == "-" ? nullptr : mmproj.c_str(), 4096, 4);
    if (!m) { fprintf(stderr, "could not load\n"); return 1; }
    auto t1 = std::chrono::steady_clock::now();
    size_t at = user.find("{media}");
    if (at != std::string::npos) user.replace(at, 7, fitai::media_marker());
    std::string prompt = fitai::format(m, system, user) + prefix;
    std::string error;
    std::string out = fitai::answer(m, prompt, grammar, max_tokens,
                                    media.empty() ? nullptr : (const unsigned char *) media.data(), media.size(), nullptr, error);
    auto t2 = std::chrono::steady_clock::now();
    if (!error.empty()) { fprintf(stderr, "error: %s\n", error.c_str()); return 1; }
    printf("%s\n", out.c_str());
    fprintf(stderr, "[load %.1f s, answer %.1f s]\n",
            std::chrono::duration<double>(t1 - t0).count(), std::chrono::duration<double>(t2 - t1).count());
    fitai::unload(m);
    return 0;
}
