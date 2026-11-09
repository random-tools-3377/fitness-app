#include "fitness_ai.h"

#include <cstring>
#include <memory>
#include <vector>

#include "ggml-backend.h"
#include "llama.h"
#include "mtmd.h"
#include "mtmd-helper.h"

namespace fitai {

struct Model {
    llama_model * model = nullptr;
    mtmd_context * media = nullptr;
    int n_ctx = 0;
    int n_threads = 0;
};

void init(const char * lib_dir) {
    llama_log_set([](enum ggml_log_level level, const char * text, void *) {
        if (level >= GGML_LOG_LEVEL_ERROR) fprintf(stderr, "%s", text);
    }, nullptr);
    if (lib_dir && *lib_dir) ggml_backend_load_all_from_path(lib_dir); else ggml_backend_load_all();
    llama_backend_init();
}

Model * load(const char * model_path, const char * mmproj_path, int n_ctx, int n_threads) {
    llama_model_params mp = llama_model_default_params();
    mp.n_gpu_layers = 0;
    llama_model * model = llama_model_load_from_file(model_path, mp);
    if (!model) return nullptr;
    auto * m = new Model();
    m->model = model;
    m->n_ctx = n_ctx;
    m->n_threads = n_threads;
    if (mmproj_path && *mmproj_path) {
        mtmd_context_params cp = mtmd_context_params_default();
        cp.use_gpu = false;
        cp.n_threads = n_threads;
        cp.print_timings = false;
        cp.warmup = false;
        m->media = mtmd_init_from_file(mmproj_path, model, cp);
        if (!m->media) {
            unload(m);
            return nullptr;
        }
    }
    return m;
}

void unload(Model * m) {
    if (!m) return;
    if (m->media) mtmd_free(m->media);
    if (m->model) llama_model_free(m->model);
    delete m;
}

const char * media_marker() { return mtmd_default_marker(); }

std::string format(Model * m, const std::string & system, const std::string & user) {
    const char * tmpl = llama_model_chat_template(m->model, nullptr);
    std::vector<llama_chat_message> msgs;
    if (!system.empty()) msgs.push_back({"system", system.c_str()});
    msgs.push_back({"user", user.c_str()});
    std::vector<char> buf(4096 + system.size() + user.size());
    int n = llama_chat_apply_template(tmpl, msgs.data(), msgs.size(), true, buf.data(), (int32_t) buf.size());
    if (n > (int) buf.size()) {
        buf.resize(n);
        n = llama_chat_apply_template(tmpl, msgs.data(), msgs.size(), true, buf.data(), (int32_t) buf.size());
    }
    if (n < 0) return system + "\n\n" + user + "\n";
    return std::string(buf.data(), n);
}

// Feeds plain text to the context at positions from [pos], with logits for the last token.
static bool feed_text(llama_context * ctx, const llama_vocab * vocab, const std::string & text, llama_pos & pos, int n_batch) {
    int n = -llama_tokenize(vocab, text.c_str(), (int32_t) text.size(), nullptr, 0, true, true);
    std::vector<llama_token> tokens(n);
    if (llama_tokenize(vocab, text.c_str(), (int32_t) text.size(), tokens.data(), n, true, true) < 0) return false;
    llama_batch_ext * batch = llama_batch_ext_init(ctx);
    bool ok = true;
    for (int i = 0; i < n && ok; i += n_batch) {
        const int count = std::min(n_batch, n - i);
        llama_batch_ext_clear(batch);
        for (int j = 0; j < count; j++) {
            const int32_t idx = llama_batch_ext_add_token(batch, 0, tokens[i + j]);
            const llama_pos p = pos + i + j;
            llama_batch_ext_set_pos(batch, idx, &p);
        }
        if (i + count == n) llama_batch_ext_set_output_logits(batch, count - 1, true);
        ok = llama_process(ctx, LLAMA_PROCESS_TYPE_DECODE, batch) == 0;
    }
    llama_batch_ext_free(batch);
    pos += n;
    return ok;
}

std::string answer(Model * m, const std::string & prompt, const std::string & grammar, int max_tokens,
                   const unsigned char * media, size_t media_len, const volatile bool * stop, std::string & error) {
    error.clear();
    const llama_vocab * vocab = llama_model_get_vocab(m->model);
    llama_context_params cp = llama_context_default_params();
    cp.n_ctx = m->n_ctx;
    cp.n_batch = 512;
    cp.n_ubatch = 512;
    cp.n_threads = m->n_threads;
    cp.n_threads_batch = m->n_threads;
    llama_context * ctx = llama_init_from_model(m->model, cp);
    if (!ctx) { error = "no context"; return ""; }

    llama_pos pos = 0;
    bool ok;
    if (media && media_len > 0 && m->media) {
        mtmd_helper_bitmap_wrapper bw = mtmd_helper_bitmap_init_from_buf(m->media, media, media_len, false, mtmd_helper_init_opt_default());
        if (!bw.bitmap) { llama_free(ctx); error = "the photo or recording couldn't be read"; return ""; }
        mtmd_input_text text { prompt.c_str(), prompt.size(), true, true };
        mtmd_input_chunks * chunks = mtmd_input_chunks_init();
        const mtmd_bitmap * bitmaps[] = { bw.bitmap };
        ok = mtmd_tokenize(m->media, chunks, &text, bitmaps, 1) == 0;
        llama_pos new_pos = 0;
        if (ok) ok = mtmd_helper_eval_chunks(m->media, ctx, chunks, 0, 0, 512, true, &new_pos) == 0;
        pos = new_pos;
        mtmd_input_chunks_free(chunks);
        mtmd_bitmap_free(bw.bitmap);
        if (bw.video_ctx) mtmd_helper_video_free(bw.video_ctx);
    } else {
        ok = feed_text(ctx, vocab, prompt, pos, 512);
    }
    if (!ok) { llama_free(ctx); error = "the model couldn't read the prompt"; return ""; }

    llama_sampler * smpl = llama_sampler_chain_init(llama_sampler_chain_default_params());
    if (!grammar.empty()) {
        llama_sampler * g = llama_sampler_init_grammar(vocab, grammar.c_str(), "root");
        if (!g) { llama_sampler_free(smpl); llama_free(ctx); error = "bad grammar"; return ""; }
        llama_sampler_chain_add(smpl, g);
    }
    // Free text (the weekly summary) is kept from going round in circles; held text needs no help.
    if (grammar.empty()) llama_sampler_chain_add(smpl, llama_sampler_init_penalties(llama_vocab_n_tokens(vocab), 64, 1.1f, 0.0f, 0.0f));
    llama_sampler_chain_add(smpl, llama_sampler_init_greedy());

    std::string out;
    llama_batch_ext * batch = llama_batch_ext_init(ctx);
    for (int i = 0; i < max_tokens; i++) {
        if (stop && *stop) break;
        if (pos >= m->n_ctx - 1) break;
        llama_token t = llama_sampler_sample(smpl, ctx, -1);
        if (llama_vocab_is_eog(vocab, t)) break;
        char piece[256];
        int n = llama_token_to_piece(vocab, t, piece, sizeof(piece), 0, false);
        if (n > 0) out.append(piece, n);
        llama_batch_ext_clear(batch);
        const int32_t idx = llama_batch_ext_add_token(batch, 0, t);
        llama_batch_ext_set_pos(batch, idx, &pos);
        llama_batch_ext_set_output_logits(batch, 0, true);
        pos++;
        if (llama_process(ctx, LLAMA_PROCESS_TYPE_DECODE, batch) != 0) break;
    }
    llama_batch_ext_free(batch);
    llama_sampler_free(smpl);
    llama_free(ctx);
    return out;
}

}  // namespace fitai
