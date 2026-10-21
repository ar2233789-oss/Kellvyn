package com.kellvyn.assistant;

/** Pure validation rules shared by the downloader and native unit tests. */
final class NativeModelPolicy {
    static final long MODEL_BYTES = 2_008_432_640L;
    static final String MODEL_SHA256 = "a53a59001894c58e6bdb5b9b227709f91a2e3e556baa7d85acf9c55402ba5cf5";
    static final String MODEL_URL = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/41c5569623a7c189d6eec3e5225260c58cedbce7/gemma-4-E2B-it-gpu.litertlm";

    private NativeModelPolicy() {}

    static boolean isVerified(long bytes, String marker) {
        return bytes == MODEL_BYTES && MODEL_SHA256.equals(marker.trim());
    }

    static boolean canAppend(long offset, int response, String contentRange) {
        if (offset <= 0 || offset >= MODEL_BYTES || response != 206 || contentRange == null) return false;
        String expected = "bytes " + offset + "-" + (MODEL_BYTES - 1) + "/" + MODEL_BYTES;
        return expected.equals(contentRange.trim());
    }
}