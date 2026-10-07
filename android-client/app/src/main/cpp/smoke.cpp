#include "engine.h"
#include <iostream>
#include <stdexcept>
int main(int argc, char **argv) {
    if (argc != 2) return 2;
    try {
        OfflineEngine engine(argv[1]);
        auto english = engine.generate("<|im_start|>system\nYou are a helpful assistant. Give a short answer.<|im_end|>\n<|im_start|>user\nWhat is two plus two?<|im_end|>\n<|im_start|>assistant\n", [](const std::string &) {});
        std::cout << "ENGLISH: " << english << '\n';
        if (english.find('4') == std::string::npos && english.find("four") == std::string::npos) throw std::runtime_error("Arithmetic smoke test failed");
        auto hindi = engine.generate("<|im_start|>system\nYou are JARVIS. Reply briefly in Hindi.<|im_end|>\n<|im_start|>user\nनमस्ते, आप कैसे हैं?<|im_end|>\n<|im_start|>assistant\n", [](const std::string &) {});
        std::cout << "HINDI: " << hindi << '\n';
        if (hindi.empty()) throw std::runtime_error("Empty Hindi reply");
        return 0;
    } catch (const std::exception &e) { std::cerr << e.what() << '\n'; return 1; }
}
