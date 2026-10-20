// Static 3D calibration view of the native rain-streak geometry.
// Diagnostic only; the application effect remains native Compose Canvas.
scene_width = 221;
scene_height = 420;
layer_count = 3;
drop_spacing = 13;
drop_length = 11;
drop_slant = 3.6;
frame_time = 0.0; // Override with -D frame_time=0.25 etc.
$fn = 8;
module streak(x, y, z, scale = 1) {
    translate([x, y, z])
        hull() {
            sphere(r = 0.32 * scale);
            translate([-drop_slant * scale, drop_length * scale, 0])
                sphere(r = 0.22 * scale);
        }
}
for (layer = [0 : layer_count - 1]) {
    for (i = [0 : floor(scene_width / drop_spacing)]) {
        speed = 0.65 + ((i * 17) % 55) / 100;
        initial_phase = ((i * 37 + layer * 19) % 97) / 97;
        raw_phase = frame_time / 2.2 * speed + initial_phase;
        phase = raw_phase - floor(raw_phase);
        x = (i * drop_spacing) % scene_width;
        y = phase * (scene_height + drop_length + 10) - drop_length;
        z = layer * 6;
        scale = 0.75 + ((i + layer) % 3) * 0.18;
        color([0.84, 0.90, 0.93, 0.58 - layer * 0.12])
            streak(x - drop_slant, y, z, scale);
    }
}
