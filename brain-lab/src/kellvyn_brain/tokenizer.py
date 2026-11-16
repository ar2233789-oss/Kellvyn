"""Tokenizers trained/defined for the Kellvyn project.

ByteTokenizer is the transparent baseline. ByteBPETokenizer learns merge rules
from the supplied corpus and never loads a third-party vocabulary.
"""

from __future__ import annotations

from collections import Counter
from dataclasses import dataclass
from typing import Any


@dataclass(frozen=True)
class ByteTokenizer:
    """Maps UTF-8 bytes to stable IDs; useful for unit tests and baseline checks."""

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
            elif token_id not in (self.PAD_ID, self.BOS_ID, self.EOS_ID):
                raise ValueError(f"token ID outside vocabulary: {token_id}")
        return bytes(byte_values).decode("utf-8", errors="replace")


class ByteBPETokenizer:
    """A small byte-level BPE tokenizer whose merge table is learned from text.

    This intentionally straightforward implementation favors auditability over
    speed. Its O(number_of_merges * corpus_length) training/encoding cost makes
    it a research prototype, not the production tokenizer for a huge corpus.
    """

    FORMAT = "kellvyn-byte-bpe-v1"
    PAD_ID = 0
    BOS_ID = 1
    EOS_ID = 2
    BYTE_OFFSET = 3
    BASE_VOCAB_SIZE = 259

    def __init__(self, merges: list[tuple[int, int]]) -> None:
        self.merges = tuple((int(left), int(right)) for left, right in merges)
        pieces: list[bytes] = [b"", b"", b""]
        pieces.extend(bytes([value]) for value in range(256))
        for left, right in self.merges:
            if left < self.BYTE_OFFSET or right < self.BYTE_OFFSET:
                raise ValueError("BPE merges cannot contain special token IDs")
            if left >= len(pieces) or right >= len(pieces):
                raise ValueError("BPE merge references a token that was not created yet")
            pieces.append(pieces[left] + pieces[right])
        self._pieces = tuple(pieces)

    @property
    def vocab_size(self) -> int:
        return len(self._pieces)

    @property
    def merge_count(self) -> int:
        return len(self.merges)

    @classmethod
    def train(
        cls,
        text: str,
        *,
        num_merges: int = 64,
        min_frequency: int = 2,
    ) -> "ByteBPETokenizer":
        if not isinstance(text, str):
            raise TypeError("text must be str")
        if num_merges < 0 or min_frequency < 2:
            raise ValueError("num_merges must be non-negative and min_frequency at least 2")

        symbols = [byte + cls.BYTE_OFFSET for byte in text.encode("utf-8")]
        merges: list[tuple[int, int]] = []

        for _ in range(num_merges):
            pair_counts = Counter(zip(symbols, symbols[1:]))
            if not pair_counts:
                break
            pair, frequency = min(
                pair_counts.items(),
                key=lambda item: (-item[1], item[0][0], item[0][1]),
            )
            if frequency < min_frequency:
                break

            left, right = pair
            merged_id = cls.BASE_VOCAB_SIZE + len(merges)
            merges.append((left, right))

            merged: list[int] = []
            index = 0
            while index < len(symbols):
                if (
                    index + 1 < len(symbols)
                    and symbols[index] == left
                    and symbols[index + 1] == right
                ):
                    merged.append(merged_id)
                    index += 2
                else:
                    merged.append(symbols[index])
                    index += 1
            symbols = merged

        return cls(merges)

    def encode(
        self,
        text: str,
        *,
        add_bos: bool = True,
        add_eos: bool = True,
    ) -> list[int]:
        if not isinstance(text, str):
            raise TypeError("text must be str")
        symbols = [byte + self.BYTE_OFFSET for byte in text.encode("utf-8")]
        for merge_index, (left, right) in enumerate(self.merges):
            merged_id = self.BASE_VOCAB_SIZE + merge_index
            merged: list[int] = []
            index = 0
            while index < len(symbols):
                if (
                    index + 1 < len(symbols)
                    and symbols[index] == left
                    and symbols[index + 1] == right
                ):
                    merged.append(merged_id)
                    index += 2
                else:
                    merged.append(symbols[index])
                    index += 1
            symbols = merged

        if add_bos:
            symbols.insert(0, self.BOS_ID)
        if add_eos:
            symbols.append(self.EOS_ID)
        return symbols

    def decode(self, token_ids: list[int]) -> str:
        output = bytearray()
        for token_id in token_ids:
            if not isinstance(token_id, int):
                raise TypeError("token IDs must be integers")
            if token_id in (self.PAD_ID, self.BOS_ID, self.EOS_ID):
                continue
            if token_id < self.BYTE_OFFSET or token_id >= self.vocab_size:
                raise ValueError(f"token ID outside vocabulary: {token_id}")
            output.extend(self._pieces[token_id])
        return bytes(output).decode("utf-8", errors="replace")

    def to_dict(self) -> dict[str, Any]:
        return {
            "format": self.FORMAT,
            "base_vocab_size": self.BASE_VOCAB_SIZE,
            "merges": [list(pair) for pair in self.merges],
        }

    @classmethod
    def from_dict(cls, payload: dict[str, Any]) -> "ByteBPETokenizer":
        if payload.get("format") != cls.FORMAT:
            raise ValueError("unsupported Kellvyn tokenizer format")
        if payload.get("base_vocab_size") != cls.BASE_VOCAB_SIZE:
            raise ValueError("tokenizer base vocabulary does not match this implementation")
        raw_merges = payload.get("merges")
        if not isinstance(raw_merges, list):
            raise ValueError("tokenizer merges must be a list")
        merges: list[tuple[int, int]] = []
        for pair in raw_merges:
            if (
                not isinstance(pair, list)
                or len(pair) != 2
                or not all(isinstance(value, int) for value in pair)
            ):
                raise ValueError("each BPE merge must contain exactly two integer IDs")
            merges.append((pair[0], pair[1]))
        return cls(merges)
