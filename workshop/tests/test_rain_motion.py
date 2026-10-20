import unittest

from workshop.rain_motion import drop_phase, drop_position, drop_speed


class RainMotionTests(unittest.TestCase):
    def test_speed_range_matches_native_limits(self):
        speeds = [drop_speed(i) for i in range(1000)]
        self.assertGreaterEqual(min(speeds), 0.65)
        self.assertLessEqual(max(speeds), 1.19)

    def test_phases_are_distributed_instead_of_starting_together(self):
        phases = [drop_phase(i, 0.0) for i in range(72)]
        self.assertGreater(len(set(round(x, 3) for x in phases)), 60)

    def test_horizontal_lanes_are_stable_between_frames(self):
        for i in range(120):
            a = drop_position(i, 0.0, 393, 873, drop_count=120)
            b = drop_position(i, 1 / 60, 393, 873, drop_count=120)
            self.assertAlmostEqual(a.x_px, b.x_px, places=8)

    def test_non_wrap_motion_is_smooth_at_60_fps(self):
        height = 1000.0
        max_step = 0.0
        wrap_count = 0
        for drop in range(120):
            previous = drop_position(drop, 0.0, 440, height, drop_count=120)
            for frame in range(1, 301):
                current = drop_position(drop, frame / 60.0, 440, height, drop_count=120)
                delta = abs(current.y_px - previous.y_px)
                # Crossing the lower edge and re-entering at the top is intentional.
                if delta > height / 2:
                    wrap_count += 1
                else:
                    max_step = max(max_step, delta)
                    self.assertLess(delta, 10.0, f"drop={drop}, frame={frame}, step={delta}")
                previous = current
        self.assertGreater(wrap_count, 0)
        self.assertLess(max_step, 10.0)

    def test_positions_are_deterministic(self):
        a = drop_position(17, 1.25, 393, 873, density=2.75, drop_count=100)
        b = drop_position(17, 1.25, 393, 873, density=2.75, drop_count=100)
        self.assertEqual(a, b)


if __name__ == "__main__":
    unittest.main()
