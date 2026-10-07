#include "engine.h"
#include <algorithm>
#include <memory>
#include <stdexcept>
#include <thread>
#include <vector>

std::atomic<bool> cancelled{false};

OfflineEngine::OfflineEngine(const std::string &path) {
    llama_backend_init();
    auto params = llama_model_default_params();
    params.n_gpu_layers = 0;
    params.use_mmap = true;
    model_ = llama_model_load_from_file(path.c_str(), params);
    if (!model_) throw std::runtime_error("Offline model could not load. Restart the app and free some RAM.");
}
OfflineEngine::~OfflineEngine() { if (model_) llama_model_free(model_); }

std::string OfflineEngine::generate(const std::string &prompt,
        const std::function<void(const std::string &)> &progress) {
    const auto *vocab = llama_model_get_vocab(model_);
    int count = -llama_tokenize(vocab, prompt.data(), static_cast<int>(prompt.size()), nullptr, 0, true, true);
    if (count <= 0 || count > 1536) throw std::runtime_error("Message is too long. Clear chat or send a shorter message.");
    std::vector<llama_token> tokens(count);
    if (llama_tokenize(vocab, prompt.data(), static_cast<int>(prompt.size()), tokens.data(), count, true, true) < 0)
        throw std::runtime_error("Could not read the message.");
    auto cp = llama_context_default_params();
    cp.n_ctx = 2048;
    cp.n_batch = 256;
    cp.n_ubatch = 128;
    cp.n_threads = std::max(1u, std::min(4u, std::thread::hardware_concurrency()));
    cp.n_threads_batch = cp.n_threads;
    std::unique_ptr<llama_context, decltype(&llama_free)> ctx(llama_init_from_model(model_, cp), llama_free);
    if (!ctx) throw std::runtime_error("Not enough free memory to start AI. Close other apps and retry.");
    std::unique_ptr<llama_batch_ext, decltype(&llama_batch_ext_free)> batch(llama_batch_ext_init(ctx.get()), llama_batch_ext_free);
    if (!batch) throw std::runtime_error("Could not allocate AI batch.");
    auto setBatch = [&](const llama_token *data, int n, int start) {
        llama_batch_ext_clear(batch.get());
        for (int i = 0; i < n; ++i) {
            int idx = llama_batch_ext_add_token(batch.get(), 0, data[i]);
            llama_pos pos = start + i;
            llama_batch_ext_set_pos(batch.get(), idx, &pos);
        }
        llama_batch_ext_set_output_logits(batch.get(), n - 1, true);
    };
    for (int start = 0; start < count; start += 256) {
        if (cancelled) throw std::runtime_error("Reply stopped.");
        const int size = std::min(256, count - start);
        setBatch(tokens.data() + start, size, start);
        if (llama_process(ctx.get(), LLAMA_PROCESS_TYPE_DECODE, batch.get()) != 0)
            throw std::runtime_error("AI could not process the message.");
    }
    auto sp = llama_sampler_chain_default_params();
    std::unique_ptr<llama_sampler, decltype(&llama_sampler_free)> sampler(llama_sampler_chain_init(sp), llama_sampler_free);
    llama_sampler_chain_add(sampler.get(), llama_sampler_init_top_k(40));
    llama_sampler_chain_add(sampler.get(), llama_sampler_init_top_p(0.9f, 1));
    llama_sampler_chain_add(sampler.get(), llama_sampler_init_temp(0.6f));
    llama_sampler_chain_add(sampler.get(), llama_sampler_init_dist(42));
    std::string result;
    for (int i = 0; i < 192; ++i) {
        if (cancelled) break;
        llama_token token = llama_sampler_sample(sampler.get(), ctx.get(), -1);
        if (llama_vocab_is_eog(vocab, token)) break;
        std::vector<char> piece(256);
        int length = llama_token_to_piece(vocab, token, piece.data(), static_cast<int>(piece.size()), 0, false);
        if (length < 0) {
            piece.resize(-length);
            length = llama_token_to_piece(vocab, token, piece.data(), static_cast<int>(piece.size()), 0, false);
        }
        if (length < 0) throw std::runtime_error("Could not decode AI text.");
        result.append(piece.data(), length);
        if (i % 8 == 0) progress(result);
        setBatch(&token, 1, count + i);
        if (llama_process(ctx.get(), LLAMA_PROCESS_TYPE_DECODE, batch.get()) != 0)
            throw std::runtime_error("AI reply failed.");
    }
    progress(result);
    return result;
}
