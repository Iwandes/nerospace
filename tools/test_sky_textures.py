import unittest
from pathlib import Path

import numpy as np
from PIL import Image

import gen_sky_textures as sky


class SkyTextureTest(unittest.TestCase):
    def test_shipped_nebulae_keep_vivid_distinct_palettes(self):
        root = Path(__file__).resolve().parent.parent / sky.ASSET_DIR / 'sky'
        colors = []
        for name in ['nebula_a', 'nebula_b']:
            with Image.open(root / (name + '.png')) as image:
                pixels = np.asarray(image)
            visible = pixels[..., :3][pixels[..., 3] > 40].astype(np.float32) / 255
            saturation = (visible.max(axis=1) - visible.min(axis=1)) / np.maximum(visible.max(axis=1), 0.001)
            self.assertGreater(np.median(saturation), 0.4, name)
            colors.append(visible.mean(axis=0))
        self.assertGreater(np.linalg.norm(colors[0] - colors[1]), 0.3)

    def test_noise_is_continuous_and_reproducible(self):
        a = sky.noise_space('volume-test')
        b = sky.noise_space('volume-test')
        x = np.linspace(-2, 2, 64, dtype=np.float32)
        np.testing.assert_array_equal(a(x, x, x), b(x, x, x))
        self.assertLess(np.max(np.abs(a(x, x, x) - a(x + 1e-4, x, x))), 0.001)
        self.assertTrue(np.isfinite(a(x, x, x)).all())

    def test_invalid_octaves_fail_explicitly(self):
        for octaves in [0, -1, 9]:
            with self.assertRaises(ValueError):
                sky.volume_fbm(sky.noise_space('invalid'), 0, 0, 0, octaves)

    def test_band_tiles_without_a_seam_or_a_hard_top(self):
        band = np.asarray(sky.galaxy_band('seam', 128, 64, '#DDCCAA', '#667788', '#101020', '#FFFFFF'))
        np.testing.assert_array_equal(band[:, 0], band[:, -1])
        self.assertEqual(0, band[0, :, 3].max())
        self.assertEqual(0, band[-1, :, 3].max())
        self.assertGreater(np.count_nonzero(band[..., 3]), 128)

    def test_every_star_sprite_is_visible_even_on_an_even_sized_grid(self):
        for sheet in [sky.star_sheet(), sky.star_sheet_natural()]:
            alpha = np.asarray(sheet)[..., 3]
            for y in range(4):
                for x in range(4):
                    self.assertGreater(alpha[y * 16:(y + 1) * 16, x * 16:(x + 1) * 16].max(), 20)

    def test_planet_disc_and_lights_share_the_land_mask(self):
        rgb, alpha, land = sky.earth_parts('earth', 96, baked_light=False)
        lights = np.asarray(sky.city_lights('lights', 96, 'earth'))[..., 3]
        self.assertTrue(np.isfinite(rgb).all())
        self.assertEqual(0, alpha[0, 0])
        self.assertGreater(alpha[48, 48], 0.99)
        self.assertEqual(0, lights[land == 0].max())

    def test_filaments_fade_before_the_quad_boundary(self):
        image = np.asarray(sky.nebula_patch('edge', 96, ['#BB8866', '#556677', '#DDDDBB']))
        self.assertEqual(0, image[0, :, 3].max())
        self.assertEqual(0, image[:, -1, 3].max())
        self.assertGreater(image[..., 3].max(), 40)


if __name__ == '__main__':
    unittest.main()
