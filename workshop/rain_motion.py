"""Diagnostic mirror of the rain-drop placement formula in MainActivity.kt.

This module is a mathematical calibration aid, not the Android renderer. The native
source audit locks the constants mirrored here so the model cannot silently drift.
"""
from __future__ import annotations

from dataclasses import dataclass


@dataclass(frozen=True)
class RainDrop:
    index: int
    x_px: float
    y_px: float
    speed: float
    phase: float


def drop_speed(index: int) -> float:
    """Mirror 0.65 + ((drop * 17) % 55) / 100.0 from the native renderer."""
    return 0.65 + ((index * 17) % 55) / 100.0


def drop_phase(index: int, elapsed_seconds: float) -> float:
    """Return normalized phase in [0, 1), matching the Kotlin frame-clock formula."""
    initial_phase = ((index * 37) % 97) / 97.0
    return (elapsed_seconds / 2.2 * drop_speed(index) + initial_phase) % 1.0


def drop_position(
    index: int,
    elapsed_seconds: float,
    width_px: float,
    height_px: float,
    density: float = 1.0,
    drop_count: int = 100,
) -> RainDrop:
    """Compute one drop's position in physical pixels for diagnostics/tests."""
    if index < 0:
        raise ValueError("index must be non-negative")
    if elapsed_seconds < 0:
        raise ValueError("elapsed_seconds must be non-negative")
    if width_px <= 0 or height_px <= 0 or density <= 0 or drop_count <= 0:
        raise ValueError("viewport, density, and drop_count must be positive")

    drop_length = 11.0 * density
    travel_height = height_px + drop_length + 10.0 * density
    lane = (index * 53) % drop_count
    x = (lane / drop_count) * width_px
    phase = drop_phase(index, elapsed_seconds)
    y = phase * travel_height - drop_length
    return RainDrop(index, x, y, drop_speed(index), phase)
