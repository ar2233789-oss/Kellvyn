"""Tokenizers trained/defined for the Kellvyn project.

ByteTokenizer is a transparent byte baseline. ByteBPETokenizer learns all merge
rules from the supplied training text; no external vocabulary is loaded.
"""

from __future__ import annotations

import heapq
from collections import defaultdict
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
    """Byte-level BPE with an incrementally maintained linked sequence and heap.

    Training updates only pair counts adjacent to changed positions, rather than
    rescanning the complete corpus for every merge. Encoding uses merge ranks in
    a priority queue. The implementation is still Python and stores per-position
    state; it is a research baseline, not yet the large-corpus Rust/C++ trainer.
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
        seen_pairs: set[tuple[int, int]] = set()
        for left, right in self.merges:
            if left < self.BYTE_OFFSET or right < self.BYTE_OFFSET:
                raise ValueError("BPE merges cannot contain special token IDs")
            if left >= len(pieces) or right >= len(pieces):
                raise ValueError("BPE merge references a token that was not created yet")
            pair = (left, right)
            if pair in seen_pairs:
                raise ValueError("BPE merge table contains a duplicate pair")
            seen_pairs.add(pair)
            pieces.append(pieces[left] + pieces[right])
        self._pieces = tuple(pieces)
        self._merge_ranks = {pair: rank for rank, pair in enumerate(self.merges)}

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
        length = len(symbols)
        if length < 2 or num_merges == 0:
            return cls([])

        previous = [index - 1 for index in range(length)]
        following = [index + 1 for index in range(length)]
        following[-1] = -1
        alive = [True] * length

        pair_counts: dict[tuple[int, int], int] = defaultdict(int)
        pair_positions: dict[tuple[int, int], set[int]] = defaultdict(set)
        pair_versions: dict[tuple[int, int], int] = defaultdict(int)
        heap: list[tuple[int, int, int, int]] = []

        def refresh(pair: tuple[int, int]) -> None:
            pair_versions[pair] += 1
            count = pair_counts[pair]
            if count > 0:
                heapq.heappush(
                    heap,
                    (-count, pair[0], pair[1], pair_versions[pair]),
                )

        def add_pair(left_index: int) -> None:
            if left_index < 0 or not alive[left_index]:
                return
            right_index = following[left_index]
            if right_index < 0 or not alive[right_index]:
                return
            pair = (symbols[left_index], symbols[right_index])
            pair_positions[pair].add(left_index)
            pair_counts[pair] += 1
            refresh(pair)

        def remove_pair(left_index: int) -> None:
            if left_index < 0 or not alive[left_index]:
                return
            right_index = following[left_index]
            if right_index < 0 or not alive[right_index]:
                return
            pair = (symbols[left_index], symbols[right_index])
            positions = pair_positions[pair]
            if left_index not in positions:
                raise RuntimeError("BPE pair index bookkeeping is inconsistent")
            positions.remove(left_index)
            pair_counts[pair] -= 1
            refresh(pair)

        for index in range(length - 1):
            add_pair(index)

        merges: list[tuple[int, int]] = []
        while heap and len(merges) < num_merges:
            while heap:
                negative_count, left, right, version = heapq.heappop(heap)
                pair = (left, right)
                if (
                    pair_versions[pair] == version
                    and pair_counts[pair] == -negative_count
                ):
                    break
            else:
                break

            frequency = -negative_count
            if frequency < min_frequency:
                break

            merge_id = cls.BASE_VOCAB_SIZE + len(merges)
            merges.append(pair)
            # Snapshot positions because merges mutate the live occurrence sets.
            candidates = sorted(pair_positions[pair])

            # Left-to-right processing gives the same non-overlapping rule as
            # the reference BPE definition (e.g. a a a -> [aa, a]).
            for left_index in candidates:
                if not alive[left_index]:
                    continue
                right_index = following[left_index]
                if right_index < 0 or not alive[right_index]:
                    continue
                if (
                    symbols[left_index] != left
                    or symbols[right_index] != right
                ):
                    continue

                before = previous[left_index]
                after = following[right_index]

                remove_pair(before)
                remove_pair(left_index)
                remove_pair(right_index)

                symbols[left_index] = merge_id
                following[left_index] = after
                if after >= 0:
                    previous[after] = left_index
                alive[right_index] = False
                previous[right_index] = -1
                following[right_index] = -1

                add_pair(before)
                add_pair(left_index)

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
        length = len(symbols)

        if length == 0 or not self.merges:
            if add_bos:
                symbols.insert(0, self.BOS_ID)
            if add_eos:
                symbols.append(self.EOS_ID)
            return symbols

        previous = [index - 1 for index in range(length)]
        following = [index + 1 for index in range(length)]
        following[-1] = -1
        alive = [True] * length
        heap: list[tuple[int, int]] = []

        def push_if_mergeable(left_index: int) -> None:
            if left_index < 0 or not alive[left_index]:
                return
            right_index = following[left_index]
            if right_index < 0 or not alive[right_index]:
                return
            rank = self._merge_ranks.get((symbols[left_index], symbols[right_index]))
            if rank is not None:
                heapq.heappush(heap, (rank, left_index))

        for index in range(length - 1):
            push_if_mergeable(index)

        while heap:
            rank, left_index = heapq.heappop(heap)
            if not alive[left_index]:
                continue
            right_index = following[left_index]
            if right_index < 0 or not alive[right_index]:
                continue
            if (symbols[left_index], symbols[right_index]) != self.merges[rank]:
                continue

            before = previous[left_index]
            after = following[right_index]
            symbols[left_index] = self.BASE_VOCAB_SIZE + rank
            following[left_index] = after
            if after >= 0:
                previous[after] = left_index
            alive[right_index] = False
            previous[right_index] = -1
            following[right_index] = -1

            push_if_mergeable(before)
            push_if_mergeable(left_index)

        encoded: list[int] = []
        index = 0
        while index >= 0:
            if alive[index]:
                encoded.append(symbols[index])
            index = following[index]

        if add_bos:
            encoded.insert(0, self.BOS_ID)
        if add_eos:
            encoded.append(self.EOS_ID)
        return encoded

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
