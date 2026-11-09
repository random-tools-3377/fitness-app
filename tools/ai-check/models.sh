# The versions of llama.cpp and of the models the app's AI uses, in one place: read by the AI
# check and by the build of the AI model files. Every file is pinned to a revision and a SHA-256.
LLAMA_TAG=b11900
LLAMA_COMMIT=74b104c9ba5717116b7e65e0cd35397778653304

# Text: describing a meal, pasting a recipe, the weekly summary. Qwen3 1.7B, Apache-2.0.
TEXT_REPO=ggml-org/Qwen3-1.7B-GGUF
TEXT_REV=daeb8e2d528a760970442092f6bf1e55c3b659eb
TEXT_FILE=Qwen3-1.7B-Q4_K_M.gguf
TEXT_SHA=d2387ca2dbfee2ffabce7120d3770dadca0b293052bc2f0e138fdc940d9bc7b5

# Speech: saying a meal. Qwen3-ASR 0.6B, Apache-2.0.
SPEECH_REPO=ggml-org/Qwen3-ASR-0.6B-GGUF
SPEECH_REV=928ab958557df9aa2ef1c93e0e83c7ad0933fae2
SPEECH_FILE=Qwen3-ASR-0.6B-Q8_0.gguf
SPEECH_SHA=bca259818b50ca7c4c05e9bdb35a5dc04fa039653a6d6f3f0f331f96f6aa1971
SPEECH_MMPROJ=mmproj-Qwen3-ASR-0.6B-Q8_0.gguf
SPEECH_MMPROJ_SHA=41a342b5e4c514e968cb756de6cd1b7be39eff43c44c57a2ef5fc6522e36603d

# Plate photos. SmolVLM 500M Instruct, Apache-2.0.
PLATE_REPO=ggml-org/SmolVLM-500M-Instruct-GGUF
PLATE_REV=72e986006ef53e37cdd3f6d4241c90b0f01df376
PLATE_FILE=SmolVLM-500M-Instruct-Q8_0.gguf
PLATE_SHA=9d4612de6a42214499e301494a3ecc2be0abdd9de44e663bda63f1152fad1bf4
PLATE_MMPROJ=mmproj-SmolVLM-500M-Instruct-Q8_0.gguf
PLATE_MMPROJ_SHA=d1eb8b6b23979205fdf63703ed10f788131a3f812c7b1f72e0119d5d81295150
