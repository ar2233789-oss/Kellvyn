"""Transparent byte-level tokenizer used only for the first pipeline proof.

This is intentionally simple. Production training must replace it with a BPE or
Unigram tokenizer trained on a licensed, documented Kellvyn corpus.
"""

from __future__ import annotations

from dataclasses import dataclass


@dataclass(frozen=True)
class ByteTokenizer:
    """Maps UTF-8 bytes to stable IDs without importing a pretrained vocabulary."""

    PAD_ID: int = 0
    BOS_ID: int = 1
    EOS_ID: int = 2
    BYTE_OFFSET: int = 3
    VOCAB_SIZE: int = 259

    def encode(
        self,
        text: str,
        *,
        add_bos: bool = True,
        add_eos: bool = True,
    ) -> list[int]:
        if not isinstance(text, str):
            raise TypeError("text must be str")
        ids = [byte + self.BYTE_OFFSET for byte in text.encode("utf-8")]
        if add_bos:
            ids.insert(0, self.BOS_ID)
        if add_eos:
            ids.append(self.EOS_ID)
        return ids

    def decode(self, token_ids: list[int], *, skip_special: bool = True) -> str:
        byte_values: list[int] = []
        for token_id in token_ids:
            if not isinstance(token_id, int):
                raise TypeError("token IDs must be integers")
            if self.BYTE_OFFSET <= token_id < self.VOCAB_SIZE:
                byte_values.append(token_id - self.BYTE_OFFSET)
            elif not skip_special and token_id in (self.PAD_ID, self.BOS_ID, self.EOS_ID):
                continue
            elif token_id not in (self.PAD_ID, self.BOS_ID, self.EOS_ID):
                raise ValueError(f"token ID outside vocabulary: {token_id}")
        return bytes(byte_values).decode("utf-8", errors="replace")
